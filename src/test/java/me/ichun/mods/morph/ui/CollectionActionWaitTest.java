package me.ichun.mods.morph.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CollectionActionWaitTest {
    @Test void acknowledgedMutationWaitsForItsCompleteSnapshot() {
        var state = new CollectionActionWait();
        state.snapshot(3);
        state.start(8);
        assertFalse(state.acknowledge(7, 4));
        assertTrue(state.pending());
        assertTrue(state.acknowledge(8, 4));
        assertTrue(state.pending());
        state.snapshot(3);
        assertTrue(state.pending());
        state.snapshot(4);
        assertFalse(state.pending());
    }
    @Test void snapshotBeforeAckAndRejectedActionBothFinishOnlyAfterMatchingAck() {
        var state = new CollectionActionWait();
        state.snapshot(10);
        state.start(1);
        state.snapshot(11);
        assertTrue(state.pending());
        state.acknowledge(1, 11);
        assertFalse(state.pending());
        state.start(2);
        state.acknowledge(2, 11);
        assertFalse(state.pending());
    }
    @Test void overlappingRequestsAndNegativeSequencesAreRejected() {
        var state = new CollectionActionWait();
        assertThrows(IllegalStateException.class, () -> state.start(-1));
        state.start(0);
        assertThrows(IllegalStateException.class, () -> state.start(1));
    }
}
