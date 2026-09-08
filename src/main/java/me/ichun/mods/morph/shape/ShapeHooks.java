package me.ichun.mods.morph.shape;

import java.util.function.Function;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

/** Loader entrypoints install a sided resolver; no client class is loaded on a server. */
public final class ShapeHooks {
    private static final ThreadLocal<Boolean> VANILLA_QUERY = ThreadLocal.withInitial(() -> false);
    private static final java.util.Map<Player, Boolean> READY =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static volatile Function<Player, String> formResolver = player -> null;
    private static volatile Function<Player, String> clientFormResolver = player -> null;

    private ShapeHooks() {}

    public static void setFormResolver(Function<Player, String> resolver) {
        formResolver = java.util.Objects.requireNonNull(resolver);
    }

    public static void setClientFormResolver(Function<Player, String> resolver) {
        clientFormResolver = java.util.Objects.requireNonNull(resolver);
    }

    public static EntityDimensions dimensions(Player player, Pose pose, EntityDimensions vanilla) {
        if (VANILLA_QUERY.get() || !READY.containsKey(player)) return vanilla; // Avatar constructor invokes this method.
        var resolver = player.level().isClientSide() ? clientFormResolver : formResolver;
        return MorphDimensions.forPose(player.level(), resolver.apply(player), pose, vanilla);
    }

    public static void refresh(Player player) {
        READY.put(player, Boolean.TRUE);
        player.refreshDimensions();
    }

    /** Check before changing authoritative state. An empty form means returning to the player. */
    public static boolean canFit(Player player, String nextForm) {
        // Avatar exposes its vanilla defaults, but the mixin must be bypassed for this query.
        EntityDimensions vanilla;
        VANILLA_QUERY.set(true);
        try {
            vanilla = player.getDefaultDimensions(player.getPose());
        } finally {
            VANILLA_QUERY.remove();
        }
        EntityDimensions target = MorphDimensions.forPose(player.level(), nextForm, player.getPose(), vanilla)
                .scale(player.getScale());
        return player.level().noCollision(player, target.makeBoundingBox(player.position()).deflate(1.0E-7));
    }

}
