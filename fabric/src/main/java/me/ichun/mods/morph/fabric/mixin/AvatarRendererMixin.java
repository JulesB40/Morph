package me.ichun.mods.morph.fabric.mixin;
import me.ichun.mods.morph.fabric.client.MorphSnapshotHolder;
import me.ichun.mods.morph.client.MorphRenderSnapshots;
import me.ichun.mods.morph.fabric.client.MorphFabricClient;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void morph$extract(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        if (me.ichun.mods.morph.client.transition.MorphTransitionRenderer.isRendering()) return;
        var holder = (MorphSnapshotHolder) state;
        var transition = me.ichun.mods.morph.client.MorphTransitions.extract(avatar, state, MorphFabricClient.FORMS.get(avatar.getUUID()));
        holder.morph$setTransition(transition);
        holder.morph$setSnapshot(transition == null ? MorphRenderSnapshots.extract(avatar, state, MorphFabricClient.FORMS.get(avatar.getUUID())) : null);
    }
}
