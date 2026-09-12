package me.ichun.mods.morph.client.animation;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Copies player-controlled poses after native extraction, without ticking the adapter. */
public final class MorphPlayerPoses {
    private MorphPlayerPoses() {}

    public static void apply(AvatarRenderState player, LivingEntityRenderState target) {
        target.pose = player.pose;
        target.bedOrientation = player.bedOrientation;
        target.isAutoSpinAttack = player.isAutoSpinAttack;
        if (target instanceof HumanoidRenderState humanoid) {
            humanoid.isCrouching = player.isCrouching;
            humanoid.isPassenger = player.isPassenger;
            humanoid.isFallFlying = player.isFallFlying;
            humanoid.speedValue = player.speedValue;
        }
        if (target instanceof IllagerRenderState illager) {
            illager.isRiding = player.isPassenger;
        }
    }
}
