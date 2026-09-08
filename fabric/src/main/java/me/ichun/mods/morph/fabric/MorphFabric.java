package me.ichun.mods.morph.fabric;

import net.minecraft.commands.arguments.IdentifierArgument;
import me.ichun.mods.morph.model.MorphCollection;
import me.ichun.mods.morph.server.MorphSavedData;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Avatar;



public final class MorphFabric implements ModInitializer {
    public static MorphSavedData data(MinecraftServer server) { return server.overworld().getDataStorage().computeIfAbsent(MorphSavedData.TYPE); }
    private static MorphCollection forms(ServerPlayer player) { return data(player.level().getServer()).collection(player.getUUID()); }
    private static void sync(ServerPlayer player) {
        me.ichun.mods.morph.shape.ShapeHooks.refresh(player);
        me.ichun.mods.morph.ability.MorphAbilities.tick(player, forms(player).activeForm());
        owned(player);
        var payload = new MorphAppearance(player.getUUID(), forms(player).activeForm(), data(player.level().getServer()).showNametag(player.getUUID()));
        for (var observer : player.level().getServer().getPlayerList().getPlayers()) {
            if (ServerPlayNetworking.canSend(observer, MorphAppearance.TYPE)) ServerPlayNetworking.send(observer, payload);
        }
    }
    private static int nametag(ServerPlayer player, Boolean visible) {
        var saved = data(player.level().getServer());
        saved.setShowNametag(player.getUUID(), visible == null ? !saved.showNametag(player.getUUID()) : visible);
        var payload = new MorphAppearance(player.getUUID(), forms(player).activeForm(), saved.showNametag(player.getUUID()));
        for (var observer : player.level().getServer().getPlayerList().getPlayers())
            if (ServerPlayNetworking.canSend(observer, MorphAppearance.TYPE)) ServerPlayNetworking.send(observer, payload);
        player.sendSystemMessage(Component.translatable(saved.showNametag(player.getUUID()) ? "morph.nametag.shown" : "morph.nametag.hidden"));
        return 1;
    }
    private static void owned(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, MorphOwned.TYPE)) ServerPlayNetworking.send(player, new MorphOwned(forms(player).ownedForms(), forms(player).activeForm()));
    }
    private static String validForm(ServerPlayer player, String raw) {
        var id = Identifier.tryParse(raw);
        if (id == null || !id.getNamespace().equals("minecraft")) return null;
        var type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        if (type == null) return null;
        try {
            return me.ichun.mods.morph.shape.MorphDimensions.supports(player.level(), id.toString()) ? id.toString() : null;
        } catch (RuntimeException ignored) { return null; }
    }
    private static void transform(ServerPlayer player, String previous) {
        var payload = new MorphTransition(player.getUUID(), previous, forms(player).activeForm(),
                me.ichun.mods.morph.model.MorphSounds.DURATION_TICKS);
        for (var observer : net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(player)) {
            if (ServerPlayNetworking.canSend(observer, MorphTransition.TYPE)) ServerPlayNetworking.send(observer, payload);
        }
        if (ServerPlayNetworking.canSend(player, MorphTransition.TYPE)) ServerPlayNetworking.send(player, payload);
        me.ichun.mods.morph.model.MorphSounds.schedule(player);
    }
    private static int grant(ServerPlayer player, String raw) {
        String id = validForm(player, raw);
        if (id == null) { player.sendSystemMessage(Component.literal("Unsupported form: " + raw)); return 0; }
        if (!forms(player).unlock(id)) return 0;
        data(player.level().getServer()).setDirty();
        player.sendSystemMessage(Component.literal("Morph acquired: " + id)); owned(player);
        return 1;
    }
    @Override public void onInitialize() {
        me.ichun.mods.morph.ability.MorphAttributes.setHealthSync((player, health) -> {
            if (player.connection == null || !ServerPlayNetworking.canSend(player, MorphHealth.TYPE)) return;
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket(player.getId(),
                    java.util.List.of(player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH))));
            ServerPlayNetworking.send(player, new MorphHealth(health));
        });
        net.minecraft.core.Registry.register(BuiltInRegistries.SOUND_EVENT,
                me.ichun.mods.morph.model.MorphSounds.ID, me.ichun.mods.morph.model.MorphSounds.EVENT);
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
        PayloadTypeRegistry.serverboundPlay().register(MorphAction.TYPE, MorphAction.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(MorphAction.TYPE, (payload, context) ->
                me.ichun.mods.morph.ability.MorphActions.flap(context.player()));
        PayloadTypeRegistry.clientboundPlay().register(MorphAppearance.TYPE, MorphAppearance.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MorphTransition.TYPE, MorphTransition.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MorphHealth.TYPE, MorphHealth.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MorphOwned.TYPE, MorphOwned.CODEC);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                me.ichun.mods.morph.model.MorphSounds.cancel(player);
                forms(player).reset(); data(player.level().getServer()).setDirty(); sync(player);
            } else if (!(entity instanceof Avatar) && source.getEntity() instanceof ServerPlayer killer) {
                grant(killer, BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
            }
        });
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> sync(newPlayer));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> { if (entity instanceof ServerPlayer player) sync(player); });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            for (var player : server.getPlayerList().getPlayers()) {
                if (ServerPlayNetworking.canSend(handler.player, MorphAppearance.TYPE)) {
                    ServerPlayNetworking.send(handler.player, new MorphAppearance(player.getUUID(), forms(player).activeForm(), data(player.level().getServer()).showNametag(player.getUUID())));
                }
            }
            sync(handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            me.ichun.mods.morph.model.MorphSounds.cancel(handler.player);
            me.ichun.mods.morph.ability.MorphAbilities.cleanup(handler.player);
            for (var observer : server.getPlayerList().getPlayers()) {
                if (observer != handler.player && ServerPlayNetworking.canSend(observer, MorphAppearance.TYPE))
                    ServerPlayNetworking.send(observer, new MorphAppearance(handler.player.getUUID(), ""));
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
            Commands.literal("morph")
                .then(Commands.literal("nametag").executes(ctx -> nametag(ctx.getSource().getPlayerOrException(), null))
                    .then(Commands.literal("toggle").executes(ctx -> nametag(ctx.getSource().getPlayerOrException(), null)))
                    .then(Commands.literal("on").executes(ctx -> nametag(ctx.getSource().getPlayerOrException(), true)))
                    .then(Commands.literal("off").executes(ctx -> nametag(ctx.getSource().getPlayerOrException(), false))))
                .then(Commands.literal("menu").executes(ctx -> { owned(ctx.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("list").executes(ctx -> {
                    var player = ctx.getSource().getPlayerOrException();
                    player.sendSystemMessage(Component.literal("Owned forms: " + String.join(", ", forms(player).ownedForms())));
                    return forms(player).ownedForms().size();
                }))
                .then(Commands.literal("select").then(Commands.argument("form", IdentifierArgument.id())
                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(forms(ctx.getSource().getPlayerOrException()).ownedForms(), builder))
                    .executes(ctx -> {
                        var player = ctx.getSource().getPlayerOrException();
                        if (!player.isAlive() || player.isSpectator()) { ctx.getSource().sendFailure(Component.literal("You cannot morph right now")); return 0; }
                        var form = validForm(player, IdentifierArgument.getId(ctx, "form").toString());
                        if (form == null) { ctx.getSource().sendFailure(Component.literal("Unsupported living form")); return 0; }
                        if (!forms(player).activeForm().equals(form) && !me.ichun.mods.morph.shape.ShapeHooks.canFit(player, form)) { ctx.getSource().sendFailure(Component.literal("Not enough room for that form")); return 0; }
                        String previous = forms(player).activeForm();
                        var result = forms(player).select(form, player.level().getServer().overworld().getGameTime());
                        return switch (result) {
                            case CHANGED -> {
                                me.ichun.mods.morph.ability.MorphAttributes.begin(player, form);
                                data(ctx.getSource().getServer()).setDirty();
                                sync(player);
                                transform(player, previous);
                                yield 1;
                            }
                            case UNCHANGED -> { owned(player); yield 1; }
                            case COOLDOWN -> {
                                ctx.getSource().sendFailure(Component.literal("Wait a moment before changing forms again."));
                                yield 0;
                            }
                            case NOT_OWNED -> {
                                ctx.getSource().sendFailure(Component.literal("Acquire that form before selecting it."));
                                yield 0;
                            }
                        };
                    })))
                .then(Commands.literal("reset").executes(ctx -> {
                    var player = ctx.getSource().getPlayerOrException();
                    if (!me.ichun.mods.morph.shape.ShapeHooks.canFit(player, "")) { ctx.getSource().sendFailure(Component.literal("Not enough room to return to player form")); return 0; }
                    String previous = forms(player).activeForm();
                    boolean changed = forms(player).reset();
                    if (changed && player.isAlive()) me.ichun.mods.morph.ability.MorphAttributes.begin(player, "");
                    if (changed) data(ctx.getSource().getServer()).setDirty();
                    sync(player);
                    if (changed && player.isAlive()) transform(player, previous);
                    player.sendSystemMessage(Component.literal("Returned to player form")); return 1;
                }))
                .then(Commands.literal("grant").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                    .then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("form", IdentifierArgument.id())
                        .executes(ctx -> grant(EntityArgument.getPlayer(ctx, "player"), IdentifierArgument.getId(ctx, "form").toString())))))
        ));
    }
}
