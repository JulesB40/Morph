package me.ichun.mods.morph.progression;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static me.ichun.mods.morph.progression.BiomassDefinitions.*;
import static me.ichun.mods.morph.progression.BiomassLedger.Status.*;

class BiomassLedgerTest {
    private final BiomassDefinitions definitions = BiomassDefinitions.defaults();
    private BiomassLedger acquired(BiomassLedger ledger, double health) {
        return ledger.acquire(definitions, true, health, 1, 1).after();
    }

    @Test void starterCanEarnAndPurchaseWithoutOwningANewVariant() {
        var ledger = BiomassLedger.locked();
        assertEquals(LOCKED, ledger.acquire(definitions, true, 10, 1, 1).status());
        ledger = ledger.unlock();
        assertEquals(100, definitions.capacity(ledger));
        for (int repeat = 0; repeat < 5; repeat++) ledger = acquired(ledger, 10);
        assertEquals(50, ledger.balance());
        var purchased = ledger.purchase(definitions, Upgrade.CAPACITY);
        assertEquals(CHANGED, purchased.status());
        assertEquals(0, purchased.after().balance());
        assertEquals(200, definitions.capacity(purchased.after()));
        assertEquals(50, ledger.balance());
        assertEquals(0, ledger.level(Upgrade.CAPACITY));
    }

    @Test void acquisitionHasExplicitFlooredBoundedYieldAndSpatialEligibility() {
        var empty = BiomassLedger.locked().unlock();
        assertEquals(7, acquired(empty, 7.9).balance());
        assertEquals(100, acquired(empty, Double.MAX_VALUE).balance());
        assertEquals(0, acquired(empty, .99).balance());
        assertEquals(UNCHANGED, acquired(empty, 100).acquire(definitions, true, 100, 1, 1).status());
        assertEquals(INELIGIBLE, empty.acquire(definitions, false, 10, 1, 1).status());
        assertEquals(INELIGIBLE, empty.acquire(definitions, true, 10, 8.01, 1).status());
        assertEquals(INELIGIBLE, empty.acquire(definitions, true, 10, 1, 3.01).status());
        assertEquals(10, empty.acquire(definitions, true, 10, 8, 3).after().balance());
        for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1}) {
            assertThrows(IllegalArgumentException.class, () -> empty.acquire(definitions, true, bad, 1, 1));
            assertThrows(IllegalArgumentException.class, () -> empty.acquire(definitions, true, 1, bad, 1));
            assertThrows(IllegalArgumentException.class, () -> empty.acquire(definitions, true, 1, 1, bad));
        }
        assertThrows(IllegalArgumentException.class, () -> empty.acquire(definitions, true, 1, 0, 1));
    }

    @Test void chargesOnlyCommitAlongsideSuccessfulActionsAndRejectStaleState() {
        var ledger = acquired(BiomassLedger.locked().unlock(), 10);
        var paid = ledger.charge(definitions, true, Action.MORPH);
        assertEquals(5, paid.after().balance());
        assertSame(ledger, paid.complete(ledger, false));
        assertEquals(paid.after(), paid.complete(ledger, true));
        assertThrows(IllegalStateException.class, () -> paid.complete(paid.after(), true));
        var denied = ledger.charge(definitions, true, Action.POWERFUL_ABILITY);
        assertEquals(INSUFFICIENT_BALANCE, denied.status());
        assertSame(ledger, denied.complete(ledger, true));
        assertSame(ledger, ledger.charge(definitions, false, Action.POWERFUL_ABILITY).after());
        var locked = BiomassLedger.locked();
        assertEquals(UNCHANGED, locked.charge(definitions, true, Action.RESET).status());
        assertEquals(LOCKED, locked.charge(definitions, true, Action.MORPH).status());
    }

    @Test void purchaseChecksPrerequisitesMaxLevelAndFundsBeforeChangingEitherField() {
        var ledger = acquired(BiomassLedger.locked().unlock(), 100);
        assertEquals(PREREQUISITE, ledger.purchase(definitions, Upgrade.CRITICAL_CAPACITY).status());
        var efficiency = ledger.purchase(definitions, Upgrade.EFFICIENCY).after();
        assertEquals(75, efficiency.balance());
        assertEquals(125, definitions.efficiencyPercent(efficiency));
        assertEquals(87, acquired(efficiency, 10).balance());
        var noFunds = new BiomassLedger(1, true, 24, Map.of());
        var denied = noFunds.purchase(definitions, Upgrade.EFFICIENCY);
        assertEquals(INSUFFICIENT_BALANCE, denied.status());
        assertSame(noFunds, denied.after());
        var maximum = new BiomassLedger(1, true, 0, Map.of(Upgrade.REACH, 3));
        assertEquals(MAX_LEVEL, maximum.purchase(definitions, Upgrade.REACH).status());
    }

    @Test void criticalReserveExpandsStorageWithoutAutomaticSpending() {
        var ledger = new BiomassLedger(4, true, 199, Map.of(Upgrade.CAPACITY, 1, Upgrade.CRITICAL_CAPACITY, 1));
        assertEquals(200, definitions.capacity(ledger));
        assertEquals(250, definitions.criticalCapacity(ledger));
        assertEquals(250, acquired(ledger, 100).balance());
        assertEquals(199, ledger.balance());
    }

    @Test void everyDefaultUpgradeIsReachableByRepeatedEligibleKills() {
        var ledger = BiomassLedger.locked().unlock();
        for (Upgrade upgrade : Upgrade.values()) {
            for (int level = 1; level <= definitions.upgrades().get(upgrade).maxLevel(); level++) {
                for (int kill = 0; kill < 12; kill++) ledger = acquired(ledger, 100);
                var result = ledger.purchase(definitions, upgrade);
                assertEquals(CHANGED, result.status(), upgrade + " level " + level);
                ledger = result.after();
                assertEquals(level, ledger.level(upgrade));
            }
        }
        assertEquals(600, definitions.capacity(ledger));
        assertEquals(1050, definitions.criticalCapacity(ledger));
        assertEquals(200, definitions.efficiencyPercent(ledger));
        assertEquals(40, definitions.maxAbsorptionVolume(ledger));
        assertEquals(6, definitions.absorptionReach(ledger));
    }

    @Test void validationRejectsBadStateCyclesAndIncompatibleReloadWithoutClamping() {
        var map = new HashMap<Upgrade, Integer>();
        map.put(Upgrade.REACH, 1);
        var ledger = new BiomassLedger(0, true, 0, map);
        map.clear();
        assertEquals(1, ledger.level(Upgrade.REACH));
        assertThrows(UnsupportedOperationException.class, () -> ledger.upgrades().clear());
        assertThrows(IllegalArgumentException.class, () -> new BiomassLedger(0, false, 1, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new BiomassLedger(-1, true, 0, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new BiomassLedger(0, true, -1, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> definitions.validate(new BiomassLedger(0, true, 101, Map.of())));
        assertThrows(IllegalArgumentException.class, () -> definitions.validate(new BiomassLedger(0, true, 0, Map.of(Upgrade.REACH, 4))));
        var cyclic = new HashMap<>(definitions.upgrades());
        cyclic.put(Upgrade.CAPACITY, new UpgradeDefinition(List.of(1), Map.of(Upgrade.CRITICAL_CAPACITY, 1)));
        assertThrows(IllegalArgumentException.class, () -> new BiomassDefinitions(100, cyclic, definitions.actionCosts()));
        var exhausted = new BiomassLedger(Long.MAX_VALUE, true, 0, Map.of());
        assertThrows(IllegalStateException.class, () -> acquired(exhausted, 10));
        assertEquals(UNCHANGED, exhausted.charge(definitions, true, Action.RESET).status());
    }
}
