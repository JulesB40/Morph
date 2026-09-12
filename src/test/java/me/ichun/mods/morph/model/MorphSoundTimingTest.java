package me.ichun.mods.morph.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MorphSoundTimingTest {
    @Test void centersThreeSecondSampleWithoutDelayingShortMorphs() {
        assertEquals(20, MorphSounds.startDelayTicks(100), "Preserve the existing five-second timing");
        assertEquals(0, MorphSounds.startDelayTicks(1));
        assertEquals(0, MorphSounds.startDelayTicks(60));
        assertEquals(0, MorphSounds.startDelayTicks(61), "Odd residual tick stays at the end");
        assertEquals(1, MorphSounds.startDelayTicks(62));
        assertEquals(570, MorphSounds.startDelayTicks(1200));
    }

    @Test void rejectsDurationsOutsideTransitionContract() {
        assertThrows(IllegalArgumentException.class, () -> MorphSounds.startDelayTicks(0));
        assertThrows(IllegalArgumentException.class, () -> MorphSounds.startDelayTicks(1201));
    }
}
