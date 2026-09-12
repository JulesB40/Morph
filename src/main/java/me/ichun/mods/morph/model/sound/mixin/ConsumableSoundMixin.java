package me.ichun.mods.morph.model.sound.mixin;

import me.ichun.mods.morph.model.sound.MorphSoundForwarding;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Consumable.class)
abstract class ConsumableSoundMixin {
    @Redirect(method = "emitParticlesAndSounds", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;playSound(Lnet/minecraft/sounds/SoundEvent;FF)V"))
    private void morph$consume(LivingEntity consumer, SoundEvent sound, float volume, float pitch,
            RandomSource random, LivingEntity source, ItemStack stack, int particleCount) {
        consumer.playSound(MorphSoundForwarding.consume(consumer, stack, sound), volume, pitch);
    }
}
