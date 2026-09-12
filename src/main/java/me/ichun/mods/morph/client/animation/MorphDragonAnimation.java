package me.ichun.mods.morph.client.animation;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.entity.state.EnderDragonRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.LivingEntity;

/** Native dragon flap inputs without boss AI, world events or detached entity ticks. */
public final class MorphDragonAnimation {
    private static final Map<LivingEntity, Clock> CLOCKS = new WeakHashMap<>();

    private MorphDragonAnimation() {}

    public static void apply(Avatar avatar, LivingEntity adapter, EnderDragonRenderState state, float partialTick) {
        var motion = avatar.getDeltaMovement();
        state.flapTime = CLOCKS.computeIfAbsent(adapter, ignored -> new Clock()).sample(avatar.tickCount,
            partialTick, motion.horizontalDistance(), motion.y, avatar.onGround(), state.deathTime == 0);
    }

    static final class Clock {
        private boolean initialized;
        private int tick;
        private float previous;
        private float current;

        float sample(int tick, float partialTick, double horizontalSpeed, double verticalSpeed, boolean grounded, boolean alive) {
            if (!initialized || tick < this.tick) {
                initialized = true;
                this.tick = tick;
                previous = 0;
                current = alive ? rate(horizontalSpeed, verticalSpeed, grounded) : 0;
            } else if (tick > this.tick) {
                // An adapter may not have been rendered for a while. Bound catch-up,
                // but interpolate only the latest tick, never a multi-tick jump.
                long elapsed = Math.min((long) tick - this.tick, 5L);
                float rate = alive ? rate(horizontalSpeed, verticalSpeed, grounded) : 0;
                previous = wrap(current + rate * (elapsed - 1));
                current = previous + rate;
                this.tick = tick;
            }
            float partial = Float.isFinite(partialTick) ? Math.clamp(partialTick, 0F, 1F) : 0F;
            return wrap(previous + (current - previous) * partial);
        }
    }

    static float rate(double horizontalSpeed, double verticalSpeed, boolean grounded) {
        // Ground contact uses the native resting rate without setting a boss sitting phase.
        if (grounded) return 0.1F;
        if (!Double.isFinite(horizontalSpeed) || !Double.isFinite(verticalSpeed) || horizontalSpeed < 0) return 0;
        float rate = 0.2F / ((float) horizontalSpeed * 10F + 1F);
        rate *= (float) Math.pow(2.0, Math.clamp(verticalSpeed, -16.0, 16.0));
        // Keep extreme external velocities from overflowing or losing phase precision.
        return Math.clamp(rate, 0F, 4F);
    }

    private static float wrap(float phase) {
        return phase - (float) Math.floor(phase);
    }
}
