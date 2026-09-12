package me.ichun.mods.morph.definition;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.io.IOException;
import java.io.InputStream;
import me.ichun.mods.morph.config.MorphPolicies;
import me.ichun.mods.morph.config.MorphPolicySnapshot.ServerMode;

/** Parse and validate before a single volatile publication; rejected reloads keep the last snapshot. */
public final class DefinitionReloadService {
    private volatile DefinitionSnapshot current = DefinitionSnapshot.empty();
    private final Predicate<ServerMode> supportedModes;
    public DefinitionReloadService() { this(MorphPolicies::supports); }
    public DefinitionReloadService(Predicate<ServerMode> supportedModes) {
        this.supportedModes = Objects.requireNonNull(supportedModes);
    }
    public DefinitionSnapshot current() { return current; }
    /** Caller retains stream ownership. Read at most the document cap plus one sentinel byte. */
    public synchronized ReloadResult reload(InputStream document) {
        try {
            if (document == null) throw new IOException("Missing definition document");
            return reload(document.readNBytes(DefinitionParser.MAX_BYTES + 1));
        } catch (IOException error) {
            return new ReloadResult(false, current.revision(), List.of("Cannot read definition document"));
        }
    }
    public synchronized ReloadResult reload(byte[] document) {
        try {
            var candidate = DefinitionParser.parse(document, Math.incrementExact(current.revision()));
            if (!supportedModes.test(candidate.policy().mode())) throw new IllegalArgumentException("Mode has no installed authoritative implementation");
            current = candidate;
            return new ReloadResult(true, candidate.revision(), List.of());
        } catch (RuntimeException error) {
            String message = error.getMessage() == null ? "Invalid definition document" : error.getMessage();
            return new ReloadResult(false, current.revision(), List.of(message.substring(0, Math.min(256, message.length()))));
        }
    }
    public record ReloadResult(boolean accepted, long revision, List<String> errors) {
        public ReloadResult { errors = List.copyOf(errors); }
    }
}
