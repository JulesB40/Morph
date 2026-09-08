package me.ichun.mods.morph.client.animation;

import net.minecraft.client.renderer.entity.state.BatRenderState;
import net.minecraft.client.renderer.entity.state.BeeRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Drives native flying poses without ticking detached mob AI. */
public final class MorphFlyingAnimation {
    private MorphFlyingAnimation() {}

    public static void apply(LivingEntityRenderState state, boolean stationaryOnGround) {
        if (state instanceof BatRenderState bat) {
            // A player is not a ceiling-hanging bat. Start at a stable epoch so fresh
            // snapshots advance the native wing cycle instead of restarting each frame.
            bat.isResting = false;
            bat.restAnimationState.stop();
            if (!bat.flyAnimationState.isStarted()) bat.flyAnimationState.start(0);
        } else if (state instanceof BeeRenderState bee) {
            bee.isOnGround = stationaryOnGround;
        }
    }
}
