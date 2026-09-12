package me.ichun.mods.morph.server;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import me.ichun.mods.morph.model.MorphCollection;
import me.ichun.mods.morph.model.CollectionSnapshot;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Shared across dimensions via the overworld; vanilla owns saving and backup lifecycle. */
public final class MorphSavedData extends SavedData {
    private static final Codec<String> FORM_ID_CODEC = Codec.STRING.validate(id -> MorphCollection.isFormId(id)
            ? DataResult.success(id) : DataResult.error(() -> "Invalid Morph form ID"));
    private static final Codec<String> ACTIVE_ID_CODEC = Codec.STRING.validate(id -> id.isEmpty() || MorphCollection.isFormId(id)
            ? DataResult.success(id) : DataResult.error(() -> "Invalid active Morph form ID"));
    private static final Codec<MorphCollection> LEGACY_COLLECTION_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            FORM_ID_CODEC.listOf(0, MorphCollection.MAX_FORMS).fieldOf("forms").forGetter(MorphCollection::ownedForms),
            ACTIVE_ID_CODEC.optionalFieldOf("active", "").forGetter(MorphCollection::activeForm)
    ).apply(instance, (forms, active) -> MorphCollection.restore(1, forms, active)));
    private static final Codec<MorphCollection> COLLECTION_CODEC = CollectionSnapshot.CODEC.xmap(MorphCollection::restore, MorphCollection::snapshot);
    private static final MapCodec<MorphSavedData> LEGACY_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, LEGACY_COLLECTION_CODEC).fieldOf("players").forGetter(data -> data.players),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.BOOL).optionalFieldOf("nametags", Map.of()).forGetter(data -> data.nametags)
    ).apply(instance, (players, nametags) -> new MorphSavedData(players, nametags, true)));
    private static final MapCodec<MorphSavedData> CURRENT_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, COLLECTION_CODEC).fieldOf("players").forGetter(data -> data.players),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.BOOL).optionalFieldOf("nametags", Map.of()).forGetter(data -> data.nametags)
    ).apply(instance, (players, nametags) -> new MorphSavedData(players, nametags, false)));
    public static final Codec<MorphSavedData> CODEC = Codec.intRange(1, MorphCollection.CURRENT_SCHEMA)
            .dispatch("schema_version", data -> MorphCollection.CURRENT_SCHEMA, version -> version == 1 ? LEGACY_CODEC : CURRENT_CODEC);
    public static final SavedDataType<MorphSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("morph", "collections"), MorphSavedData::new, CODEC, null);
    private final Map<UUID, MorphCollection> players;

    private final Map<UUID, Boolean> nametags;
    private final boolean migratedFromSchema1;
    public MorphSavedData() { this(Map.of(), Map.of(), false); }
    private MorphSavedData(Map<UUID, MorphCollection> players, Map<UUID, Boolean> nametags, boolean migratedFromSchema1) {
        this.players = new HashMap<>(players);
        this.nametags = new HashMap<>(nametags);
        this.migratedFromSchema1 = migratedFromSchema1;
    }
    /** The storage integration can preserve the original file before dirtying a migrated save. */
    public boolean migratedFromSchema1() { return migratedFromSchema1; }
    public boolean showNametag(UUID player) { return nametags.getOrDefault(player, true); }
    public void setShowNametag(UUID player, boolean visible) {
        if (showNametag(player) == visible) return;
        if (visible) nametags.remove(player); else nametags.put(player, false);
        setDirty();
    }
    public MorphCollection collection(UUID player) {
        return players.computeIfAbsent(player, ignored -> new MorphCollection());
    }
}
