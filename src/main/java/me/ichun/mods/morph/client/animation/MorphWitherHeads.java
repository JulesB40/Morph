package me.ichun.mods.morph.client.animation;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.WitherRenderState;

/** Supplies side-head look angles that the detached Wither adapter cannot tick. */
public final class MorphWitherHeads {
    private MorphWitherHeads() {}

    public static void apply(LivingEntityRenderState state) {
        if (!(state instanceof WitherRenderState wither)) return;
        // Vanilla subtracts bodyRot from these world-space angles when posing each head.
        float worldYaw = wither.bodyRot + wither.yRot;
        for (int i = 0; i < wither.yHeadRots.length; i++) {
            wither.yHeadRots[i] = worldYaw;
            wither.xHeadRots[i] = wither.xRot;
        }
    }
}
