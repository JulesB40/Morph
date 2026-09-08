package me.ichun.mods.morph.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import me.ichun.mods.morph.client.transition.MorphTransitionRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;

/** Explicit event-driven animations; ordinary appearance synchronization never starts one. */
public final class MorphTransitions {
    private record Transition(String from, String to, long started, int duration) {}
    private static final Map<UUID, Transition> ACTIVE = new HashMap<>();
    private MorphTransitions() {}

    public static void start(UUID player, String from, String to, int duration) {
        var level = Minecraft.getInstance().level;
        if (level == null || from.equals(to) || duration <= 0 || level.getPlayerByUUID(player) == null) return;
        ACTIVE.put(player, new Transition(from, to, level.getGameTime(), duration));
    }

    public static void cancel(UUID player) { ACTIVE.remove(player); }
    public static void reconcile(UUID player, String currentForm) {
        var transition = ACTIVE.get(player);
        if (transition != null && !transition.to.equals(currentForm == null ? "" : currentForm)) cancel(player);
    }
    public static void clear() { ACTIVE.clear(); }
    public static void retainPlayers(java.util.Set<UUID> present) { ACTIVE.keySet().retainAll(present); }

    public static MorphTransitionRenderer.Frame extract(Avatar avatar, AvatarRenderState source, String currentForm) {
        var transition = ACTIVE.get(avatar.getUUID());
        if (transition == null) return null;
        String current = currentForm == null ? "" : currentForm;
        float elapsed = avatar.level().getGameTime() - transition.started + source.ageInTicks - avatar.tickCount;
        if (!current.equals(transition.to) || !avatar.isAlive() || source.isSpectator || elapsed >= transition.duration) {
            cancel(avatar.getUUID());
            return null;
        }
        var from = transition.from.isEmpty() ? source : MorphRenderSnapshots.extract(avatar, source, transition.from);
        var to = transition.to.isEmpty() ? source : MorphRenderSnapshots.extract(avatar, source, transition.to);
        if (from == null || to == null) return null;
        return new MorphTransitionRenderer.Frame(from, to, Math.clamp(elapsed / transition.duration, 0.0F, 1.0F));
    }
}
