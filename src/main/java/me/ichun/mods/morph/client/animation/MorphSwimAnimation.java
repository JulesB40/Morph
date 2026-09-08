package me.ichun.mods.morph.client.animation;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import java.io.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

/** Bounded rotation-only reader for the shipped Blockbench Bedrock animation export. */
public final class MorphSwimAnimation {
    private static final Identifier FILE = Identifier.fromNamespaceAndPath("morph", "animations/swim.animation.json");
    private static final Set<String> BONES = Set.of("head", "body", "right_hind_leg", "left_hind_leg", "right_front_leg", "left_front_leg");
    private record Frame(float time, float x, float y, float z) {}
    record Clip(float length, Map<String, List<Frame>> tracks) {}
    private record Clips(Clip normal, Clip fast) {}
    private static volatile Clips clips;
    private static final Clip EMPTY = new Clip(1, Map.of());
    private MorphSwimAnimation() {}

    public static void reload(ResourceManager manager) {
        me.ichun.mods.morph.client.transition.MorphTransitionRenderer.clearFailures();
        Clips loaded = new Clips(EMPTY, EMPTY);
        try {
            var resource = manager.getResource(FILE);
            if (resource.isPresent()) {
                try (var input = resource.get().open()) {
                    byte[] bytes = input.readNBytes(65537);
                    if (bytes.length > 65536) throw new IOException("Animation exceeds 64 KiB");
                    String json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                    loaded = new Clips(parse(json), parse(json, "animation.morph.quadruped_fast_swim"));
                }
            }
        } catch (RuntimeException | IOException failure) {
            LogUtils.getLogger().warn("Invalid Morph swimming animation; using vanilla poses", failure);
        }
        clips = loaded;
    }

    static Clip parse(String json) { return parse(json, "animation.morph.quadruped_swim"); }
    static Clip parse(String json, String name) {
        JsonObject animation = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("animations")
            .getAsJsonObject(name);
        float length = animation.get("animation_length").getAsFloat();
        if (!Float.isFinite(length) || length <= 0 || length > 60) throw new IllegalArgumentException("Invalid clip length");
        Map<String, List<Frame>> tracks = new HashMap<>();
        JsonObject bones = animation.getAsJsonObject("bones");
        for (String bone : BONES) {
            if (!bones.has(bone)) continue;
            var channel = bones.getAsJsonObject(bone).get("rotation");
            if (channel == null) continue;
            List<Frame> frames = new ArrayList<>();
            if (channel.isJsonArray()) frames.add(frame(0, channel));
            else {
                JsonObject keys = channel.getAsJsonObject();
                if (keys.size() > 128) throw new IllegalArgumentException("Too many keyframes");
                for (var entry : keys.entrySet()) {
                    float time = Float.parseFloat(entry.getKey());
                    if (!Float.isFinite(time) || time < 0 || time > length) throw new IllegalArgumentException("Invalid time");
                    JsonElement value = entry.getValue();
                    if (value.isJsonObject()) value = value.getAsJsonObject().get("post");
                    frames.add(frame(time, value));
                }
            }
            frames.sort(Comparator.comparingDouble(Frame::time));
            if (!frames.isEmpty()) tracks.put(bone, List.copyOf(frames));
        }
        return new Clip(length, Map.copyOf(tracks));
    }
    private static Frame frame(float time, JsonElement value) {
        JsonArray angles = value.getAsJsonArray();
        if (angles.size() != 3) throw new IllegalArgumentException("Expected XYZ rotation");
        float[] rotation = new float[3];
        for (int i = 0; i < 3; i++) {
            rotation[i] = angles.get(i).getAsFloat();
            if (!Float.isFinite(rotation[i]) || Math.abs(rotation[i]) > 360) throw new IllegalArgumentException("Invalid angle");
            rotation[i] *= (float) (Math.PI / 180);
        }
        return new Frame(time, rotation[0], rotation[1], rotation[2]);
    }
    static float loopTime(float age, float length) {
        return (age / 20F % length + length) % length;
    }
    public static void apply(ModelPart root, float age, float blend) { apply(root, age, blend, false); }
    public static void apply(ModelPart root, float age, float blend, boolean fast) {
        if (!(blend > 0) || !Float.isFinite(age)) return;
        if (clips == null) reload(Minecraft.getInstance().getResourceManager());
        Clips loaded = clips;
        Clip current = fast ? loaded.fast : loaded.normal;
        float time = loopTime(age, current.length);
        for (var entry : current.tracks.entrySet()) {
            if (!root.hasChild(entry.getKey())) continue;
            ModelPart part = root.getChild(entry.getKey());
            List<Frame> frames = entry.getValue();
            Frame from = frames.getFirst(), to = from;
            for (Frame next : frames) {
                to = next;
                if (next.time >= time) break;
                from = next;
            }
            float alpha = to.time == from.time ? 0 : Math.clamp((time - from.time) / (to.time - from.time), 0, 1);
            // Export channels are offsets from the native model's rest rotation.
            var rest = part.getInitialPose();
            part.xRot += (rest.xRot() + from.x + (to.x - from.x) * alpha - part.xRot) * blend;
            part.yRot += (rest.yRot() + from.y + (to.y - from.y) * alpha - part.yRot) * blend;
            part.zRot += (rest.zRot() + from.z + (to.z - from.z) * alpha - part.zRot) * blend;
        }
    }
}
