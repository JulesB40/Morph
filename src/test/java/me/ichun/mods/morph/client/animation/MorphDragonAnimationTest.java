package me.ichun.mods.morph.client.animation;

import net.minecraft.client.model.monster.dragon.EnderDragonModel;
import net.minecraft.client.renderer.entity.state.EnderDragonRenderState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MorphDragonAnimationTest {
    @Test void rateMatchesNativeHoverTravelAscentDescentAndRestingExamples() {
        assertEquals(0.2F, MorphDragonAnimation.rate(0, 0, false));
        assertEquals(0.1F, MorphDragonAnimation.rate(0.1, 0, false));
        assertEquals(0.4F, MorphDragonAnimation.rate(0, 1, false));
        assertEquals(0.1F, MorphDragonAnimation.rate(0, -1, false));
        assertEquals(0.1F, MorphDragonAnimation.rate(1, 1, true));
        assertEquals(0, MorphDragonAnimation.rate(Double.NaN, 0, false));
        assertTrue(MorphDragonAnimation.rate(0, Double.MAX_VALUE, false) <= 4F);
    }

    @Test void repeatedSnapshotsAndPartialSamplesNeverAdvanceATickTwice() {
        var clock = new MorphDragonAnimation.Clock();
        assertEquals(0F, clock.sample(10, 0, 0, 0, false, true));
        assertEquals(0.1F, clock.sample(10, 0.5F, 0, 0, false, true));
        assertEquals(0.1F, clock.sample(10, 0.5F, 1, 1, true, true));
        assertEquals(0.2F, clock.sample(10, 1, 0, 0, false, true));
        assertEquals(0.2F, clock.sample(11, 0, 0, 0, false, true));
        assertEquals(0.3F, clock.sample(11, 0.5F, 0, 0, false, true), 0.00001F);
        float dead = clock.sample(12, 0, 0, 0, false, false);
        assertEquals(dead, clock.sample(12, 1, 0, 0, false, false));
        assertEquals(dead, clock.sample(13, 1, 0, 0, false, false));
        assertEquals(0F, clock.sample(0, 0, 0, 0, false, true));
    }

    @Test void longGapsCatchUpAtMostFiveTicksAndKeepPhaseFinite() {
        var gap = new MorphDragonAnimation.Clock();
        var fiveTicks = new MorphDragonAnimation.Clock();
        gap.sample(1, 1, 0.1, 0, false, true);
        fiveTicks.sample(1, 1, 0.1, 0, false, true);
        assertEquals(fiveTicks.sample(6, 0.5F, 0.1, 0, false, true),
            gap.sample(100_000, 0.5F, 0.1, 0, false, true));
        for (int tick = 100_001; tick < 100_100; tick++) {
            float phase = gap.sample(tick, 0.5F, 0, 10, false, true);
            assertTrue(Float.isFinite(phase));
            assertTrue(phase >= 0 && phase < 1);
        }
    }

    @Test void actualNativeWingsAndTipsMoveAcrossFreshRenderStates() {
        var root = EnderDragonModel.createBodyLayer().bakeRoot();
        var model = new EnderDragonModel(root);
        var leftWing = root.getChild("body").getChild("left_wing");
        var rightWing = root.getChild("body").getChild("right_wing");
        var leftTip = leftWing.getChild("left_wing_tip");
        var rightTip = rightWing.getChild("right_wing_tip");
        var clock = new MorphDragonAnimation.Clock();
        var first = new EnderDragonRenderState();
        first.flapTime = clock.sample(1, 0.5F, 0, 0, false, true);
        model.setupAnim(first);
        float wing = leftWing.zRot, tip = leftTip.zRot;
        var next = new EnderDragonRenderState();
        next.flapTime = clock.sample(2, 0.5F, 0, 0, false, true);
        model.setupAnim(next);
        assertNotEquals(wing, leftWing.zRot);
        assertNotEquals(tip, leftTip.zRot);
        assertEquals(-leftWing.zRot, rightWing.zRot);
        assertEquals(-leftTip.zRot, rightTip.zRot);
        assertFalse(next.isSitting);
        assertFalse(next.isLandingOrTakingOff);
        assertNull(next.beamOffset);
    }
}
