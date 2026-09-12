package me.ichun.mods.morph.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/** A complete immutable owner view; null active ID denotes the player's own appearance. */
public record CollectionSnapshot(int schemaVersion, long revision, List<CollectionEntry> entries, EntryId activeEntryId) {
    public static final int MAX_BYTES = 4_300_000;
    private record Fields(long revision, List<CollectionEntry> entries, Optional<EntryId> activeEntryId) {}
    private static final Codec<Fields> FIELDS_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.validate(Codec.checkRange(0L, Long.MAX_VALUE)).fieldOf("revision").forGetter(Fields::revision),
            CollectionEntry.CODEC.listOf(0, MorphCollection.MAX_FORMS).fieldOf("entries").forGetter(Fields::entries),
            EntryId.CODEC.optionalFieldOf("active_entry_id").forGetter(Fields::activeEntryId)
    ).apply(instance, Fields::new));
    public static final Codec<CollectionSnapshot> CODEC = FIELDS_CODEC.comapFlatMap(fields -> {
        try { return DataResult.success(new CollectionSnapshot(MorphCollection.CURRENT_SCHEMA,
                fields.revision, fields.entries, fields.activeEntryId.orElse(null))); }
        catch (IllegalArgumentException error) { return DataResult.error(error::getMessage); }
    }, snapshot -> new Fields(snapshot.revision, snapshot.entries, Optional.ofNullable(snapshot.activeEntryId)));

    public CollectionSnapshot {
        if (schemaVersion != MorphCollection.CURRENT_SCHEMA || revision < 0 || entries == null
                || entries.size() > MorphCollection.MAX_FORMS)
            throw new IllegalArgumentException("Invalid collection snapshot");
        entries = List.copyOf(entries);
        var ids = new HashSet<EntryId>();
        var orders = new HashSet<Integer>();
        long descriptorBytes = 0;
        for (var entry : entries) {
            if (!ids.add(entry.id()) || !orders.add(entry.order()))
                throw new IllegalArgumentException("Duplicate collection entry or order");
            // The save embeds descriptor JSON as a JSON string. Include its escaped representation.
            descriptorBytes += FormDescriptorJson.canonical(new com.google.gson.JsonPrimitive(entry.descriptor().canonicalJson()))
                    .getBytes(StandardCharsets.UTF_8).length;
        }
        // Includes a conservative bound for IDs, entry metadata and the collection envelope.
        if (descriptorBytes + entries.size() * 256L + 256 > MAX_BYTES)
            throw new IllegalArgumentException("Collection exceeds byte limit");
        if (activeEntryId != null && !ids.contains(activeEntryId))
            throw new IllegalArgumentException("Active entry is not owned");
    }
}
