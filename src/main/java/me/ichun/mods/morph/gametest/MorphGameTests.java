package me.ichun.mods.morph.gametest;

import com.mojang.serialization.JsonOps;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.function.Consumer;
import me.ichun.mods.morph.server.MorphSavedData;
import me.ichun.mods.morph.server.MorphService;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.util.ProblemReporter;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Integration tests run in a real GameTestServer; no client or EULA mutation required. */
@EventBusSubscriber(modid = "morph")
public final class MorphGameTests {
    private MorphGameTests() {}

    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        if (!Boolean.getBoolean("morph.enableGameTests")) return;
        event.register(Registries.TEST_FUNCTION, helper -> {
            helper.register(id("selection_rules"), MorphGameTests::selectionRules);
            helper.register(id("kill_acquires_pig"), MorphGameTests::killAcquiresPig);
            helper.register(id("saved_data_round_trip"), MorphGameTests::savedDataRoundTrip);
            helper.register(id("pig_geometry_and_reset"), MorphGameTests::pigGeometryAndReset);
            helper.register(id("ceiling_rejects_tall_form"), MorphGameTests::ceilingRejectsTallForm);
            helper.register(id("crouch_cannot_stand_through_ceiling"), MorphGameTests::crouchCannotStandThroughCeiling);
            helper.register(id("flight_cleanup_and_player_save"), MorphGameTests::flightCleanupAndPlayerSave);
            helper.register(id("external_flight_preserved"), MorphGameTests::externalFlightPreserved);
            helper.register(id("aquatic_air_on_actual_tick"), MorphGameTests::aquaticAirOnActualTick);
            helper.register(id("fall_event_immunity_and_cleanup"), MorphGameTests::fallEventImmunityAndCleanup);
            helper.register(id("flight_survives_game_mode_change"), MorphGameTests::flightSurvivesGameModeChange);
        });
    }

    @SubscribeEvent
    public static void registerTests(RegisterGameTestsEvent event) {
        if (!Boolean.getBoolean("morph.enableGameTests")) return;
        var environment = event.registerEnvironment(id("smoke"));
        for (String name : new String[] {"selection_rules", "kill_acquires_pig", "saved_data_round_trip",
                "pig_geometry_and_reset", "ceiling_rejects_tall_form", "crouch_cannot_stand_through_ceiling",
                "flight_cleanup_and_player_save", "external_flight_preserved", "aquatic_air_on_actual_tick",
                "fall_event_immunity_and_cleanup", "flight_survives_game_mode_change"}) {
            ResourceKey<Consumer<GameTestHelper>> function = ResourceKey.create(Registries.TEST_FUNCTION, id(name));
            event.registerTest(id(name), new FunctionGameTestInstance(function,
                    new TestData<>(environment, Identifier.withDefaultNamespace("empty"), 100, 0, true)));
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("morph", path);
    }

    private static void selectionRules(GameTestHelper helper) {
        var player = connectedPlayer(helper);
        player.setGameMode(GameType.SURVIVAL);
        var forms = MorphService.collection(player);
        helper.assertFalse(MorphService.select(player, "minecraft:pig"), "Unowned pig must be rejected");
        forms.unlock("minecraft:pig");
        forms.unlock("minecraft:cow");
        forms.unlock("minecraft:item");
        forms.unlock("minecraft:missing_morph_test_entity");
        helper.assertFalse(MorphService.select(player, "minecraft:item"), "Nonliving entity must be rejected even if saved as owned");
        helper.assertFalse(MorphService.select(player, "minecraft:missing_morph_test_entity"), "Unknown registry ID must be rejected");
        helper.assertFalse(MorphService.select(player, "minecraft:player"), "Player entity ID must be rejected");
        helper.assertTrue(MorphService.select(player, "minecraft:pig"), "Owned living pig must be selectable");
        helper.assertValueEqual(forms.activeForm(), "minecraft:pig", "Selected form");
        helper.assertFalse(MorphService.select(player, "minecraft:cow"), "Immediate second selection must be rate limited");
        helper.assertTrue(MorphService.reset(player), "Reset must always be available");
        helper.assertValueEqual(forms.activeForm(), "", "Reset appearance");
        helper.assertFalse(MorphService.select(player, "minecraft:cow"), "Reset must not bypass cooldown");
        helper.runAfterDelay(21, () -> {
            helper.assertTrue(MorphService.select(player, "minecraft:cow"), "Selection must recover after cooldown");
            player.setGameMode(GameType.SPECTATOR);
            helper.assertFalse(MorphService.select(player, "minecraft:pig"), "Spectator must not select forms");
            helper.succeed();
        });
    }

    private static void killAcquiresPig(GameTestHelper helper) {
        var player = connectedPlayer(helper);
        player.setGameMode(GameType.SURVIVAL);
        var pig = helper.spawn(EntityTypes.PIG, 1, 1, 1);
        helper.assertValueEqual(pig.getType(), EntityTypes.PIG, "Spawned pig registry type");
        helper.assertFalse(MorphService.collection(player).ownedForms().contains("minecraft:pig"), "Collection starts without pig");
        pig.hurtServer(helper.getLevel(), player.damageSources().playerAttack(player), 1000.0F);
        helper.assertTrue(pig.isDeadOrDying(), "Test attack must kill pig");
        helper.assertTrue(MorphService.collection(player).ownedForms().contains("minecraft:pig"),
                "Registered living-death listener must acquire pig for killer");
        helper.assertTrue(MorphService.select(player, "minecraft:pig"), "Acquired form must be selectable");
        helper.succeed();
    }

    private static void savedDataRoundTrip(GameTestHelper helper) {
        var player = connectedPlayer(helper);
        player.setGameMode(GameType.SURVIVAL);
        MorphService.collection(player).unlock("minecraft:pig");
        helper.assertTrue(MorphService.select(player, "minecraft:pig"), "Select form before save");
        var storage = helper.getLevel().getServer().overworld().getDataStorage();
        var saved = storage.computeIfAbsent(MorphSavedData.TYPE);
        helper.assertTrue(saved.isDirty(), "Service selection must mark overworld saved data dirty");
        var encoded = MorphSavedData.CODEC.encodeStart(JsonOps.INSTANCE, saved).getOrThrow();
        var loaded = MorphSavedData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        helper.assertValueEqual(loaded.collection(player.getUUID()).ownedForms(),
                MorphService.collection(player).ownedForms(), "Saved collection ownership");
        helper.assertValueEqual(loaded.collection(player.getUUID()).activeForm(), "minecraft:pig", "Saved active form");
        helper.assertTrue(loaded.collection(java.util.UUID.randomUUID()).ownedForms().isEmpty(), "Different player must not inherit ownership");
        helper.succeed();
    }

    private static void pigGeometryAndReset(GameTestHelper helper) {
        var player = connectedPlayer(helper);
        player.setGameMode(GameType.SURVIVAL);
        float originalHeight = player.getBbHeight();
        float originalWidth = player.getBbWidth();
        float originalEyeHeight = player.getEyeHeight();
        var pig = EntityTypes.PIG.create(helper.getLevel(), EntitySpawnReason.LOAD);
        helper.assertTrue(pig != null, "Vanilla pig adapter must exist");
        var expected = pig.getDimensions(Pose.STANDING);
        MorphService.collection(player).unlock("minecraft:pig");
        helper.assertTrue(MorphService.select(player, "minecraft:pig"), "Pig selection must succeed");
        near(helper, player.getBbHeight(), expected.height(), "Morphed bounding-box height matches vanilla pig");
        near(helper, player.getBbWidth(), expected.width(), "Morphed bounding-box width matches vanilla pig");
        near(helper, player.getEyeHeight(), expected.eyeHeight(), "Morphed eye height matches vanilla pig");
        helper.assertTrue(Math.abs(player.getBbHeight() - originalHeight) > 0.1F, "Selection must actually change player height");
        helper.assertTrue(MorphService.reset(player), "Reset in open space must succeed");
        near(helper, player.getBbHeight(), originalHeight, "Reset restores player height");
        near(helper, player.getBbWidth(), originalWidth, "Reset restores player width");
        near(helper, player.getEyeHeight(), originalEyeHeight, "Reset restores player eye height");
        helper.succeed();
    }

    private static void ceilingRejectsTallForm(GameTestHelper helper) {
        var player = connectedPlayer(helper);
        player.setGameMode(GameType.SURVIVAL);
        var forms = MorphService.collection(player);
        forms.unlock("minecraft:pig");
        forms.unlock("minecraft:enderman");
        helper.assertTrue(MorphService.select(player, "minecraft:pig"), "Initial pig selection");
        helper.runAfterDelay(21, () -> {
            // Reposition after elapsed ticks to avoid gravity changing the ceiling clearance.
            player.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(0.5, 2.0, 0.5)));
            helper.setBlock(0, 4, 0, Blocks.STONE);
            float pigHeight = player.getBbHeight();
            helper.assertFalse(MorphService.select(player, "minecraft:enderman"), "Two-block clearance must reject tall enderman");
            helper.assertValueEqual(forms.activeForm(), "minecraft:pig", "Rejected tall form preserves selected pig");
            near(helper, player.getBbHeight(), pigHeight, "Rejected tall form preserves collision height");
            helper.setBlock(0, 4, 0, Blocks.AIR);
            helper.assertTrue(MorphService.select(player, "minecraft:enderman"), "Removing ceiling must permit enderman immediately");
            helper.assertTrue(player.getBbHeight() > 2.0F, "Enderman must use tall geometry");
            helper.succeed();
        });
    }

    private static void crouchCannotStandThroughCeiling(GameTestHelper helper) {
        var player = connectedPlayer(helper);
        player.setGameMode(GameType.SURVIVAL);
        MorphService.collection(player).unlock("minecraft:pig");
        helper.assertTrue(MorphService.select(player, "minecraft:pig"), "Initial pig selection");
        float standingHeight = player.getBbHeight();
        player.setPose(Pose.CROUCHING);
        player.refreshDimensions();
        helper.assertTrue(player.getBbHeight() < standingHeight, "Crouching must shrink morph geometry");
        // Pig standing height is 0.9 and crouched height is 0.75: leave 0.8 blocks of clearance.
        player.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(0.5, 2.2, 0.5)));
        helper.setBlock(0, 3, 0, Blocks.STONE);
        player.setShiftKeyDown(false);
        player.updatePoseForTest();
        helper.assertValueEqual(player.getPose(), Pose.CROUCHING, "Releasing crouch must not stand through ceiling");
        helper.assertTrue(helper.getLevel().noCollision(player), "Crouched shape must remain clear of ceiling");
        helper.assertFalse(MorphService.reset(player), "Reset to taller player must be rejected under ceiling");
        helper.assertValueEqual(MorphService.collection(player).activeForm(), "minecraft:pig", "Rejected reset preserves pig form");
        helper.setBlock(0, 3, 0, Blocks.AIR);
        player.updatePoseForTest();
        player.refreshDimensions();
        helper.assertValueEqual(player.getPose(), Pose.STANDING, "Removing ceiling permits normal standing");
        near(helper, player.getBbHeight(), standingHeight, "Standing restores morph height");
        helper.succeed();
    }

    private static void flightCleanupAndPlayerSave(GameTestHelper helper) {
        var player = connectedPlayer(helper);
        player.setGameMode(GameType.SURVIVAL);
        helper.assertFalse(player.getAbilities().mayfly, "Survival baseline has no flight grant");
        helper.assertFalse(player.getAbilities().flying, "Survival baseline is not flying");
        MorphService.collection(player).unlock("minecraft:bat");
        helper.assertTrue(MorphService.select(player, "minecraft:bat"), "Select flying bat");
        helper.assertTrue(player.getAbilities().mayfly, "Selection grants flight immediately");
        helper.assertFalse(player.getAbilities().flying, "Selecting flight must not force takeoff");
        player.getAbilities().flying = true;
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        player.saveWithoutId(output);
        var saved = output.buildResult().getCompound("abilities").orElseThrow();
        helper.assertFalse(saved.getBoolean("mayfly").orElseThrow(), "Vanilla player save must exclude Morph flight permission");
        helper.assertFalse(saved.getBoolean("flying").orElseThrow(), "Vanilla player save must exclude Morph flying state");
        helper.assertTrue(player.getAbilities().mayfly && player.getAbilities().flying, "Saving must not mutate live flight flags");
        helper.assertTrue(MorphService.reset(player), "Reset flying morph");
        helper.assertFalse(player.getAbilities().mayfly, "Reset revokes Morph-owned flight permission");
        helper.assertFalse(player.getAbilities().flying, "Reset ends Morph-owned flight");
        helper.succeed();
    }

    private static void externalFlightPreserved(GameTestHelper helper) {
        var player = connectedPlayer(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        MorphService.collection(player).unlock("minecraft:bee");
        helper.assertTrue(MorphService.select(player, "minecraft:bee"), "Select bee with external flight already granted");
        helper.assertTrue(MorphService.reset(player), "Reset bee with external grant");
        helper.assertTrue(player.getAbilities().mayfly && player.getAbilities().flying, "Morph must preserve preexisting flight flags");
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        player.saveWithoutId(output);
        var saved = output.buildResult().getCompound("abilities").orElseThrow();
        helper.assertTrue(saved.getBoolean("mayfly").orElseThrow(), "External flight permission remains saveable");
        helper.assertTrue(saved.getBoolean("flying").orElseThrow(), "External flying state remains saveable");
        helper.succeed();
    }

    private static void aquaticAirOnActualTick(GameTestHelper helper) {
        var player = connectedPlayer(helper);
        player.setGameMode(GameType.SURVIVAL);
        MorphService.collection(player).unlock("minecraft:cod");
        helper.assertTrue(MorphService.select(player, "minecraft:cod"), "Select aquatic cod");
        for (int x = -1; x <= 1; x++) {
            for (int y = 1; y <= 4; y++) {
                for (int z = -1; z <= 1; z++) helper.setBlock(x, y, z, Blocks.WATER);
            }
        }
        player.setNoGravity(true);
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        // Embedded test connections are not on the socket tick loop. Drive the same real
        // player tick entrypoint that ServerGamePacketListenerImpl.tick normally invokes.
        helper.onEachTick(player::doTick);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(player.isUnderWater(), "Fixture must actually submerge the player's eyes");
            player.setAirSupply(10);
            helper.runAfterDelay(1, () -> {
                helper.assertTrue(player.getAirSupply() >= player.getMaxAirSupply() - 2,
                        "Registered pre-tick hook must replenish aquatic air before drowning");
                helper.assertTrue(MorphService.reset(player), "Reset aquatic form while underwater");
                player.setAirSupply(10);
                helper.runAfterDelay(2, () -> {
                    helper.assertTrue(player.isUnderWater(), "Reset player must remain underwater for control");
                    helper.assertTrue(player.getAirSupply() < 10, "Normal player air must decrease after aquatic reset");
                    helper.succeed();
                });
            });
        });
    }

    private static void fallEventImmunityAndCleanup(GameTestHelper helper) {
        var player = connectedPlayer(helper);
        player.setGameMode(GameType.SURVIVAL);
        MorphService.collection(player).unlock("minecraft:parrot");
        helper.assertTrue(MorphService.select(player, "minecraft:parrot"), "Select fall-immune parrot");
        var morphedFall = NeoForge.EVENT_BUS.post(new LivingFallEvent(player, 10.0, 1.0F));
        helper.assertTrue(morphedFall.isCanceled(), "Registered fall listener cancels damage event for parrot");
        helper.assertTrue(MorphService.reset(player), "Reset fall-immune form");
        var normalFall = NeoForge.EVENT_BUS.post(new LivingFallEvent(player, 10.0, 1.0F));
        helper.assertFalse(normalFall.isCanceled(), "Fall event must no longer be canceled after reset");
        helper.succeed();
    }

    private static void flightSurvivesGameModeChange(GameTestHelper helper) {
        var player = connectedPlayer(helper);
        player.setGameMode(GameType.SURVIVAL);
        MorphService.collection(player).unlock("minecraft:bat");
        helper.assertTrue(MorphService.select(player, "minecraft:bat"), "Select bat before game mode change");
        helper.assertTrue(player.getAbilities().mayfly, "Bat flight initially granted");
        player.setGameMode(GameType.ADVENTURE);
        player.doTick();
        helper.assertTrue(player.getAbilities().mayfly, "Actual player tick must restore bat flight after adventure mode updates vanilla flags");
        helper.assertTrue(MorphService.reset(player), "Reset bat after changing game mode");
        helper.assertFalse(player.getAbilities().mayfly, "Cleanup must still revoke Morph-owned permission after game mode change");
        helper.succeed();
    }

    private static void near(GameTestHelper helper, float actual, float expected, String label) {
        helper.assertTrue(Math.abs(actual - expected) < 0.0001F, label + ": expected " + expected + ", got " + actual);
    }

    private static TestPlayer connectedPlayer(GameTestHelper helper) {
        // Vanilla's deprecated mock helper hardcodes CREATIVE; use a real mutable game mode.
        UUID playerId = UUID.randomUUID();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(playerId, "morph-" + playerId.toString().substring(0, 8)), false);
        // NeoForge identifies test-player subclasses when skipping real configuration handshakes.
        var player = new TestPlayer(helper, cookie);
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(0.5, 2.0, 0.5)));
        return player;
    }

    private static final class TestPlayer extends ServerPlayer {
        private TestPlayer(GameTestHelper helper, CommonListenerCookie cookie) {
            super(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        }

        private void updatePoseForTest() {
            updatePlayerPose();
        }
    }
}
