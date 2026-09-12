package me.ichun.mods.morph.fabric.client;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
public interface MorphSnapshotHolder {
    me.ichun.mods.morph.client.transition.MorphTransitionRenderer.Frame morph$getTransition();
    void morph$setTransition(me.ichun.mods.morph.client.transition.MorphTransitionRenderer.Frame transition);
    EntityRenderState morph$getSnapshot();
    void morph$setSnapshot(EntityRenderState snapshot);
}
