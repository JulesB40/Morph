package me.ichun.mods.morph.lab;

import me.ichun.mods.morph.client.MorphRenderSnapshots;
import me.ichun.mods.morph.client.animation.MorphOtherSwimming;
import me.ichun.mods.morph.client.animation.MorphSwimState;
import me.ichun.mods.morph.client.animation.MorphWitherHeads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.animal.sniffer.SnifferModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.monster.wither.WitherBossModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.SnifferRenderState;
import net.minecraft.client.renderer.entity.state.WitherRenderState;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Named component oracles, not full-frame visual assertions. */
final class LabComponentProbes {
    private LabComponentProbes() {}

    static Map<String, Object> run(Minecraft client, String name) {
        Map<String, Object> result = new LinkedHashMap<>(switch (name) {
            case "wither-heads" -> wither();
            case "sniffer-middle-legs" -> sniffer(client);
            case "dragon-renderer" -> dragon(client);
            default -> throw new IllegalArgumentException("Unknown component probe: " + name);
        });
        result.put("probe", name);
        result.put("scope", "component; not a framebuffer or gameplay oracle");
        return result;
    }

    private static Map<String, Object> wither() {
        var model = new WitherBossModel(WitherBossModel.createBodyLayer(CubeDeformation.NONE).bakeRoot());
        var lookup = model.root().createPartLookup();
        // body yaw, relative look yaw, pitch, expected world-space side-head yaw.
        float[][] cases = {{30, 45, -20, 75}, {-150, -40, 35, -190}, {0, 0, 0, 0}};
        List<Map<String, Object>> samples = new ArrayList<>();
        boolean passed = true;
        boolean baselineDistinguishes = false;
        for (float[] test : cases) {
            var state = new WitherRenderState();
            state.bodyRot = test[0];
            state.yRot = test[1];
            state.xRot = test[2];
            model.setupAnim(state);
            float untreatedYaw = lookup.apply("right_head").yRot;
            MorphWitherHeads.apply(state);
            model.setupAnim(state);
            float expectedYaw = (float) Math.toRadians(test[1]);
            float expectedPitch = (float) Math.toRadians(test[2]);
            baselineDistinguishes |= Math.abs(untreatedYaw - expectedYaw) > .01F;
            List<Map<String, Object>> heads = new ArrayList<>();
            for (String head : List.of("right_head", "left_head")) {
                var part = lookup.apply(head);
                passed &= close(part.yRot, expectedYaw) && close(part.xRot, expectedPitch);
                heads.add(Map.of("part", head, "yaw", part.yRot, "pitch", part.xRot));
            }
            for (int index = 0; index < state.yHeadRots.length; index++) {
                passed &= close(state.yHeadRots[index], test[3]) && close(state.xHeadRots[index], test[2]);
            }
            samples.add(Map.of("bodyYaw", test[0], "relativeYaw", test[1], "pitch", test[2],
                    "expectedWorldYaw", test[3], "expectedModelYaw", expectedYaw,
                    "untreatedModelYaw", untreatedYaw, "heads", heads));
        }
        return Map.of("passed", passed && baselineDistinguishes, "baselineDistinguishes", baselineDistinguishes, "samples", samples);
    }

    private static boolean close(float actual, float expected) {
        return Float.isFinite(actual) && Math.abs(actual - expected) < .0001F;
    }

    private static Map<String, Object> dragon(Minecraft client) {
        var entity = EntityTypes.ENDER_DRAGON.create(client.level, EntitySpawnReason.LOAD);
        if (entity == null) throw new IllegalStateException("Native dragon creation failed");
        entity.setId(client.player.getId());
        var renderer = client.getEntityRenderDispatcher().getRenderer(entity);
        var nativeState = renderer.createRenderState(entity, .5F);
        var playerState = (AvatarRenderState) client.getEntityRenderDispatcher().getRenderer(client.player)
                .createRenderState(client.player, .5F);
        Object morphState = MorphRenderSnapshots.extract(client.player, playerState, "minecraft:ender_dragon");
        return Map.of("passed", morphState != null && nativeState.getClass().isInstance(morphState),
                "nativeRenderer", renderer.getClass().getName(), "nativeState", nativeState.getClass().getName(),
                "morphState", morphState == null ? "null" : morphState.getClass().getName());
    }

    private static Map<String, Object> sniffer(Minecraft client) {
        var entity = EntityTypes.SNIFFER.create(client.level, EntitySpawnReason.LOAD);
        if (entity == null) throw new IllegalStateException("Native Sniffer creation failed");
        var renderer = client.getEntityRenderDispatcher().getRenderer(entity);
        if (!(renderer instanceof LivingEntityRenderer<?, ?, ?> living) || !(living.getModel() instanceof SnifferModel model)) {
            throw new IllegalStateException("Expected native SnifferModel");
        }
        String[] names = {"right_front_leg", "left_front_leg", "right_mid_leg", "left_mid_leg", "right_hind_leg", "left_hind_leg"};
        float[][] angles = new float[4][names.length];
        var lookup = model.root().createPartLookup();
        List<Map<String, Object>> samples = new ArrayList<>();
        for (int frame = 0; frame < angles.length; frame++) {
            var state = new SnifferRenderState();
            state.entityType = EntityTypes.SNIFFER;
            state.ageInTicks = frame * 7F;
            state.isInWater = true;
            ((MorphSwimState) state).morph$setSwimBlend(1F);
            model.setupAnim(state);
            MorphOtherSwimming.apply(model, state);
            Map<String, Object> posed = new LinkedHashMap<>();
            for (int part = 0; part < names.length; part++) {
                var limb = lookup.apply(names[part]);
                if (limb == null) throw new IllegalStateException("Missing native model part " + names[part]);
                angles[frame][part] = limb.xRot;
                posed.put(names[part], limb.xRot);
            }
            samples.add(Map.of("age", state.ageInTicks, "xRot", posed));
        }
        List<String> staticParts = new ArrayList<>();
        for (int part = 0; part < names.length; part++) {
            float min = angles[0][part], max = min;
            for (float[] sample : angles) { min = Math.min(min, sample[part]); max = Math.max(max, sample[part]); }
            if (!Float.isFinite(min) || !Float.isFinite(max) || max - min < .01F) staticParts.add(names[part]);
        }
        return Map.of("passed", staticParts.isEmpty(), "staticOrNonfiniteParts", staticParts, "samples", samples);
    }
}
