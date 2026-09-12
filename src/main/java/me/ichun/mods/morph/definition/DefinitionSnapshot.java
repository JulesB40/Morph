package me.ichun.mods.morph.definition;

import java.util.Map;
import java.util.Set;
import me.ichun.mods.morph.config.MorphPolicySnapshot;

/** Validated definitions are data only; they do not grant native traits or mod support. */
public record DefinitionSnapshot(long revision, MorphPolicySnapshot policy,
        Map<String, MobDefinition> mobs, Map<String, TraitDefinition> traits) {
    public DefinitionSnapshot {
        if (revision < 0 || policy == null || policy.revision() != revision || mobs == null || traits == null
                || mobs.size() > 256 || traits.size() > 256)
            throw new IllegalArgumentException("Invalid definition snapshot");
        mobs = Map.copyOf(mobs); traits = Map.copyOf(traits);
        for (var entry : traits.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().id())) throw new IllegalArgumentException("Trait key mismatch");
        }
        for (var entry : mobs.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().species())) throw new IllegalArgumentException("Mob key mismatch");
            if (!traits.keySet().containsAll(entry.getValue().traits())) throw new IllegalArgumentException("Unknown mob trait reference");
        }
    }
    public static DefinitionSnapshot empty() {
        return new DefinitionSnapshot(0, MorphPolicySnapshot.defaults(), Map.of(), Map.of());
    }
    public record MobDefinition(String species, boolean enabled, Set<String> traits, String adapter, int adapterVersion) {
        public MobDefinition {
            if (!MorphPolicySnapshot.validId(species) || !MorphPolicySnapshot.validId(adapter)
                    || adapterVersion < 1 || adapterVersion > 65535) throw new IllegalArgumentException("Invalid mob definition");
            traits = MorphPolicySnapshot.identifiers(traits, 32);
        }
    }
    public record TraitDefinition(String id, boolean enabled, int cooldownTicks, boolean terrainHarm) {
        public TraitDefinition {
            if (!MorphPolicySnapshot.validId(id) || cooldownTicks < 0 || cooldownTicks > 72000)
                throw new IllegalArgumentException("Invalid trait definition");
        }
    }
}
