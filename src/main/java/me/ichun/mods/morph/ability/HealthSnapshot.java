package me.ichun.mods.morph.ability;

/** A server-authored health unit conversion, distinct from combat or regeneration. */
public record HealthSnapshot(float before, float after) {
    public HealthSnapshot {
        if (!Float.isFinite(before) || !Float.isFinite(after) || before <= 0 || after <= 0
            || before > 1_000_000 || after > 1_000_000) throw new IllegalArgumentException("Invalid morph health");
    }

    public boolean includesPendingDamage(float clientHealth) { return clientHealth > before; }
}
