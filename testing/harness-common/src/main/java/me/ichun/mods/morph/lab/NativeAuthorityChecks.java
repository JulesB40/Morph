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
import java.util.OptionalLong;
import java.util.UUID;
import me.ichun.mods.morph.ability.MorphAbilities;
import me.ichun.mods.morph.ability.MorphAttributes;
import me.ichun.mods.morph.api.MorphEvents;
import me.ichun.mods.morph.model.CollectionSnapshot;
import me.ichun.mods.morph.model.FormDescriptor;
import me.ichun.mods.morph.model.MorphSounds;
import me.ichun.mods.morph.network.CollectionProtocol;
import me.ichun.mods.morph.network.CollectionProtocol.Code;
import me.ichun.mods.morph.network.CollectionProtocol.Opcode;
import me.ichun.mods.morph.server.MorphAuthority;
import me.ichun.mods.morph.shape.ShapeHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Native service/physics checks with a mock connection; explicitly not live-client authorization evidence. */
public final class NativeAuthorityChecks {
    public enum Case { STALE_ACTIONS, CRAMPED_ACTIVE_DELETE, CANCELED_ACTIONS, REQUEST_RATE, MOCK_ACQUISITION_DENIED }
    private NativeAuthorityChecks() {}
    private record State(CollectionSnapshot collection, float health, float maximum,
            OptionalLong soundTick, List<Double> attributes, String bounds) {}

    private static State state(ServerPlayer player) {
        List<Double> attributes = new ArrayList<>();
        for (var attribute : MorphAttributes.copiedAttributes()) {
            var value = player.getAttribute(attribute);
            attributes.add(value == null ? null : value.getValue());
        }
        // ArrayList permits absent player attribute instances; the record remains local to this test.
        return new State(MorphAuthority.collection(player).snapshot(), player.getHealth(), player.getMaxHealth(),
                MorphSounds.scheduledTick(player), attributes, player.getBoundingBox().toString());
    }

    public static void run(GameTestHelper helper, Case scenario) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "lab-authority"), false);
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {};
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        player.connection = new ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        player.setPose(Pose.STANDING);
        player.setPos(helper.absoluteVec(new Vec3(2.5, 2, 2.5)));
        List<Map<String, Object>> checks = new ArrayList<>();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("schema", "morph-lab.native-authority.v1");
        event.put("scenario", "authority_" + scenario.name().toLowerCase(java.util.Locale.ROOT));
        event.put("scope", "native server service calls, collision, health and scheduled sound; mock connection absent from player list; no real packets or authenticated acquisition");
        event.put("server_tick", helper.getLevel().getGameTime());
        event.put("checks", checks);
        BlockPos roof = helper.absolutePos(new BlockPos(2, 3, 2));
        var oldRoof = helper.getLevel().getBlockState(roof);
        BlockPos floor = helper.absolutePos(new BlockPos(2, 1, 2));
        var oldFloor = helper.getLevel().getBlockState(floor);
        try {
            helper.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(roof, Blocks.AIR.defaultBlockState());
            require(!MorphAuthority.canAcquire(player), "Mock player must not qualify as a live authenticated player");
            if (scenario == Case.MOCK_ACQUISITION_DENIED) {
                var pig = helper.spawn(EntityTypes.PIG, 3, 2, 3);
                try {
                    var before = state(player);
                    equal(Code.DENIED, MorphAuthority.capture(player, pig), "mock acquisition", checks);
                    unchanged(before, player, "mock acquisition", checks);
                } finally { pig.discard(); }
            } else {
                var bat = FormDescriptor.species("minecraft:bat");
                var pig = FormDescriptor.species("minecraft:pig");
                equal(Code.CHANGED, MorphAuthority.grantDescriptor(player, bat), "trusted fixture bat grant", checks);
                equal(Code.CHANGED, MorphAuthority.grantDescriptor(player, pig), "trusted fixture pig grant", checks);
                if (scenario == Case.REQUEST_RATE) {
                    long revision = MorphAuthority.collection(player).revision();
                    equal(Code.UNCHANGED, MorphAuthority.action(player, new CollectionProtocol.Action(0, revision, Opcode.REQUEST_SNAPSHOT, null, false)).code(), "first snapshot request", checks);
                    equal(Code.COOLDOWN, MorphAuthority.action(player, new CollectionProtocol.Action(1, revision, Opcode.REQUEST_SNAPSHOT, null, false)).code(), "same-tick snapshot throttle", checks);
                    for (long sequence = 2; sequence < 20; sequence++)
                        equal(Code.UNCHANGED, MorphAuthority.action(player, new CollectionProtocol.Action(sequence, revision, Opcode.FAVORITE, bat.entryId(), false)).code(), "within action budget", checks);
                    var before = state(player);
                    equal(Code.COOLDOWN, MorphAuthority.action(player, new CollectionProtocol.Action(20, revision, Opcode.FAVORITE, bat.entryId(), true)).code(), "21st action throttled", checks);
                    unchanged(before, player, "rate rejection", checks);
                } else if (scenario == Case.CANCELED_ACTIONS) {
                    try (var hook = MorphAuthority.events().register(action -> action.target().equals(player.getUUID())
                            && action.action() == MorphEvents.Action.SELECT ? MorphEvents.Decision.cancel("fixture selection denial") : MorphEvents.Decision.allow())) {
                        var before = state(player);
                        equal(Code.CANCELED, MorphAuthority.selectEntry(player, bat.entryId()), "canceled select", checks);
                        unchanged(before, player, "canceled select", checks);
                    }
                    equal(Code.CHANGED, MorphAuthority.selectEntry(player, bat.entryId()), "bat activation", checks);
                    for (var action : List.of(MorphEvents.Action.DELETE, MorphEvents.Action.RESET)) {
                        try (var hook = MorphAuthority.events().register(value -> value.target().equals(player.getUUID())
                                && value.action() == action ? MorphEvents.Decision.cancel("fixture deletion denial") : MorphEvents.Decision.allow())) {
                            var before = state(player);
                            equal(Code.CANCELED, MorphAuthority.deleteEntry(player, bat.entryId()), "canceled active delete via " + action, checks);
                            unchanged(before, player, "canceled active delete via " + action, checks);
                        }
                    }
                } else {
                    equal(Code.CHANGED, MorphAuthority.selectEntry(player, bat.entryId()), "bat activation", checks);
                    if (scenario == Case.STALE_ACTIONS) {
                        long stale = MorphAuthority.collection(player).revision() - 1;
                        long sequence = 0;
                        for (var opcode : List.of(Opcode.SELECT, Opcode.DELETE, Opcode.FAVORITE)) {
                            var before = state(player);
                            var id = opcode == Opcode.SELECT ? pig.entryId() : bat.entryId();
                            equal(Code.STALE, MorphAuthority.action(player, new CollectionProtocol.Action(sequence++, stale, opcode, id, opcode == Opcode.FAVORITE)).code(), "stale " + opcode, checks);
                            unchanged(before, player, "stale " + opcode, checks);
                        }
                        var forms = MorphAuthority.collection(player);
                        equal(Code.CHANGED, MorphAuthority.action(player, new CollectionProtocol.Action(sequence, forms.revision(), Opcode.FAVORITE, bat.entryId(), true)).code(), "fresh favorite", checks);
                        require(forms.entry(bat.entryId()).favorite(), "Fresh favorite did not persist");
                        var beforeReplay = state(player);
                        equal(Code.STALE, MorphAuthority.action(player, new CollectionProtocol.Action(sequence, forms.revision(), Opcode.FAVORITE, bat.entryId(), false)).code(), "replayed sequence", checks);
                        unchanged(beforeReplay, player, "replayed sequence", checks);
                    } else {
                        helper.getLevel().setBlockAndUpdate(roof, Blocks.STONE.defaultBlockState());
                        require(ShapeHooks.canFit(player, bat), "Low ceiling fixture must fit bat");
                        require(!ShapeHooks.canFit(player, (FormDescriptor) null), "Low ceiling fixture must block standing self");
                        var before = state(player);
                        equal(Code.NO_SPACE, MorphAuthority.deleteEntry(player, bat.entryId()), "cramped active deletion", checks);
                        unchanged(before, player, "cramped active deletion", checks);
                        helper.getLevel().setBlockAndUpdate(roof, Blocks.AIR.defaultBlockState());
                        require(ShapeHooks.canFit(player, (FormDescriptor) null), "Open control must fit self");
                        equal(Code.CHANGED, MorphAuthority.deleteEntry(player, bat.entryId()), "open active deletion", checks);
                        var forms = MorphAuthority.collection(player);
                        require(forms.activeEntryId() == null && forms.entry(bat.entryId()) == null && forms.entry(pig.entryId()) != null,
                                "Open deletion must remove only the active entry and reset to self");
                        require(forms.revision() == before.collection().revision() + 1, "Active deletion must commit one collection revision");
                    }
                }
            }
            event.put("status", "pass");
        } catch (Throwable error) {
            event.put("status", "fail"); event.put("error", error.toString());
            if (error instanceof Error fatal) throw fatal;
            throw new IllegalStateException("Native authority check failed", error);
        } finally {
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

    private static void unchanged(State expected, ServerPlayer player, String label, List<Map<String, Object>> checks) {
        State actual = state(player);
        checks.add(Map.of("check", label + " preserves collection/health/attributes/sound/bounds", "pass", expected.equals(actual)));
        require(expected.equals(actual), label + " changed authoritative state");
    }
    private static void equal(Code expected, Code actual, String label, List<Map<String, Object>> checks) {
        checks.add(Map.of("check", label, "expected", expected.name(), "actual", actual.name(), "pass", expected == actual));
        require(expected == actual, label + ": expected " + expected + ", actual " + actual);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void record(Map<String, Object> event) {
        String json = new Gson().toJson(event);
        System.out.println("MORPH_LAB_AUTHORITY " + json);
        String output = System.getProperty("morph.lab.events");
        if (output == null) return;
        try {
            Path path = Path.of(output);
            Files.createDirectories(path.toAbsolutePath().getParent());
            Files.writeString(path, json + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (java.io.IOException error) { throw new IllegalStateException("Cannot record authority evidence", error); }
    }
}
