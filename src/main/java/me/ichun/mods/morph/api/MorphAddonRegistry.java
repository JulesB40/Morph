package me.ichun.mods.morph.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import me.ichun.mods.morph.config.MorphPolicySnapshot;

/** Explicit support declarations. Registering metadata alone does not install capture/render code. */
public final class MorphAddonRegistry {
    private volatile Map<Key, AdapterRegistration> adapters = Map.of();
    public record Key(String id, int version) {
        public Key {
            if (!MorphPolicySnapshot.validId(id) || version < 1 || version > 65535)
                throw new IllegalArgumentException("Invalid adapter identity");
        }
    }
    public record AdapterRegistration(Key key, Set<String> species,
            boolean captureInstalled, boolean shapeInstalled, boolean renderInstalled, boolean traitsDeclared) {
        public AdapterRegistration {
            if (key == null) throw new IllegalArgumentException("Missing adapter key");
            species = MorphPolicySnapshot.identifiers(species, 256);
            if (species.isEmpty()) throw new IllegalArgumentException("Declare at least one supported species");
        }
        public boolean available() { return captureInstalled && shapeInstalled && renderInstalled && traitsDeclared; }
    }
    public synchronized void register(AdapterRegistration registration) {
        if (registration == null || adapters.containsKey(registration.key()) || adapters.size() >= 128)
            throw new IllegalArgumentException("Duplicate, null or excess adapter registration");
        var next = new LinkedHashMap<>(adapters);
        next.put(registration.key(), registration);
        adapters = Map.copyOf(next);
    }
    public boolean availability(String adapter, int version, String species) {
        var registration = adapters.get(new Key(adapter, version));
        return registration != null && registration.available() && registration.species().contains(species);
    }
    public Map<Key, AdapterRegistration> snapshot() { return adapters; }
}
