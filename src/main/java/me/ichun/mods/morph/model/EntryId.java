package me.ichun.mods.morph.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/** Content identity of a captured appearance, independent of ownership and attributes. */
public record EntryId(String value) implements Comparable<EntryId> {
    public static final Codec<EntryId> CODEC = Codec.STRING.comapFlatMap(value -> {
        try { return DataResult.success(new EntryId(value)); }
        catch (IllegalArgumentException error) { return DataResult.error(error::getMessage); }
    }, EntryId::value);

    public EntryId {
        if (value == null || !value.matches("v1:[0-9a-f]{64}"))
            throw new IllegalArgumentException("Invalid Morph entry ID");
    }

    @Override public int compareTo(EntryId other) { return value.compareTo(other.value); }
    @Override public String toString() { return value; }
}
