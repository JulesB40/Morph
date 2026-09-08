package me.ichun.mods.morph.fabric.client;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
public interface MorphSnapshotHolder {
    LivingEntityRenderState morph$getSnapshot();
    void morph$setSnapshot(LivingEntityRenderState snapshot);
}
