package me.ichun.mods.morph.ability;

import java.util.Set;

/** Explicit vanilla traits, retaining unknown/modded forms' normal player behavior. */
public record FormTraits(boolean flight, boolean waterBreathing, boolean fallImmunity) {
    private static final FormTraits NONE = new FormTraits(false, false, false);
    private static final Set<String> FLYERS = Set.of("minecraft:bat", "minecraft:bee",
            "minecraft:blaze", "minecraft:ghast", "minecraft:wither");
    private static final Set<String> NO_FALL = Set.of("minecraft:bat", "minecraft:bee", "minecraft:parrot",
            "minecraft:phantom", "minecraft:vex", "minecraft:ender_dragon", "minecraft:chicken",
            "minecraft:cat", "minecraft:ocelot", "minecraft:iron_golem", "minecraft:magma_cube");
    private static final Set<String> AQUATIC = Set.of(
            "minecraft:cod", "minecraft:salmon", "minecraft:tropical_fish", "minecraft:pufferfish",
            "minecraft:squid", "minecraft:glow_squid", "minecraft:axolotl", "minecraft:turtle",
            "minecraft:guardian", "minecraft:elder_guardian", "minecraft:drowned", "minecraft:iron_golem");

    public static FormTraits forForm(String form) {
        if (form == null || form.isEmpty()) return NONE;
        boolean flight = FLYERS.contains(form);
        boolean water = AQUATIC.contains(form) || MorphTraits.isUndead(form);
        boolean fall = flight || NO_FALL.contains(form);
        return flight || water || fall ? new FormTraits(flight, water, fall) : NONE;
    }
}
