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

    private static final KeyMapping FAVORITES = new KeyMapping("key.morph.favorites", GLFW.GLFW_KEY_RIGHT_BRACKET,
            KeyMapping.Category.GAMEPLAY);
    private static final MorphScreen.Actions COLLECTION_ACTIONS = CollectionClientUi.actions(action ->
            ClientPacketDistributor.sendToServer(new MorphNetwork.CollectionAction(action)));

    private MorphUi() {}

    public static void initialize(IEventBus modBus) {
        me.ichun.mods.morph.shape.ShapeHooks.setClientDescriptorResolver(player -> {
            var active = me.ichun.mods.morph.client.DescriptorState.active(player.getUUID());
            return active == null ? null : active.descriptor();
        });
        modBus.addListener(MorphUi::registerKeys);
        modBus.addListener(MorphUi::registerPayloads);
        NeoForge.EVENT_BUS.addListener(MorphUi::tick);
        NeoForge.EVENT_BUS.addListener(MorphUi::entityJoined);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) { event.register(OPEN); event.register(FAVORITES); }

    private static void registerPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(MorphNetwork.SnapshotPage.TYPE, (payload, context) -> CollectionClientUi.receive(payload.value()));
        event.register(MorphNetwork.ActionAck.TYPE, (payload, context) -> CollectionClientUi.receive(payload.value()));
        event.register(MorphNetwork.DescriptorAppearance.TYPE, (payload, context) -> {
            var value = payload.value();
            if (!me.ichun.mods.morph.client.DescriptorState.accept(value)) return;
            ClientMorphState.update(value.subject(), value.active() == null ? "" : value.active().descriptor().species());
            me.ichun.mods.morph.client.nametag.MorphNameTags.update(value.subject(), value.showNametag());
        });
        event.register(MorphNetwork.DescriptorTransition.TYPE, (payload, context) -> {
            var value = payload.value();
            if (me.ichun.mods.morph.client.DescriptorState.accept(value))
                me.ichun.mods.morph.client.MorphTransitions.start(value.subject(), value.from(), value.to(), value.startTick(), value.duration());
        });
        event.register(MorphNetwork.Health.TYPE, (payload, context) ->
                me.ichun.mods.morph.client.health.MorphHealthSync.apply(payload.value()));
        event.register(MorphNetwork.State.TYPE, (payload, context) ->
                { ClientMorphState.update(payload.playerId(), payload.formId());
                  me.ichun.mods.morph.client.nametag.MorphNameTags.update(payload.playerId(), payload.showNametag()); });
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
        while (FAVORITES.consumeClick()) {
            if (minecraft.player != null && minecraft.gui.screen() == null) CollectionClientUi.open(COLLECTION_ACTIONS, true);
        }
        while (OPEN.consumeClick()) {
            if (minecraft.player != null && minecraft.gui.screen() == null) {
                CollectionClientUi.open(COLLECTION_ACTIONS, false);
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
