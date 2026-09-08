package me.ichun.mods.morph.ability.mixin;

import me.ichun.mods.morph.ability.MorphTraits;
import me.ichun.mods.morph.shape.ShapeHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Entity.class)
abstract class EntitySwimMixin {
    @ModifyVariable(method = "moveRelative", at = @At("HEAD"), argsOnly = true)
    private float morph$swimAcceleration(float speed) {
        if ((Object) this instanceof Player player && !player.isSpectator()
                && !player.getAbilities().flying && player.isInWater()) {
            return (float)(speed * MorphTraits.swimMultiplier(ShapeHooks.form(player)));
        }
        return speed;
    }
}
