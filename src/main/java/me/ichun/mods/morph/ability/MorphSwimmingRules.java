package me.ichun.mods.morph.ability;

import me.ichun.mods.morph.shape.ShapeHooks;
import net.minecraft.world.entity.player.Player;

/** Ordinary zombie variants walk along the bottom; drowned retain their aquatic locomotion. */
public final class MorphSwimmingRules {
    private MorphSwimmingRules() {}

    public static boolean walksUnderwater(String form) {
        return "minecraft:zombie".equals(form) || "minecraft:husk".equals(form)
                || "minecraft:zombie_villager".equals(form) || "minecraft:zombified_piglin".equals(form);
    }

    public static boolean canSwim(String form) { return !walksUnderwater(form); }

    public static boolean blocksSwimming(Player player) {
        return !player.isSpectator() && !player.getAbilities().flying && !player.isPassenger()
                && walksUnderwater(ShapeHooks.form(player));
    }

    public static void beforeTravel(Player player) {
        if (!blocksSwimming(player) || !player.isInWater()) return;
        player.setSwimming(false);
        player.setSprinting(false);
    }

    /** Gate only voluntary midwater jump input; currents, knockback and ground jumps remain intact. */
    public static boolean allowsJumpInput(Player player) {
        return !blocksSwimming(player) || !player.isInWater() || player.onGround();
    }
}
