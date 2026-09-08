package me.ichun.mods.morph.ability;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;

/** Server-authoritative transient traits, shared by both loaders. Call from the server thread. */
public final class MorphAbilities {
    private static final Map<ServerPlayer, FlightLease> FLIGHT = new WeakHashMap<>();
    private static Predicate<ServerPlayer> externalFlight = player -> false;

    private MorphAbilities() {}

    /** Integration hook for another mod that grants flight while a Morph lease is active. */
    public static void addExternalFlightResolver(Predicate<ServerPlayer> resolver) {
        externalFlight = externalFlight.or(java.util.Objects.requireNonNull(resolver));
    }

    /** Give up our claim without clearing flags when another provider explicitly takes over. */
    public static void preserveExternalFlight(ServerPlayer player) {
        FlightLease lease = FLIGHT.get(player);
        if (lease != null) lease.owned = false;
    }

    public static boolean preventsFallDamage(String form) { return FormTraits.forForm(form).fallImmunity(); }

    /** Run before the player's tick so air replenishment precedes drowning damage. */
    public static void tick(ServerPlayer player, String activeForm) {
        if (!player.isAlive()) {
            cleanup(player);
            return;
        }
        FormTraits traits = FormTraits.forForm(activeForm);
        if (traits.waterBreathing() && player.isUnderWater()) player.setAirSupply(player.getMaxAirSupply());
        if (traits.fallImmunity()) player.resetFallDistance();
        if (!traits.flight()) {
            cleanup(player);
            return;
        }
        if (hasExternalFlight(player)) {
            // Creative/spectator or a cooperating provider now owns the permission.
            FLIGHT.remove(player);
            return;
        }
        Abilities abilities = player.getAbilities();
        FlightLease lease = FLIGHT.get(player);
        var mode = player.gameMode.getGameModeForPlayer();
        if (lease != null && lease.gameMode != mode) {
            // Vanilla reapplies its ability defaults when switching survival/adventure modes.
            // This is not another provider revoking the lease.
            FLIGHT.remove(player);
            lease = null;
        }
        if (lease == null) {
            lease = new FlightLease(!abilities.mayfly, abilities.mayfly, abilities.flying, mode);
            FLIGHT.put(player, lease);
            if (lease.owned) {
                abilities.mayfly = true;
                // Do not force takeoff or change movement speed/invulnerability/build flags.
                player.onUpdateAbilities();
            }
        } else if (lease.owned && !abilities.mayfly) {
            // Respect another system revoking flight; do not fight it every tick.
            lease.owned = false;
        }
    }

    /** Call immediately on form reset/change, death and logout; never revoke a pre-existing grant. */
    public static void cleanup(ServerPlayer player) {
        FlightLease lease = FLIGHT.remove(player);
        if (lease == null || !lease.owned || hasExternalFlight(player)) return;
        Abilities abilities = player.getAbilities();
        if (!abilities.mayfly) return; // Another system has already changed the permission.
        abilities.mayfly = lease.baselineMayfly;
        abilities.flying = lease.baselineFlying;
        player.resetFallDistance();
        player.onUpdateAbilities();
    }

    /** Used only at the vanilla save callsite, never for network ability updates. */
    public static Abilities.Packed packForSave(Player player, Abilities abilities) {
        Abilities.Packed packed = abilities.pack();
        if (!(player instanceof ServerPlayer serverPlayer)) return packed;
        FlightLease lease = FLIGHT.get(serverPlayer);
        if (lease == null || !lease.owned || !abilities.mayfly || hasExternalFlight(serverPlayer)) return packed;
        return new Abilities.Packed(packed.invulnerable(), lease.baselineFlying, lease.baselineMayfly,
                packed.instabuild(), packed.mayBuild(), packed.flyingSpeed(), packed.walkingSpeed());
    }

    private static boolean hasExternalFlight(ServerPlayer player) {
        return player.isCreative() || player.isSpectator() || externalFlight.test(player);
    }

    private static final class FlightLease {
        private boolean owned;
        private final boolean baselineMayfly;
        private final boolean baselineFlying;
        private final net.minecraft.world.level.GameType gameMode;
        private FlightLease(boolean owned, boolean baselineMayfly, boolean baselineFlying,
                            net.minecraft.world.level.GameType gameMode) {
            this.owned = owned;
            this.baselineMayfly = baselineMayfly;
            this.baselineFlying = baselineFlying;
            this.gameMode = gameMode;
        }
    }
}
