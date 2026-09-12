package me.ichun.mods.morph.model.sound.mixin;

import me.ichun.mods.morph.model.sound.MorphSoundForwarding;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
abstract class EntitySoundCaptureMixin {
    @Inject(method = "playSound(Lnet/minecraft/sounds/SoundEvent;FF)V", at = @At("HEAD"), cancellable = true)
    private void morph$capture(SoundEvent sound, float volume, float pitch, CallbackInfo ci) {
        if (MorphSoundForwarding.capture((Entity) (Object) this, sound, volume, pitch)) ci.cancel();
    }
}
