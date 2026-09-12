package me.ichun.mods.morph.server;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.UUID;
import me.ichun.mods.morph.model.FormDescriptor;
import me.ichun.mods.morph.model.MorphCollection;
import net.minecraft.nbt.NbtOps;
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

    @Test void nametagPreferencePersistsAndOldSavesDefaultToVisible() {
        UUID id = UUID.randomUUID();
        var old = MorphSavedData.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"schema_version\":1,\"players\":{}}")).getOrThrow();
        assertTrue(old.showNametag(id));
        old.setShowNametag(id, false);
        var restored = MorphSavedData.CODEC.parse(JsonOps.INSTANCE,
                MorphSavedData.CODEC.encodeStart(JsonOps.INSTANCE, old).getOrThrow()).getOrThrow();
        assertFalse(restored.showNametag(id));
        assertTrue(restored.showNametag(UUID.randomUUID()));
        restored.collection(id).reset();
        assertFalse(restored.showNametag(id));
        restored.setShowNametag(id, true);
        assertTrue(restored.showNametag(id));
    }

    @Test void futureSchemaAndMalformedIdsProduceCodecErrors() {
        assertTrue(MorphSavedData.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"schema_version\":3,\"players\":{}}")).error().isPresent());
        String malformed = "{\"schema_version\":1,\"players\":{\"00000000-0000-0000-0000-000000000001\":{\"forms\":[\"minecraft:pig{Health:999}\"]}}}";
        assertTrue(MorphSavedData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(malformed)).error().isPresent());
    }

    @Test void realSchemaOneCodecMigrationDeduplicatesAndPreservesActiveAndNametags() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var input = JsonParser.parseString("{\"schema_version\":1,\"players\":{\"" + id
                + "\":{\"forms\":[\"minecraft:pig\",\"minecraft:pig\"],\"active\":\"minecraft:pig\"}},\"nametags\":{\"" + id + "\":false}}");
        var migrated = MorphSavedData.CODEC.parse(JsonOps.INSTANCE, input).getOrThrow();
        assertTrue(migrated.migratedFromSchema1());
        var forms = migrated.collection(id);
        assertEquals(0, forms.revision());
        assertEquals(1, forms.entries().size());
        var entry = forms.entries().getFirst();
        assertEquals("v1:4066fd35e02757b46702c7638e66f7b58aafe86b2479d5bcc0f164b90aad7390", entry.id().value());
        assertEquals(entry.id(), forms.activeEntryId());
        assertEquals(0, entry.revision());
        assertEquals(0, entry.order());
        assertFalse(entry.favorite());
        assertTrue(entry.descriptor().attributes().isEmpty());
        assertFalse(migrated.showNametag(id));
        forms.favorite(entry.id(), true);
        var encoded = MorphSavedData.CODEC.encodeStart(JsonOps.INSTANCE, migrated).getOrThrow();
        assertEquals(2, encoded.getAsJsonObject().get("schema_version").getAsInt());
        var reloaded = MorphSavedData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertFalse(reloaded.migratedFromSchema1());
        assertEquals(forms.snapshot(), reloaded.collection(id).snapshot());
        assertFalse(reloaded.showNametag(id));
    }

    @Test void nbtSavePreservesBabyColorSizeFavoriteNametagAndAttributeTypes() {
        var original = new MorphSavedData();
        var id = UUID.randomUUID();
        var forms = original.collection(id);
        for (int color = 0; color <= 1; color++) {
            var sheep = new FormDescriptor(1, "minecraft:sheep", "morph:sheep", 1,
                    java.util.Map.of("baby", true, "color", color), null, java.util.Map.of(),
                    java.util.Map.of("minecraft:max_health", 8.0), null);
            forms.acquire(sheep, MorphCollection.AttributeMergePolicy.KEEP_EXISTING);
            forms.favorite(sheep.entryId(), true);
        }
        var slime = new FormDescriptor(1, "minecraft:slime", "morph:slime", 1,
                java.util.Map.of("size", 1), null, java.util.Map.of(), java.util.Map.of(), null);
        forms.acquire(slime, MorphCollection.AttributeMergePolicy.KEEP_EXISTING);
        forms.select(slime.entryId(), 20);
        original.setShowNametag(id, false);
        var encoded = MorphSavedData.CODEC.encodeStart(NbtOps.INSTANCE, original).getOrThrow();
        var restored = MorphSavedData.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
        assertEquals(forms.snapshot(), restored.collection(id).snapshot());
        assertFalse(restored.showNametag(id));
        assertTrue(restored.showNametag(UUID.randomUUID()));
    }

    @Test void orphanLegacyActiveDoesNotGrantOwnershipAndForgedCurrentEntryFails() {
        var id = UUID.randomUUID();
        var input = JsonParser.parseString("{\"schema_version\":1,\"players\":{\"" + id
                + "\":{\"forms\":[\"minecraft:pig\"],\"active\":\"minecraft:sheep\"}}}");
        var data = MorphSavedData.CODEC.parse(JsonOps.INSTANCE, input).getOrThrow();
        assertNull(data.collection(id).activeEntryId());
        var encoded = MorphSavedData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow().getAsJsonObject();
        var savedEntry = encoded.getAsJsonObject("players").getAsJsonObject(id.toString()).getAsJsonArray("entries").get(0).getAsJsonObject();
        savedEntry.addProperty("id", "v1:" + "0".repeat(64));
        assertTrue(MorphSavedData.CODEC.parse(JsonOps.INSTANCE, encoded).error().isPresent());
    }
}
