package me.ichun.mods.morph.progression;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static me.ichun.mods.morph.progression.BiomassDefinitions.*;
import static me.ichun.mods.morph.progression.BiomassRuntime.Status.*;

class BiomassRuntimeTest {
    private static final UUID PLAYER = new UUID(0, 1);
    private static final UUID OTHER = new UUID(0, 2);
    private static class Store implements BiomassRuntime.Store {
        final Map<UUID, BiomassLedger> ledgers = new HashMap<>();
        int commits;
        boolean conflict;
        public BiomassLedger read(UUID id) { return ledgers.getOrDefault(id, BiomassLedger.locked()); }
        public boolean commit(UUID id, long expected, BiomassLedger after) {
            if (conflict || read(id).revision() != expected) return false;
            assertEquals(expected + 1, after.revision());
            ledgers.put(id, after); commits++; return true;
        }
    }

    @Test void firstEligibleKillUnlocksAndCreditsInOneCommitAndClassicDoesNothing() {
        var store = new Store(); var runtime = new BiomassRuntime(store, BiomassRuntime.DEFAULTS);
        assertEquals(DISABLED, runtime.gain(PLAYER, false, true, 10, 1, 1).status());
        assertEquals(LOCKED, runtime.gain(PLAYER, true, false, 10, 1, 1).status());
        assertEquals(INELIGIBLE, runtime.gain(PLAYER, true, true, 10, 9, 1).status());
        assertEquals(UNCHANGED, runtime.gain(PLAYER, true, true, .5, 1, 1).status());
        assertEquals(0, store.commits);
        assertFalse(store.read(PLAYER).unlocked());
        assertEquals(CHANGED, runtime.gain(PLAYER, true, true, 10, 1, 1).status());
        assertEquals(1, store.commits);
        assertEquals(1, store.read(PLAYER).revision());
        assertEquals(10, store.read(PLAYER).balance());
        assertTrue(store.read(PLAYER).unlocked());
        assertEquals(BiomassLedger.locked(), store.read(OTHER));
    }

    @Test void retriedPurchaseCannotBuyAnExtraLevelEvenWithEnoughFunds() {
        var store = new Store(); var runtime = new BiomassRuntime(store, BiomassRuntime.DEFAULTS);
        runtime.gain(PLAYER, true, true, 100, 1, 1);
        assertEquals(CHANGED, runtime.purchase(PLAYER, true, 1, Upgrade.EFFICIENCY).status());
        assertEquals(75, store.read(PLAYER).balance());
        assertEquals(STALE, runtime.purchase(PLAYER, true, 1, Upgrade.EFFICIENCY).status());
        assertEquals(75, store.read(PLAYER).balance());
        assertEquals(1, store.read(PLAYER).level(Upgrade.EFFICIENCY));
        assertEquals(2, store.commits);
        assertEquals(CHANGED, runtime.purchase(PLAYER, true, 2, Upgrade.EFFICIENCY).status());
        assertEquals(0, store.read(PLAYER).balance());
        assertEquals(2, store.read(PLAYER).level(Upgrade.EFFICIENCY));
    }

    @Test void staleCommitAndDeniedPurchasesConserveBalanceAndLevels() {
        var store = new Store(); var runtime = new BiomassRuntime(store, BiomassRuntime.DEFAULTS);
        runtime.gain(PLAYER, true, true, 100, 1, 1);
        var before = store.read(PLAYER);
        store.conflict = true;
        assertEquals(STALE, runtime.purchase(PLAYER, true, before.revision(), Upgrade.EFFICIENCY).status());
        assertSame(before, store.read(PLAYER));
        store.conflict = false;
        assertEquals(PREREQUISITE, runtime.purchase(PLAYER, true, 1, Upgrade.CRITICAL_CAPACITY).status());
        assertEquals(DISABLED, runtime.purchase(PLAYER, false, 1, Upgrade.EFFICIENCY).status());
        assertEquals(STALE, runtime.purchase(PLAYER, true, -1, Upgrade.EFFICIENCY).status());
        assertSame(before, store.read(PLAYER));
        assertEquals(1, store.commits);
    }

    @Test void encodedRestartPreservesReplayRevisionAndCurrencyConservation() {
        var store = new Store(); var runtime = new BiomassRuntime(store, BiomassRuntime.DEFAULTS);
        runtime.gain(PLAYER, true, true, 100, 1, 1);
        runtime.purchase(PLAYER, true, 1, Upgrade.EFFICIENCY);
        byte[] saved = BiomassCodec.encode(store.read(PLAYER), BiomassRuntime.DEFAULTS);
        var restarted = new Store();
        restarted.ledgers.put(PLAYER, BiomassCodec.decode(saved, BiomassRuntime.DEFAULTS));
        var afterRestart = new BiomassRuntime(restarted, BiomassRuntime.DEFAULTS);
        assertEquals(STALE, afterRestart.purchase(PLAYER, true, 1, Upgrade.EFFICIENCY).status());
        assertEquals(75, restarted.read(PLAYER).balance());
        afterRestart.gain(PLAYER, true, false, 10, 1, 1);
        var ledger = restarted.read(PLAYER);
        assertEquals(87, ledger.balance()); // 100 earned - 25 purchased + floor(10 * 1.25).
        var failedAction = ledger.charge(BiomassRuntime.DEFAULTS, true, Action.MORPH);
        assertEquals(87, failedAction.complete(ledger, false).balance());
        assertEquals(82, failedAction.complete(ledger, true).balance());
    }
}
