package me.ichun.mods.morph.client.animation.mixin;
import me.ichun.mods.morph.client.animation.MorphSwimState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
@Mixin(LivingEntityRenderState.class)
public class LivingRenderSwimStateMixin implements MorphSwimState {
    @Unique private boolean morph$adapter;
    @Unique private float morph$blend;
    @Unique private boolean morph$fast;
    public boolean morph$isMorphAdapter() { return morph$adapter; }
    public void morph$setMorphAdapter(boolean adapter) { morph$adapter = adapter; }
    public boolean morph$fastSwimming() { return morph$fast; }
    public void morph$setFastSwimming(boolean fast) { morph$fast = fast; }
    public float morph$swimBlend() { return morph$blend; }
    public void morph$setSwimBlend(float blend) { morph$blend = blend; }
}
