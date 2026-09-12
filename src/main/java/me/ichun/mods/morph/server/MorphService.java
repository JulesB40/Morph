package me.ichun.mods.morph.server;

import com.mojang.brigadier.CommandDispatcher;
import me.ichun.mods.morph.model.MorphCollection;
import me.ichun.mods.morph.network.MorphNetwork;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** NeoForge lifecycle adapter; shared authority owns collection mutations. */
public final class MorphService {
    private MorphService() {}

    public static void initialize() {
        MorphAuthority.setTransport(new MorphAuthority.Transport() {
            public void collection(ServerPlayer player) {
                var forms = MorphAuthority.collection(player);
                MorphNetwork.sendSnapshot(player, forms.snapshot());
            }
            public void appearance(ServerPlayer player) { MorphNetwork.broadcastState(player, MorphService.collection(player).activeForm()); }
            public void transition(ServerPlayer player, me.ichun.mods.morph.model.CollectionEntry previous, me.ichun.mods.morph.model.CollectionEntry next) { MorphNetwork.broadcastDescriptorTransition(player, previous, next); }
        });
        me.ichun.mods.morph.shape.ShapeHooks.setDescriptorResolver(player -> player instanceof ServerPlayer serverPlayer ? collection(serverPlayer).activeDescriptor() : null);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerAboutToStartEvent event) ->
                MorphAuthority.startConfiguration(event.getServer(), net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent event) ->
                me.ichun.mods.morph.config.MorphConfiguration.stop(event.getServer()));
        NeoForge.EVENT_BUS.addListener(MorphService::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, MorphService::onDeath);
        NeoForge.EVENT_BUS.addListener(MorphService::onLogin);
        NeoForge.EVENT_BUS.addListener(MorphService::onRespawn);
        NeoForge.EVENT_BUS.addListener(MorphService::onDimensionChange);
        NeoForge.EVENT_BUS.addListener(MorphService::onStartTracking);
        NeoForge.EVENT_BUS.addListener(MorphService::tickAbilities);
        NeoForge.EVENT_BUS.addListener(MorphService::onFall);
        NeoForge.EVENT_BUS.addListener(MorphService::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(MorphService::onDamageReduction);
        NeoForge.EVENT_BUS.addListener(MorphService::onLogout);
    }

    public static MorphCollection collection(ServerPlayer player) { return MorphAuthority.collection(player); }
    public static boolean showNametag(ServerPlayer player) { return MorphAuthority.showNametag(player); }
    public static boolean select(ServerPlayer player, String form) { return MorphAuthority.select(player, form); }
    public static boolean reset(ServerPlayer player) { return MorphAuthority.reset(player); }
    public static void requestCollection(ServerPlayer player) { MorphAuthority.requestCollection(player); }
    private static void sync(ServerPlayer player) { MorphAuthority.sync(player); }

    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer deadPlayer) {
            MorphAuthority.died(deadPlayer);
        } else if (!(event.getEntity() instanceof Player) && event.getSource().getEntity() instanceof ServerPlayer killer
                && !(killer instanceof net.neoforged.neoforge.common.util.FakePlayer) && MorphAuthority.canAcquire(killer)) {
            MorphAuthority.capture(killer, event.getEntity());
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

    private static void onDamageReduction(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre event) {
        // NeoForge ignores getDamageAfterMagicAbsorb's return value and uses this
        // container instead. The shared vanilla return hook supplies Fabric's path.
        if (event.getEntity() instanceof ServerPlayer player && "minecraft:witch".equals(collection(player).activeForm())) {
            float fraction = event.getSource().getEntity() == player ? 1F
                    : event.getSource().is(net.minecraft.tags.DamageTypeTags.WITCH_RESISTANT_TO) ? .85F : 0F;
            if (fraction > 0) event.getContainer().setReduction(
                    net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction.INNATE_RESISTANCE,
                    event.getNewDamage() * fraction);
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MorphNetwork.broadcastDeparture(player);
            MorphAuthority.disconnected(player);
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
        MorphCommands.register(dispatcher);
    }
}
