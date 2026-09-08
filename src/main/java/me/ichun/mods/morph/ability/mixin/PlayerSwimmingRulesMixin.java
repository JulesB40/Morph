package me.ichun.mods.morph.ability.mixin;

import me.ichun.mods.morph.ability.MorphSwimmingRules;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
abstract class PlayerSwimmingRulesMixin {
    @Inject(method = "updateSwimming", at = @At("HEAD"), cancellable = true)
    private void morph$walkUnderwater(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (MorphSwimmingRules.blocksSwimming(player)) {
            player.setSwimming(false);
            ci.cancel();
        }
    }

    @Inject(method = "travel", at = @At("HEAD"))
    private void morph$underwaterMovement(Vec3 input, CallbackInfo ci) {
        MorphSwimmingRules.beforeTravel((Player) (Object) this);
    }
}
