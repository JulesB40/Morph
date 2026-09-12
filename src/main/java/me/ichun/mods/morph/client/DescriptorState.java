package me.ichun.mods.morph.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import me.ichun.mods.morph.model.CollectionEntry;
import me.ichun.mods.morph.network.AppearanceProtocol;

/** Connection-scoped descriptor state; consumers refresh visuals only for accepted messages. */
public final class DescriptorState {
    private static final Map<UUID, State> states = new HashMap<>();
    private DescriptorState() {}
    private static final class State {
        UUID epoch;
        long sequence = -1, generation = -1;
        CollectionEntry active;
        State(UUID epoch) { this.epoch = epoch; }
    }
    public static CollectionEntry active(UUID subject) { var state = states.get(subject); return state == null ? null : state.active; }
    public static boolean accept(AppearanceProtocol.Appearance appearance) {
        var state = states.get(appearance.subject());
        if (state == null || !state.epoch.equals(appearance.epoch())) {
            state = new State(appearance.epoch()); states.put(appearance.subject(), state);
        }
        if (appearance.sequence() <= state.sequence) return false;
        state.sequence = appearance.sequence(); state.active = appearance.active(); return true;
    }
    public static boolean accept(AppearanceProtocol.Transition transition) {
        var state = states.get(transition.subject());
        // Appearance establishes the session. TCP delivery preserves server publication order.
        if (state == null || !state.epoch.equals(transition.epoch()) || transition.generation() <= state.generation) return false;
        state.generation = transition.generation(); return true;
    }
    public static void clear() { states.clear(); }
}
