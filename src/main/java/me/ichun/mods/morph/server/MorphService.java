package me.ichun.mods.morph.server;

import com.mojang.brigadier.CommandDispatcher;
import me.ichun.mods.morph.model.MorphCollection;
import me.ichun.mods.morph.network.MorphNetwork;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Server-thread-only authoritative operations. Forms are entity IDs, not saved entity variants. */
public final class MorphService {
    private MorphService() {}

    public static void initialize() {
        NeoForge.EVENT_BUS.addListener(MorphService::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, MorphService::onDeath);
        NeoForge.EVENT_BUS.addListener(MorphService::onLogin);
        NeoForge.EVENT_BUS.addListener(MorphService::onRespawn);
        NeoForge.EVENT_BUS.addListener(MorphService::onDimensionChange);
        NeoForge.EVENT_BUS.addListener(MorphService::onStartTracking);
        NeoForge.EVENT_BUS.addListener(MorphService::tickAbilities);
        NeoForge.EVENT_BUS.addListener(MorphService::onFall);
        NeoForge.EVENT_BUS.addListener(MorphService::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(MorphService::onLogout);
    }

    private static MorphSavedData data(ServerPlayer player) {
        return player.level().getServer().overworld().getDataStorage().computeIfAbsent(MorphSavedData.TYPE);
    }

    public static boolean showNametag(ServerPlayer player) { return data(player).showNametag(player.getUUID()); }
    private static int nametag(ServerPlayer player, Boolean visible) {
        data(player).setShowNametag(player.getUUID(), visible == null ? !showNametag(player) : visible);
        MorphNetwork.broadcastState(player, collection(player).activeForm());
        player.sendSystemMessage(Component.translatable(showNametag(player) ? "morph.nametag.shown" : "morph.nametag.hidden"));
        return 1;
    }

    public static MorphCollection collection(ServerPlayer player) { return data(player).collection(player.getUUID()); }

    private static boolean validForm(ServerPlayer player, String form) {
        return MorphCollection.isFormId(form)
                && me.ichun.mods.morph.shape.MorphDimensions.supports(player.level(), form);
    }

    public static boolean select(ServerPlayer player, String form) {
        if (!player.isAlive() || player.isSpectator()) return fail(player, "You cannot morph right now.");
        if (!validForm(player, form)) return fail(player, "That is not a supported living entity form.");
        if (!me.ichun.mods.morph.shape.ShapeHooks.canFit(player, form))
            return fail(player, "There is not enough room for that form here.");
        MorphCollection forms = collection(player);
        String previous = forms.activeForm();
        return switch (forms.select(form, player.level().getServer().overworld().getGameTime())) {
            case NOT_OWNED -> fail(player, "Acquire that form before selecting it.");
            case COOLDOWN -> fail(player, "Wait one second between morph selections.");
            case UNCHANGED -> { requestCollection(player); yield true; }
            case CHANGED -> {
                me.ichun.mods.morph.ability.MorphAttributes.begin(player, form);
                data(player).setDirty(); sync(player); transform(player, previous); yield true;
            }
        };
    }

    public static boolean reset(ServerPlayer player) {
        if (player.isAlive() && !me.ichun.mods.morph.shape.ShapeHooks.canFit(player, ""))
            return fail(player, "Move somewhere with enough room to return to player form.");
        String previous = collection(player).activeForm();
        boolean changed = collection(player).reset();
        if (changed && player.isAlive()) me.ichun.mods.morph.ability.MorphAttributes.begin(player, "");
        if (changed) data(player).setDirty();
        sync(player);
        if (changed && player.isAlive()) transform(player, previous);
        return true;
    }

    private static void transform(ServerPlayer player, String previous) {
        MorphNetwork.broadcastTransition(player, previous, collection(player).activeForm());
        me.ichun.mods.morph.model.MorphSounds.schedule(player);
    }

    public static void requestCollection(ServerPlayer player) {
        MorphCollection forms = collection(player);
        MorphNetwork.sendCollection(player, forms.ownedForms(), forms.activeForm());
    }

    private static void sync(ServerPlayer player) {
        MorphCollection forms = collection(player);
        if (!forms.activeForm().isEmpty() && !validForm(player, forms.activeForm())) {
            forms.reset();
            data(player).setDirty();
        }
        me.ichun.mods.morph.shape.ShapeHooks.refresh(player);
        me.ichun.mods.morph.ability.MorphAbilities.tick(player, forms.activeForm());
        MorphNetwork.broadcastState(player, forms.activeForm());
        requestCollection(player);
    }

    private static boolean fail(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message));
        return false;
    }

    private static boolean grant(ServerPlayer player, String form) {
        if (!validForm(player, form)) return fail(player, "That is not a supported living entity form.");
        if (collection(player).unlock(form)) {
            data(player).setDirty();
            requestCollection(player);
            player.sendSystemMessage(Component.literal("Acquired morph: " + form));
            return true;
        }
        return fail(player, "You already own that form, or your collection is full (256 forms).");
    }

    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer deadPlayer) {
            // Collections survive death; reset appearance, so respawn begins as the player.
            me.ichun.mods.morph.model.MorphSounds.cancel(deadPlayer);
            if (collection(deadPlayer).reset()) data(deadPlayer).setDirty();
            sync(deadPlayer);
        } else if (!(event.getEntity() instanceof Player) && event.getSource().getEntity() instanceof ServerPlayer killer) {
            String form = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).toString();
            if (!collection(killer).ownedForms().contains(form)) grant(killer, form);
        }
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    private static void tickAbilities(net.neoforged.neoforge.event.tick.PlayerTickEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            me.ichun.mods.morph.ability.MorphAbilities.tick(player, collection(player).activeForm());
            me.ichun.mods.morph.model.MorphSounds.tick(player);
        }
    }

    private static void onFall(net.neoforged.neoforge.event.entity.living.LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && me.ichun.mods.morph.ability.MorphAbilities.preventsFallDamage(collection(player).activeForm()))
            event.setCanceled(true);
    }

    private static void onIncomingDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.isAlive()
                && me.ichun.mods.morph.ability.MorphTraits.preventsDamage(player, collection(player).activeForm(), event.getSource()))
            event.setCanceled(true);
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            me.ichun.mods.morph.ability.MorphAbilities.cleanup(player);
            me.ichun.mods.morph.model.MorphSounds.cancel(player);
        }
    }
    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }
    private static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }
    private static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer && event.getTarget() instanceof ServerPlayer subject) {
            MorphNetwork.sendState(observer, subject.getUUID(), collection(subject).activeForm(), showNametag(subject));
        }
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) { registerCommands(event.getDispatcher()); }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("morph")
                .then(Commands.literal("nametag").executes(ctx -> nametag(ctx.getSource().getPlayerOrException(), null))
                    .then(Commands.literal("toggle").executes(ctx -> nametag(ctx.getSource().getPlayerOrException(), null)))
                    .then(Commands.literal("on").executes(ctx -> nametag(ctx.getSource().getPlayerOrException(), true)))
                    .then(Commands.literal("off").executes(ctx -> nametag(ctx.getSource().getPlayerOrException(), false))))
                .then(Commands.literal("list").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    var forms = collection(player).ownedForms();
                    context.getSource().sendSuccess(() -> Component.literal(forms.isEmpty()
                            ? "No forms acquired. Defeat a living mob to acquire its form."
                            : "Morphs: " + String.join(", ", forms)), false);
                    requestCollection(player);
                    return forms.size();
                }))
                .then(Commands.literal("select").then(Commands.argument("entity", IdentifierArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                collection(context.getSource().getPlayerOrException()).ownedForms(), builder))
                        .executes(context -> select(context.getSource().getPlayerOrException(),
                                IdentifierArgument.getId(context, "entity").toString()) ? 1 : 0)))
                .then(Commands.literal("reset").executes(context -> reset(context.getSource().getPlayerOrException()) ? 1 : 0))
                .then(Commands.literal("grant").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                        .then(Commands.argument("entity", IdentifierArgument.id()).executes(context ->
                                grant(context.getSource().getPlayerOrException(), IdentifierArgument.getId(context, "entity").toString()) ? 1 : 0))));
    }
}
