package me.ichun.mods.morph.ability;

import java.util.Map;
import java.util.WeakHashMap;
import me.ichun.mods.morph.shape.ShapeHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;

/** Validated server-side active abilities. A packet never chooses its form or impulse. */
public final class MorphActions {
    private static final Map<ServerPlayer, Long> LAST_FLAP = new WeakHashMap<>();
    private MorphActions() {}
    public static void cleanup(ServerPlayer player) { LAST_FLAP.remove(player); }

    public static double flapImpulse(String form) {
        if (form == null) return 0;
        return switch (form) {
            case "minecraft:parrot", "minecraft:vex" -> 0.42;
            case "minecraft:phantom" -> 0.52;
            case "minecraft:ender_dragon" -> 1.2;
            default -> 0;
        };
    }

    public static boolean flap(ServerPlayer player) {
        double impulse = flapImpulse(ShapeHooks.form(player));
        if (impulse == 0 || !player.isAlive() || player.isSpectator() || player.onGround()
                || player.isPassenger() || player.getAbilities().flying) return false;
        long now = player.level().getGameTime();
        Long last = LAST_FLAP.get(player);
        // A new press requires a release tick; duplicate packets cannot stack velocity.
        if (last != null && now - last < 2) return false;
        LAST_FLAP.put(player, now);
        double fluidHeight = player.getFluidHeight(player.isInLava() ? FluidTags.LAVA : FluidTags.WATER);
        if ((player.isInWater() || player.isInLava()) && fluidHeight > player.getFluidJumpThreshold()) {
            impulse = 0.04;
        } else {
            float factor = player.level().getBlockState(player.blockPosition()).getBlock().getJumpFactor();
            if (factor == 1) factor = player.level().getBlockState(BlockPos.containing(player.getX(),
                    player.getBoundingBox().minY - 0.5000001, player.getZ())).getBlock().getJumpFactor();
            impulse *= factor;
            var boost = player.getEffect(MobEffects.JUMP_BOOST);
            if (boost != null) impulse += 0.1 * (boost.getAmplifier() + 1);
        }
        player.setDeltaMovement(player.getDeltaMovement().add(0, impulse, 0));
        player.fallDistance = 0;
        player.hurtMarked = true;
        player.connection.resetFlyingTicks();
        return true;
    }

    public static void tick(ServerPlayer player, String form) {
        if (flapImpulse(form) == 0 || !player.isAlive() || player.isSpectator()) {
            LAST_FLAP.remove(player);
            return;
        }
        player.fallDistance = 0;
        player.connection.resetFlyingTicks();
    }
}
