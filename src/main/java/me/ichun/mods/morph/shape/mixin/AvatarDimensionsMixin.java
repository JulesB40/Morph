package me.ichun.mods.morph.shape.mixin;

import me.ichun.mods.morph.shape.ShapeHooks;

import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** All pose-fit, dismount and refresh queries must agree about the player's dimensions. */
@Mixin(Avatar.class)
public abstract class AvatarDimensionsMixin {
    @Inject(method = "getDefaultDimensions", at = @At("RETURN"), cancellable = true)
    private void morph$dimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> result) {
        if ((Object) this instanceof Player player) {
            result.setReturnValue(ShapeHooks.dimensions(player, pose, result.getReturnValue()));
        }
    }
}
