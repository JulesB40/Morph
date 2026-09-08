package me.ichun.mods.morph.client.animation.mixin;

import me.ichun.mods.morph.client.animation.MorphSwimState;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerModel.class)
public abstract class VillagerSwimMixin {
    @Shadow @Final private ModelPart rightLeg;
    @Shadow @Final private ModelPart leftLeg;
    @Shadow @Final private ModelPart arms;
    @Shadow @Final private ModelPart head;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/VillagerRenderState;)V", at = @At("TAIL"))
    private void morph$swim(VillagerRenderState state, CallbackInfo ci) {
        MorphSwimState swim = (MorphSwimState) state;
        float blend = swim.morph$swimBlend();
        if (blend <= 0) return;
        float phase = state.ageInTicks * (swim.morph$fastSwimming() ? .55F : .3F);
        float kick = Mth.sin(phase) * .55F;
        rightLeg.xRot = Mth.lerp(blend, rightLeg.xRot, kick);
        leftLeg.xRot = Mth.lerp(blend, leftLeg.xRot, -kick);
        // Villagers have one joined arm mesh; keep it joined and paddle at the shoulders.
        arms.xRot = Mth.lerp(blend, arms.xRot, -1.2F + Mth.cos(phase) * .2F);
        if (swim.morph$fastSwimming()) head.xRot = Mth.lerp(blend, head.xRot, -1.15F);
    }
}
