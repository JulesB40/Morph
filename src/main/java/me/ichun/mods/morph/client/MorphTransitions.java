package me.ichun.mods.morph.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import me.ichun.mods.morph.client.transition.MorphTransitionRenderer;
import me.ichun.mods.morph.model.CollectionEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;

/** Explicit event-driven animations; ordinary appearance synchronization never starts one. */
public final class MorphTransitions {
    private record Endpoint(String species, CollectionEntry entry) {
        static Endpoint of(CollectionEntry entry) {
            return new Endpoint(entry == null ? "" : entry.descriptor().species(), entry);
        }
        boolean sameAppearance(Endpoint other) {
            if (!species.equals(other.species)) return false;
            if (entry == null || other.entry == null) return entry == other.entry;
            return entry.id().equals(other.entry.id()) && entry.revision() == other.entry.revision();
        }
    }
    private record Transition(Endpoint from, Endpoint to, long started, int duration) {}
    private static final Map<UUID, Transition> ACTIVE = new HashMap<>();
    private MorphTransitions() {}

    public static void start(UUID player, String from, String to, int duration) {
        var level = Minecraft.getInstance().level;
        if (level == null || from.equals(to) || duration <= 0 || level.getPlayerByUUID(player) == null) return;
        ACTIVE.put(player, new Transition(new Endpoint(from, null), new Endpoint(to, null), level.getGameTime(), duration));
    }

    public static void start(UUID player, CollectionEntry from, CollectionEntry to, long startTick, int duration) {
        var level = Minecraft.getInstance().level;
        var source = Endpoint.of(from);
        var destination = Endpoint.of(to);
        if (level == null || source.sameAppearance(destination) || duration <= 0 || duration > 1200
                || level.getPlayerByUUID(player) == null) return;
        ACTIVE.put(player, new Transition(source, destination, startTick, duration));
    }

    public static void cancel(UUID player) { ACTIVE.remove(player); }
    public static void reconcile(UUID player, String currentForm) {
        var transition = ACTIVE.get(player);
        if (transition != null && !transition.to.species.equals(currentForm == null ? "" : currentForm)) cancel(player);
    }
    public static void clear() { ACTIVE.clear(); }
    public static void retainPlayers(java.util.Set<UUID> present) { ACTIVE.keySet().retainAll(present); }

    public static MorphTransitionRenderer.Frame extract(Avatar avatar, AvatarRenderState source, String currentForm) {
        var transition = ACTIVE.get(avatar.getUUID());
        if (transition == null) return null;
        String current = currentForm == null ? "" : currentForm;
        float elapsed = avatar.level().getGameTime() - transition.started + source.ageInTicks - avatar.tickCount;
        var active = DescriptorState.active(avatar.getUUID());
        boolean descriptorMatches = transition.to.entry == null || active != null
                && transition.to.sameAppearance(Endpoint.of(active));
        if (!current.equals(transition.to.species) || !descriptorMatches || !avatar.isAlive()
                || source.isSpectator || elapsed >= transition.duration) {
            cancel(avatar.getUUID());
            return null;
        }
        me.ichun.mods.morph.client.nametag.MorphNameTags.apply(avatar.getUUID(), source);
        var from = extractEndpoint(avatar, source, transition.from);
        var to = extractEndpoint(avatar, source, transition.to);
        if (from == null || to == null) return null;
        return new MorphTransitionRenderer.Frame(from, to, Math.clamp(elapsed / transition.duration, 0.0F, 1.0F));
    }

    private static net.minecraft.client.renderer.entity.state.EntityRenderState extractEndpoint(
            Avatar avatar, AvatarRenderState source, Endpoint endpoint) {
        if (endpoint.species.isEmpty()) return source;
        return endpoint.entry == null ? MorphRenderSnapshots.extract(avatar, source, endpoint.species)
                : MorphRenderSnapshots.extract(avatar, source, endpoint.entry);
    }
}
