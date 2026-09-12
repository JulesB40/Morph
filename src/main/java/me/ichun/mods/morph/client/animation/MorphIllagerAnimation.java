package me.ichun.mods.morph.client.animation;

import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.monster.illager.AbstractIllager.IllagerArmPose;

/** Native illager weapon poses assume a right-hand weapon; player hands can differ. */
public final class MorphIllagerAnimation {
    private MorphIllagerAnimation() {}

    public static void applyWeaponHand(IllagerRenderState state, ModelPart rightArm, ModelPart leftArm, ModelPart head) {
        HumanoidArm active = state.isUsingItem && state.useItemHand == InteractionHand.OFF_HAND
            ? state.mainArm.getOpposite() : state.mainArm;
        if (active != HumanoidArm.LEFT) return;
        if (state.armPose == IllagerArmPose.CROSSBOW_CHARGE) {
            AnimationUtils.animateCrossbowCharge(rightArm, leftArm, state.maxCrossbowChargeDuration, state.ticksUsingItem, false);
        } else if (state.armPose == IllagerArmPose.CROSSBOW_HOLD) {
            AnimationUtils.animateCrossbowHold(rightArm, leftArm, head, false);
        } else if (state.armPose == IllagerArmPose.BOW_AND_ARROW) {
            leftArm.yRot = 0.1F + head.yRot;
            leftArm.xRot = (float) (-Math.PI / 2) + head.xRot;
            leftArm.zRot = 0F;
            rightArm.xRot = -0.9424779F + head.xRot;
            rightArm.yRot = head.yRot + 0.4F;
            rightArm.zRot = (float) (-Math.PI / 2);
        }
    }
}
