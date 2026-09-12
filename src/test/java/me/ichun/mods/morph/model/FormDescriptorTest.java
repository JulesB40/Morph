package me.ichun.mods.morph.model;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FormDescriptorTest {
    static FormDescriptor sheep(int color, boolean baby, String name) {
        return new FormDescriptor(1, "minecraft:sheep", "morph:sheep", 1,
                Map.of("baby", baby, "color", color), name, Map.of(), Map.of(), null);
    }

    @Test void fixedIdentityFixturesAndAttributeExclusion() {
        var pig = FormDescriptor.species("minecraft:pig");
        assertEquals("{\"adapter\":\"morph:species\",\"adapter_version\":1,\"custom_name\":null,\"equipment\":{},\"profile_uuid\":null,\"species\":\"minecraft:pig\",\"variant\":{},\"version\":1}", pig.identityJson());
        assertEquals("v1:4066fd35e02757b46702c7638e66f7b58aafe86b2479d5bcc0f164b90aad7390", pig.entryId().value());
        var red = sheep(14, false, "Rosé");
        assertEquals("v1:c81a2ed04901caf434530c5c4f8a7724e2553e6682ee177cfaa82b7db9185d9e", red.entryId().value());
        assertEquals(red.entryId(), sheep(14, false, "Rose\u0301").entryId());
        assertEquals(red.entryId(), red.withAttributes(Map.of("minecraft:max_health", 8.0)).entryId());
        assertNotEquals(red.entryId(), sheep(0, false, "Rosé").entryId());
        assertNotEquals(red.entryId(), sheep(14, true, "Rosé").entryId());
        var slime = new FormDescriptor(1, "minecraft:slime", "morph:slime", 1,
                Map.of("size", 2), null, Map.of(), Map.of(), null);
        assertEquals("v1:a4540a1bad43d61ccbb89b2fc1f0064953518b97727fc539c2266ef9f20ca8ee", slime.entryId().value());
    }

    @Test void profileRefreshDoesNotChangePlayerUuidIdentity() {
        var uuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var first = new FormDescriptor(1, "minecraft:player", "morph:player", 1, Map.of(), null, Map.of(), Map.of(),
                new FormDescriptor.PlayerProfile(uuid, "Player", null, FormDescriptor.SkinModel.WIDE));
        var refreshed = new FormDescriptor(1, "minecraft:player", "morph:player", 1, Map.of(), null, Map.of(), Map.of(),
                new FormDescriptor.PlayerProfile(uuid, "Updated", "a".repeat(64), FormDescriptor.SkinModel.SLIM));
        assertEquals("v1:bdf8ed969dfe5761b4b3349512023da62e6c175ba7cb97ec75ff9245d3c1f592", first.entryId().value());
        assertEquals(first.entryId(), refreshed.entryId());
        assertNotEquals(first, refreshed);
        assertFalse(MorphCollection.isFormId(first.species()));
    }

    @Test void codecsRetainBooleanAndZeroOneIntegersInBothOps() {
        for (var descriptor : List.of(sheep(0, true, null), sheep(1, false, null),
                new FormDescriptor(1, "minecraft:slime", "morph:slime", 1, Map.of("size", 1), null, Map.of(), Map.of(), null))) {
            var json = FormDescriptor.CODEC.encodeStart(JsonOps.INSTANCE, descriptor).getOrThrow();
            assertTrue(json.isJsonPrimitive() && json.getAsJsonPrimitive().isString());
            assertEquals(descriptor, FormDescriptor.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
            var nbt = FormDescriptor.CODEC.encodeStart(NbtOps.INSTANCE, descriptor).getOrThrow();
            assertEquals(descriptor, FormDescriptor.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow());
        }
    }

    @Test void equivalentJsonOrderingIsAcceptedAndIdentityRecomputed() {
        var original = sheep(1, true, "A\nB").withAttributes(Map.of("minecraft:max_health", 8.0));
        var reversed = new com.google.gson.JsonObject();
        var keys = new java.util.ArrayList<>(original.toJson().keySet());
        java.util.Collections.reverse(keys);
        for (String key : keys) reversed.add(key, original.toJson().get(key));
        var restored = FormDescriptor.fromJson(reversed.toString());
        assertEquals(original, restored);
        assertEquals(8.0, restored.attributes().get("minecraft:max_health"));
        assertTrue(restored.identityJson().contains("A\\u000aB"));
    }

    @Test void inputMapsAreDefensivelyCopiedAndUnknownAdaptersRemainBounded() {
        var values = new HashMap<String, Object>();
        values.put("pattern", "spotted");
        var descriptor = new FormDescriptor(1, "example:creature", "example:appearance", 2,
                values, null, Map.of(), Map.of(), null);
        values.put("pattern", "striped");
        assertEquals("spotted", descriptor.variant().get("pattern"));
        assertThrows(UnsupportedOperationException.class, () -> descriptor.variant().clear());
        assertEquals(descriptor, FormDescriptor.fromJson(descriptor.canonicalJson()));
    }

    @Test void malformedAndOverLimitInputIsRejectedRatherThanTruncated() {
        for (int color : new int[]{-1, 16}) assertThrows(IllegalArgumentException.class, () -> sheep(color, false, null));
        for (int size : new int[]{0, 17}) assertThrows(IllegalArgumentException.class, () ->
                new FormDescriptor(1, "minecraft:slime", "morph:slime", 1, Map.of("size", size), null, Map.of(), Map.of(), null));
        assertDoesNotThrow(() -> sheep(15, true, "é".repeat(128)));
        assertThrows(IllegalArgumentException.class, () -> sheep(15, true, "é".repeat(128) + "x"));
        assertThrows(IllegalArgumentException.class, () -> sheep(1, false, "\ud800"));
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, 1_000_001})
            assertThrows(IllegalArgumentException.class, () -> sheep(1, false, null).withAttributes(Map.of("minecraft:max_health", value)));
        String json = sheep(1, false, null).canonicalJson();
        assertThrows(IllegalArgumentException.class, () -> FormDescriptor.fromJson(json.replace("\"version\":1", "\"version\":1,\"version\":1")));
        assertThrows(IllegalArgumentException.class, () -> FormDescriptor.fromJson(json.replace("\"baby\":false", "\"baby\":0")));
        assertThrows(IllegalArgumentException.class, () -> FormDescriptor.fromJson(json.replace("\"color\":1", "\"color\":1.5")));
        assertThrows(IllegalArgumentException.class, () -> FormDescriptor.fromJson(json.substring(0, json.length() - 1) + ",\"nbt\":{}}"));
        assertThrows(IllegalArgumentException.class, () -> FormDescriptor.fromJson(json + "{}"));
        assertTrue(FormDescriptor.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(" ".repeat(FormDescriptor.MAX_BYTES + 1))).error().isPresent());
    }
}
