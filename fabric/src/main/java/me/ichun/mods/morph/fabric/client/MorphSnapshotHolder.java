package me.ichun.mods.morph.fabric.client;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
public interface MorphSnapshotHolder {
    me.ichun.mods.morph.client.transition.MorphTransitionRenderer.Frame morph$getTransition();
    void morph$setTransition(me.ichun.mods.morph.client.transition.MorphTransitionRenderer.Frame transition);
    LivingEntityRenderState morph$getSnapshot();
    void morph$setSnapshot(LivingEntityRenderState snapshot);
}
