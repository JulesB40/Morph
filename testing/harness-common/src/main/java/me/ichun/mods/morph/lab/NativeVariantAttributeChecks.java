package me.ichun.mods.morph.lab;

import com.google.gson.Gson;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.ichun.mods.morph.ability.MorphAttributes;
import me.ichun.mods.morph.model.FormDescriptor;
import me.ichun.mods.morph.model.MorphCollection;
import me.ichun.mods.morph.server.MorphAuthority;
import me.ichun.mods.morph.shape.MorphDimensions;
import me.ichun.mods.morph.shape.ShapeHooks;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/** Native setters supply expected values; no candidate capture helper computes the oracle. */
public final class NativeVariantAttributeChecks {
    private static final Identifier HEALTH_ADD = Identifier.fromNamespaceAndPath("morph_lab", "variant_health_add");
    private static final Identifier HEALTH_MULTIPLY = Identifier.fromNamespaceAndPath("morph_lab", "variant_health_multiply");
    private static final Identifier SPEED_MULTIPLY = Identifier.fromNamespaceAndPath("morph_lab", "variant_speed_multiply");
    private NativeVariantAttributeChecks() {}

    public static void verify(GameTestHelper helper) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "lab-variant"), false);
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {};
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        player.connection = new ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        var forms = MorphAuthority.collection(player);
        var maximum = player.getAttribute(Attributes.MAX_HEALTH);
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        maximum.setBaseValue(24);
        maximum.addPermanentModifier(new AttributeModifier(HEALTH_ADD, 4, AttributeModifier.Operation.ADD_VALUE));
        maximum.addPermanentModifier(new AttributeModifier(HEALTH_MULTIPLY, .5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        speed.addPermanentModifier(new AttributeModifier(SPEED_MULTIPLY, .25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        double originalSpeedBase = speed.getBaseValue();
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        ItemStack hand = player.getItemBySlot(EquipmentSlot.MAINHAND).copy();
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST).copy();
        List<Map<String, Object>> observations = new ArrayList<>();
        var event = new java.util.LinkedHashMap<String, Object>();
        event.put("schema", "morph-lab.native-variant-attributes.v1");
        event.put("scenario", "native_variant_attributes");
        event.put("scope", "direct settled attribute ticks and detached native setter oracles; no transition duration, equipment modifier ticking, real client input or kill acquisition proof");
        event.put("observations", observations);
        try {
            near(42, player.getMaxHealth(), "external baseline maximum");
            player.setHealth(21);
            long selectionTick = 0;
            for (boolean baby : new boolean[]{false, true}) {
                var nativeSheep = EntityTypes.SHEEP.create(helper.getLevel(), EntitySpawnReason.LOAD);
                require(nativeSheep != null, "Native sheep creation failed");
                nativeSheep.setBaby(baby);
                nativeSheep.setColor(DyeColor.RED);
                var descriptor = new FormDescriptor(1, "minecraft:sheep", "morph:sheep", 1,
                        Map.of("baby", baby, "color", 14), null, Map.of(), Map.of(), null);
                check(helper, player, forms, descriptor, nativeSheep, selectionTick += 20, null, null, observations);
                gear(player, hand, chest);
            }
            for (int size : new int[]{1, 4, 16}) {
                var nativeSlime = EntityTypes.SLIME.create(helper.getLevel(), EntitySpawnReason.LOAD);
                require(nativeSlime != null, "Native slime creation failed");
                nativeSlime.setSize(size, false);
                var descriptor = new FormDescriptor(1, "minecraft:slime", "morph:slime", 1,
                        Map.of("size", size), null, Map.of(), Map.of(), null);
                check(helper, player, forms, descriptor, nativeSlime, selectionTick += 20, null, null, observations);
                gear(player, hand, chest);
            }
            var nativeSheep = EntityTypes.SHEEP.create(helper.getLevel(), EntitySpawnReason.LOAD);
            require(nativeSheep != null, "Native override sheep creation failed");
            nativeSheep.setBaby(true);
            nativeSheep.setColor(DyeColor.RED);
            var identity = new FormDescriptor(1, "minecraft:sheep", "morph:sheep", 1,
                    Map.of("baby", true, "color", 14), null, Map.of(), Map.of(), null);
            // Same identity, revised attributes: the second check detects stale species/EntryId caches.
            for (double capturedHealth : new double[]{32, 48}) {
                var descriptor = identity.withAttributes(Map.of("minecraft:max_health", capturedHealth,
                        "minecraft:movement_speed", .4));
                require(descriptor.entryId().equals(identity.entryId()), "Captured attributes changed appearance identity");
                check(helper, player, forms, descriptor, nativeSheep, selectionTick += 20, capturedHealth, .4, observations);
                gear(player, hand, chest);
            }
            forms.reset();
            MorphAttributes.tick(player, "");
            near(42, player.getMaxHealth(), "self tick restores external maximum");
            near(21, player.getHealth(), "self tick preserves half health");
            near(originalSpeedBase * 1.25, speed.getValue(), "self tick preserves speed modifier");
            require(maximum.hasModifier(HEALTH_ADD) && maximum.hasModifier(HEALTH_MULTIPLY) && speed.hasModifier(SPEED_MULTIPLY), "External modifier identity was lost");
            near(24, maximum.getBaseValue(), "Player health base is unchanged");
            require(!maximum.hasModifier(MorphAttributes.MODIFIER), "Self tick left a morph modifier");
            gear(player, hand, chest);
            fallbackShapes(helper);
            // Cleanup is separately exercised while still morphed, rather than only after self reset.
            var finalDescriptor = identity.withAttributes(Map.of("minecraft:max_health", 64.0, "minecraft:movement_speed", .4));
            check(helper, player, forms, finalDescriptor, nativeSheep, selectionTick + 20, 64.0, .4, observations);
            near(21, MorphAttributes.healthForSave(player), "Save health remains in unmorphed external units");
            MorphAttributes.cleanup(player);
            near(42, player.getMaxHealth(), "cleanup restores external maximum");
            near(21, player.getHealth(), "cleanup preserves half health");
            near(24, maximum.getBaseValue(), "cleanup preserves player base");
            require(maximum.hasModifier(HEALTH_ADD) && maximum.hasModifier(HEALTH_MULTIPLY), "cleanup removed external modifiers");
            require(!maximum.hasModifier(MorphAttributes.MODIFIER), "cleanup retained morph modifier");
            gear(player, hand, chest);
            event.put("status", "pass");
        } catch (Throwable error) {
            event.put("status", "fail"); event.put("error", error.toString());
            if (error instanceof Error fatal) throw fatal;
            throw new IllegalStateException("Native variant attributes failed", error);
        } finally {
            forms.reset();
            MorphAttributes.cleanup(player);
            MorphAuthority.disconnected(player);
            channel.finishAndReleaseAll();
            record(event);
        }
        helper.succeed();
    }

    private static void check(GameTestHelper helper, ServerPlayer player, MorphCollection forms,
            FormDescriptor descriptor, LivingEntity reference, long tick, Double capturedHealth,
            Double capturedSpeed, List<Map<String, Object>> observations) {
        MorphAttributes.cleanup(player); // No begin: this test explicitly checks the settled endpoint.
        forms.acquire(descriptor, MorphCollection.AttributeMergePolicy.MAX_BASE);
        var selected = forms.select(descriptor.entryId(), tick);
        require(selected == MorphCollection.SelectionResult.CHANGED || selected == MorphCollection.SelectionResult.UNCHANGED, "Fixture selection failed");
        require(descriptor.equals(ShapeHooks.descriptor(player)), "Loader resolver did not expose the expected descriptor");
        double nativeHealth = reference.getAttribute(Attributes.MAX_HEALTH).getBaseValue();
        double nativeSpeed = reference.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();
        double expectedHealth = ((capturedHealth == null ? nativeHealth : capturedHealth) + 4) * 1.5;
        double expectedSpeed = (capturedSpeed == null ? nativeSpeed : capturedSpeed) * 1.25;
        MorphAttributes.tick(player, descriptor.species());
        observations.add(Map.of("species", descriptor.species(), "variant", descriptor.variant(),
                "captured", descriptor.attributes(), "expected_max_health", expectedHealth,
                "actual_max_health", player.getMaxHealth(), "expected_speed_attribute", expectedSpeed,
                "actual_speed_attribute", player.getAttribute(Attributes.MOVEMENT_SPEED).getValue()));
        near(expectedHealth, player.getMaxHealth(), "native/captured maximum");
        near(expectedHealth / 2, player.getHealth(), "variant preserves half health");
        near(expectedSpeed, player.getAttribute(Attributes.MOVEMENT_SPEED).getValue(), "native/captured speed attribute");
        var nativeAttack = reference.getAttribute(Attributes.ATTACK_DAMAGE);
        if (nativeAttack != null) {
            var actualAttack = player.getAttribute(Attributes.ATTACK_DAMAGE);
            var attackOracle = new net.minecraft.world.entity.ai.attributes.AttributeInstance(actualAttack.getAttribute(), ignored -> {});
            attackOracle.replaceFrom(actualAttack);
            attackOracle.removeModifier(MorphAttributes.MODIFIER);
            attackOracle.setBaseValue(nativeAttack.getBaseValue());
            near(attackOracle.getValue(), actualAttack.getValue(), "native variant attack with retained non-Morph modifiers");
        }
        near(24, player.getAttribute(Attributes.MAX_HEALTH).getBaseValue(), "variant does not rewrite player base");
        for (var pose : List.of(Pose.STANDING, Pose.CROUCHING, Pose.SWIMMING)) {
            var expected = reference.getDimensions(pose);
            var actual = MorphDimensions.forPose(helper.getLevel(), descriptor, pose, EntityDimensions.scalable(7, 9));
            near(expected.width(), actual.width(), "native variant width " + pose);
            near(expected.height(), actual.height(), "native variant height " + pose);
            near(expected.eyeHeight(), actual.eyeHeight(), "native variant eye height " + pose);
        }
    }
    private static void fallbackShapes(GameTestHelper helper) {
        var sentinel = EntityDimensions.scalable(.73F, 1.91F).withEyeHeight(1.71F);
        for (String species : new String[]{"", "not an id", "minecraft:missing_fixture", "fixture:unsupported"})
            require(MorphDimensions.forPose(helper.getLevel(), species, Pose.STANDING, sentinel) == sentinel,
                    "Unsupported/self species did not retain supplied fallback");
        require(MorphDimensions.forPose(helper.getLevel(), (FormDescriptor) null, Pose.STANDING, sentinel) == sentinel,
                "Self descriptor did not retain supplied fallback");
        var unsupported = new FormDescriptor(1, "minecraft:sheep", "fixture:unsupported", 1, Map.of(), null, Map.of(), Map.of(), null);
        require(MorphDimensions.forPose(helper.getLevel(), unsupported, Pose.STANDING, sentinel) == sentinel,
                "Unavailable adapter did not retain supplied fallback");
    }
    private static void gear(ServerPlayer player, ItemStack hand, ItemStack chest) {
        require(ItemStack.matches(hand, player.getItemBySlot(EquipmentSlot.MAINHAND)), "Morph changed live held equipment");
        require(ItemStack.matches(chest, player.getItemBySlot(EquipmentSlot.CHEST)), "Morph changed live armor equipment");
    }
    private static void near(double expected, double actual, String message) {
        require(Double.isFinite(actual) && Math.abs(expected - actual) <= 0.0001 * Math.max(1, Math.abs(expected)),
                message + ": expected " + expected + ", actual " + actual);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void record(Map<String, Object> event) {
        String json = new Gson().toJson(event);
        System.out.println("MORPH_LAB_VARIANT_ATTRIBUTES " + json);
        String output = System.getProperty("morph.lab.events");
        if (output == null) return;
        try {
            Path path = Path.of(output); Files.createDirectories(path.toAbsolutePath().getParent());
            Files.writeString(path, json + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (java.io.IOException error) { throw new IllegalStateException("Cannot record variant attribute evidence", error); }
    }
}
