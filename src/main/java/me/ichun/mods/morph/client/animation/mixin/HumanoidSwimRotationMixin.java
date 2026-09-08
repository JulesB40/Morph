package me.ichun.mods.morph.client.animation.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import me.ichun.mods.morph.client.animation.MorphSwimState;
import net.minecraft.client.renderer.entity.DrownedRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LivingEntityRenderer.class)
public class HumanoidSwimRotationMixin {
    @Inject(method = "setupRotations", at = @At("TAIL"))
    private void morph$swimRotation(LivingEntityRenderState state, PoseStack poses, float bodyRot, float scale, CallbackInfo ci) {
        // IllagerRenderState inherits HumanoidRenderState through UndeadRenderState,
        // but its dedicated villager-family hook supplies this rotation.
        if (!(state instanceof HumanoidRenderState humanoid) || state instanceof AvatarRenderState || state instanceof IllagerRenderState
            || (Object) this instanceof DrownedRenderer || ((MorphSwimState) state).morph$swimBlend() <= 0
            || humanoid.swimAmount <= 0 || humanoid.isFallFlying) return;
        poses.mulPose(Axis.XP.rotationDegrees(humanoid.swimAmount * (state.isInWater ? -90F - state.xRot : -90F)));
        if (humanoid.isVisuallySwimming) poses.translate(0F, -1F, .3F);
    }
}
