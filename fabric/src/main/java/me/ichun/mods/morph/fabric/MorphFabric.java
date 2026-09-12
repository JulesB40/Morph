package me.ichun.mods.morph.fabric;

import me.ichun.mods.morph.model.MorphCollection;
import me.ichun.mods.morph.server.MorphSavedData;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Avatar;



public final class MorphFabric implements ModInitializer {
    public static MorphSavedData data(MinecraftServer server) { return MorphSavedData.load(server); }
    private static MorphCollection forms(ServerPlayer player) { return data(player.level().getServer()).collection(player.getUUID()); }
    private static void sync(ServerPlayer player) { me.ichun.mods.morph.server.MorphAuthority.sync(player); }
    private static void owned(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, MorphSnapshotPage.TYPE))
            for (var page : me.ichun.mods.morph.network.CollectionProtocol.pages(forms(player).snapshot()))
                ServerPlayNetworking.send(player, new MorphSnapshotPage(page));
    }
    @Override public void onInitialize() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(server ->
            me.ichun.mods.morph.server.MorphAuthority.startConfiguration(server, net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(me.ichun.mods.morph.config.MorphConfiguration::stop);
        me.ichun.mods.morph.server.MorphAuthority.setTransport(new me.ichun.mods.morph.server.MorphAuthority.Transport() {
            public void collection(ServerPlayer player) { owned(player); }
            public void appearance(ServerPlayer player) {
                var payload = new MorphDescriptorAppearance(me.ichun.mods.morph.server.MorphAuthority.appearance(player));
                for (var observer : player.level().getServer().getPlayerList().getPlayers())
                    if (ServerPlayNetworking.canSend(observer, MorphDescriptorAppearance.TYPE)) ServerPlayNetworking.send(observer, payload);
            }
            public void transition(ServerPlayer player, me.ichun.mods.morph.model.CollectionEntry previous, me.ichun.mods.morph.model.CollectionEntry next) {
                var payload = new MorphDescriptorTransition(me.ichun.mods.morph.server.MorphAuthority.transition(player, previous, next));
                for (var observer : net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(player))
                    if (ServerPlayNetworking.canSend(observer, MorphDescriptorTransition.TYPE)) ServerPlayNetworking.send(observer, payload);
                if (ServerPlayNetworking.canSend(player, MorphDescriptorTransition.TYPE)) ServerPlayNetworking.send(player, payload);
            }
        });
        me.ichun.mods.morph.ability.MorphAttributes.setHealthSync((player, health) -> {
            if (player.connection == null || !ServerPlayNetworking.canSend(player, MorphHealth.TYPE)) return;
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket(player.getId(),
                    java.util.List.of(player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH))));
            ServerPlayNetworking.send(player, new MorphHealth(health));
        });
        net.minecraft.core.Registry.register(BuiltInRegistries.SOUND_EVENT,
                me.ichun.mods.morph.model.MorphSounds.ID, me.ichun.mods.morph.model.MorphSounds.EVENT);
        me.ichun.mods.morph.shape.ShapeHooks.setDescriptorResolver(player -> player instanceof ServerPlayer serverPlayer ? forms(serverPlayer).activeDescriptor() : null);
        me.ichun.mods.morph.shape.ShapeHooks.setFormResolver(player -> player instanceof ServerPlayer serverPlayer ? forms(serverPlayer).activeForm() : null);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.START_SERVER_TICK.register(server -> {
            for (var player : server.getPlayerList().getPlayers()) {
                me.ichun.mods.morph.ability.MorphAbilities.tick(player, forms(player).activeForm());
                me.ichun.mods.morph.model.MorphSounds.tick(player);
            }
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayer player
            && player.isAlive()
            && me.ichun.mods.morph.ability.MorphTraits.preventsDamage(player, forms(player).activeForm(), source)));
        PayloadTypeRegistry.serverboundPlay().register(MorphCollectionAction.TYPE, MorphCollectionAction.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MorphSnapshotPage.TYPE, MorphSnapshotPage.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MorphActionAck.TYPE, MorphActionAck.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MorphDescriptorAppearance.TYPE, MorphDescriptorAppearance.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MorphDescriptorTransition.TYPE, MorphDescriptorTransition.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(MorphCollectionAction.TYPE, (payload, context) ->
            context.server().execute(() -> {
                var ack = me.ichun.mods.morph.server.MorphAuthority.action(context.player(), payload.value());
                ServerPlayNetworking.send(context.player(), new MorphActionAck(ack));
            }));
        PayloadTypeRegistry.serverboundPlay().register(MorphAction.TYPE, MorphAction.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(MorphAction.TYPE, (payload, context) ->
                me.ichun.mods.morph.ability.MorphActions.flap(context.player()));
        PayloadTypeRegistry.clientboundPlay().register(MorphAppearance.TYPE, MorphAppearance.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MorphTransition.TYPE, MorphTransition.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MorphHealth.TYPE, MorphHealth.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MorphOwned.TYPE, MorphOwned.CODEC);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                me.ichun.mods.morph.server.MorphAuthority.died(player);
            } else if (!(entity instanceof Avatar) && source.getEntity() instanceof ServerPlayer killer
                    && me.ichun.mods.morph.server.MorphAuthority.canAcquire(killer)) {
                me.ichun.mods.morph.server.MorphAuthority.capture(killer, entity);
            }
        });
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> sync(newPlayer));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> { if (entity instanceof ServerPlayer player) sync(player); });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!ServerPlayNetworking.canSend(handler.player, MorphSnapshotPage.TYPE)
                    || !ServerPlayNetworking.canSend(handler.player, MorphDescriptorAppearance.TYPE)
                    || !ServerPlayNetworking.canSend(handler.player, MorphDescriptorTransition.TYPE)
                    || !ServerPlayNetworking.canSend(handler.player, MorphActionAck.TYPE)) {
                handler.disconnect(Component.literal("Morph requires a client with descriptor protocol v3. Update Morph on client and server."));
                return;
            }
            for (var player : server.getPlayerList().getPlayers())
                ServerPlayNetworking.send(handler.player, new MorphDescriptorAppearance(me.ichun.mods.morph.server.MorphAuthority.appearance(player)));
            sync(handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            me.ichun.mods.morph.model.MorphSounds.cancel(handler.player);
            me.ichun.mods.morph.ability.MorphAbilities.cleanup(handler.player);
            var payload = new MorphDescriptorAppearance(me.ichun.mods.morph.server.MorphAuthority.departure(handler.player));
            for (var observer : server.getPlayerList().getPlayers())
                if (observer != handler.player && ServerPlayNetworking.canSend(observer, MorphDescriptorAppearance.TYPE)) ServerPlayNetworking.send(observer, payload);
            me.ichun.mods.morph.server.MorphAuthority.disconnected(handler.player);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> me.ichun.mods.morph.server.MorphCommands.register(dispatcher));
    }
}
