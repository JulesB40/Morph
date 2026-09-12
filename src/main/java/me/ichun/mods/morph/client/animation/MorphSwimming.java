package me.ichun.mods.morph.client.animation;

import net.minecraft.client.renderer.entity.state.*;
import net.minecraft.world.entity.Avatar;

public final class MorphSwimming {
    private MorphSwimming() {}
    public static void extract(Avatar avatar, AvatarRenderState source, LivingEntityRenderState target, String formId) {
        ((MorphSwimState) target).morph$setMorphAdapter(true);
        boolean sinkingUndead = !me.ichun.mods.morph.ability.MorphSwimmingRules.canSwim(formId);
        if (sinkingUndead && target.pose == net.minecraft.world.entity.Pose.SWIMMING)
            target.pose = net.minecraft.world.entity.Pose.STANDING;
        if (target instanceof HumanoidRenderState humanoid) {
            humanoid.swimAmount = sinkingUndead ? 0F : source.swimAmount;
            humanoid.isVisuallySwimming = !sinkingUndead && source.isVisuallySwimming;
        }
        if (target instanceof DolphinRenderState dolphin) {
            dolphin.isMoving = avatar.getDeltaMovement().horizontalDistanceSqr() > 1.0E-7;
        }
        if (target instanceof TurtleRenderState turtle) {
            turtle.isOnLand = !source.isInWater && avatar.onGround();
            turtle.isLayingEgg = false;
        }
        // Vanilla swimAmount smooths entry; dry land immediately restores its native pose.
        // Walking water movement also paddles.
        float movement = Math.min(1F, (float) avatar.getDeltaMovement().length() * 8F);
        float blend = !sinkingUndead && source.isInWater && source.deathTime == 0 ? Math.max(source.swimAmount, movement) : 0F;
        ((MorphSwimState) target).morph$setSwimBlend(blend);
        ((MorphSwimState) target).morph$setFastSwimming(!sinkingUndead && source.isInWater && source.isVisuallySwimming);
        MorphAquaticSwimming.extract(avatar, source, target);
    }
}
