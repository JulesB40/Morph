package me.ichun.mods.morph.client.animation;

import net.minecraft.client.renderer.entity.state.BreezeRenderState;
import net.minecraft.client.renderer.entity.state.CreakingRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Animation inputs normally maintained by native entity ticks, derived without running AI. */
public final class MorphNativeMotion {
    private MorphNativeMotion() {}

    public static void apply(LivingEntityRenderState state) {
        if (state instanceof BreezeRenderState breeze) {
            // Native Breeze.tick starts idle independently of shooting/jumping/sliding.
            // Reuse a stable epoch because render snapshots are recreated every frame.
            breeze.idle.startIfStopped(0);
        }
        if (state instanceof CreakingRenderState creaking) {
            // The player's actual walking phase is authoritative. The detached mob's
            // AI movement lock must not suppress it, nor does this permit player motion.
            creaking.canMove = state.deathTime == 0 && state.walkAnimationSpeed > 0;
        }
    }
}
