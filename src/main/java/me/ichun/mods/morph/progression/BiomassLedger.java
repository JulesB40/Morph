package me.ichun.mods.morph.progression;

import java.util.Map;
import static me.ichun.mods.morph.progression.BiomassDefinitions.*;

/** Immutable per-player state. Only the authoritative service publishes successful candidate states. */
public record BiomassLedger(long revision, boolean unlocked, int balance, Map<Upgrade, Integer> upgrades) {
    public enum Status { CHANGED, UNCHANGED, LOCKED, INSUFFICIENT_BALANCE, MAX_LEVEL, PREREQUISITE, INELIGIBLE }
    public record Result(Status status, BiomassLedger before, BiomassLedger after) {
        public boolean accepted() { return status == Status.CHANGED || status == Status.UNCHANGED; }
        /** Failed actions retain the original ledger; callers must also roll back their own game mutations. */
        public BiomassLedger complete(BiomassLedger current, boolean actionSucceeded) {
            if (!before.equals(current)) throw new IllegalStateException("Stale biomass transaction");
            return accepted() && actionSucceeded ? after : before;
        }
    }

    public BiomassLedger {
        upgrades = Map.copyOf(upgrades);
        if (revision < 0 || balance < 0 || balance > MAX_UNITS
                || upgrades.values().stream().anyMatch(n -> n <= 0 || n > 16)
                || (!unlocked && (balance != 0 || !upgrades.isEmpty())))
            throw new IllegalArgumentException("Invalid biomass ledger");
    }
    public static BiomassLedger locked() { return new BiomassLedger(0, false, 0, Map.of()); }
    public int level(Upgrade upgrade) { return upgrades.getOrDefault(upgrade, 0); }
    public BiomassLedger unlock() { return unlocked ? this : new BiomassLedger(nextRevision(), true, 0, upgrades); }

    /** Eligibility and the base-health value must come from server policy/native attributes, never a client. */
    public Result acquire(BiomassDefinitions definitions, boolean eligible, double nativeBaseHealth,
                          double volume, double distance) {
        definitions.validate(this);
        if (!Double.isFinite(nativeBaseHealth) || nativeBaseHealth < 0 || !Double.isFinite(volume) || volume <= 0
                || !Double.isFinite(distance) || distance < 0)
            throw new IllegalArgumentException("Invalid biomass acquisition measurements");
        if (!unlocked) return reject(Status.LOCKED);
        if (!eligible || volume > definitions.maxAbsorptionVolume(this) || distance > definitions.absorptionReach(this))
            return reject(Status.INELIGIBLE);
        // Duplicate variants still yield biomass. Bound before conversion/multiplication to avoid overflow.
        int gain = (int) (Math.min(nativeBaseHealth, 100) * definitions.efficiencyPercent(this) / 100);
        int next = Math.min(definitions.criticalCapacity(this), balance + gain);
        return replace(next, upgrades);
    }

    public Result purchase(BiomassDefinitions definitions, Upgrade upgrade) {
        definitions.validate(this);
        java.util.Objects.requireNonNull(upgrade);
        if (!unlocked) return reject(Status.LOCKED);
        var definition = definitions.upgrades().get(upgrade);
        int current = level(upgrade);
        if (current == definition.maxLevel()) return reject(Status.MAX_LEVEL);
        for (var required : definition.prerequisites().entrySet())
            if (level(required.getKey()) < required.getValue()) return reject(Status.PREREQUISITE);
        int cost = definition.costs().get(current);
        if (balance < cost) return reject(Status.INSUFFICIENT_BALANCE);
        var next = new java.util.EnumMap<Upgrade, Integer>(Upgrade.class);
        next.putAll(upgrades);
        next.put(upgrade, current + 1);
        return replace(balance - cost, next);
    }

    /** Non-biomass modes bypass the economy entirely; RESET is free even before unlocking. */
    public Result charge(BiomassDefinitions definitions, boolean biomassMode, Action action) {
        java.util.Objects.requireNonNull(action);
        if (!biomassMode || action == Action.RESET) return reject(Status.UNCHANGED);
        definitions.validate(this);
        if (!unlocked) return reject(Status.LOCKED);
        int cost = definitions.actionCosts().get(action);
        return balance < cost ? reject(Status.INSUFFICIENT_BALANCE) : replace(balance - cost, upgrades);
    }

    private Result replace(int nextBalance, Map<Upgrade, Integer> nextUpgrades) {
        if (balance == nextBalance && upgrades.equals(nextUpgrades)) return reject(Status.UNCHANGED);
        return new Result(Status.CHANGED, this, new BiomassLedger(nextRevision(), unlocked, nextBalance, nextUpgrades));
    }
    private Result reject(Status status) { return new Result(status, this, this); }
    private long nextRevision() {
        if (revision == Long.MAX_VALUE) throw new IllegalStateException("Biomass revision exhausted");
        return revision + 1;
    }
}
