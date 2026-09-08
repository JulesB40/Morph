package me.ichun.mods.morph.model;

import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Loader-independent ownership and selection rules. No entity NBT is accepted. */
public final class MorphCollection {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_FORMS = 256;
    public static final int MAX_ID_LENGTH = 256;
    public static final long COOLDOWN_TICKS = 20;
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    private final TreeSet<String> owned = new TreeSet<>();
    private String active = "";
    private long lastSelectionTick = Long.MIN_VALUE;

    public enum SelectionResult { CHANGED, UNCHANGED, NOT_OWNED, COOLDOWN }

    public static MorphCollection restore(int version, Collection<String> forms, String active) {
        if (version != CURRENT_SCHEMA) throw new IllegalArgumentException("Unsupported Morph schema: " + version);
        if (forms.size() > MAX_FORMS) throw new IllegalArgumentException("Too many saved forms");
        MorphCollection collection = new MorphCollection();
        for (String form : forms) collection.unlock(form);
        // An orphaned active form must never grant ownership on load.
        collection.active = collection.owned.contains(active) ? active : "";
        return collection;
    }

    public static boolean isFormId(String id) {
        return id != null && id.length() <= MAX_ID_LENGTH && ID.matcher(id).matches()
                && !id.equals("minecraft:player");
    }

    public List<String> ownedForms() { return List.copyOf(owned); }
    public String activeForm() { return active; }

    public boolean unlock(String form) {
        if (!isFormId(form)) throw new IllegalArgumentException("Invalid Morph form ID");
        if (owned.contains(form) || owned.size() >= MAX_FORMS) return false;
        return owned.add(form);
    }

    public SelectionResult select(String form, long tick) {
        if (!owned.contains(form)) return SelectionResult.NOT_OWNED;
        if (active.equals(form)) return SelectionResult.UNCHANGED;
        if (lastSelectionTick != Long.MIN_VALUE && tick >= lastSelectionTick
                && tick - lastSelectionTick < COOLDOWN_TICKS) return SelectionResult.COOLDOWN;
        active = form;
        lastSelectionTick = tick;
        return SelectionResult.CHANGED;
    }

    /** Returning to the player is always available, but does not bypass selection cooldown. */
    public boolean reset() {
        if (active.isEmpty()) return false;
        active = "";
        return true;
    }
}
