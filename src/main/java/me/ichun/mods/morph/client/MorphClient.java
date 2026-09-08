package me.ichun.mods.morph.client;

import me.ichun.mods.morph.Morph;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Avatar;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only entrypoint; safe to omit entirely on dedicated servers. */
@Mod(value = Morph.MOD_ID, dist = Dist.CLIENT)
public final class MorphClient {
    private static final ContextKey<me.ichun.mods.morph.client.transition.MorphTransitionRenderer.Frame> TRANSITION =
            new ContextKey<>(Identifier.fromNamespaceAndPath(Morph.MOD_ID, "transition_render_state"));
    private static final ContextKey<LivingEntityRenderState> MORPH_STATE =
            new ContextKey<>(Identifier.fromNamespaceAndPath(Morph.MOD_ID, "form_render_state"));

    public MorphClient(IEventBus modBus) {
        me.ichun.mods.morph.shape.ShapeHooks.setClientFormResolver(player -> ClientMorphState.lookup(player.getUUID()));
        me.ichun.mods.morph.ui.MorphUi.initialize(modBus);
        modBus.addListener(MorphClient::registerModifiers);
        modBus.addListener((net.neoforged.neoforge.client.event.AddClientReloadListenersEvent event) ->
            event.addListener(Identifier.fromNamespaceAndPath(Morph.MOD_ID, "swim_animation"),
                (net.minecraft.server.packs.resources.ResourceManagerReloadListener)
                    me.ichun.mods.morph.client.animation.MorphSwimAnimation::reload));
        NeoForge.EVENT_BUS.addListener(MorphClient::renderPlayer);
        NeoForge.EVENT_BUS.addListener(MorphClient::loggedOut);
    }

    private static void registerModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerAvatarEntityModifier(new AvatarRenderStateModifier() {
            @Override
            public <T extends Avatar & ClientAvatarEntity> void accept(T avatar, AvatarRenderState state) {
                if (me.ichun.mods.morph.client.transition.MorphTransitionRenderer.isRendering()) return;
                var transition = MorphTransitions.extract(avatar, state, ClientMorphState.lookup(avatar.getUUID()));
                state.setRenderData(TRANSITION, transition);
                state.setRenderData(MORPH_STATE,
                        transition == null ? MorphRenderSnapshots.extract(avatar, state, ClientMorphState.lookup(avatar.getUUID())) : null);
            }
        });
    }

    private static void renderPlayer(RenderPlayerEvent.Pre<?> event) {
        if (me.ichun.mods.morph.client.transition.MorphTransitionRenderer.isRendering()) return;
        var transition = event.getRenderState().getRenderData(TRANSITION);
        var replacement = event.getRenderState().getRenderData(MORPH_STATE);
        if (replacement == null && transition == null) return;
        var minecraft = Minecraft.getInstance();
        var camera = minecraft.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        if (camera == null) return;
        if (transition != null) {
            me.ichun.mods.morph.client.transition.MorphTransitionRenderer.render(
                    transition, event.getPoseStack(), event.getSubmitNodeCollector(), camera);
            event.setCanceled(true);
            return;
        }
        var renderer = minecraft.getEntityRenderDispatcher().getRenderer(replacement);
        var pose = event.getPoseStack();
        pose.pushPose();
        try {
            renderer.submit(replacement, pose, event.getSubmitNodeCollector(), camera);
            event.setCanceled(true);
        } finally {
            pose.popPose();
        }
    }

    private static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientMorphState.clear();
    }
}
