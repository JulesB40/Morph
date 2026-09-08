package me.ichun.mods.morph.client.animation.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import me.ichun.mods.morph.client.animation.MorphSwimState;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class VillagerFamilySwimRotationMixin {
    @Inject(method = "setupRotations", at = @At("TAIL"))
    private void morph$horizontalSwim(LivingEntityRenderState state, PoseStack poses, float bodyRot, float scale, CallbackInfo ci) {
        if (!(state instanceof VillagerRenderState) && !(state instanceof IllagerRenderState)) return;
        MorphSwimState swim = (MorphSwimState) state;
        if (!swim.morph$fastSwimming() || swim.morph$swimBlend() <= 0) return;
        poses.mulPose(Axis.XP.rotationDegrees(swim.morph$swimBlend() * (-90F - state.xRot)));
        poses.translate(0F, -1F, .3F);
    }
}
