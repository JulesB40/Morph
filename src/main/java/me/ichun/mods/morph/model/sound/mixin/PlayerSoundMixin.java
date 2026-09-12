package me.ichun.mods.morph.model.sound.mixin;

import me.ichun.mods.morph.model.sound.MorphSoundForwarding;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
abstract class PlayerSoundMixin {
    @Inject(method = "playStepSound", at = @At("HEAD"), cancellable = true)
    private void morph$step(BlockPos pos, BlockState block, CallbackInfo ci) {
        if (MorphSoundForwarding.step((Player) (Object) this, pos, block)) ci.cancel();
    }

    @Inject(method = "getHurtSound", at = @At("HEAD"), cancellable = true)
    private void morph$hurt(DamageSource source, CallbackInfoReturnable<SoundEvent> cir) {
        var answer = MorphSoundForwarding.query((Player) (Object) this,
                entity -> ((LivingSoundAccess) entity).morph$getHurtSound(source));
        if (answer.replaced()) cir.setReturnValue(answer.value());
    }

    @Inject(method = "getDeathSound", at = @At("HEAD"), cancellable = true)
    private void morph$death(CallbackInfoReturnable<SoundEvent> cir) {
        var answer = MorphSoundForwarding.query((Player) (Object) this,
                entity -> ((LivingSoundAccess) entity).morph$getDeathSound());
        if (answer.replaced()) cir.setReturnValue(answer.value());
    }

    @Inject(method = "getFallSounds", at = @At("HEAD"), cancellable = true)
    private void morph$fall(CallbackInfoReturnable<LivingEntity.Fallsounds> cir) {
        var answer = MorphSoundForwarding.query((Player) (Object) this, LivingEntity::getFallSounds);
        if (answer.replaced()) cir.setReturnValue(answer.value());
    }

    @Inject(method = "getSwimSound", at = @At("HEAD"), cancellable = true)
    private void morph$swim(CallbackInfoReturnable<SoundEvent> cir) {
        var answer = MorphSoundForwarding.query((Player) (Object) this,
                entity -> ((EntitySoundAccess) entity).morph$getSwimSound());
        if (answer.replaced()) cir.setReturnValue(answer.value());
    }

    @Inject(method = "getSwimSplashSound", at = @At("HEAD"), cancellable = true)
    private void morph$splash(CallbackInfoReturnable<SoundEvent> cir) {
        var answer = MorphSoundForwarding.query((Player) (Object) this,
                entity -> ((EntitySoundAccess) entity).morph$getSwimSplashSound());
        if (answer.replaced()) cir.setReturnValue(answer.value());
    }

    @Inject(method = "getSwimHighSpeedSplashSound", at = @At("HEAD"), cancellable = true)
    private void morph$fastSplash(CallbackInfoReturnable<SoundEvent> cir) {
        var answer = MorphSoundForwarding.query((Player) (Object) this,
                entity -> ((EntitySoundAccess) entity).morph$getSwimHighSpeedSplashSound());
        if (answer.replaced()) cir.setReturnValue(answer.value());
    }
}
