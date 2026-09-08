package me.ichun.mods.morph.client.transition;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** Captures posed vanilla meshes before submitting immutable, deforming morph geometry. */
public final class MorphTransitionRenderer {
    private static final Identifier SKIN = Identifier.fromNamespaceAndPath("morph", "textures/skin/morphskin.png");
    private static int renderDepth;
    private static boolean loggedFailure;

    private MorphTransitionRenderer() {}

    public record Frame(LivingEntityRenderState from, LivingEntityRenderState to, float progress) {}

    /** Loader hooks use this guard when the source/destination is an ordinary avatar. */
    public static boolean isRendering() { return renderDepth != 0; }

    public static void render(Frame frame, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        renderDepth++;
        try {
            float progress = Math.clamp(frame.progress(), 0.0F, 1.0F);
            if (progress >= 1.0F) {
                submit(frame.to(), poses, collector, camera);
                return;
            }
            // Respect vanilla invisibility rather than exposing a player through the transition.
            if ((frame.from().isInvisible && frame.from().isInvisibleToPlayer)
                || (frame.to().isInvisible && frame.to().isInvisibleToPlayer)) {
                // Vanilla still handles glowing outlines and visible equipment on invisible players.
                submit(frame.to(), poses, collector, camera);
                return;
            }
            float deformation = ease(Math.clamp((progress - 0.125F) / 0.75F, 0.0F, 1.0F));
            float alpha = progress < 0.125F ? ease(progress / 0.125F)
                : progress > 0.875F ? 1.0F - ease((progress - 0.875F) / 0.125F) : 1.0F;
            Mesh previous = capture(frame.from(), poses, camera);
            Mesh next = capture(frame.to(), poses, camera);
            if (previous.vertices.isEmpty() || next.vertices.isEmpty()) {
                submit(progress < 0.5F ? frame.from() : frame.to(), poses, collector, camera);
                return;
            }
            // The skin covers a normal body only at the two ends of the animation.
            if (progress < 0.125F) submit(frame.from(), poses, collector, camera);
            else if (progress > 0.875F) submit(frame.to(), poses, collector, camera);
            if (alpha <= 0.0F) return;
            List<float[]> vertices = interpolate(previous, next, deformation, alpha < 1.0F);
            int color = (Math.round(alpha * 255.0F) << 24) | 0xFFFFFF;
            int light = frame.to().lightCoords;
            // Vertices already include the renderer's complete pose. An identity submission
            // prevents applying the entity/camera transform a second time in the deferred pass.
            collector.submitCustomGeometry(new PoseStack(), RenderTypes.entityTranslucent(SKIN), (ignored, buffer) -> {
                for (float[] vertex : vertices) {
                    buffer.addVertex(vertex[0], vertex[1], vertex[2], color, vertex[3], vertex[4],
                        OverlayTexture.NO_OVERLAY, light, vertex[5], vertex[6], vertex[7]);
                }
            });
        } catch (RuntimeException failure) {
            if (!loggedFailure) {
                loggedFailure = true;
                LogUtils.getLogger().warn("Morph transition mesh unavailable; using destination appearance", failure);
            }
            submit(frame.to(), poses, collector, camera);
        } finally {
            renderDepth--;
        }
    }

    static float ease(float value) { return (1.0F - (float) Math.cos(Math.PI * value)) * 0.5F; }

    private static void submit(LivingEntityRenderState state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        // Work on an independent stack so even an unsupported renderer cannot unbalance its caller.
        PoseStack isolated = new PoseStack();
        isolated.last().set(poses.last());
        Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(state).submit(state, isolated, collector, camera);
    }

    private static Mesh capture(LivingEntityRenderState state, PoseStack poses, CameraRenderState camera) {
        Mesh mesh = new Mesh();
        // Runtime proxies implement the actual loader's collector interface, including its
        // extension methods. Vanilla default overloads funnel into the full model method.
        SubmitNodeCollector capture = (SubmitNodeCollector) Proxy.newProxyInstance(
            SubmitNodeCollector.class.getClassLoader(), new Class<?>[] { SubmitNodeCollector.class }, (proxy, method, args) -> {
                if (method.getName().equals("order")) return proxy;
                if (method.getName().equals("submitModel") && args.length == 10) {
                    captureModel(args, mesh);
                    return null;
                }
                if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
                // Items, text, shadows and unrelated custom geometry are not part of the body.
                if (method.getName().equals("toString")) return "Morph mesh collector";
                if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                if (method.getName().equals("equals")) return proxy == args[0];
                return null;
            });
        submit(state, poses, capture, camera);
        return mesh;
    }

    @SuppressWarnings("unchecked")
    private static void captureModel(Object[] args, Mesh mesh) {
        Model<Object> model = (Model<Object>) args[0];
        model.setupAnim(args[1]);
        model.renderToBuffer((PoseStack) args[2], mesh, (Integer) args[4], (Integer) args[5], -1);
    }

    static List<float[]> interpolate(Mesh from, Mesh to, float progress, boolean expandSkin) {
        int count = Math.max(from.vertices.size(), to.vertices.size());
        float[] fromCenter = from.center();
        float[] toCenter = to.center();
        List<float[]> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            // Complete missing quads shrink into / grow out of the opposite body center.
            float[] a = i < from.vertices.size() ? from.vertices.get(i) : fromCenter;
            float[] b = i < to.vertices.size() ? to.vertices.get(i) : toCenter;
            float[] vertex = new float[8];
            for (int j = 0; j < 8; j++) vertex[j] = a[j] + (b[j] - a[j]) * progress;
            float length = (float) Math.sqrt(vertex[5] * vertex[5] + vertex[6] * vertex[6] + vertex[7] * vertex[7]);
            if (length > 0.00001F) {
                vertex[5] /= length; vertex[6] /= length; vertex[7] /= length;
            } else vertex[6] = 1.0F;
            if (expandSkin) {
                // A tiny shell offset avoids z-fighting against the normally textured body.
                vertex[0] += vertex[5] * 0.001F;
                vertex[1] += vertex[6] * 0.001F;
                vertex[2] += vertex[7] * 0.001F;
            }
            result.add(vertex);
        }
        return List.copyOf(result);
    }

    static final class Mesh implements VertexConsumer {
        private final List<float[]> vertices = new ArrayList<>();
        private float[] current;

        float[] center() {
            float[] center = new float[8];
            for (float[] vertex : vertices) {
                center[0] += vertex[0]; center[1] += vertex[1]; center[2] += vertex[2];
            }
            for (int i = 0; i < 3; i++) center[i] /= vertices.size();
            center[6] = 1.0F;
            return center;
        }

        @Override public VertexConsumer addVertex(float x, float y, float z) {
            current = new float[] {x, y, z, 0, 0, 0, 1, 0};
            vertices.add(current);
            return this;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setColor(int color) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { current[3] = u; current[4] = v; return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) {
            current[5] = x; current[6] = y; current[7] = z; return this;
        }
        @Override public VertexConsumer setLineWidth(float width) { return this; }
    }
}
