package me.ichun.mods.morph.client.animation.mixin;

import me.ichun.mods.morph.client.animation.MorphSwimState;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IllagerModel.class)
public abstract class IllagerSwimMixin {
    @Shadow @Final private ModelPart rightLeg;
    @Shadow @Final private ModelPart leftLeg;
    @Shadow @Final private ModelPart rightArm;
    @Shadow @Final private ModelPart leftArm;
    @Shadow @Final private ModelPart arms;
    @Shadow @Final private ModelPart head;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/IllagerRenderState;)V", at = @At("TAIL"))
    private void morph$swim(IllagerRenderState state, CallbackInfo ci) {
        MorphSwimState swim = (MorphSwimState) state;
        if (swim.morph$isMorphAdapter())
            me.ichun.mods.morph.client.animation.MorphIllagerAnimation.applyWeaponHand(state, rightArm, leftArm, head);
        float blend = swim.morph$swimBlend();
        if (blend <= 0 || state.isRiding) return;
        float phase = state.ageInTicks * (swim.morph$fastSwimming() ? .55F : .3F);
        float kick = Mth.sin(phase) * .55F;
        rightLeg.xRot = Mth.lerp(blend, rightLeg.xRot, kick);
        leftLeg.xRot = Mth.lerp(blend, leftLeg.xRot, -kick);
        // Leave weapon-use, attack, casting and celebration poses intact.
        if (state.attackTime <= 0 && state.attackAnim <= 0
            && (state.leftArmPose == HumanoidModel.ArmPose.EMPTY || state.leftArmPose == HumanoidModel.ArmPose.ITEM)
            && (state.rightArmPose == HumanoidModel.ArmPose.EMPTY || state.rightArmPose == HumanoidModel.ArmPose.ITEM)
            && (state.armPose == AbstractIllager.IllagerArmPose.NEUTRAL
                || state.armPose == AbstractIllager.IllagerArmPose.CROSSED)) {
            arms.visible = false;
            rightArm.visible = true;
            leftArm.visible = true;
            rightArm.xRot = Mth.lerp(blend, rightArm.xRot, -1.4F + Mth.cos(phase) * .65F);
            leftArm.xRot = Mth.lerp(blend, leftArm.xRot, -1.4F - Mth.cos(phase) * .65F);
            rightArm.zRot = Mth.lerp(blend, rightArm.zRot, .25F);
            leftArm.zRot = Mth.lerp(blend, leftArm.zRot, -.25F);
        }
        if (swim.morph$fastSwimming()) head.xRot = Mth.lerp(blend, head.xRot, -1.15F);
    }
}
