package me.ichun.mods.morph.client.animation;

import net.minecraft.client.renderer.entity.state.BatRenderState;
import net.minecraft.client.renderer.entity.state.BeeRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.ChickenRenderState;
import net.minecraft.client.renderer.entity.state.ParrotRenderState;
import net.minecraft.client.model.animal.parrot.ParrotModel;

/** Drives native flying poses without ticking detached mob AI. */
public final class MorphFlyingAnimation {
    private MorphFlyingAnimation() {}

    public static void apply(LivingEntityRenderState state, boolean stationaryOnGround) {
        apply(state, stationaryOnGround, stationaryOnGround);
    }

    public static void apply(LivingEntityRenderState state, boolean onGround, boolean stationaryOnGround) {
        if (state instanceof BatRenderState bat) {
            // A player is not a ceiling-hanging bat. Start at a stable epoch so fresh
            // snapshots advance the native wing cycle instead of restarting each frame.
            bat.isResting = false;
            bat.restAnimationState.stop();
            if (!bat.flyAnimationState.isStarted()) bat.flyAnimationState.start(0);
        } else if (state instanceof BeeRenderState bee) {
            bee.isOnGround = stationaryOnGround;
        } else if (state instanceof ChickenRenderState chicken) {
            // Airborne native chickens/parrots advance flap by 1.8 radians per tick.
            // A stable epoch also works when extraction creates a fresh state each frame.
            chicken.flap = state.ageInTicks * 1.8F;
            chicken.flapSpeed = onGround || state.isInWater ? 0F : 1F;
        } else if (state instanceof ParrotRenderState parrot) {
            if (parrot.pose == ParrotModel.Pose.STANDING || parrot.pose == ParrotModel.Pose.FLYING) {
                parrot.pose = onGround || state.isInWater ? ParrotModel.Pose.STANDING : ParrotModel.Pose.FLYING;
                parrot.flapAngle = onGround || state.isInWater ? 0F : (float) Math.sin(state.ageInTicks * 1.8F) + 1F;
            }
        }
    }
}
