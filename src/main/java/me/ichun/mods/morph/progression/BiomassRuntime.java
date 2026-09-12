package me.ichun.mods.morph.progression;

import java.util.Objects;
import java.util.UUID;
import static me.ichun.mods.morph.progression.BiomassDefinitions.Upgrade;

/** Server-thread coordinator. The store is shared across dimensions and persists committed ledgers. */
public final class BiomassRuntime {
    public static final BiomassDefinitions DEFAULTS = BiomassDefinitions.defaults();
    public interface Store {
        BiomassLedger read(UUID player);
        boolean commit(UUID player, long expectedRevision, BiomassLedger replacement);
    }
    public enum Status { CHANGED, UNCHANGED, DISABLED, STALE, LOCKED, INSUFFICIENT_BALANCE, MAX_LEVEL, PREREQUISITE, INELIGIBLE }
    public record Outcome(Status status, BiomassLedger ledger) {
        public boolean accepted() { return status == Status.CHANGED || status == Status.UNCHANGED; }
    }
    private final Store store;
    private final BiomassDefinitions definitions;

    public BiomassRuntime(Store store, BiomassDefinitions definitions) {
        this.store = Objects.requireNonNull(store);
        this.definitions = Objects.requireNonNull(definitions);
    }

    /** Expected revision makes a retried purchase reject instead of buying the next level. */
    public Outcome purchase(UUID player, boolean biomassMode, long expectedRevision, Upgrade upgrade) {
        var before = store.read(player);
        if (!biomassMode) return new Outcome(Status.DISABLED, before);
        if (expectedRevision < 0 || before.revision() != expectedRevision) return new Outcome(Status.STALE, before);
        return commit(player, before.purchase(definitions, upgrade));
    }

    /** Called only from a real server kill event after source/player filters and acquisition hooks. */
    public Outcome gain(UUID player, boolean biomassMode, boolean unlockOnEligibleKill,
                        double nativeBaseHealth, double volume, double distance) {
        var before = store.read(player);
        if (!biomassMode) return new Outcome(Status.DISABLED, before);
        return commit(player, prepareGain(before, definitions, unlockOnEligibleKill, nativeBaseHealth, volume, distance));
    }

    /** Unlock and the first gain form one revision/commit, never an intermediate persisted unlock. */
    public static BiomassLedger.Result prepareGain(BiomassLedger before, BiomassDefinitions definitions,
                                                   boolean unlockOnEligibleKill, double health, double volume, double distance) {
        definitions.validate(before);
        var working = before;
        if (!before.unlocked() && unlockOnEligibleKill)
            working = new BiomassLedger(before.revision(), true, 0, before.upgrades());
        var gain = working.acquire(definitions, true, health, volume, distance);
        if (!gain.accepted()) return new BiomassLedger.Result(gain.status(), before, before);
        if (before.unlocked()) return gain;
        // A positive-yield eligible kill is the starter milestone. Zero-yield kills do not unlock.
        if (gain.after().balance() == 0) return new BiomassLedger.Result(BiomassLedger.Status.UNCHANGED, before, before);
        return new BiomassLedger.Result(BiomassLedger.Status.CHANGED, before, gain.after());
    }

    private Outcome commit(UUID player, BiomassLedger.Result result) {
        if (result.status() != BiomassLedger.Status.CHANGED)
            return new Outcome(Status.valueOf(result.status().name()), result.before());
        if (!store.commit(player, result.before().revision(), result.after()))
            return new Outcome(Status.STALE, store.read(player));
        return new Outcome(Status.CHANGED, result.after());
    }
}
