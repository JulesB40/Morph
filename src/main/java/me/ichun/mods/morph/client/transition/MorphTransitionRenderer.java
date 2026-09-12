package me.ichun.mods.morph.client.transition;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Captures posed vanilla meshes before submitting immutable, deforming morph geometry. */
public final class MorphTransitionRenderer {
    private static final Identifier SKIN = Identifier.fromNamespaceAndPath("morph", "textures/skin/morphskin.png");
    private static int renderDepth;
    private static boolean loggedFailure;
    private static final Set<Object> UNSUPPORTED = new HashSet<>();
    private static final Set<Object> BROKEN_FALLBACKS = new HashSet<>();
    private static final ThreadLocal<CaptureCollector> CAPTURE = ThreadLocal.withInitial(CaptureCollector::new);

    private MorphTransitionRenderer() {}

    public record Frame(EntityRenderState from, EntityRenderState to, float progress) {}

    /** Loader hooks use this guard when the source/destination is an ordinary avatar. */
    public static boolean isRendering() { return renderDepth != 0; }

    public static boolean render(Frame frame, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        if (invisibleNonLiving(frame.from()) || invisibleNonLiving(frame.to())) return false;
        renderDepth++;
        try {
            float progress = Math.clamp(frame.progress(), 0.0F, 1.0F);
            if (!Float.isFinite(progress) || progress >= 1.0F) {
                submit(frame.to(), poses, collector, camera);
                return true;
            }
            if (progress <= 0.0F) {
                submit(frame.from(), poses, collector, camera);
                return true;
            }
            if (UNSUPPORTED.contains(failureKey(frame.from())) || UNSUPPORTED.contains(failureKey(frame.to()))) {
                return safeFallback(frame.to(), poses, collector, camera);
            }
            // Respect vanilla invisibility rather than exposing a player through the transition.
            if (hiddenLiving(frame.from()) || hiddenLiving(frame.to())) {
                // Vanilla still handles glowing outlines and visible equipment on invisible players.
                submit(frame.to(), poses, collector, camera);
                return true;
            }
            float deformation = ease(Math.clamp((progress - 0.125F) / 0.75F, 0.0F, 1.0F));
            float alpha = progress < 0.125F ? ease(progress / 0.125F)
                : progress > 0.875F ? 1.0F - ease((progress - 0.875F) / 0.125F) : 1.0F;
            Mesh previous = deformation >= 1.0F ? null : capture(frame.from(), poses, camera);
            Mesh next = deformation <= 0.0F ? previous : capture(frame.to(), poses, camera);
            if (previous == null) previous = next;
            if (previous.size == 0 || next.size == 0) {
                submit(progress < 0.5F ? frame.from() : frame.to(), poses, collector, camera);
                return true;
            }
            // The skin covers a normal body only at the two ends of the animation.
            if (progress < 0.125F) submit(frame.from(), poses, collector, camera);
            else if (progress > 0.875F) submit(frame.to(), poses, collector, camera);
            if (alpha <= 0.0F) return true;
            PackedMesh vertices = interpolate(previous, next, deformation, alpha < 1.0F);
            int color = (Math.round(alpha * 255.0F) << 24) | 0xFFFFFF;
            int light = frame.to().lightCoords;
            // Vertices already include the renderer's complete pose. An identity submission
            // prevents applying the entity/camera transform a second time in the deferred pass.
            collector.submitCustomGeometry(new PoseStack(), RenderTypes.entityTranslucent(SKIN), (ignored, buffer) -> {
                for (int i = 0; i < vertices.data.length; i += 8) {
                    float[] data = vertices.data;
                    buffer.addVertex(data[i], data[i + 1], data[i + 2], color, data[i + 3], data[i + 4],
                        OverlayTexture.NO_OVERLAY, light, data[i + 5], data[i + 6], data[i + 7]);
                }
            });
            return true;
        } catch (RuntimeException failure) {
            if (!loggedFailure) {
                loggedFailure = true;
                LogUtils.getLogger().warn("Morph transition mesh unavailable; using destination appearance", failure);
            }
            return safeFallback(frame.to(), poses, collector, camera);
        } finally {
            renderDepth--;
        }
    }

    /** True means a replacement was submitted; false leaves the original avatar visible. */
    public static boolean renderSnapshot(EntityRenderState state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        if (invisibleNonLiving(state)) return false;
        renderDepth++;
        try { return safeFallback(state, poses, collector, camera); }
        finally { renderDepth--; }
    }

    private static boolean hiddenLiving(EntityRenderState state) {
        return state instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState living
                && living.isInvisible && living.isInvisibleToPlayer;
    }

    private static boolean invisibleNonLiving(EntityRenderState state) {
        return state.isInvisible && !(state instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState);
    }

    static float ease(float value) { return (1.0F - (float) Math.cos(Math.PI * value)) * 0.5F; }

    private static void submit(EntityRenderState state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        // Work on an independent stack so even an unsupported renderer cannot unbalance its caller.
        PoseStack isolated = new PoseStack();
        isolated.last().set(poses.last());
        Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(state).submit(state, isolated, collector, camera);
    }

    private static Object failureKey(EntityRenderState state) {
        return state.entityType != null ? state.entityType : state.getClass();
    }

    /** Resource reloads can repair an unsupported model or an earlier bad texture. */
    public static void clearFailures() {
        UNSUPPORTED.clear();
        BROKEN_FALLBACKS.clear();
        loggedFailure = false;
    }

    private static boolean safeFallback(EntityRenderState state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        if (BROKEN_FALLBACKS.contains(failureKey(state))) return false;
        try { submit(state, poses, collector, camera); return true; }
        catch (RuntimeException failure) {
            // A broken third-party renderer must not turn the fallback into a crash loop.
            if (BROKEN_FALLBACKS.size() < 128) BROKEN_FALLBACKS.add(failureKey(state));
            LogUtils.getLogger().warn("Morph cannot submit renderer for {}; keeping player appearance", failureKey(state), failure);
            return false;
        }
    }

    private static Mesh capture(EntityRenderState state, PoseStack poses, CameraRenderState camera) {
        Mesh mesh = new Mesh();
        CaptureCollector capture = CAPTURE.get();
        Mesh previous = capture.mesh;
        capture.mesh = mesh;
        try {
            submit(state, poses, capture.proxy, camera);
            if ((mesh.size & 3) != 0) throw new IllegalArgumentException("Incomplete morph quads");
            return mesh;
        } catch (RuntimeException failure) {
            // Quarantine only the failing form, not the other (often human) endpoint.
            if (UNSUPPORTED.size() < 128) UNSUPPORTED.add(failureKey(state));
            throw failure;
        } finally { capture.mesh = previous; }
    }

    private static final class CaptureCollector {
        private Mesh mesh;
        private final SubmitNodeCollector proxy = (SubmitNodeCollector) Proxy.newProxyInstance(
            SubmitNodeCollector.class.getClassLoader(), new Class<?>[] { SubmitNodeCollector.class }, (proxy, method, args) -> {
                if (method.getName().equals("order")) return proxy;
                if (method.getName().equals("submitModel") && args != null && args.length == 10) {
                    captureModel(args, mesh);
                    return null;
                }
                if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
                if (method.getName().equals("toString")) return "Morph mesh collector";
                if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                if (method.getName().equals("equals")) return proxy == args[0];
                return null;
            });
    }

    @SuppressWarnings("unchecked")
    private static void captureModel(Object[] args, Mesh mesh) {
        Model<Object> model = (Model<Object>) args[0];
        PoseStack modelPoses = (PoseStack) args[2];
        // Armor dye, foil and trim passes reuse the same posed mesh. Capturing those
        // texture-only passes repeatedly would darken the fade and inflate its topology.
        if (mesh.duplicatePass(model, args[1], modelPoses.last().pose(), modelPoses.last().normal())) return;
        model.setupAnim(args[1]);
        me.ichun.mods.morph.client.animation.MorphOtherSwimming.apply(model, args[1]);
        model.renderToBuffer((PoseStack) args[2], mesh, (Integer) args[4], (Integer) args[5], -1);
    }

    /** The deferred draw owns this buffer; it never retains a mutable model or capture buffer. */
    static final class PackedMesh {
        private final float[] data;
        private PackedMesh(float[] data) { this.data = data; }
        int size() { return data.length / 8; }
        float component(int vertex, int component) { return data[vertex * 8 + component]; }
    }

    static PackedMesh interpolate(Mesh from, Mesh to, float progress, boolean expandSkin) {
        int count = Math.max(from.size, to.size);
        // Centers are used only by unmatched quads; equal-sized meshes need neither scan.
        float[] fromCenter = from.size < to.size ? from.center() : null;
        float[] toCenter = to.size < from.size ? to.center() : null;
        float[] result = new float[count * 8];
        int shared = Math.min(from.size, to.size) * 8;
        // A contiguous loop allows the JVM to vectorize interpolation and hoist bounds checks.
        float[] fromData = from.data, toData = to.data;
        for (int i = 0; i < shared; i++) result[i] = fromData[i] + (toData[i] - fromData[i]) * progress;
        if (from.size < to.size) {
            for (int i = shared; i < result.length; i++) {
                float origin = fromCenter[i & 7];
                result[i] = origin + (toData[i] - origin) * progress;
            }
        } else if (to.size < from.size) {
            for (int i = shared; i < result.length; i++)
                result[i] = fromData[i] + (toCenter[i & 7] - fromData[i]) * progress;
        }
        for (int offset = 0; offset < result.length; offset += 8) {
            float nx = result[offset + 5], ny = result[offset + 6], nz = result[offset + 7];
            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length > 0.00001F) { nx /= length; ny /= length; nz /= length; }
            else ny = 1.0F;
            if (expandSkin) {
                result[offset] += nx * 0.001F;
                result[offset + 1] += ny * 0.001F;
                result[offset + 2] += nz * 0.001F;
            }
            result[offset + 5] = nx; result[offset + 6] = ny; result[offset + 7] = nz;
        }
        return new PackedMesh(result);
    }

    static final class Mesh implements VertexConsumer {
        static final int MAX_VERTICES = 32768;
        private float[] data = new float[256 * 8];
        private int size;
        private int current = -1;
        private Object lastModel, lastState;
        private Matrix4f lastPose;
        private Matrix3f lastNormal;

        boolean duplicatePass(Object model, Object state, Matrix4f pose, Matrix3f normal) {
            if (lastModel == model && lastState == state && lastPose != null
                && lastPose.equals(pose) && lastNormal.equals(normal)) return true;
            lastModel = model; lastState = state;
            if (lastPose == null) {
                lastPose = new Matrix4f(pose); lastNormal = new Matrix3f(normal);
            } else { lastPose.set(pose); lastNormal.set(normal); }
            return false;
        }

        float[] center() {
            float[] center = new float[8];
            for (int i = 0; i < size * 8; i += 8) {
                center[0] += data[i]; center[1] += data[i + 1]; center[2] += data[i + 2];
            }
            if (size > 0) for (int i = 0; i < 3; i++) center[i] /= size;
            center[6] = 1.0F;
            return center;
        }

        private static void finite(float value) {
            if (!Float.isFinite(value) || Math.abs(value) > 1.0E7F)
                throw new IllegalArgumentException("Invalid morph vertex");
        }
        @Override public VertexConsumer addVertex(float x, float y, float z) {
            finite(x); finite(y); finite(z);
            if (size == MAX_VERTICES) throw new IllegalArgumentException("Morph geometry budget exceeded");
            current = size++ * 8;
            if (current == data.length) data = Arrays.copyOf(data, Math.min(data.length * 2, MAX_VERTICES * 8));
            data[current] = x; data[current + 1] = y; data[current + 2] = z; data[current + 6] = 1;
            return this;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setColor(int color) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { finite(u); finite(v); data[current + 3] = u; data[current + 4] = v; return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) {
            finite(x); finite(y); finite(z);
            data[current + 5] = x; data[current + 6] = y; data[current + 7] = z; return this;
        }
        @Override public VertexConsumer setLineWidth(float width) { return this; }
    }
}
