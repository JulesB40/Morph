package me.ichun.mods.morph.model.sound.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface EntitySoundAccess {
    @Invoker("playStepSound") void morph$playStepSound(BlockPos pos, BlockState state);
    @Invoker("getSwimSound") SoundEvent morph$getSwimSound();
    @Invoker("getSwimSplashSound") SoundEvent morph$getSwimSplashSound();
    @Invoker("getSwimHighSpeedSplashSound") SoundEvent morph$getSwimHighSpeedSplashSound();
}
