package me.ichun.mods.morph.server;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.nio.ByteBuffer;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.WeakHashMap;
import me.ichun.mods.morph.model.MorphCollection;
import me.ichun.mods.morph.model.CollectionSnapshot;
import me.ichun.mods.morph.progression.BiomassCodec;
import me.ichun.mods.morph.progression.BiomassDefinitions;
import me.ichun.mods.morph.progression.BiomassLedger;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;

/** Shared across dimensions via the overworld; schema migration preserves its source before native saving. */
public final class MorphSavedData extends SavedData {
    private static final Map<MinecraftServer, MorphSavedData> LOADED = new WeakHashMap<>();
    private static final BiomassDefinitions BIOMASS_DEFINITIONS = BiomassDefinitions.defaults();
    private static final Codec<BiomassLedger> BIOMASS_CODEC = Codec.BYTE_BUFFER.comapFlatMap(buffer -> {
        if (buffer.remaining() > BiomassCodec.MAX_BYTES) return DataResult.error(() -> "Oversized biomass save");
        byte[] bytes = new byte[buffer.remaining()];
        buffer.duplicate().get(bytes);
        try { return DataResult.success(BiomassCodec.decode(bytes, BIOMASS_DEFINITIONS)); }
        catch (IllegalArgumentException invalid) { return DataResult.error(invalid::getMessage); }
    }, ledger -> ByteBuffer.wrap(BiomassCodec.encode(ledger, BIOMASS_DEFINITIONS)));
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
    ).apply(instance, (players, nametags) -> new MorphSavedData(players, nametags, Map.of(), true)));
    private static final MapCodec<MorphSavedData> CURRENT_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, COLLECTION_CODEC).fieldOf("players").forGetter(data -> data.players),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.BOOL).optionalFieldOf("nametags", Map.of()).forGetter(data -> data.nametags),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, BIOMASS_CODEC).optionalFieldOf("biomass", Map.of()).forGetter(data -> data.biomass)
    ).apply(instance, (players, nametags, biomass) -> new MorphSavedData(players, nametags, biomass, false)));
    public static final Codec<MorphSavedData> CODEC = Codec.intRange(1, MorphCollection.CURRENT_SCHEMA)
            .dispatch("schema_version", data -> MorphCollection.CURRENT_SCHEMA, version -> version == 1 ? LEGACY_CODEC : CURRENT_CODEC);
    public static final SavedDataType<MorphSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("morph", "collections"), MorphSavedData::new, CODEC, null);

    /** Server-thread entry point. Native computeIfAbsent would replace a failed decode with dirty empty data. */
    public static MorphSavedData load(MinecraftServer server) {
        MorphSavedData cached = LOADED.get(server);
        if (cached != null) return cached;
        Path dataFolder = DimensionType.getStorageFolder(Level.OVERWORLD, server.getWorldPath(LevelResource.ROOT)).resolve("data");
        Path file = TYPE.id().withSuffix(".dat").resolveAgainst(dataFolder);
        MorphSavedData loaded = Files.exists(file) ? readExisting(file) : new MorphSavedData();
        // No native cache mutation occurs until complete decode and any required backup succeed.
        server.overworld().getDataStorage().set(TYPE, loaded);
        LOADED.put(server, loaded);
        return loaded;
    }

    static MorphSavedData readExisting(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            net.minecraft.nbt.CompoundTag root;
            try (var input = new ByteArrayInputStream(bytes)) {
                boolean compressed = bytes.length >= 2 && (bytes[0] & 255) == 0x1f && (bytes[1] & 255) == 0x8b;
                root = compressed ? NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap())
                        : NbtIo.read(new DataInputStream(input));
            }
            if (root == null || root.get("data") == null) throw new IllegalArgumentException("Missing Morph save data");
            var decoded = CODEC.parse(NbtOps.INSTANCE, root.get("data"));
            if (decoded.error().isPresent()) throw new IllegalArgumentException(decoded.error().orElseThrow().message());
            MorphSavedData result = decoded.result().orElseThrow(() -> new IllegalArgumentException("Empty Morph save decode"));
            if (result.migratedFromSchema1()) preserveSchemaOne(file, bytes);
            return result;
        } catch (IOException | IllegalArgumentException error) {
            throw new IllegalStateException("Cannot load Morph collections; original save preserved: " + file, error);
        }
    }

    private static void preserveSchemaOne(Path file, byte[] bytes) throws IOException {
        final String hash;
        try { hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        Path backup = file.resolveSibling(file.getFileName() + ".schema1-" + hash + ".bak");
        try { Files.write(backup, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE); }
        catch (java.nio.file.FileAlreadyExistsException existing) {
            if (Files.size(backup) != bytes.length || !java.util.Arrays.equals(Files.readAllBytes(backup), bytes))
                throw new IOException("Existing Morph migration backup does not match its source", existing);
        }
    }
    private final Map<UUID, MorphCollection> players;

    private final Map<UUID, Boolean> nametags;
    private final Map<UUID, BiomassLedger> biomass;
    private final boolean migratedFromSchema1;
    public MorphSavedData() { this(Map.of(), Map.of(), Map.of(), false); }
    private MorphSavedData(Map<UUID, MorphCollection> players, Map<UUID, Boolean> nametags,
            Map<UUID, BiomassLedger> biomass, boolean migratedFromSchema1) {
        this.players = new HashMap<>(players);
        this.nametags = new HashMap<>(nametags);
        this.biomass = new HashMap<>(biomass);
        this.migratedFromSchema1 = migratedFromSchema1;
    }
    /** The storage integration can preserve the original file before dirtying a migrated save. */
    public boolean migratedFromSchema1() { return migratedFromSchema1; }

    public BiomassLedger biomass(UUID player) { return biomass.getOrDefault(player, BiomassLedger.locked()); }

    /** Server-thread compare-and-commit after an authoritative action has succeeded. */
    public boolean commitBiomass(UUID player, long expectedRevision, BiomassLedger replacement) {
        var current = biomass(player);
        if (current.revision() != expectedRevision) return false;
        BIOMASS_DEFINITIONS.validate(replacement);
        if (current.equals(replacement)) return true;
        if (expectedRevision == Long.MAX_VALUE || replacement.revision() != expectedRevision + 1) return false;
        biomass.put(player, replacement);
        setDirty();
        return true;
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
