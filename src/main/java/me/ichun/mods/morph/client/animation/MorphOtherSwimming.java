package me.ichun.mods.morph.client.animation;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;

/** Anatomy-preserving paddles for vanilla families outside the shared biped/quadruped bases. */
public final class MorphOtherSwimming {
    private static final Map<Model<?>, Function<String, ModelPart>> LOOKUPS = new WeakHashMap<>();
    private static final String[] LIMBS = {"right_hind_leg", "left_hind_leg", "right_front_leg", "left_front_leg",
            "right_middle_hind_leg", "left_middle_hind_leg", "right_middle_front_leg", "left_middle_front_leg",
            "right_leg", "left_leg", "right_hind_foot", "left_hind_foot", "right_front_foot", "left_front_foot",
            "right_mid_leg", "left_mid_leg"};
    private static final Set<String> AQUATIC = Set.of("axolotl", "cod", "salmon", "pufferfish", "tropical_fish",
            "dolphin", "squid", "glow_squid", "guardian", "elder_guardian", "turtle", "frog", "tadpole",
            "nautilus", "zombie_nautilus");

    private MorphOtherSwimming() {}

    public static boolean hasNativeAquaticPose(LivingEntityRenderState state) {
        return state.entityType != null && AQUATIC.contains(BuiltInRegistries.ENTITY_TYPE.getKey(state.entityType).getPath());
    }

    public static void apply(Model<?> model, Object renderState) {
        if (!(renderState instanceof LivingEntityRenderState state) || !(state instanceof MorphSwimState swim)
                || swim.morph$swimBlend() <= 0 || hasNativeAquaticPose(state)
                || model instanceof HumanoidModel<?> || model instanceof QuadrupedModel<?>
                || model instanceof VillagerModel || model instanceof IllagerModel<?>) return;
        float blend = Math.clamp(swim.morph$swimBlend(), 0F, 1F);
        boolean fast = swim.morph$fastSwimming();
        var lookup = LOOKUPS.computeIfAbsent(model, key -> key.root().createPartLookup());
        for (int i = 0; i < LIMBS.length; i++) {
            ModelPart limb = lookup.apply(LIMBS[i]);
            if (limb == null) continue;
            float offset = paddle(state.ageInTicks, i, fast);
            limb.xRot += (limb.getInitialPose().xRot() + offset - limb.xRot) * blend;
        }
        // Tails remain in their own rig: a small lateral stroke never moves or reorients
        // the animal's skeleton, shell, body, wings, or attached equipment.
        ModelPart tail = lookup.apply("tail");
        if (tail != null) {
            float target = tail.getInitialPose().yRot() + paddle(state.ageInTicks, 0, fast) * 0.2F;
            tail.yRot += (target - tail.yRot) * blend;
        }
    }

    static float paddle(float age, int limb, boolean fast) {
        // Opposite legs alternate; middle pairs are phase-shifted for eight-legged rigs.
        float phase = (limb % 2 == 0 ? 0F : (float) Math.PI) + (limb / 2) * 0.75F;
        return (float) Math.sin(age * (fast ? 0.72F : 0.38F) + phase) * (fast ? 0.95F : 0.55F);
    }
}
