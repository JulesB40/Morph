package me.ichun.mods.morph.lab;

import com.google.gson.Gson;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import me.ichun.mods.morph.ability.MorphAbilities;
import me.ichun.mods.morph.api.MorphEvents;
import me.ichun.mods.morph.config.MorphPolicies;
import me.ichun.mods.morph.config.MorphPolicySnapshot;
import me.ichun.mods.morph.model.FormDescriptor;
import me.ichun.mods.morph.model.MorphSounds;
import me.ichun.mods.morph.network.CollectionProtocol.Code;
import me.ichun.mods.morph.progression.BiomassDefinitions;
import me.ichun.mods.morph.progression.BiomassLedger;
import me.ichun.mods.morph.progression.BiomassRuntime;
import me.ichun.mods.morph.server.MorphAuthority;
import me.ichun.mods.morph.server.MorphSavedData;
import me.ichun.mods.morph.shape.ShapeHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Real authority/command/save paths with a mock connection, not a real client or authenticated kill test. */
public final class NativeBiomassChecks {
    public enum Case { CLASSIC_BYPASS, GAIN_PURCHASE_PERSISTENCE, SELECTION_CONSERVATION, COMMAND_PARSE }
    private NativeBiomassChecks() {}

    public static void run(GameTestHelper helper, Case scenario) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "lab-biomass"), false);
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {};
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        player.connection = new ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        player.setPose(Pose.STANDING);
        player.setPos(helper.absoluteVec(new Vec3(2.5, 2, 2.5)));
        var checks = new ArrayList<Map<String, Object>>();
        var event = new LinkedHashMap<String, Object>();
        event.put("schema", "morph-lab.native-biomass.v1");
        event.put("scenario", "biomass_" + scenario.name().toLowerCase(java.util.Locale.ROOT));
        event.put("scope", "native authority selection, registered command execution, actual compressed NBT; mock connection absent from player list; gains invoke trusted runtime with native pig measurements, not death-event authorization; no process restart");
        event.put("server_tick", helper.getLevel().getGameTime());
        event.put("checks", checks);
        BlockPos roof = helper.absolutePos(new BlockPos(2, 3, 2));
        BlockPos floor = helper.absolutePos(new BlockPos(2, 1, 2));
        var oldRoof = helper.getLevel().getBlockState(roof);
        var oldFloor = helper.getLevel().getBlockState(floor);
        Supplier<MorphPolicySnapshot> previousBinding = null;
        try {
            previousBinding = policyBinding();
            var defaults = MorphPolicySnapshot.defaults();
            var policy = scenario == Case.CLASSIC_BYPASS ? defaults : new MorphPolicySnapshot(1,
                    MorphPolicySnapshot.ServerMode.BIOMASS, true, defaults.durationTicks(), defaults.morphSounds(),
                    defaults.morphPlayers(), defaults.selectorPlayers(), defaults.forms(), defaults.abilities());
            MorphPolicies.bind(() -> policy);
            helper.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(roof, Blocks.AIR.defaultBlockState());
            equal(false, MorphAuthority.canAcquire(player), "mock player is not authenticated", checks);
            equal(BiomassLedger.locked(), ledger(player), "fresh saved ledger", checks);
            if (scenario == Case.COMMAND_PARSE) commandParse(player, checks);
            else if (scenario == Case.CLASSIC_BYPASS) classic(helper, player, checks);
            else if (scenario == Case.GAIN_PURCHASE_PERSISTENCE) purchases(helper, player, checks);
            else selections(helper, player, roof, checks);
            event.put("status", "pass");
        } catch (Throwable failure) {
            event.put("status", "fail"); event.put("error", failure.toString());
            if (failure instanceof Error fatal) throw fatal;
            throw new IllegalStateException("Native biomass check failed", failure);
        } finally {
            if (previousBinding != null) MorphPolicies.bind(previousBinding);
            MorphSounds.cancel(player);
            MorphAbilities.cleanup(player);
            MorphAuthority.disconnected(player);
            MorphAuthority.collection(player).reset();
            helper.getLevel().setBlockAndUpdate(roof, oldRoof);
            helper.getLevel().setBlockAndUpdate(floor, oldFloor);
            channel.finishAndReleaseAll();
            record(event);
        }
        helper.succeed();
    }

    private static void classic(GameTestHelper helper, ServerPlayer player, List<Map<String, Object>> checks) throws Exception {
        equal(BiomassRuntime.Status.DISABLED, gainPig(helper, player, checks).status(), "Classic gain bypass", checks);
        var pig = FormDescriptor.species("minecraft:pig");
        equal(Code.CHANGED, MorphAuthority.grantDescriptor(player, pig), "trusted Classic grant", checks);
        equal(Code.CHANGED, MorphAuthority.selectEntry(player, pig.entryId()), "Classic morph without biomass", checks);
        equal(0, command(player, "morph biomass buy efficiency 0"), "Classic purchase rejected", checks);
        equal(BiomassLedger.locked(), ledger(player), "Classic operations preserve empty ledger", checks);
    }

    private static void purchases(GameTestHelper helper, ServerPlayer player, List<Map<String, Object>> checks) throws Exception {
        for (int kill = 1; kill <= 10; kill++) {
            equal(BiomassRuntime.Status.CHANGED, gainPig(helper, player, checks).status(), "trusted gain " + kill, checks);
            equal(kill * 10, ledger(player).balance(), "pig gain accumulation " + kill, checks);
            equal((long) kill, ledger(player).revision(), "single revision including initial unlock " + kill, checks);
        }
        equal(true, ledger(player).unlocked(), "first positive kill unlock", checks);
        equal(1, command(player, "morph biomass status"), "registered status command executes", checks);
        equal(1, command(player, "morph biomass buy efficiency 10"), "purchase first efficiency", checks);
        equal(75, ledger(player).balance(), "purchase costs exactly 25", checks);
        equal(1, ledger(player).level(BiomassDefinitions.Upgrade.EFFICIENCY), "first efficiency level", checks);
        var purchased = ledger(player);
        equal(0, command(player, "morph biomass buy efficiency 10"), "duplicate command rejected despite sufficient funds", checks);
        equal(purchased, ledger(player), "duplicate command conserves full ledger", checks);
        var restored = compressedRoundTrip(MorphAuthority.data(player));
        equal(purchased, restored.biomass(player.getUUID()), "compressed save preserves ledger", checks);
        equal(BiomassLedger.locked(), restored.biomass(UUID.randomUUID()), "save isolates absent player", checks);
        var restarted = new BiomassRuntime(new BiomassRuntime.Store() {
            public BiomassLedger read(UUID id) { return restored.biomass(id); }
            public boolean commit(UUID id, long revision, BiomassLedger replacement) { return restored.commitBiomass(id, revision, replacement); }
        }, BiomassRuntime.DEFAULTS);
        equal(BiomassRuntime.Status.STALE, restarted.purchase(player.getUUID(), true, 10,
                BiomassDefinitions.Upgrade.EFFICIENCY).status(), "loaded revision rejects old purchase", checks);
        equal(purchased, restored.biomass(player.getUUID()), "replay after disk roundtrip conserves ledger", checks);
        gainPig(helper, player, checks);
        equal(87, ledger(player).balance(), "native pig after efficiency gains floor(12.5)=12", checks);
    }

    private static void selections(GameTestHelper helper, ServerPlayer player, BlockPos roof,
                                   List<Map<String, Object>> checks) throws Exception {
        gainPig(helper, player, checks);
        var bat = FormDescriptor.species("minecraft:bat");
        var pig = FormDescriptor.species("minecraft:pig");
        var golem = FormDescriptor.species("minecraft:iron_golem");
        for (var descriptor : List.of(bat, pig, golem))
            equal(Code.CHANGED, MorphAuthority.grantDescriptor(player, descriptor), "trusted grant " + descriptor.species(), checks);
        var before = ledger(player);
        var collection = MorphAuthority.collection(player).snapshot();
        try (var hook = MorphAuthority.events().register(action -> action.target().equals(player.getUUID())
                && action.action() == MorphEvents.Action.SELECT ? MorphEvents.Decision.cancel("biomass fixture denial") : MorphEvents.Decision.allow())) {
            equal(Code.CANCELED, MorphAuthority.selectEntry(player, bat.entryId()), "canceled selection", checks);
            equal(before, ledger(player), "canceled selection has no fee", checks);
            equal(collection, MorphAuthority.collection(player).snapshot(), "canceled selection preserves collection", checks);
        }
        helper.getLevel().setBlockAndUpdate(roof, Blocks.STONE.defaultBlockState());
        equal(false, ShapeHooks.canFit(player, golem), "ceiling excludes golem", checks);
        equal(Code.NO_SPACE, MorphAuthority.selectEntry(player, golem.entryId()), "cramped selection", checks);
        equal(before, ledger(player), "cramped selection has no fee", checks);
        equal(collection, MorphAuthority.collection(player).snapshot(), "cramped selection preserves collection", checks);
        helper.getLevel().setBlockAndUpdate(roof, Blocks.AIR.defaultBlockState());
        equal(Code.CHANGED, MorphAuthority.selectEntry(player, bat.entryId()), "successful paid morph", checks);
        equal(5, ledger(player).balance(), "successful morph costs exactly five", checks);
        equal(2L, ledger(player).revision(), "successful morph advances ledger once", checks);
        before = ledger(player);
        equal(Code.UNCHANGED, MorphAuthority.selectEntry(player, bat.entryId()), "same form selection", checks);
        equal(Code.COOLDOWN, MorphAuthority.selectEntry(player, pig.entryId()), "same-tick second selection", checks);
        equal(before, ledger(player), "unchanged and cooldown requests have no fee", checks);
        equal(Code.CHANGED, MorphAuthority.resetEntry(player), "reset to self", checks);
        equal(before, ledger(player), "reset is free", checks);
        // Fixture consumes the remaining five through the same persisted ledger port, without another form change.
        var debit = before.charge(BiomassRuntime.DEFAULTS, true, BiomassDefinitions.Action.MORPH);
        equal(true, MorphAuthority.data(player).commitBiomass(player.getUUID(), before.revision(), debit.after()), "empty balance fixture", checks);
        collection = MorphAuthority.collection(player).snapshot();
        before = ledger(player);
        equal(Code.DENIED, MorphAuthority.selectEntry(player, pig.entryId()), "insufficient balance rejection", checks);
        equal(before, ledger(player), "insufficient balance conserves ledger", checks);
        equal(collection, MorphAuthority.collection(player).snapshot(), "insufficient balance preserves self", checks);
    }

    private static BiomassRuntime.Outcome gainPig(GameTestHelper helper, ServerPlayer player,
                                                  List<Map<String, Object>> checks) {
        var pig = helper.spawn(EntityTypes.PIG, 3, 2, 2);
        try {
            var before = ledger(player);
            equal(Code.DENIED, MorphAuthority.capture(player, pig), "mock kill authorization rejected", checks);
            equal(before, ledger(player), "unauthenticated capture cannot grant currency", checks);
            double health = pig.getAttribute(Attributes.MAX_HEALTH).getBaseValue();
            equal(10.0, health, "native pig base health oracle", checks);
            double width = pig.getBbWidth();
            return MorphAuthority.biomass(player).gain(player.getUUID(), MorphAuthority.biomassEnabled(player), true,
                    health, width * width * pig.getBbHeight(), player.distanceTo(pig));
        } finally { pig.discard(); }
    }

    private static void commandParse(ServerPlayer player, List<Map<String, Object>> checks) {
        var dispatcher = player.level().getServer().getCommands().getDispatcher();
        var source = player.createCommandSourceStack();
        for (String command : new String[]{"morph biomass", "morph biomass status", "morph biomass buy capacity 0",
                "morph biomass buy critical_capacity 9223372036854775807"}) {
            var parsed = dispatcher.parse(command, source);
            equal(true, !parsed.getReader().canRead() && parsed.getContext().getCommand() != null
                    && parsed.getExceptions().isEmpty(), "complete command parses: " + command, checks);
        }
        for (String command : new String[]{"morph biomass buy capacity", "morph biomass buy capacity -1",
                "morph biomass buy capacity 9223372036854775808", "morph biomass buy unknown 1"}) {
            var parsed = dispatcher.parse(command, source);
            equal(true, parsed.getReader().canRead() || parsed.getContext().getCommand() == null,
                    "invalid command rejects: " + command, checks);
        }
        equal(BiomassLedger.locked(), ledger(player), "parsing has no side effects", checks);
    }

    private static int command(ServerPlayer player, String command) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return player.level().getServer().getCommands().getDispatcher().execute(command, player.createCommandSourceStack());
    }
    private static BiomassLedger ledger(ServerPlayer player) { return MorphAuthority.data(player).biomass(player.getUUID()); }

    private static MorphSavedData compressedRoundTrip(MorphSavedData saved) throws java.io.IOException {
        Path file = Files.createTempFile("morph-lab-biomass-", ".dat");
        try {
            var tag = (CompoundTag) MorphSavedData.CODEC.encodeStart(NbtOps.INSTANCE, saved).getOrThrow();
            NbtIo.writeCompressed(tag, file);
            return MorphSavedData.CODEC.parse(NbtOps.INSTANCE, NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap())).getOrThrow();
        } finally { Files.deleteIfExists(file); }
    }

    @SuppressWarnings("unchecked")
    private static Supplier<MorphPolicySnapshot> policyBinding() throws ReflectiveOperationException {
        // Test-only access restores the exact live reload supplier instead of replacing it with a frozen snapshot.
        var field = MorphPolicies.class.getDeclaredField("source");
        field.setAccessible(true);
        return (Supplier<MorphPolicySnapshot>) field.get(null);
    }
    private static void equal(Object expected, Object actual, String label, List<Map<String, Object>> checks) {
        boolean pass = java.util.Objects.equals(expected, actual);
        checks.add(Map.of("check", label, "expected", String.valueOf(expected), "actual", String.valueOf(actual), "pass", pass));
        if (!pass) throw new AssertionError(label + ": expected " + expected + ", actual " + actual);
    }
    private static void record(Map<String, Object> event) {
        String json = new Gson().toJson(event);
        System.out.println("MORPH_LAB_BIOMASS " + json);
        String output = System.getProperty("morph.lab.events");
        if (output == null) return;
        try {
            Path path = Path.of(output);
            Files.createDirectories(path.toAbsolutePath().getParent());
            Files.writeString(path, json + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (java.io.IOException error) { throw new IllegalStateException("Cannot record biomass evidence", error); }
    }
}
