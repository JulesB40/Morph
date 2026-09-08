package me.ichun.mods.morph.client.animation.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import me.ichun.mods.morph.client.animation.MorphOtherSwimming;
import me.ichun.mods.morph.client.animation.MorphSwimState;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Non-bipeds retain their native orientation; fast swimming only follows their look pitch. */
@Mixin(LivingEntityRenderer.class)
public abstract class OtherSwimRotationMixin {
    @Inject(method = "setupRotations", at = @At("TAIL"))
    private void morph$otherPitch(LivingEntityRenderState state, PoseStack poses, float rotation, float scale, CallbackInfo ci) {
        if (!(state instanceof MorphSwimState swim) || swim.morph$swimBlend() <= 0 || !swim.morph$fastSwimming()
                || state instanceof AvatarRenderState || state instanceof HumanoidRenderState
                || state instanceof VillagerRenderState || state instanceof IllagerRenderState
                || state.deathTime > 0 || state.isUpsideDown || MorphOtherSwimming.hasNativeAquaticPose(state)) return;
        poses.mulPose(Axis.XP.rotationDegrees(-Math.clamp(state.xRot, -55F, 55F) * swim.morph$swimBlend()));
    }
}
