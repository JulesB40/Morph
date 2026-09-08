package me.ichun.mods.morph.ability.mixin;

import me.ichun.mods.morph.ability.MorphAttributes;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public abstract class LivingEntityHealthSaveMixin {
    @Redirect(method = "addAdditionalSaveData", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getHealth()F"))
    private float morph$saveUnmorphedHealth(LivingEntity entity) {
        return MorphAttributes.healthForSave(entity);
    }
}
