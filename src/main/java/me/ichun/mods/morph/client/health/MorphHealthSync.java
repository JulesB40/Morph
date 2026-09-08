package me.ichun.mods.morph.client.health;

import me.ichun.mods.morph.ability.HealthSnapshot;
import net.minecraft.client.Minecraft;

/** The server sends the updated maximum attribute immediately before this message. */
public final class MorphHealthSync {
    private MorphHealthSync() {}

    public static void apply(HealthSnapshot health) {
        var player = Minecraft.getInstance().player;
        if (player == null || !player.isAlive()) return;
        // Preserve any real damage since the previous health packet, including damage in
        // the same server tick as the conversion. The conversion itself never creates hurt.
        if (health.includesPendingDamage(player.getHealth())) player.hurtTo(health.before());
        player.setHealth(health.after());
    }
}
