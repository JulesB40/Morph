package me.ichun.mods.morph.progression;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Authored economy for the opt-in biomass mode, not legacy balance compatibility. */
public record BiomassDefinitions(int starterCapacity, Map<Upgrade, UpgradeDefinition> upgrades,
                                 Map<Action, Integer> actionCosts) {
    public static final int MAX_UNITS = 1_000_000;
    public enum Upgrade { CAPACITY, EFFICIENCY, ABSORPTION, REACH, CRITICAL_CAPACITY }
    public enum Action { RESET, MORPH, BASIC_ABILITY, POWERFUL_ABILITY, CHANNEL_TICK }

    public record UpgradeDefinition(List<Integer> costs, Map<Upgrade, Integer> prerequisites) {
        public UpgradeDefinition {
            costs = List.copyOf(costs);
            prerequisites = Map.copyOf(prerequisites);
            if (costs.isEmpty() || costs.size() > 16 || costs.stream().anyMatch(n -> n <= 0 || n > MAX_UNITS)
                    || prerequisites.values().stream().anyMatch(n -> n <= 0 || n > 16))
                throw new IllegalArgumentException("Invalid biomass upgrade definition");
        }
        public int maxLevel() { return costs.size(); }
    }

    public BiomassDefinitions {
        upgrades = Map.copyOf(upgrades);
        actionCosts = Map.copyOf(actionCosts);
        if (starterCapacity <= 0 || starterCapacity > 10_000
                || !upgrades.keySet().equals(Set.of(Upgrade.values()))
                || !actionCosts.keySet().equals(Set.of(Action.values()))
                || actionCosts.get(Action.RESET) != 0
                || actionCosts.values().stream().anyMatch(n -> n < 0 || n > MAX_UNITS))
            throw new IllegalArgumentException("Invalid biomass definitions");
        for (var entry : upgrades.entrySet()) {
            for (var required : entry.getValue().prerequisites().entrySet()) {
                if (required.getValue() > upgrades.get(required.getKey()).maxLevel())
                    throw new IllegalArgumentException("Unreachable upgrade prerequisite");
            }
            visit(entry.getKey(), upgrades, new java.util.HashSet<>());
        }
    }

    private static void visit(Upgrade id, Map<Upgrade, UpgradeDefinition> definitions, Set<Upgrade> path) {
        if (!path.add(id)) throw new IllegalArgumentException("Cyclic upgrade prerequisites");
        for (Upgrade required : definitions.get(id).prerequisites().keySet()) visit(required, definitions, path);
        path.remove(id);
    }

    public static BiomassDefinitions defaults() {
        return new BiomassDefinitions(100, Map.of(
                Upgrade.CAPACITY, new UpgradeDefinition(List.of(50, 100, 200, 300, 400), Map.of()),
                Upgrade.EFFICIENCY, new UpgradeDefinition(List.of(25, 75, 150, 250), Map.of()),
                Upgrade.ABSORPTION, new UpgradeDefinition(List.of(50, 100, 200, 300), Map.of()),
                Upgrade.REACH, new UpgradeDefinition(List.of(25, 75, 150), Map.of()),
                Upgrade.CRITICAL_CAPACITY, new UpgradeDefinition(List.of(100, 200, 300), Map.of(Upgrade.CAPACITY, 1))),
                Map.of(Action.RESET, 0, Action.MORPH, 5, Action.BASIC_ABILITY, 10,
                        Action.POWERFUL_ABILITY, 25, Action.CHANNEL_TICK, 1));
    }

    public int capacity(BiomassLedger ledger) { return starterCapacity + 100 * ledger.level(Upgrade.CAPACITY); }
    /** Extra storage is a reserve above normal capacity; it does not cause damage or automatic spending. */
    public int criticalCapacity(BiomassLedger ledger) {
        return capacity(ledger) * (100 + 25 * ledger.level(Upgrade.CRITICAL_CAPACITY)) / 100;
    }
    public int efficiencyPercent(BiomassLedger ledger) { return 100 + 25 * ledger.level(Upgrade.EFFICIENCY); }
    public double maxAbsorptionVolume(BiomassLedger ledger) { return 8 + 8 * ledger.level(Upgrade.ABSORPTION); }
    public double absorptionReach(BiomassLedger ledger) { return 3 + ledger.level(Upgrade.REACH); }

    public void validate(BiomassLedger ledger) {
        for (var level : ledger.upgrades().entrySet()) {
            UpgradeDefinition definition = upgrades.get(level.getKey());
            if (level.getValue() > definition.maxLevel()) throw new IllegalArgumentException("Unsupported upgrade level");
            for (var required : definition.prerequisites().entrySet())
                if (ledger.level(required.getKey()) < required.getValue())
                    throw new IllegalArgumentException("Missing upgrade prerequisite");
        }
        if (ledger.balance() > criticalCapacity(ledger)) throw new IllegalArgumentException("Biomass exceeds capacity");
    }
}
