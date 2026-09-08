package me.ichun.mods.morph.server;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MorphSavedDataTest {
    @Test void codecRoundTripPreservesIndependentPlayerOwnershipAndActiveForm() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        MorphSavedData original = new MorphSavedData();
        original.collection(first).unlock("minecraft:pig");
        original.collection(first).select("minecraft:pig", 20);
        original.collection(second).unlock("minecraft:cow");
        var encoded = MorphSavedData.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        MorphSavedData restored = MorphSavedData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals("minecraft:pig", restored.collection(first).activeForm());
        assertEquals(java.util.List.of("minecraft:cow"), restored.collection(second).ownedForms());
        assertEquals("", restored.collection(second).activeForm());
    }

    @Test void futureSchemaAndMalformedIdsProduceCodecErrors() {
        assertTrue(MorphSavedData.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"schema_version\":2,\"players\":{}}")).error().isPresent());
        String malformed = "{\"schema_version\":1,\"players\":{\"00000000-0000-0000-0000-000000000001\":{\"forms\":[\"minecraft:pig{Health:999}\"]}}}";
        assertTrue(MorphSavedData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(malformed)).error().isPresent());
    }
}
