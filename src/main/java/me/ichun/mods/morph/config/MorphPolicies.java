package me.ichun.mods.morph.config;

import java.util.EnumSet;
import java.util.Objects;
import java.util.function.Supplier;

/** Shared read facade. Definition reload owns publication; loaders own service wiring. */
public final class MorphPolicies {
    private static volatile Supplier<MorphPolicySnapshot> source = MorphPolicySnapshot::defaults;
    private static final EnumSet<MorphPolicySnapshot.ServerMode> SUPPORTED = EnumSet.of(
            MorphPolicySnapshot.ServerMode.CLASSIC, MorphPolicySnapshot.ServerMode.COMMAND);
    private MorphPolicies() {}
    public static MorphPolicySnapshot current() { return source.get(); }
    public static synchronized boolean supports(MorphPolicySnapshot.ServerMode mode) { return SUPPORTED.contains(mode); }
    /** Call only after the authoritative mode implementation has been installed. */
    public static synchronized void registerMode(MorphPolicySnapshot.ServerMode mode) {
        SUPPORTED.add(Objects.requireNonNull(mode));
    }
    /** Install the same atomic snapshot reader used by the definition service. */
    public static synchronized void bind(Supplier<MorphPolicySnapshot> snapshots) {
        source = Objects.requireNonNull(snapshots);
    }
}
