package me.ichun.mods.morph.config;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MorphConfigurationTest {
    @TempDir Path directory;
    private final Object server = new Object();
    @AfterEach void stop() { MorphConfiguration.stop(server); }

    @Test void runtimeReloadDeniesExplicitPlayerAndRetainsPolicyOnError() throws Exception {
        assertTrue(MorphConfiguration.start(server, directory).accepted());
        Path file = directory.resolve("morph/definitions.json");
        assertTrue(Files.exists(file));
        UUID denied = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Files.writeString(file, """
            {"schema_version":1,"policy":{"mode":"COMMAND","duration_ticks":200,
             "morph_players":{"deny":["00000000-0000-0000-0000-000000000001"]}}}
            """);
        assertTrue(MorphConfiguration.reload().accepted());
        var accepted = MorphPolicies.current();
        assertEquals(200, accepted.durationTicks());
        assertFalse(accepted.canMorph(denied, "minecraft:pig"));
        assertTrue(accepted.canUseSelector(denied));
        assertFalse(accepted.canAcquireByKill(UUID.randomUUID(), "minecraft:pig"));
        Files.writeString(file, "{\"schema_version\":1,\"policy\":{\"duration_ticks\":0}}");
        var rejected = MorphConfiguration.reload();
        assertFalse(rejected.accepted());
        assertEquals(accepted.revision(), rejected.revision());
        assertSame(accepted, MorphPolicies.current());
        assertTrue(Files.readString(file).contains("duration_ticks\":0"));
        Files.delete(file);
        assertFalse(MorphConfiguration.reload().accepted());
        assertSame(accepted, MorphPolicies.current());
    }

    @Test void invalidStartupDoesNotOverwriteAndServerStopClearsState() throws Exception {
        Files.createDirectories(directory.resolve("morph"));
        Path file = directory.resolve("morph/definitions.json");
        Files.writeString(file, "operator's invalid content");
        assertFalse(MorphConfiguration.start(server, directory).accepted());
        assertEquals("operator's invalid content", Files.readString(file));
        assertEquals(MorphPolicySnapshot.defaults(), MorphPolicies.current());
        assertThrows(IllegalStateException.class, () -> MorphConfiguration.start(new Object(), directory));
        MorphConfiguration.stop(new Object());
        assertEquals(file.toAbsolutePath(), MorphConfiguration.file());
        MorphConfiguration.stop(server);
        assertNull(MorphConfiguration.file());
        assertFalse(MorphConfiguration.reload().accepted());
        assertEquals(MorphPolicySnapshot.defaults(), MorphPolicies.current());
    }
}
