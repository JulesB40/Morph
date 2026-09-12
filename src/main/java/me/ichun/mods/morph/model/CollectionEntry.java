package me.ichun.mods.morph.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Descriptor revisions change only when descriptor content changes. */
public record CollectionEntry(EntryId id, FormDescriptor descriptor, long revision, boolean favorite, int order) {
    private record Fields(EntryId id, FormDescriptor descriptor, long revision, boolean favorite, int order) {}
    private static final Codec<Fields> FIELDS_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            EntryId.CODEC.fieldOf("id").forGetter(Fields::id),
            FormDescriptor.CODEC.fieldOf("descriptor").forGetter(Fields::descriptor),
            Codec.LONG.validate(Codec.checkRange(0L, Long.MAX_VALUE)).fieldOf("revision").forGetter(Fields::revision),
            Codec.BOOL.fieldOf("favorite").forGetter(Fields::favorite),
            Codec.intRange(0, MorphCollection.MAX_FORMS - 1).fieldOf("order").forGetter(Fields::order)
    ).apply(instance, Fields::new));
    public static final Codec<CollectionEntry> CODEC = FIELDS_CODEC.comapFlatMap(fields -> {
        try { return DataResult.success(new CollectionEntry(fields.id, fields.descriptor, fields.revision, fields.favorite, fields.order)); }
        catch (IllegalArgumentException error) { return DataResult.error(error::getMessage); }
    }, entry -> new Fields(entry.id, entry.descriptor, entry.revision, entry.favorite, entry.order));

    public CollectionEntry {
        if (id == null || descriptor == null || !id.equals(descriptor.entryId()))
            throw new IllegalArgumentException("Entry ID does not match its appearance");
        if (revision < 0 || order < 0 || order >= MorphCollection.MAX_FORMS)
            throw new IllegalArgumentException("Invalid entry metadata");
    }
}
