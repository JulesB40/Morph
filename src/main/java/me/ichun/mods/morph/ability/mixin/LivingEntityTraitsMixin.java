package me.ichun.mods.morph.ability.mixin;

import me.ichun.mods.morph.ability.MorphTraits;
import me.ichun.mods.morph.shape.ShapeHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.phys.Vec3;

@Mixin(LivingEntity.class)
abstract class LivingEntityTraitsMixin {
    @Inject(method = "travel", at = @At("RETURN"))
    private void morph$movement(Vec3 input, CallbackInfo ci) {
        if ((Object) this instanceof Player player) MorphTraits.movement(player);
    }
    @Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
    private void morph$climb(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player && !player.isSpectator()
                && player.horizontalCollision && MorphTraits.climbs(ShapeHooks.form(player))) cir.setReturnValue(true);
    }

    @Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
    private void morph$resist(MobEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player && MorphTraits.rejectsEffect(player, effect)) cir.setReturnValue(false);
    }

    @Inject(method = "hurtServer", at = @At("RETURN"))
    private void morph$attackEffect(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (amount > 0 && cir.getReturnValueZ()) MorphTraits.afterDamage((LivingEntity) (Object) this, source);
    }
}
