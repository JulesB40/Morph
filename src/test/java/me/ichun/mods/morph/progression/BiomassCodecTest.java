package me.ichun.mods.morph.progression;

import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static me.ichun.mods.morph.progression.BiomassDefinitions.Upgrade;

class BiomassCodecTest {
    private final BiomassDefinitions definitions = BiomassDefinitions.defaults();

    @Test void codecHasAStableVersionedLayoutAndRoundTrips() {
        var ledger = new BiomassLedger(7, true, 42, Map.of(Upgrade.REACH, 2, Upgrade.CAPACITY, 1));
        // Version, big-endian revision, unlock, big-endian balance, count, sorted (id, level) pairs.
        byte[] expected = {1, 0, 0, 0, 0, 0, 0, 0, 7, 1, 0, 0, 0, 42, 2, 1, 1, 4, 2};
        assertArrayEquals(expected, BiomassCodec.encode(ledger, definitions));
        assertEquals(ledger, BiomassCodec.decode(expected, definitions));
        assertEquals(BiomassLedger.locked(), BiomassCodec.decode(BiomassCodec.encode(BiomassLedger.locked(), definitions), definitions));
    }

    @Test void malformedWireCannotBecomeAValidLedger() {
        byte[] valid = {1, 0, 0, 0, 0, 0, 0, 0, 7, 1, 0, 0, 0, 42, 2, 1, 1, 4, 2};
        for (int size = 0; size < valid.length; size++) {
            byte[] truncated = Arrays.copyOf(valid, size);
            assertThrows(IllegalArgumentException.class, () -> BiomassCodec.decode(truncated, definitions));
        }
        for (int[] mutation : new int[][]{{0, 2}, {9, 2}, {14, 6}, {17, 1}, {17, 9}, {18, 0}, {18, 4}}) {
            byte[] bad = valid.clone();
            bad[mutation[0]] = (byte) mutation[1];
            assertThrows(IllegalArgumentException.class, () -> BiomassCodec.decode(bad, definitions));
        }
        assertThrows(IllegalArgumentException.class, () -> BiomassCodec.decode(Arrays.copyOf(valid, valid.length + 1), definitions));
        assertThrows(IllegalArgumentException.class, () -> BiomassCodec.decode(new byte[BiomassCodec.MAX_BYTES + 1], definitions));
    }
}
