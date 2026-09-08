package me.ichun.mods.morph.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealthSnapshotTest {
    @Test void conversionToFewerHeartsIsNotDamage() {
        assertFalse(new HealthSnapshot(20, 6).includesPendingDamage(20));
        assertFalse(new HealthSnapshot(10, 3).includesPendingDamage(10));
    }

    @Test void realDamageBeforeConversionRemainsDamage() {
        assertTrue(new HealthSnapshot(18, 5.4F).includesPendingDamage(20));
        assertTrue(new HealthSnapshot(5, 16.666666F).includesPendingDamage(6));
    }

    @Test void returnToHumanAndPendingHealingDoNotFlash() {
        assertFalse(new HealthSnapshot(6, 20).includesPendingDamage(6));
        assertFalse(new HealthSnapshot(6, 20).includesPendingDamage(5));
    }

    @Test void rejectsNonFiniteDeadAndUnboundedValues() {
        for (float invalid : new float[] {Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, 0, -1, 1_000_001}) {
            assertThrows(IllegalArgumentException.class, () -> new HealthSnapshot(invalid, 6));
            assertThrows(IllegalArgumentException.class, () -> new HealthSnapshot(20, invalid));
        }
    }
}
