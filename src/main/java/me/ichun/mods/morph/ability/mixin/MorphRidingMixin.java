package me.ichun.mods.morph.ability.mixin;
import me.ichun.mods.morph.ability.MorphInteractions;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Player.class)
public abstract class MorphRidingMixin {
    @Inject(method = "interactOn", at = @At("HEAD"), cancellable = true)
    private void morph$mount(Entity target, InteractionHand hand, Vec3 location, CallbackInfoReturnable<InteractionResult> cir) {
        Player rider = (Player)(Object)this;
        if (!(target instanceof Player mount) || rider == mount || rider.isSpectator() || rider.isPassenger()
                || rider.isShiftKeyDown() || !mount.getPassengers().isEmpty() || !MorphInteractions.rideable(mount)) return;
        if (rider.level().isClientSide()) { cir.setReturnValue(InteractionResult.SUCCESS); return; }
        if (rider.startRiding(mount)) {
            MorphInteractions.mounted(mount);
            ((ServerPlayer)mount).connection.send(new ClientboundSetPassengersPacket(mount));
            cir.setReturnValue(InteractionResult.SUCCESS);
        }
    }
}
