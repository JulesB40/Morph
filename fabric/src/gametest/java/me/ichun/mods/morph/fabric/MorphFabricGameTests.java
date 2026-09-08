package me.ichun.mods.morph.fabric;

import net.minecraft.world.level.block.Blocks;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import me.ichun.mods.morph.ability.MorphAttributes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.util.ProblemReporter;
import me.ichun.mods.morph.server.MorphSavedData;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.GameType;

public final class MorphFabricGameTests {
    @GameTest(maxTicks = 100)
    public void transformationSoundLifecycle(GameTestHelper helper) {
        var player = player(helper);
        helper.assertValueEqual(command(player, "morph select minecraft:pig"), 0, "Unowned form rejected");
        helper.assertTrue(me.ichun.mods.morph.model.MorphSounds.scheduledTick(player).isEmpty(), "Rejection schedules no sound");
        MorphFabric.data(helper.getLevel().getServer()).collection(player.getUUID()).unlock("minecraft:pig");
        helper.assertValueEqual(command(player, "morph select minecraft:pig"), 1, "Owned form accepted");
        long due = me.ichun.mods.morph.model.MorphSounds.scheduledTick(player).orElseThrow();
        helper.assertValueEqual(due, player.level().getServer().overworld().getGameTime() + 20, "Original sound starts at tick twenty");
        helper.runAfterDelay(5, () -> {
            command(player, "morph select minecraft:pig");
            command(player, "morph menu");
            helper.assertValueEqual(me.ichun.mods.morph.model.MorphSounds.scheduledTick(player).orElseThrow(), due, "Redundant selection and menu sync preserve sound timing");
        });
        helper.runAfterDelay(21, () -> {
            me.ichun.mods.morph.model.MorphSounds.tick(player);
            helper.assertTrue(me.ichun.mods.morph.model.MorphSounds.scheduledTick(player).isEmpty(), "Due sound consumed");
            command(player, "morph reset");
            helper.assertTrue(me.ichun.mods.morph.model.MorphSounds.scheduledTick(player).isPresent(), "Returning to human schedules audio");
            me.ichun.mods.morph.model.MorphSounds.cancel(player);
            command(player, "morph reset");
            helper.assertTrue(me.ichun.mods.morph.model.MorphSounds.scheduledTick(player).isEmpty(), "Redundant reset stays silent");
            helper.succeed();
        });
    }
    @GameTest(maxTicks = 100)
    public void commandsAndGeometry(GameTestHelper helper) {
        var player = player(helper);
        var forms = MorphFabric.data(helper.getLevel().getServer()).collection(player.getUUID());
        helper.assertValueEqual(command(player, "morph select minecraft:pig"), 0, "Unowned form rejected");
        forms.unlock("minecraft:pig"); forms.unlock("minecraft:cow"); forms.unlock("minecraft:item");
        helper.assertValueEqual(command(player, "morph select minecraft:item"), 0, "Nonliving form rejected");
        helper.assertValueEqual(command(player, "morph select minecraft:pig"), 1, "Namespaced selection accepted");
        helper.assertValueEqual(forms.activeForm(), "minecraft:pig", "Selected pig");
        helper.assertValueEqual(command(player, "morph select minecraft:pig"), 1, "Selecting current form succeeds without consuming cooldown");
        helper.assertTrue(player.getBbHeight() < 1.0F, "Pig shape replaces player server hitbox");
        helper.assertValueEqual(command(player, "morph select minecraft:cow"), 0, "Cooldown enforced");
        helper.assertValueEqual(command(player, "morph reset"), 1, "Reset works");
        helper.assertTrue(player.getBbHeight() > 1.5F, "Player shape restored");
        helper.assertValueEqual(command(player, "morph select minecraft:cow"), 0, "Reset cannot bypass cooldown");
        helper.runAfterDelay(21, () -> {
            helper.assertValueEqual(command(player, "morph select minecraft:cow"), 1, "Cooldown recovers");
            player.setGameMode(GameType.SPECTATOR);
            helper.assertValueEqual(command(player, "morph select minecraft:pig"), 0, "Spectator cannot select");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 100)
    public void killAcquiresAndPersists(GameTestHelper helper) {
        var player = player(helper);
        var forms = MorphFabric.data(helper.getLevel().getServer()).collection(player.getUUID());
        var pig = helper.spawn(EntityTypes.PIG, 1, 1, 1);
        pig.hurtServer(helper.getLevel(), player.damageSources().playerAttack(player), 1000.0F);
        helper.assertTrue(pig.isDeadOrDying(), "Attack killed pig");
        helper.assertTrue(forms.ownedForms().contains("minecraft:pig"), "Fabric death event acquired pig");
        helper.assertValueEqual(command(player, "morph select minecraft:pig"), 1, "Acquired form selectable");
        var saved = MorphFabric.data(helper.getLevel().getServer());
        helper.assertTrue(saved.isDirty(), "Selection marks saved data dirty");
        var json = MorphSavedData.CODEC.encodeStart(JsonOps.INSTANCE, saved).getOrThrow();
        var restored = MorphSavedData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        helper.assertValueEqual(restored.collection(player.getUUID()).activeForm(), "minecraft:pig", "Selection persisted");
        helper.assertTrue(restored.collection(UUID.randomUUID()).ownedForms().isEmpty(), "Ownership isolated by UUID");
        helper.succeed();
    }

    @GameTest(maxTicks = 100)
    public void flightAndFallTraits(GameTestHelper helper) {
        var player = player(helper);
        var forms = MorphFabric.data(helper.getLevel().getServer()).collection(player.getUUID());
        forms.unlock("minecraft:blaze");
        helper.assertFalse(player.getAbilities().mayfly, "Survival starts without flight");
        helper.assertValueEqual(command(player, "morph select minecraft:blaze"), 1, "Select flying form");
        helper.assertTrue(player.getAbilities().mayfly, "Selection immediately grants flight");
        helper.assertFalse(me.ichun.mods.morph.ability.MorphAbilities.packForSave(player, player.getAbilities()).mayFly(), "Morph flight is transient when saving");
        float health = player.getHealth();
        player.hurtServer(helper.getLevel(), player.damageSources().fall(), 5.0F);
        helper.assertValueEqual(player.getHealth(), health, "Fabric damage event prevents blaze fall damage");
        helper.assertValueEqual(command(player, "morph reset"), 1, "Reset flying form");
        helper.assertFalse(player.getAbilities().mayfly, "Reset removes Morph flight");
        player.getAbilities().mayfly = true;
        me.ichun.mods.morph.ability.MorphAbilities.tick(player, "minecraft:blaze");
        me.ichun.mods.morph.ability.MorphAbilities.cleanup(player);
        helper.assertTrue(player.getAbilities().mayfly, "Pre-existing flight survives cleanup");
        helper.succeed();
    }

    @GameTest(maxTicks = 250)
    public void attributeDefaultsAndPersistence(GameTestHelper helper) {
        var player = player(helper);
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
        var restored = player(helper);
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

    @GameTest(maxTicks = 250)
    public void externalAttributesSurviveMorph(GameTestHelper helper) {
        var player = player(helper);
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

    @GameTest(maxTicks = 250)
    public void animatedHealthSelectionAndReset(GameTestHelper helper) {
        var player = player(helper);
        player.setHealth(10.0F);
        MorphFabric.data(helper.getLevel().getServer()).collection(player.getUUID()).unlock("minecraft:pig");
        helper.assertValueEqual(command(player, "morph select minecraft:pig"), 1, "Select pig through command");
        near(helper, player.getMaxHealth(), 20.0F, "Selection starts at previous maximum");
        helper.onEachTick(() -> MorphAttributes.tick(player, MorphFabric.data(helper.getLevel().getServer()).collection(player.getUUID()).activeForm()));
        helper.runAfterDelay(50, () -> {
            helper.assertTrue(player.getMaxHealth() > 10.0F && player.getMaxHealth() < 20.0F, "Health interpolates during black transition");
            near(helper, player.getHealth() / player.getMaxHealth(), 0.5F, "Transition preserves injury ratio");
        });
        helper.runAfterDelay(101, () -> {
            near(helper, player.getMaxHealth(), 10.0F, "Completed selection has pig maximum");
            near(helper, player.getHealth(), 5.0F, "Completed selection keeps half health");
            helper.assertValueEqual(command(player, "morph reset"), 1, "Reset through command");
        });
        helper.runAfterDelay(202, () -> {
            near(helper, player.getMaxHealth(), 20.0F, "Animated reset restores human maximum");
            near(helper, player.getHealth(), 10.0F, "Animated reset keeps half health");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 100)
    public void passiveTraitHooks(GameTestHelper helper) {
        var spider = player(helper);
        var spiderForms = MorphFabric.data(helper.getLevel().getServer()).collection(spider.getUUID());
        spiderForms.unlock("minecraft:spider");
        spiderForms.select("minecraft:spider", 0);
        spider.horizontalCollision = true;
        helper.assertTrue(spider.onClimbable(), "Spider wall contact activates actual climb hook");
        spider.horizontalCollision = false;
        helper.assertFalse(spider.onClimbable(), "Spider cannot climb without wall contact");
        var zombie = player(helper);
        var zombieForms = MorphFabric.data(helper.getLevel().getServer()).collection(zombie.getUUID());
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
        var fish = player(helper);
        float startHealth = fish.getHealth();
        for (int tick = 0; tick < 301; tick++) me.ichun.mods.morph.ability.MorphTraits.tick(fish, "minecraft:cod");
        helper.assertTrue(fish.getHealth() < startHealth, "Fish dries out after air allowance expires");
        var turtle = player(helper);
        float turtleHealth = turtle.getHealth();
        for (int tick = 0; tick < 301; tick++) me.ichun.mods.morph.ability.MorphTraits.tick(turtle, "minecraft:turtle");
        near(helper, turtle.getHealth(), turtleHealth, "Amphibious turtle remains healthy on land");
        helper.succeed();
    }

    @GameTest(maxTicks = 100)
    public void hostilityAndRidingHooks(GameTestHelper helper) {
        var mount = player(helper);
        var forms = MorphFabric.data(helper.getLevel().getServer()).collection(mount.getUUID());
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
        var rider = player(helper);
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

    @GameTest(maxTicks = 200)
    public void intimidationMovesCreeper(GameTestHelper helper) {
        var player = player(helper);
        helper.getLevel().addNewPlayer(player);
        var forms = MorphFabric.data(helper.getLevel().getServer()).collection(player.getUUID());
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

    private static int command(ServerPlayer player, String command) {
        try { return player.level().getServer().getCommands().getDispatcher().execute(command, player.createCommandSourceStack()); }
        catch (Exception failure) { throw new IllegalStateException("Command failed: " + command, failure); }
    }

    private static ServerPlayer player(GameTestHelper helper) {
        var id = UUID.randomUUID();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(id, "morph-" + id.toString().substring(0, 8)), false);
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        player.connection = new ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, player, cookie);
        player.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(0.5, 2.0, 0.5)));
        player.setGameMode(GameType.SURVIVAL);
        player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        return player;
    }
}
