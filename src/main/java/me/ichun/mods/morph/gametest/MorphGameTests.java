package me.ichun.mods.morph.gametest;

import com.mojang.serialization.JsonOps;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import me.ichun.mods.morph.ability.MorphAttributes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.level.storage.TagValueInput;

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
            helper.register(id("attribute_defaults_and_persistence"), MorphGameTests::attributeDefaultsAndPersistence);
            helper.register(id("external_attributes_survive_morph"), MorphGameTests::externalAttributesSurviveMorph);
            helper.register(id("animated_health_selection_and_reset"), MorphGameTests::animatedHealthSelectionAndReset);
            helper.register(id("passive_trait_hooks"), MorphGameTests::passiveTraitHooks);
            helper.register(id("intimidation_moves_creeper"), MorphGameTests::intimidationMovesCreeper);
            helper.register(id("hostility_and_riding_hooks"), MorphGameTests::hostilityAndRidingHooks);
            helper.register(id("selection_rules"), MorphGameTests::selectionRules);
            helper.register(id("transformation_sound_lifecycle"), MorphGameTests::transformationSoundLifecycle);
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
        for (String name : new String[] {"intimidation_moves_creeper", "hostility_and_riding_hooks", "passive_trait_hooks", "attribute_defaults_and_persistence", "external_attributes_survive_morph", "animated_health_selection_and_reset", "selection_rules", "transformation_sound_lifecycle", "kill_acquires_pig", "saved_data_round_trip",
                "pig_geometry_and_reset", "ceiling_rejects_tall_form", "crouch_cannot_stand_through_ceiling",
                "flight_cleanup_and_player_save", "external_flight_preserved", "aquatic_air_on_actual_tick",
                "fall_event_immunity_and_cleanup", "flight_survives_game_mode_change"}) {
            ResourceKey<Consumer<GameTestHelper>> function = ResourceKey.create(Registries.TEST_FUNCTION, id(name));
            event.registerTest(id(name), new FunctionGameTestInstance(function,
                    new TestData<>(environment, Identifier.withDefaultNamespace("empty"), 250, 0, true)));
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("morph", path);
    }

    private static void transformationSoundLifecycle(GameTestHelper helper) {
        var player = connectedPlayer(helper);
        player.setGameMode(GameType.SURVIVAL);
        var forms = MorphService.collection(player);
        helper.assertFalse(MorphService.select(player, "minecraft:pig"), "Unowned selection rejected");
        helper.assertTrue(me.ichun.mods.morph.model.MorphSounds.scheduledTick(player).isEmpty(), "Rejection schedules no sound");
        forms.unlock("minecraft:pig");
        helper.assertTrue(MorphService.select(player, "minecraft:pig"), "Owned selection accepted");
        long due = me.ichun.mods.morph.model.MorphSounds.scheduledTick(player).orElseThrow();
        helper.assertValueEqual(due, player.level().getServer().overworld().getGameTime() + 20, "Original clip starts one second into morph");
        helper.runAfterDelay(5, () -> {
            MorphService.select(player, "minecraft:pig");
            MorphService.requestCollection(player);
            NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(player));
            helper.assertValueEqual(me.ichun.mods.morph.model.MorphSounds.scheduledTick(player).orElseThrow(), due, "Redundant selection and login sync do not restart audio");
        });
        helper.runAfterDelay(21, () -> {
            me.ichun.mods.morph.model.MorphSounds.tick(player);
            helper.assertTrue(me.ichun.mods.morph.model.MorphSounds.scheduledTick(player).isEmpty(), "Due sound consumed exactly once");
            MorphService.reset(player);
            helper.assertTrue(me.ichun.mods.morph.model.MorphSounds.scheduledTick(player).isPresent(), "Returning to human schedules audio");
            NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(player));
            helper.assertTrue(me.ichun.mods.morph.model.MorphSounds.scheduledTick(player).isEmpty(), "Logout cancels pending sound");
            MorphService.reset(player);
            helper.assertTrue(me.ichun.mods.morph.model.MorphSounds.scheduledTick(player).isEmpty(), "Redundant reset stays silent");
            helper.succeed();
        });
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
        MorphService.collection(player).unlock("minecraft:blaze");
        helper.assertTrue(MorphService.select(player, "minecraft:blaze"), "Select flying blaze");
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
        MorphService.collection(player).unlock("minecraft:ghast");
        helper.assertTrue(MorphService.select(player, "minecraft:ghast"), "Select ghast with external flight already granted");
        helper.assertTrue(MorphService.reset(player), "Reset ghast with external grant");
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

        helper.runAfterDelay(3, () -> {
            player.doTick();
            player.doTick();
            helper.assertTrue(player.isUnderWater(), "Fixture must actually submerge the player's eyes");
            player.setAirSupply(10);
            helper.runAfterDelay(1, () -> {
                player.setAirSupply(10);
                player.doTick();
                helper.assertTrue(player.getAirSupply() > 10 && player.getAirSupply() <= 14,
                        "Registered pre-tick hook adds original four air before vanilla consumption");
                near(helper, player.getHealth(), player.getMaxHealth(), "Aquatic breath prevents drowning damage");
                helper.assertTrue(MorphService.reset(player), "Reset aquatic form while underwater");
                player.setAirSupply(10);
                helper.runAfterDelay(2, () -> {
                    player.setAirSupply(10);
                    player.doTick();
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
        MorphService.collection(player).unlock("minecraft:blaze");
        helper.assertTrue(MorphService.select(player, "minecraft:blaze"), "Select blaze before game mode change");
        helper.assertTrue(player.getAbilities().mayfly, "Blaze flight initially granted");
        player.setGameMode(GameType.ADVENTURE);
        player.doTick();
        helper.assertTrue(player.getAbilities().mayfly, "Actual player tick must restore blaze flight after adventure mode updates vanilla flags");
        helper.assertTrue(MorphService.reset(player), "Reset blaze after changing game mode");
        helper.assertFalse(player.getAbilities().mayfly, "Cleanup must still revoke Morph-owned permission after game mode change");
        helper.succeed();
    }

    private static void attributeDefaultsAndPersistence(GameTestHelper helper) {
        var player = connectedPlayer(helper);
        player.setHealth(10.0F);
        MorphAttributes.tick(player, "minecraft:pig");
        near(helper, player.getMaxHealth(), 10.0F, "Pig has ten health");
        near(helper, player.getHealth(), 5.0F, "Morph preserves half health");
        near(helper, (float) player.getAttributeValue(Attributes.MOVEMENT_SPEED), 0.1F, "Original movement speed cap");
        MorphAttributes.tick(player, "minecraft:bat");
        near(helper, player.getMaxHealth(), 6.0F, "Bat has six health");
        near(helper, player.getHealth(), 3.0F, "Switching form preserves half health");
        MorphAttributes.tick(player, "minecraft:iron_golem");
        near(helper, player.getMaxHealth(), 20.0F, "Original maximum health cap");
        MorphAttributes.tick(player, "minecraft:zombie");
        near(helper, (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE), 3.0F, "Zombie attack strength");
        near(helper, (float) player.getAttributeValue(Attributes.ARMOR), 2.0F, "Zombie natural armor");
        MorphAttributes.tick(player, "minecraft:pig");
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        player.saveWithoutId(output);
        var saved = output.buildResult();
        near(helper, saved.getFloat("Health").orElseThrow(), 10.0F, "Save normalizes health to human units");
        helper.assertFalse(saved.toString().contains("morph:form_attribute"), "Save excludes owned transient attribute modifiers");
        near(helper, player.getHealth(), 5.0F, "Saving leaves live health unchanged");
        var restored = connectedPlayer(helper);
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
        near(helper, restored.getHealth(), 10.0F, "Actual player load restores normalized health");
        MorphAttributes.tick(restored, "minecraft:pig");
        near(helper, restored.getHealth(), 5.0F, "Reapplying persisted form restores health ratio");
        MorphAttributes.tick(player, "");
        near(helper, player.getMaxHealth(), 20.0F, "Reset restores human maximum");
        near(helper, player.getHealth(), 10.0F, "Reset preserves half health");
        player.setHealth(0.0F);
        MorphAttributes.tick(player, "minecraft:bat");
        MorphAttributes.cleanup(player);
        near(helper, player.getHealth(), 0.0F, "Attribute updates never revive dead players");
        helper.succeed();
    }

    private static void externalAttributesSurviveMorph(GameTestHelper helper) {
        var player = connectedPlayer(helper);
        var maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        var external = Identifier.fromNamespaceAndPath("morph_test", "external_health");
        maxHealth.setBaseValue(24.0);
        maxHealth.addPermanentModifier(new AttributeModifier(external, 4.0, AttributeModifier.Operation.ADD_VALUE));
        player.setHealth(14.0F);
        MorphAttributes.tick(player, "minecraft:pig");
        near(helper, player.getMaxHealth(), 14.0F, "External health bonus remains on pig");
        near(helper, player.getHealth(), 7.0F, "External health keeps injury ratio");
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        player.saveWithoutId(output);
        near(helper, output.buildResult().getFloat("Health").orElseThrow(), 14.0F, "Save includes external baseline health");
        helper.assertTrue(output.buildResult().toString().contains("morph_test:external_health"), "External permanent modifier remains saveable");
        MorphAttributes.cleanup(player);
        near(helper, (float) maxHealth.getBaseValue(), 24.0F, "Cleanup preserves custom base health");
        helper.assertTrue(maxHealth.hasModifier(external), "Cleanup preserves external modifier identity");
        near(helper, player.getMaxHealth(), 28.0F, "Cleanup restores baseline plus external bonus");
        near(helper, player.getHealth(), 14.0F, "Cleanup preserves injury ratio");
        helper.succeed();
    }

    private static void animatedHealthSelectionAndReset(GameTestHelper helper) {
        var player = connectedPlayer(helper);
        player.setHealth(10.0F);
        MorphService.collection(player).unlock("minecraft:pig");
        helper.assertTrue(MorphService.select(player, "minecraft:pig"), "Select pig through service");
        near(helper, player.getMaxHealth(), 20.0F, "Selection starts at previous maximum");
        helper.onEachTick(() -> MorphAttributes.tick(player, MorphService.collection(player).activeForm()));
        helper.runAfterDelay(50, () -> {
            helper.assertTrue(player.getMaxHealth() > 10.0F && player.getMaxHealth() < 20.0F, "Health interpolates during black transition");
            near(helper, player.getHealth() / player.getMaxHealth(), 0.5F, "Transition preserves injury ratio");
        });
        helper.runAfterDelay(101, () -> {
            near(helper, player.getMaxHealth(), 10.0F, "Completed selection has pig maximum");
            near(helper, player.getHealth(), 5.0F, "Completed selection keeps half health");
            helper.assertTrue(MorphService.reset(player), "Reset through service");
        });
        helper.runAfterDelay(202, () -> {
            near(helper, player.getMaxHealth(), 20.0F, "Animated reset restores human maximum");
            near(helper, player.getHealth(), 10.0F, "Animated reset keeps half health");
            helper.succeed();
        });
    }

    private static void passiveTraitHooks(GameTestHelper helper) {
        var spider = connectedPlayer(helper);
        var spiderForms = MorphService.collection(spider);
        spiderForms.unlock("minecraft:spider");
        spiderForms.select("minecraft:spider", 0);
        spider.horizontalCollision = true;
        helper.assertTrue(spider.onClimbable(), "Spider wall contact activates actual climb hook");
        spider.horizontalCollision = false;
        helper.assertFalse(spider.onClimbable(), "Spider cannot climb without wall contact");
        var zombie = connectedPlayer(helper);
        var zombieForms = MorphService.collection(zombie);
        zombieForms.unlock("minecraft:zombie");
        zombieForms.select("minecraft:zombie", 0);
        zombie.setSwimming(true);
        zombie.updateSwimming();
        helper.assertFalse(zombie.isSwimming(), "Ordinary zombie cannot retain player swimming state");
        helper.assertTrue(me.ichun.mods.morph.ability.MorphSwimmingRules.blocksSwimming(zombie), "Zombie uses bottom-walking movement");
        var waterPos = zombie.blockPosition();
        var oldWaterBlock = helper.getLevel().getBlockState(waterPos);
        var oldAboveBlock = helper.getLevel().getBlockState(waterPos.above());
        helper.getLevel().setBlockAndUpdate(waterPos, Blocks.WATER.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(waterPos.above(), Blocks.WATER.defaultBlockState());
        zombie.doTick();
        zombie.setOnGround(false);
        helper.assertTrue(zombie.isInWater(), "Zombie input fixture is submerged");
        helper.assertFalse(me.ichun.mods.morph.ability.MorphSwimmingRules.allowsJumpInput(zombie), "Zombie cannot apply midwater swimming jump input");
        zombie.setDeltaMovement(0, 0.4, 0);
        me.ichun.mods.morph.ability.MorphSwimmingRules.beforeTravel(zombie);
        helper.assertTrue(zombie.getDeltaMovement().y == 0.4, "Zombie swimming rule preserves external upward velocity");
        zombie.setOnGround(true);
        helper.assertTrue(me.ichun.mods.morph.ability.MorphSwimmingRules.allowsJumpInput(zombie), "Zombie grounded jump remains available");
        zombie.setOnGround(false);
        zombieForms.unlock("minecraft:drowned");
        zombieForms.select("minecraft:drowned", 100);
        helper.assertFalse(me.ichun.mods.morph.ability.MorphSwimmingRules.blocksSwimming(zombie), "Drowned retains swimming eligibility");
        helper.assertTrue(me.ichun.mods.morph.ability.MorphSwimmingRules.canSwim("minecraft:drowned"), "Drowned animation remains eligible");
        helper.assertTrue(me.ichun.mods.morph.ability.MorphSwimmingRules.allowsJumpInput(zombie), "Drowned keeps midwater swimming jump input");
        helper.getLevel().setBlockAndUpdate(waterPos, oldWaterBlock);
        helper.getLevel().setBlockAndUpdate(waterPos.above(), oldAboveBlock);
        zombieForms.select("minecraft:zombie", 200);
        helper.assertFalse(zombie.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 100)), "Undead hook rejects poison");
        helper.assertFalse(zombie.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 100)), "Undead hook rejects regeneration");
        zombieForms.reset();
        helper.assertTrue(zombie.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 100)), "Reset removes undead effect immunity");
        spiderForms.unlock("minecraft:cave_spider");
        spiderForms.select("minecraft:cave_spider", 100);
        var victim = helper.spawn(EntityTypes.PIG, 2, 2, 2);
        victim.hurtServer(helper.getLevel(), spider.damageSources().playerAttack(spider), 1.0F);
        var poison = victim.getEffect(net.minecraft.world.effect.MobEffects.POISON);
        helper.assertTrue(poison != null && poison.getDuration() == 140, "Actual cave spider melee applies original poison duration");
        spiderForms.unlock("minecraft:wither_skeleton");
        spiderForms.select("minecraft:wither_skeleton", 200);
        var secondVictim = helper.spawn(EntityTypes.PIG, 3, 2, 2);
        secondVictim.hurtServer(helper.getLevel(), spider.damageSources().playerAttack(spider), 1.0F);
        var wither = secondVictim.getEffect(net.minecraft.world.effect.MobEffects.WITHER);
        helper.assertTrue(wither != null && wither.getDuration() == 200, "Actual wither skeleton melee applies original wither duration");
        helper.assertTrue(me.ichun.mods.morph.ability.MorphTraits.preventsDamage(spider, "minecraft:blaze", spider.damageSources().lava()), "Blaze resists lava");
        helper.assertFalse(me.ichun.mods.morph.ability.MorphTraits.preventsDamage(spider, "minecraft:blaze", spider.damageSources().generic()), "Fire immunity does not grant general invulnerability");
        helper.assertTrue(me.ichun.mods.morph.ability.MorphAbilities.preventsFallDamage("minecraft:chicken"), "Chicken has original fall immunity");
        spiderForms.unlock("minecraft:bat");
        spiderForms.select("minecraft:bat", 300);
        spider.setOnGround(false);
        spider.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        helper.assertTrue(me.ichun.mods.morph.ability.MorphActions.flap(spider), "Airborne bat accepts flap action");
        helper.assertTrue(spider.getDeltaMovement().y > 0.4, "Flap applies upward impulse");
        helper.assertFalse(me.ichun.mods.morph.ability.MorphActions.flap(spider), "Repeated same tick flap is rate limited");
        helper.assertFalse(spider.getAbilities().mayfly, "Bat flap does not grant creative-style flight");
        spiderForms.reset();
        helper.assertFalse(me.ichun.mods.morph.ability.MorphActions.flap(spider), "Human form cannot flap");
        var fish = connectedPlayer(helper);
        float startHealth = fish.getHealth();
        for (int tick = 0; tick < 301; tick++) me.ichun.mods.morph.ability.MorphTraits.tick(fish, "minecraft:cod");
        helper.assertTrue(fish.getHealth() < startHealth, "Fish dries out after air allowance expires");
        var turtle = connectedPlayer(helper);
        float turtleHealth = turtle.getHealth();
        for (int tick = 0; tick < 301; tick++) me.ichun.mods.morph.ability.MorphTraits.tick(turtle, "minecraft:turtle");
        near(helper, turtle.getHealth(), turtleHealth, "Amphibious turtle remains healthy on land");
        helper.succeed();
    }

    private static void hostilityAndRidingHooks(GameTestHelper helper) {
        var mount = connectedPlayer(helper);
        var forms = MorphService.collection(mount);
        forms.unlock("minecraft:zombie");
        forms.select("minecraft:zombie", 0);
        var hostile = helper.spawn(EntityTypes.ZOMBIE, 2, 2, 2);
        hostile.setTarget(mount);
        helper.assertTrue(hostile.getTarget() == null, "Hostile disguise rejects actual mob target assignment");
        hostile.hurtServer(helper.getLevel(), mount.damageSources().playerAttack(mount), 1.0F);
        helper.assertTrue(hostile.getLastHurtByMob() == mount, "Actual attack records revenge target");
        hostile.setTarget(mount);
        helper.assertTrue(hostile.getTarget() == mount, "Disguise never suppresses retaliation");
        forms.unlock("minecraft:horse");
        forms.select("minecraft:horse", 100);
        var rider = connectedPlayer(helper);
        rider.interactOn(mount, net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.phys.Vec3.ZERO);
        helper.assertTrue(rider.getVehicle() == mount, "Actual player interaction mounts horse form without saddle");
        mount.setShiftKeyDown(true);
        me.ichun.mods.morph.ability.MorphInteractions.tick(mount, "minecraft:horse");
        helper.assertFalse(rider.isPassenger(), "Mount crouch ejects passenger");
        mount.setShiftKeyDown(false);
        rider.interactOn(mount, net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.phys.Vec3.ZERO);
        helper.assertTrue(rider.getVehicle() == mount, "Horse can accept passenger again");
        forms.reset();
        me.ichun.mods.morph.ability.MorphInteractions.tick(mount, "");
        helper.assertFalse(rider.isPassenger(), "Reset to human ejects passenger");
        rider.interactOn(mount, net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.phys.Vec3.ZERO);
        helper.assertFalse(rider.isPassenger(), "Human cannot be mounted");
        helper.succeed();
    }

    private static void intimidationMovesCreeper(GameTestHelper helper) {
        var player = connectedPlayer(helper);

        var forms = MorphService.collection(player);
        forms.unlock("minecraft:cat");
        forms.select("minecraft:cat", 0);
        // Isolated high platform avoids affecting the neighboring smoke-test structures.
        var center = helper.absolutePos(new net.minecraft.core.BlockPos(3, 180, 3));
        var floor = new java.util.ArrayList<net.minecraft.core.BlockPos>();
        for (int x = -16; x <= 16; x++) for (int z = -16; z <= 16; z++) {
            var pos = center.offset(x, -1, z);
            floor.add(pos);
            helper.getLevel().setBlock(pos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        }
        player.setPos(center.getX() + .5, center.getY(), center.getZ() + .5);
        var creeper = net.minecraft.world.entity.EntityTypes.CREEPER.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.LOAD);
        helper.assertTrue(creeper != null, "Create creeper");
        creeper.setPos(center.getX() + 3.5, center.getY(), center.getZ() + .5);
        helper.getLevel().addFreshEntity(creeper);
        double initial = creeper.distanceToSqr(player);
        double[] farthest = {initial};
        helper.onEachTick(() -> farthest[0] = Math.max(farthest[0], creeper.distanceToSqr(player)));
        helper.runAfterDelay(180, () -> {
            // A creature may finish fleeing then wander again; prove it fled during the window,
            // rather than requiring its randomly chosen endpoint to still be far away at one tick.
            boolean fled = farthest[0] > initial + 9;
            forms.reset();
            creeper.discard();
            player.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(.5, 2, .5)));
            for (var pos : floor) helper.getLevel().setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(fled, "Actual creeper AI walks away from cat form over normal server ticks; initial squared distance=" + initial + ", farthest=" + farthest[0]);
            helper.succeed();
        });
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
        player.setGameMode(GameType.SURVIVAL);
        player.connection.markClientLoaded();
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
