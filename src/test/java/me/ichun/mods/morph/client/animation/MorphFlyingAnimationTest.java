package me.ichun.mods.morph.client.animation;

import net.minecraft.client.model.ambient.BatModel;
import net.minecraft.client.renderer.entity.state.BatRenderState;
import net.minecraft.client.renderer.entity.state.BeeRenderState;
import net.minecraft.client.renderer.entity.state.ChickenRenderState;
import net.minecraft.client.renderer.entity.state.ParrotRenderState;
import net.minecraft.client.model.animal.chicken.AdultChickenModel;
import net.minecraft.client.model.animal.parrot.ParrotModel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MorphFlyingAnimationTest {
    @Test
    void freshBatSnapshotsAdvanceNativeWingAnimation() {
        var root = BatModel.createBodyLayer().bakeRoot();
        var model = new BatModel(root);
        var first = new BatRenderState();
        first.isResting = true;
        first.restAnimationState.start(0);
        first.ageInTicks = 1;
        MorphFlyingAnimation.apply(first, false);
        assertFalse(first.isResting);
        assertFalse(first.restAnimationState.isStarted());
        assertTrue(first.flyAnimationState.isStarted());
        model.setupAnim(first);
        float firstYaw = root.getChild("body").getChild("right_wing").yRot;
        var next = new BatRenderState();
        next.ageInTicks = 3;
        MorphFlyingAnimation.apply(next, false);
        model.setupAnim(next);
        assertNotEquals(firstYaw, root.getChild("body").getChild("right_wing").yRot);
    }

    @Test
    void beeOnlyUsesGroundPoseWhenPlayerIsStationaryOnGround() {
        var bee = new BeeRenderState();
        MorphFlyingAnimation.apply(bee, true);
        assertTrue(bee.isOnGround);
        MorphFlyingAnimation.apply(bee, false);
        assertFalse(bee.isOnGround);
    }

    @Test void nativeChickenWingsAdvanceInAirAndRestWhileWalkingOnGround() {
        var root = AdultChickenModel.createBodyLayer().bakeRoot();
        var model = new AdultChickenModel(root);
        var first = new ChickenRenderState();
        first.ageInTicks = 1F;
        MorphFlyingAnimation.apply(first, false, false);
        model.setupAnim(first);
        float firstWing = root.getChild("right_wing").zRot;
        var next = new ChickenRenderState();
        next.ageInTicks = 3F;
        MorphFlyingAnimation.apply(next, false, false);
        model.setupAnim(next);
        assertNotEquals(firstWing, root.getChild("right_wing").zRot);
        assertEquals(-root.getChild("right_wing").zRot, root.getChild("left_wing").zRot);
        MorphFlyingAnimation.apply(next, true, false);
        model.setupAnim(next);
        assertEquals(0F, root.getChild("right_wing").zRot);
    }

    @Test void nativeParrotWingsAdvanceWithoutReplacingSittingOrPartyPoses() {
        var root = ParrotModel.createBodyLayer().bakeRoot();
        var model = new ParrotModel(root);
        var first = new ParrotRenderState();
        first.ageInTicks = 1F;
        MorphFlyingAnimation.apply(first, false, false);
        model.setupAnim(first);
        float firstWing = root.getChild("right_wing").zRot;
        var next = new ParrotRenderState();
        next.ageInTicks = 3F;
        MorphFlyingAnimation.apply(next, false, false);
        model.setupAnim(next);
        assertNotEquals(firstWing, root.getChild("right_wing").zRot);
        MorphFlyingAnimation.apply(next, true, false);
        assertEquals(ParrotModel.Pose.STANDING, next.pose);
        assertEquals(0F, next.flapAngle);
        for (var pose : new ParrotModel.Pose[] {ParrotModel.Pose.SITTING, ParrotModel.Pose.PARTY}) {
            next.pose = pose;
            MorphFlyingAnimation.apply(next, true, true);
            assertEquals(pose, next.pose);
        }
    }
}
