package me.ichun.mods.morph.client.animation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MorphOtherSwimmingTest {
    @Test void paddlesAlternateSidesAndRemainBounded() {
        for (boolean fast : new boolean[] {false, true}) {
            float bound = fast ? 0.95F : 0.55F;
            for (int tick = 0; tick < 200; tick++) {
                assertEquals(-MorphOtherSwimming.paddle(tick, 0, fast), MorphOtherSwimming.paddle(tick, 1, fast), 0.00002F);
                for (int limb = 0; limb < 14; limb++)
                    assertTrue(Math.abs(MorphOtherSwimming.paddle(tick, limb, fast)) <= bound);
            }
        }
    }

    @Test void fastSwimmingHasADistinctStrokeFromNormalSwimming() {
        assertNotEquals(MorphOtherSwimming.paddle(3, 0, false), MorphOtherSwimming.paddle(3, 0, true));
    }
}
