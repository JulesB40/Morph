package me.ichun.mods.morph.server;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MorphSaveFilesTest {
    @TempDir Path directory;

    private Path save(String data) throws Exception {
        var root = new CompoundTag();
        root.put("data", JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, JsonParser.parseString(data)));
        Path file = directory.resolve("collections.dat");
        NbtIo.writeCompressed(root, file);
        return file;
    }

    @Test void realCompressedSchemaOneSourceIsPreservedAndBackupIsIdempotent() throws Exception {
        var file = save("{\"schema_version\":1,\"players\":{}}");
        byte[] original = Files.readAllBytes(file);
        assertTrue(MorphSavedData.readExisting(file).migratedFromSchema1());
        assertArrayEquals(original, Files.readAllBytes(file));
        try (var files = Files.list(directory)) {
            var backups = files.filter(path -> path.getFileName().toString().endsWith(".bak")).toList();
            assertEquals(1, backups.size());
            assertArrayEquals(original, Files.readAllBytes(backups.getFirst()));
        }
        MorphSavedData.readExisting(file);
        try (var files = Files.list(directory)) { assertEquals(2, files.count()); }
    }

    @Test void unsupportedAndCorruptSourcesRemainUntouchedWithoutEmptyFallback() throws Exception {
        var file = save("{\"schema_version\":99,\"players\":{}}");
        byte[] original = Files.readAllBytes(file);
        assertThrows(IllegalStateException.class, () -> MorphSavedData.readExisting(file));
        assertArrayEquals(original, Files.readAllBytes(file));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
        byte[] corrupt = {0x1f, (byte) 0x8b, 0};
        Files.write(file, corrupt);
        assertThrows(IllegalStateException.class, () -> MorphSavedData.readExisting(file));
        assertArrayEquals(corrupt, Files.readAllBytes(file));
    }

    @Test void malformedPlayerAmongValidPlayersRejectsPartialDecode() throws Exception {
        var file = save("{\"schema_version\":1,\"players\":{\"00000000-0000-0000-0000-000000000001\":{\"forms\":[\"minecraft:pig\"]},\"invalid\":{\"forms\":[]}}}");
        byte[] original = Files.readAllBytes(file);
        assertThrows(IllegalStateException.class, () -> MorphSavedData.readExisting(file));
        assertArrayEquals(original, Files.readAllBytes(file));
    }
}
