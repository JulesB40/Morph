package me.ichun.mods.morph.fabric.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import me.ichun.mods.morph.client.MorphRenderSnapshots;
import me.ichun.mods.morph.fabric.MorphAppearance;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class MorphFabricClient implements ClientModInitializer {
    private static net.minecraft.client.multiplayer.ClientLevel lastLevel;
    private static int cleanupTicks;
    private static boolean jumpHeld;
    private static boolean wasGrounded = true;
    private static final java.util.Set<net.minecraft.world.entity.player.Player> SEEN = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    public static final Map<UUID, String> FORMS = new HashMap<>();
    @Override public void onInitializeClient() {
        net.fabricmc.fabric.api.resource.v1.ResourceLoader.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
            .registerReloadListener(net.minecraft.resources.Identifier.fromNamespaceAndPath("morph", "swim_animation"),
                (net.minecraft.server.packs.resources.ResourceManagerReloadListener)
                    me.ichun.mods.morph.client.animation.MorphSwimAnimation::reload);
        net.fabricmc.fabric.api.resource.v1.ResourceLoader.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
            .registerReloadListener(net.minecraft.resources.Identifier.fromNamespaceAndPath("morph", "render_snapshots"),
                (net.minecraft.server.packs.resources.ResourceManagerReloadListener) MorphRenderSnapshots::reload);
        me.ichun.mods.morph.shape.ShapeHooks.setClientDescriptorResolver(player -> {
            var active = me.ichun.mods.morph.client.DescriptorState.active(player.getUUID());
            return active == null ? null : active.descriptor();
        });
        me.ichun.mods.morph.shape.ShapeHooks.setClientFormResolver(player -> FORMS.get(player.getUUID()));
        var collectionActions = me.ichun.mods.morph.ui.CollectionClientUi.actions(action ->
                ClientPlayNetworking.send(new me.ichun.mods.morph.fabric.MorphCollectionAction(action)));
        var favorites = net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(
            new net.minecraft.client.KeyMapping("key.morph.favorites", org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_BRACKET, net.minecraft.client.KeyMapping.Category.GAMEPLAY));
        var open = net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(
            new net.minecraft.client.KeyMapping("key.morph.select", org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_BRACKET, net.minecraft.client.KeyMapping.Category.GAMEPLAY));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean jump = client.options.keyJump.isDown();
            if (client.player != null && client.gui.screen() == null && jump && !jumpHeld && !wasGrounded
                    && !client.player.onGround() && !client.player.isPassenger()
                    && me.ichun.mods.morph.ability.MorphActions.flapImpulse(FORMS.get(client.player.getUUID())) > 0
                    && ClientPlayNetworking.canSend(me.ichun.mods.morph.fabric.MorphAction.TYPE))
                ClientPlayNetworking.send(new me.ichun.mods.morph.fabric.MorphAction());
            jumpHeld = jump;
            wasGrounded = client.player == null || client.player.onGround();
            if (client.level != lastLevel) { MorphRenderSnapshots.clear(); SEEN.clear(); lastLevel = client.level; }
            if (client.level != null) for (var player : client.level.players()) if (SEEN.add(player)) me.ichun.mods.morph.shape.ShapeHooks.refresh(player);
            if (++cleanupTicks % 100 == 0 && client.level != null) {
                var present = new java.util.HashSet<UUID>();
                for (var player : client.level.players()) present.add(player.getUUID());
                MorphRenderSnapshots.retainPlayers(present);
            }
            while (favorites.consumeClick()) {
                if (client.player != null && client.gui.screen() == null) me.ichun.mods.morph.ui.CollectionClientUi.open(collectionActions, true);
            }
            while (open.consumeClick()) {
                if (client.player != null && client.gui.screen() == null) {
                    me.ichun.mods.morph.ui.CollectionClientUi.open(collectionActions, false);

                }
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(me.ichun.mods.morph.fabric.MorphSnapshotPage.TYPE, (payload, context) ->
                context.client().execute(() -> me.ichun.mods.morph.ui.CollectionClientUi.receive(payload.value())));
        ClientPlayNetworking.registerGlobalReceiver(me.ichun.mods.morph.fabric.MorphActionAck.TYPE, (payload, context) ->
                context.client().execute(() -> me.ichun.mods.morph.ui.CollectionClientUi.receive(payload.value())));
        ClientPlayNetworking.registerGlobalReceiver(me.ichun.mods.morph.fabric.MorphDescriptorAppearance.TYPE, (payload, context) -> context.client().execute(() -> {
            var value = payload.value();
            if (!me.ichun.mods.morph.client.DescriptorState.accept(value)) return;
            String form = value.active() == null ? "" : value.active().descriptor().species();
            me.ichun.mods.morph.client.MorphTransitions.reconcile(value.subject(), form);
            if (form.isEmpty()) FORMS.remove(value.subject()); else FORMS.put(value.subject(), form);
            me.ichun.mods.morph.client.nametag.MorphNameTags.update(value.subject(), value.showNametag());
            MorphRenderSnapshots.invalidate(value.subject());
            if (context.client().level != null) {
                var player = context.client().level.getPlayerByUUID(value.subject());
                if (player != null) me.ichun.mods.morph.shape.ShapeHooks.refresh(player);
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(me.ichun.mods.morph.fabric.MorphDescriptorTransition.TYPE, (payload, context) -> context.client().execute(() -> {
            var value = payload.value();
            if (me.ichun.mods.morph.client.DescriptorState.accept(value))
                me.ichun.mods.morph.client.MorphTransitions.start(value.subject(), value.from(), value.to(), value.startTick(), value.duration());
        }));
        ClientPlayNetworking.registerGlobalReceiver(me.ichun.mods.morph.fabric.MorphOwned.TYPE, (payload, context) -> context.client().execute(() -> {
            if (context.client().gui.screen() instanceof me.ichun.mods.morph.ui.MorphScreen screen) screen.update(payload.forms(), payload.active());
        }));
        ClientPlayNetworking.registerGlobalReceiver(me.ichun.mods.morph.fabric.MorphHealth.TYPE, (payload, context) ->
                context.client().execute(() -> me.ichun.mods.morph.client.health.MorphHealthSync.apply(payload.value())));
        ClientPlayNetworking.registerGlobalReceiver(MorphAppearance.TYPE, (payload, context) -> context.client().execute(() -> {
            me.ichun.mods.morph.client.MorphTransitions.reconcile(payload.player(), payload.form());
            if (payload.form().isEmpty()) FORMS.remove(payload.player()); else FORMS.put(payload.player(), payload.form());
            me.ichun.mods.morph.client.nametag.MorphNameTags.update(payload.player(), payload.showNametag());
            MorphRenderSnapshots.invalidate(payload.player());
            if (context.client().level != null) {
                var player = context.client().level.getPlayerByUUID(payload.player());
                if (player != null) me.ichun.mods.morph.shape.ShapeHooks.refresh(player);
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(me.ichun.mods.morph.fabric.MorphTransition.TYPE, (payload, context) -> context.client().execute(() ->
                me.ichun.mods.morph.client.MorphTransitions.start(payload.player(), payload.fromForm(), payload.toForm(), payload.durationTicks())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { me.ichun.mods.morph.client.ClientCollectionState.clear(); me.ichun.mods.morph.client.DescriptorState.clear(); FORMS.clear(); me.ichun.mods.morph.client.nametag.MorphNameTags.clear(); MorphRenderSnapshots.clear(); });
    }
}
