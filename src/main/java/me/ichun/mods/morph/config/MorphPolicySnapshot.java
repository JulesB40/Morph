package me.ichun.mods.morph.config;

import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Immutable server rules; empty allowlists allow everyone, explicit denies win. */
public record MorphPolicySnapshot(long revision, ServerMode mode, boolean biomassOptIn,
        int durationTicks, boolean morphSounds, PlayerFilter morphPlayers,
        PlayerFilter selectorPlayers, FormFilter forms, AbilityPolicy abilities) {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    public enum ServerMode { CLASSIC, COMMAND, DISGUISE, BIOMASS }

    public MorphPolicySnapshot {
        if (revision < 0 || mode == null || durationTicks < 1 || durationTicks > 1200
                || morphPlayers == null || selectorPlayers == null || forms == null || abilities == null)
            throw new IllegalArgumentException("Invalid morph policy");
        if (mode == ServerMode.BIOMASS && !biomassOptIn)
            throw new IllegalArgumentException("BIOMASS requires explicit opt-in");
    }

    public static MorphPolicySnapshot defaults() {
        return new MorphPolicySnapshot(0, ServerMode.CLASSIC, false, 100, true,
                new PlayerFilter(Set.of(), Set.of()), new PlayerFilter(Set.of(), Set.of()),
                new FormFilter(Set.of(), Set.of()), new AbilityPolicy(true, false, Set.of()));
    }

    public boolean canMorph(UUID player, String species) {
        return morphPlayers.allows(player) && forms.allows(species);
    }
    public boolean canUseSelector(UUID player) { return selectorPlayers.allows(player); }
    public boolean canAcquireByKill(UUID player, String species) {
        return mode != ServerMode.COMMAND && canMorph(player, species);
    }
    public MorphPolicySnapshot withRevision(long value) {
        return new MorphPolicySnapshot(value, mode, biomassOptIn, durationTicks, morphSounds,
                morphPlayers, selectorPlayers, forms, abilities);
    }
    public static boolean validId(String value) {
        return value != null && value.length() <= 256 && ID.matcher(value).matches();
    }
    public static Set<String> identifiers(Set<String> values, int maximum) {
        if (values == null || values.size() > maximum || values.stream().anyMatch(v -> !validId(v)))
            throw new IllegalArgumentException("Invalid identifier set");
        return Set.copyOf(values);
    }
    public record PlayerFilter(Set<UUID> allow, Set<UUID> deny) {
        public PlayerFilter {
            if (allow == null || deny == null || allow.size() > 256 || deny.size() > 256)
                throw new IllegalArgumentException("Player filter exceeds 256 entries");
            allow = Set.copyOf(allow); deny = Set.copyOf(deny);
        }
        public boolean allows(UUID player) {
            return player != null && !deny.contains(player) && (allow.isEmpty() || allow.contains(player));
        }
    }
    /** Exact IDs only. Legacy regex support requires a separately bounded matcher. */
    public record FormFilter(Set<String> allow, Set<String> deny) {
        public FormFilter { allow = identifiers(allow, 256); deny = identifiers(deny, 256); }
        public boolean allows(String species) {
            return validId(species) && !deny.contains(species) && (allow.isEmpty() || allow.contains(species));
        }
    }
    public record AbilityPolicy(boolean enabled, boolean terrainHarm, Set<String> disabled) {
        public AbilityPolicy { disabled = identifiers(disabled, 256); }
        public boolean allows(String ability) { return enabled && validId(ability) && !disabled.contains(ability); }
    }
}
