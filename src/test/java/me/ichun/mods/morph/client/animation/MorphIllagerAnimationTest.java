package me.ichun.mods.morph.client.animation;

import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.monster.illager.AbstractIllager.IllagerArmPose;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MorphIllagerAnimationTest {
    @Test void nativeWeaponRigMirrorsForLeftHandAndKeepsRightHandUntouched() {
        var root = IllagerModel.createBodyLayer().bakeRoot();
        var model = new IllagerModel<IllagerRenderState>(root);
        var right = root.getChild("right_arm");
        var left = root.getChild("left_arm");
        var head = root.getChild("head");
        for (var pose : new IllagerArmPose[] {IllagerArmPose.BOW_AND_ARROW, IllagerArmPose.CROSSBOW_CHARGE, IllagerArmPose.CROSSBOW_HOLD}) {
            var state = new IllagerRenderState();
            state.armPose = pose;
            state.maxCrossbowChargeDuration = 20;
            state.ticksUsingItem = 10;
            model.setupAnim(state);
            float rightX = right.xRot, rightY = right.yRot, rightZ = right.zRot;
            float leftX = left.xRot, leftY = left.yRot, leftZ = left.zRot;
            MorphIllagerAnimation.applyWeaponHand(state, right, left, head);
            assertEquals(rightX, right.xRot);
            assertEquals(leftY, left.yRot);
            state.mainArm = HumanoidArm.LEFT;
            MorphIllagerAnimation.applyWeaponHand(state, right, left, head);
            assertEquals(rightX, left.xRot, 0.00001F);
            assertEquals(-rightY, left.yRot, 0.00001F);
            assertEquals(-rightZ, left.zRot, 0.00001F);
            assertEquals(leftX, right.xRot, 0.00001F);
            assertEquals(-leftY, right.yRot, 0.00001F);
            assertEquals(-leftZ, right.zRot, 0.00001F);
            state.mainArm = HumanoidArm.RIGHT;
            state.isUsingItem = true;
            state.useItemHand = InteractionHand.OFF_HAND;
            model.setupAnim(state);
            MorphIllagerAnimation.applyWeaponHand(state, right, left, head);
            assertEquals(rightX, left.xRot, 0.00001F);
            assertEquals(-rightY, left.yRot, 0.00001F);
        }
    }
}
