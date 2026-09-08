package me.ichun.mods.morph.ability;

import java.util.Set;

/** Explicit, conservative first trait set; registry names alone never enable unknown traits. */
public record FormTraits(boolean flight, boolean waterBreathing, boolean fallImmunity) {
    private static final FormTraits NONE = new FormTraits(false, false, false);
    private static final Set<String> FLYERS = Set.of("minecraft:bat", "minecraft:bee", "minecraft:parrot");
    private static final Set<String> AQUATIC = Set.of(
            "minecraft:cod", "minecraft:salmon", "minecraft:tropical_fish", "minecraft:pufferfish",
            "minecraft:squid", "minecraft:glow_squid", "minecraft:axolotl", "minecraft:turtle",
            "minecraft:guardian", "minecraft:elder_guardian", "minecraft:drowned");

    public static FormTraits forForm(String form) {
        if (form == null || form.isEmpty()) return NONE;
        boolean flight = FLYERS.contains(form);
        boolean water = AQUATIC.contains(form);
        return flight || water ? new FormTraits(flight, water, flight) : NONE;
    }
}
