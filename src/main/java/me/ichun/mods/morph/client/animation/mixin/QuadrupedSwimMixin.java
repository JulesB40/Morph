package me.ichun.mods.morph.client.animation.mixin;
import me.ichun.mods.morph.client.animation.*;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(QuadrupedModel.class)
public class QuadrupedSwimMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)V", at = @At("TAIL"))
    private void morph$paddle(LivingEntityRenderState state, CallbackInfo ci) {
        if (state instanceof net.minecraft.client.renderer.entity.state.TurtleRenderState) return;
        float blend = ((MorphSwimState) state).morph$swimBlend();
        if (blend > 0) MorphSwimAnimation.apply(((Model<?>) (Object) this).root(), state.ageInTicks, blend, ((MorphSwimState) state).morph$fastSwimming());
    }
}
