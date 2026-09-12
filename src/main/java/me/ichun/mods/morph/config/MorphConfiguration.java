package me.ichun.mods.morph.config;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import me.ichun.mods.morph.definition.DefinitionParser;
import me.ichun.mods.morph.definition.DefinitionReloadService;

/** One active server per process; loader lifecycle and commands call this on the server thread. */
public final class MorphConfiguration {
    private static Object owner;
    private static Path file;
    private static DefinitionReloadService definitions;
    private MorphConfiguration() {}

    /** Create defaults only when absent. Invalid existing files remain untouched and use safe defaults. */
    public static synchronized DefinitionReloadService.ReloadResult start(Object server, Path configDirectory) {
        Objects.requireNonNull(server); Objects.requireNonNull(configDirectory);
        if (owner != null && owner != server) throw new IllegalStateException("Another server owns Morph configuration");
        if (owner == server) return reload();
        owner = server;
        file = configDirectory.toAbsolutePath().normalize().resolve("morph").resolve("definitions.json");
        definitions = new DefinitionReloadService();
        var service = definitions;
        MorphPolicies.bind(() -> service.current().policy());
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) createDefaults(file);
            return reload();
        } catch (IOException | RuntimeException error) {
            return rejected("Cannot initialize Morph configuration; existing file was not replaced");
        }
    }

    private static void createDefaults(Path target) throws IOException {
        byte[] bytes;
        try (var input = MorphConfiguration.class.getResourceAsStream("/morph/definitions/defaults.json")) {
            if (input == null) throw new IOException("Missing packaged defaults");
            bytes = input.readNBytes(DefinitionParser.MAX_BYTES + 1);
        }
        DefinitionParser.parse(bytes, 0);
        Path temporary = Files.createTempFile(target.getParent(), ".morph-defaults-", ".json");
        try {
            Files.write(temporary, bytes);
            // Same-directory move, deliberately without REPLACE_EXISTING: never overwrite an operator's file.
            try { Files.move(temporary, target); }
            catch (FileAlreadyExistsException racedWithOperator) { /* Read the existing file instead. */ }
        } finally { Files.deleteIfExists(temporary); }
    }

    public static synchronized DefinitionReloadService.ReloadResult reload() {
        if (definitions == null) return rejected("No server configuration is active");
        try (var input = Files.newInputStream(file)) { return definitions.reload(input); }
        catch (IOException error) { return rejected("Cannot read Morph configuration; previous revision retained"); }
    }
    public static synchronized Path file() { return file; }
    public static synchronized long revision() { return definitions == null ? 0 : definitions.current().revision(); }
    public static synchronized me.ichun.mods.morph.definition.DefinitionSnapshot snapshot() {
        return definitions == null ? me.ichun.mods.morph.definition.DefinitionSnapshot.empty() : definitions.current();
    }
    public static synchronized void stop(Object server) {
        if (owner != server) return;
        owner = null; file = null; definitions = null;
        MorphPolicies.bind(MorphPolicySnapshot::defaults);
    }
    private static DefinitionReloadService.ReloadResult rejected(String reason) {
        return new DefinitionReloadService.ReloadResult(false, revision(), List.of(reason));
    }
}
