package me.ichun.mods.morph.client.animation.mixin;

import me.ichun.mods.morph.client.animation.MorphOtherSwimming;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

/** Runs after the concrete model's complete animation, including subclass overrides. */
@Mixin(ModelFeatureRenderer.class)
public abstract class ModelFamilySwimMixin {
    @WrapOperation(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V"))
    private <S> void morph$familyPaddles(Model<S> model, S state, Operation<Void> original) {
        original.call(model, state);
        MorphOtherSwimming.apply(model, state);
    }
}
