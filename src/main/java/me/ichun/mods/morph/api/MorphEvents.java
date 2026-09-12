package me.ichun.mods.morph.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import me.ichun.mods.morph.config.MorphPolicySnapshot;

/** Immutable cancelable pre-action hooks. Service must call before any authoritative mutation. */
public final class MorphEvents {
    public enum Action { ACQUIRE, SELECT, RESET, DELETE }
    public record BeforeAction(Action action, UUID actor, UUID target, String species, long policyRevision) {
        public BeforeAction {
            Objects.requireNonNull(action); Objects.requireNonNull(actor); Objects.requireNonNull(target);
            if (policyRevision < 0 || (action != Action.RESET && !MorphPolicySnapshot.validId(species))
                    || (action == Action.RESET && !"".equals(species)))
                throw new IllegalArgumentException("Invalid action event");
        }
    }
    public record Decision(boolean allowed, String reason) {
        public Decision {
            if (reason == null || reason.length() > 256 || (!allowed && reason.isBlank()))
                throw new IllegalArgumentException("Invalid event decision");
        }
        public static Decision allow() { return new Decision(true, ""); }
        public static Decision cancel(String reason) { return new Decision(false, reason); }
    }
    @FunctionalInterface public interface Listener { Decision before(BeforeAction event); }
    private volatile List<Listener> listeners = List.of();
    public synchronized AutoCloseable register(Listener listener) {
        Objects.requireNonNull(listener);
        if (listeners.size() >= 64 || listeners.contains(listener)) throw new IllegalArgumentException("Duplicate or excess listener");
        var next = new ArrayList<>(listeners); next.add(listener); listeners = List.copyOf(next);
        return () -> unregister(listener);
    }
    private synchronized void unregister(Listener listener) {
        var next = new ArrayList<>(listeners); next.remove(listener); listeners = List.copyOf(next);
    }
    public Decision before(BeforeAction event) {
        Objects.requireNonNull(event);
        for (Listener listener : listeners) {
            try {
                Decision decision = listener.before(event);
                if (decision == null) return Decision.cancel("Addon returned no decision");
                if (!decision.allowed()) return decision;
            } catch (RuntimeException error) { return Decision.cancel("Addon pre-action hook failed"); }
        }
        return Decision.allow();
    }
}
