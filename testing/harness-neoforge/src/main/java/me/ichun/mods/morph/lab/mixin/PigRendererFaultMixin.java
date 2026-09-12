package me.ichun.mods.morph.lab.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.ichun.mods.morph.lab.LabRendererFaults;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.PigRenderer;
import net.minecraft.client.renderer.entity.state.PigRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.animal.pig.Pig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Loaded exclusively by the test mod's client mixin configuration. */
@Mixin(PigRenderer.class)
public abstract class PigRendererFaultMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/animal/pig/Pig;Lnet/minecraft/client/renderer/entity/state/PigRenderState;F)V", at = @At("HEAD"))
    private void morphLab$extract(Pig pig, PigRenderState state, float partialTick, CallbackInfo ci) {
        LabRendererFaults.check(LabRendererFaults.Stage.EXTRACTION);
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/PigRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"))
    private void morphLab$submit(PigRenderState state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        LabRendererFaults.check(LabRendererFaults.Stage.SUBMISSION);
    }
}
