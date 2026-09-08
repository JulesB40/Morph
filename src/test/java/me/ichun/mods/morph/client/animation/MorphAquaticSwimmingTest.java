package me.ichun.mods.morph.client.animation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MorphAquaticSwimmingTest {
    @Test void duplicateExtractionDoesNotAdvanceAndFastPaceDoesNotJumpPhase() {
        assertEquals(42F, MorphAquaticSwimming.advance(42, 0, true));
        assertEquals(43F, MorphAquaticSwimming.advance(42, 1, false));
        assertEquals(43.7F, MorphAquaticSwimming.advance(42, 1, true), 0.00001F);
        assertEquals(42F, MorphAquaticSwimming.advance(42, -1, false));
    }

    @Test void squidTentaclesMoveWithinTheirNativeHalfPiRange() {
        assertNotEquals(MorphAquaticSwimming.tentacleAngle(0), MorphAquaticSwimming.tentacleAngle(10));
        for (int tick = 0; tick < 1000; tick++) {
            float angle = MorphAquaticSwimming.tentacleAngle(tick);
            assertTrue(angle >= 0 && angle <= Math.PI / 2 + 0.00001);
        }
    }
}
