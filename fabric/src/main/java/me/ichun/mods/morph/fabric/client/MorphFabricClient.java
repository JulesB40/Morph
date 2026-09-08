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
        me.ichun.mods.morph.shape.ShapeHooks.setClientFormResolver(player -> FORMS.get(player.getUUID()));
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
            while (open.consumeClick()) {
                if (client.player != null && client.gui.screen() == null) {
                    client.gui.setScreen(new me.ichun.mods.morph.ui.MorphScreen(id -> client.player.connection.sendCommand(id.isEmpty() ? "morph reset" : "morph select " + id)));
                    client.player.connection.sendCommand("morph menu");
                }
            }
        });
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
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { FORMS.clear(); me.ichun.mods.morph.client.nametag.MorphNameTags.clear(); MorphRenderSnapshots.clear(); });
    }
}
