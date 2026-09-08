package me.ichun.mods.morph.server;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import me.ichun.mods.morph.model.MorphCollection;
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
    private static final Codec<MorphCollection> COLLECTION_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            FORM_ID_CODEC.listOf(0, MorphCollection.MAX_FORMS).fieldOf("forms").forGetter(MorphCollection::ownedForms),
            ACTIVE_ID_CODEC.optionalFieldOf("active", "").forGetter(MorphCollection::activeForm)
    ).apply(instance, (forms, active) -> MorphCollection.restore(MorphCollection.CURRENT_SCHEMA, forms, active)));
    public static final Codec<MorphSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(MorphCollection.CURRENT_SCHEMA, MorphCollection.CURRENT_SCHEMA)
                    .fieldOf("schema_version").forGetter(data -> MorphCollection.CURRENT_SCHEMA),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, COLLECTION_CODEC).fieldOf("players").forGetter(data -> data.players),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.BOOL).optionalFieldOf("nametags", Map.of()).forGetter(data -> data.nametags)
    ).apply(instance, (version, players, nametags) -> new MorphSavedData(players, nametags)));
    public static final SavedDataType<MorphSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("morph", "collections"), MorphSavedData::new, CODEC, null);
    private final Map<UUID, MorphCollection> players;

    private final Map<UUID, Boolean> nametags;
    public MorphSavedData() { this(Map.of(), Map.of()); }
    private MorphSavedData(Map<UUID, MorphCollection> players, Map<UUID, Boolean> nametags) {
        this.players = new HashMap<>(players);
        this.nametags = new HashMap<>(nametags);
    }
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
