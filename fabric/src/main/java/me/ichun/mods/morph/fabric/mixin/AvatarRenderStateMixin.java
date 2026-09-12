package me.ichun.mods.morph.fabric.mixin;
import me.ichun.mods.morph.fabric.client.MorphSnapshotHolder;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
@Mixin(AvatarRenderState.class)
public abstract class AvatarRenderStateMixin implements MorphSnapshotHolder {
    @Unique private me.ichun.mods.morph.client.transition.MorphTransitionRenderer.Frame morph$transition;
    public me.ichun.mods.morph.client.transition.MorphTransitionRenderer.Frame morph$getTransition() { return morph$transition; }
    public void morph$setTransition(me.ichun.mods.morph.client.transition.MorphTransitionRenderer.Frame transition) { morph$transition = transition; }
    @Unique private EntityRenderState morph$snapshot;
    public EntityRenderState morph$getSnapshot() { return morph$snapshot; }
    public void morph$setSnapshot(EntityRenderState snapshot) { morph$snapshot = snapshot; }
}
