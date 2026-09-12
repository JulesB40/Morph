package me.ichun.mods.morph.shape;

import java.util.function.Function;
import me.ichun.mods.morph.model.FormDescriptor;
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
    private static volatile Function<Player, FormDescriptor> descriptorResolver;
    private static volatile Function<Player, FormDescriptor> clientDescriptorResolver;

    private ShapeHooks() {}

    public static void setFormResolver(Function<Player, String> resolver) {
        formResolver = java.util.Objects.requireNonNull(resolver);
    }

    public static void setClientFormResolver(Function<Player, String> resolver) {
        clientFormResolver = java.util.Objects.requireNonNull(resolver);
    }

    public static void setDescriptorResolver(Function<Player, FormDescriptor> resolver) {
        descriptorResolver = java.util.Objects.requireNonNull(resolver);
    }

    public static void setClientDescriptorResolver(Function<Player, FormDescriptor> resolver) {
        clientDescriptorResolver = java.util.Objects.requireNonNull(resolver);
    }

    public static FormDescriptor descriptor(Player player) {
        var resolver = player.level().isClientSide() ? clientDescriptorResolver : descriptorResolver;
        if (resolver != null) return resolver.apply(player);
        String species = form(player);
        return species == null || species.isEmpty() ? null : FormDescriptor.species(species);
    }

    public static EntityDimensions dimensions(Player player, Pose pose, EntityDimensions vanilla) {
        if (VANILLA_QUERY.get() || !READY.containsKey(player)) return vanilla; // Avatar constructor invokes this method.
        return MorphDimensions.forPose(player.level(), descriptor(player), pose, vanilla);
    }

    public static String form(Player player) {
        return (player.level().isClientSide() ? clientFormResolver : formResolver).apply(player);
    }

    public static void refresh(Player player) {
        READY.put(player, Boolean.TRUE);
        player.refreshDimensions();
    }

    /** Check before changing authoritative state. An empty form means returning to the player. */
    public static boolean canFit(Player player, String nextForm) {
        return canFit(player, nextForm == null || nextForm.isEmpty() ? null : FormDescriptor.species(nextForm));
    }

    public static boolean canFit(Player player, FormDescriptor descriptor) {
        // Avatar exposes its vanilla defaults, but the mixin must be bypassed for this query.
        EntityDimensions vanilla;
        VANILLA_QUERY.set(true);
        try {
            vanilla = player.getDefaultDimensions(player.getPose());
        } finally {
            VANILLA_QUERY.remove();
        }
        EntityDimensions target = MorphDimensions.forPose(player.level(), descriptor, player.getPose(), vanilla)
                .scale(player.getScale());
        return player.level().noCollision(player, target.makeBoundingBox(player.position()).deflate(1.0E-7));
    }

}
