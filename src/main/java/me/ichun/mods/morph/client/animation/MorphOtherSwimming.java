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
import net.minecraft.client.renderer.entity.state.*;
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
        if (state instanceof BeeRenderState) {
            // Native bees join each left/right leg pair into one mesh.
            String[] pairs = {"front_legs", "middle_legs", "back_legs"};
            for (int pair = 0; pair < pairs.length; pair++) {
                ModelPart legs = lookup.apply(pairs[pair]);
                if (legs != null) legs.xRot += (legs.getInitialPose().xRot()
                    + paddle(state.ageInTicks, pair * 2, fast) - legs.xRot) * blend;
            }
        }
        if (canPaddleArms(state)) {
            paddleArms(lookup, state.ageInTicks, blend, fast);
        }
        if (state instanceof ChickenRenderState || state instanceof ParrotRenderState) {
            // These birds hinge their wings on Z. Bat, bee, allay, vex and phantom
            // keep their own native wing cycles and hinge axes.
            paddleBirdWings(lookup, state.ageInTicks, blend, fast);
        }
        // Tails remain in their own rig: a small lateral stroke never moves or reorients
        // the animal's skeleton, shell, body, wings, or attached equipment.
        ModelPart tail = lookup.apply("tail");
        if (tail != null) {
            float target = tail.getInitialPose().yRot() + paddle(state.ageInTicks, 0, fast) * 0.2F;
            tail.yRot += (target - tail.yRot) * blend;
        }
    }

    static boolean canPaddleArms(LivingEntityRenderState state) {
        if (state instanceof ArmedEntityRenderState armed
                && (armed.attackTime > 0 || !idleArm(armed.leftArmPose) || !idleArm(armed.rightArmPose))) return false;
        if (state instanceof AllayRenderState allay
                && (allay.isDancing || allay.isSpinning || allay.holdingAnimationProgress > 0)) return false;
        if (state instanceof CopperGolemRenderState copper && (copper.interactionGetItem.isStarted()
                || copper.interactionGetNoItem.isStarted() || copper.interactionDropItem.isStarted()
                || copper.interactionDropNoItem.isStarted() || !copper.leftHandItemState.isEmpty()
                || !copper.rightHandItemState.isEmpty())) return false;
        if (state instanceof VexRenderState vex && vex.isCharging) return false;
        if (state instanceof IronGolemRenderState golem && (golem.attackTicksRemaining > 0 || golem.offerFlowerTick > 0)) return false;
        if (state instanceof CreakingRenderState creaking && (creaking.attackAnimationState.isStarted()
                || creaking.invulnerabilityAnimationState.isStarted() || creaking.deathAnimationState.isStarted())) return false;
        if (state instanceof WardenRenderState warden && (warden.attackAnimationState.isStarted()
                || warden.sonicBoomAnimationState.isStarted() || warden.roarAnimationState.isStarted()
                || warden.sniffAnimationState.isStarted() || warden.emergeAnimationState.isStarted()
                || warden.diggingAnimationState.isStarted())) return false;
        return true;
    }

    private static boolean idleArm(HumanoidModel.ArmPose pose) {
        return pose == HumanoidModel.ArmPose.EMPTY || pose == HumanoidModel.ArmPose.ITEM;
    }

    static void paddleArms(Function<String, ModelPart> lookup, float age, float blend, boolean fast) {
        for (int side = 0; side < 2; side++) {
            ModelPart arm = lookup.apply(side == 0 ? "right_arm" : "left_arm");
            if (arm != null) arm.xRot += (arm.getInitialPose().xRot() - 0.8F + paddle(age, side, fast) - arm.xRot) * blend;
        }
    }

    static void paddleBirdWings(Function<String, ModelPart> lookup, float age, float blend, boolean fast) {
        float stroke = 0.35F + Math.abs(paddle(age, 0, fast));
        for (int side = 0; side < 2; side++) {
            ModelPart wing = lookup.apply(side == 0 ? "right_wing" : "left_wing");
            if (wing != null) wing.zRot += (wing.getInitialPose().zRot() + (side == 0 ? stroke : -stroke) - wing.zRot) * blend;
        }
    }

    static float paddle(float age, int limb, boolean fast) {
        // Opposite legs alternate; middle pairs are phase-shifted for eight-legged rigs.
        float phase = (limb % 2 == 0 ? 0F : (float) Math.PI) + (limb / 2) * 0.75F;
        return (float) Math.sin(age * (fast ? 0.72F : 0.38F) + phase) * (fast ? 0.95F : 0.55F);
    }
}
