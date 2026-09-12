package me.ichun.mods.morph.model.sound.mixin;

import me.ichun.mods.morph.model.sound.MorphSoundForwarding;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class LivingSoundMixin {
    @Inject(method = "getSoundVolume", at = @At("HEAD"), cancellable = true)
    private void morph$volume(CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof Player player) {
            var answer = MorphSoundForwarding.query(player, entity -> ((LivingSoundAccess) entity).morph$getSoundVolume());
            if (answer.replaced()) cir.setReturnValue(answer.value());
        }
    }

    @Inject(method = "getVoicePitch", at = @At("HEAD"), cancellable = true)
    private void morph$pitch(CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof Player player) {
            var answer = MorphSoundForwarding.query(player, LivingEntity::getVoicePitch);
            if (answer.replaced()) cir.setReturnValue(answer.value());
        }
    }
}
