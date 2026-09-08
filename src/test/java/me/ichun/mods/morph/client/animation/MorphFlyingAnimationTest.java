package me.ichun.mods.morph.client.animation;

import net.minecraft.client.model.ambient.BatModel;
import net.minecraft.client.renderer.entity.state.BatRenderState;
import net.minecraft.client.renderer.entity.state.BeeRenderState;
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
}
