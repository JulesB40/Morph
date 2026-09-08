package me.ichun.mods.morph.fabric.mixin;
import me.ichun.mods.morph.fabric.client.MorphSnapshotHolder;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
@Mixin(AvatarRenderState.class)
public abstract class AvatarRenderStateMixin implements MorphSnapshotHolder {
    @Unique private LivingEntityRenderState morph$snapshot;
    public LivingEntityRenderState morph$getSnapshot() { return morph$snapshot; }
    public void morph$setSnapshot(LivingEntityRenderState snapshot) { morph$snapshot = snapshot; }
}
