package me.ichun.mods.morph.client.nametag;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import me.ichun.mods.morph.ui.MorphScreen;

/** Server-synchronized per-subject preferences; vanilla still decides when a visible tag renders. */
public final class MorphNameTags {
    private static final Set<UUID> HIDDEN = new HashSet<>();
    private MorphNameTags() {}
    public static boolean visible(UUID player) { return !HIDDEN.contains(player); }
    public static void update(UUID player, boolean visible) {
        if (visible) HIDDEN.remove(player); else HIDDEN.add(player);
        if (Minecraft.getInstance().gui.screen() instanceof MorphScreen screen) screen.refreshNametag();
    }
    public static void clear() { HIDDEN.clear(); }
    public static void apply(UUID player, LivingEntityRenderState state) {
        if (!visible(player)) {
            state.nameTag = null;
            state.scoreText = null;
            state.nameTagAttachment = null;
        }
    }
}
