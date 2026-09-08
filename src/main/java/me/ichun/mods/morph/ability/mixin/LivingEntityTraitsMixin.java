package me.ichun.mods.morph.ability.mixin;

import me.ichun.mods.morph.ability.MorphTraits;
import me.ichun.mods.morph.shape.ShapeHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.phys.Vec3;

@Mixin(LivingEntity.class)
abstract class LivingEntityTraitsMixin {
    @Shadow protected boolean jumping;

    @Inject(method = "isInvertedHealAndHarm", at = @At("HEAD"), cancellable = true)
    private void morph$undeadHealing(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player && morph$hasTag(player, net.minecraft.tags.EntityTypeTags.INVERTED_HEALING_AND_HARM))
            cir.setReturnValue(true);
    }

    @Inject(method = "canFreeze", at = @At("HEAD"), cancellable = true)
    private void morph$freezeImmunity(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player && ("minecraft:skeleton".equals(ShapeHooks.form(player))
                || morph$hasTag(player, net.minecraft.tags.EntityTypeTags.FREEZE_IMMUNE_ENTITY_TYPES)))
            cir.setReturnValue(false);
    }

    @Inject(method = "canBreatheUnderwater", at = @At("HEAD"), cancellable = true)
    private void morph$underwaterBreathing(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player && me.ichun.mods.morph.ability.FormTraits.forForm(ShapeHooks.form(player)).waterBreathing())
            cir.setReturnValue(true);
    }

    @org.spongepowered.asm.mixin.Unique
    private static boolean morph$hasTag(Player player, net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> tag) {
        String form = ShapeHooks.form(player);
        var id = form == null ? null : net.minecraft.resources.Identifier.tryParse(form);
        return id != null && net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(id)
                .map(holder -> holder.is(tag)).orElse(false);
    }

    @Inject(method = "getDamageAfterMagicAbsorb", at = @At("RETURN"), cancellable = true)
    private void morph$witchResistance(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof Player player && "minecraft:witch".equals(ShapeHooks.form(player))) {
            if (source.getEntity() == player) cir.setReturnValue(0F);
            else if (source.is(net.minecraft.tags.DamageTypeTags.WITCH_RESISTANT_TO))
                cir.setReturnValue(cir.getReturnValueF() * .15F);
        }
    }

    @Redirect(method = "aiStep", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;jumping:Z", opcode = 180))
    private boolean morph$voluntaryWaterJump(LivingEntity entity) {
        return jumping && (!(entity instanceof Player player)
                || me.ichun.mods.morph.ability.MorphSwimmingRules.allowsJumpInput(player));
    }
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
