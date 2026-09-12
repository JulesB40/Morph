package me.ichun.mods.morph.client.animation;

import net.minecraft.client.model.monster.breeze.BreezeModel;
import net.minecraft.client.model.monster.creaking.CreakingModel;
import net.minecraft.client.renderer.entity.state.BreezeRenderState;
import net.minecraft.client.renderer.entity.state.CreakingRenderState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MorphNativeMotionTest {
    @Test void freshBreezeSnapshotsAdvanceNativeIdleWithoutInventingActions() {
        var root = BreezeModel.createBodyLayer().bakeRoot();
        var model = new BreezeModel(root);
        var first = new BreezeRenderState();
        first.ageInTicks = 1;
        model.setupAnim(first);
        float stopped = model.rods().yRot;
        MorphNativeMotion.apply(first);
        model.setupAnim(first);
        float firstRotation = model.rods().yRot;
        assertNotEquals(stopped, firstRotation);
        var next = new BreezeRenderState();
        next.ageInTicks = 3;
        MorphNativeMotion.apply(next);
        model.setupAnim(next);
        assertNotEquals(firstRotation, model.rods().yRot);
        assertFalse(next.shoot.isStarted());
        assertFalse(next.slide.isStarted());
        assertFalse(next.slideBack.isStarted());
        assertFalse(next.inhale.isStarted());
        assertFalse(next.longJump.isStarted());
        next.shoot.start(2);
        MorphNativeMotion.apply(next);
        assertTrue(next.shoot.isStarted());
    }

    @Test void nativeCreakingLegsFollowPlayerWalkAndStopWhenStationaryOrDead() {
        var root = CreakingModel.createBodyLayer().bakeRoot();
        var model = new CreakingModel(root);
        var leg = root.createPartLookup().apply("right_leg");
        assertNotNull(leg);
        var state = new CreakingRenderState();
        state.walkAnimationPos = 2;
        state.walkAnimationSpeed = 1;
        model.setupAnim(state);
        float nativeLocked = leg.xRot;
        MorphNativeMotion.apply(state);
        model.setupAnim(state);
        assertTrue(state.canMove);
        assertNotEquals(nativeLocked, leg.xRot);
        assertFalse(state.attackAnimationState.isStarted());
        assertFalse(state.invulnerabilityAnimationState.isStarted());
        assertFalse(state.deathAnimationState.isStarted());
        state.walkAnimationSpeed = 0;
        MorphNativeMotion.apply(state);
        assertFalse(state.canMove);
        state.walkAnimationSpeed = 1;
        state.deathTime = 1;
        MorphNativeMotion.apply(state);
        assertFalse(state.canMove);
        state.attackAnimationState.start(0);
        MorphNativeMotion.apply(state);
        assertTrue(state.attackAnimationState.isStarted());
    }
}
