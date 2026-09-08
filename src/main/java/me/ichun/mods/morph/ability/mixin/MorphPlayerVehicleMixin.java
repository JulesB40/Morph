package me.ichun.mods.morph.ability.mixin;
import me.ichun.mods.morph.ability.MorphInteractions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
/** Permit the normal mounting machinery only for eligible morphed player vehicles. */
@Mixin(Entity.class)
public abstract class MorphPlayerVehicleMixin {
    @Redirect(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EntityType;canSerialize()Z"))
    private boolean morph$allowPlayerVehicle(EntityType<?> type, Entity vehicle, boolean force, boolean sendEventAndTriggers) {
        return type.canSerialize() || ((Object)this instanceof Player && vehicle instanceof Player player
            && MorphInteractions.rideable(player));
    }
}
