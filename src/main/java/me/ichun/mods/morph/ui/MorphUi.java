package me.ichun.mods.morph.ui;

import me.ichun.mods.morph.client.ClientMorphState;
import me.ichun.mods.morph.network.MorphNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

public final class MorphUi {
    private static net.minecraft.client.multiplayer.ClientLevel lastLevel;
    private static int ticks;
    private static boolean jumpHeld;
    private static boolean wasGrounded = true;
    private static final KeyMapping OPEN = new KeyMapping("key.morph.select", GLFW.GLFW_KEY_LEFT_BRACKET,
            KeyMapping.Category.GAMEPLAY);

    private MorphUi() {}

    public static void initialize(IEventBus modBus) {
        modBus.addListener(MorphUi::registerKeys);
        modBus.addListener(MorphUi::registerPayloads);
        NeoForge.EVENT_BUS.addListener(MorphUi::tick);
        NeoForge.EVENT_BUS.addListener(MorphUi::entityJoined);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) { event.register(OPEN); }

    private static void registerPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(MorphNetwork.Health.TYPE, (payload, context) ->
                me.ichun.mods.morph.client.health.MorphHealthSync.apply(payload.value()));
        event.register(MorphNetwork.State.TYPE, (payload, context) ->
                ClientMorphState.update(payload.playerId(), payload.formId()));
        event.register(MorphNetwork.Transition.TYPE, (payload, context) ->
                me.ichun.mods.morph.client.MorphTransitions.start(payload.playerId(), payload.fromForm(),
                        payload.toForm(), payload.durationTicks()));
        event.register(MorphNetwork.Collection.TYPE, (payload, context) -> {
            if (Minecraft.getInstance().gui.screen() instanceof MorphScreen screen)
                screen.update(payload.forms(), payload.activeForm());
        });
    }

    private static void tick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        boolean jump = minecraft.options.keyJump.isDown();
        if (minecraft.player != null && minecraft.gui.screen() == null && jump && !jumpHeld && !wasGrounded
                && !minecraft.player.onGround() && !minecraft.player.isPassenger()
                && me.ichun.mods.morph.ability.MorphActions.flapImpulse(ClientMorphState.lookup(minecraft.player.getUUID())) > 0)
            ClientPacketDistributor.sendToServer(new MorphNetwork.Flap());
        jumpHeld = jump;
        wasGrounded = minecraft.player == null || minecraft.player.onGround();
        if (minecraft.level != lastLevel) {
            me.ichun.mods.morph.client.MorphRenderSnapshots.clear();
            lastLevel = minecraft.level;
        }
        if (++ticks % 100 == 0 && minecraft.level != null) {
            var present = new java.util.HashSet<java.util.UUID>();
            for (var player : minecraft.level.players()) present.add(player.getUUID());
            me.ichun.mods.morph.client.MorphRenderSnapshots.retainPlayers(present);
        }
        while (OPEN.consumeClick()) {
            if (minecraft.player != null && minecraft.gui.screen() == null) {
                minecraft.gui.setScreen(new MorphScreen(id ->
                        ClientPacketDistributor.sendToServer(new MorphNetwork.Select(id))));
                ClientPacketDistributor.sendToServer(new MorphNetwork.RequestCollection());
            }
        }
    }

    private static void entityJoined(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof net.minecraft.world.entity.player.Player player) {
            // Join event can precede level insertion; refresh on the client task queue.
            Minecraft.getInstance().execute(() -> me.ichun.mods.morph.shape.ShapeHooks.refresh(player));
        }
    }
}
