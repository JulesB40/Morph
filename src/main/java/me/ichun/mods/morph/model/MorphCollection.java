package me.ichun.mods.morph.model;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Server-thread ownership and selection rules; callers validate permissions, adapter support and fit. */
public final class MorphCollection {
    public static final int CURRENT_SCHEMA = 2;
    public static final int MAX_FORMS = 256;
    public static final int MAX_ID_LENGTH = 256;
    public static final long COOLDOWN_TICKS = 20;
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    private final Map<EntryId, CollectionEntry> owned = new HashMap<>();
    private EntryId active;
    private long revision;
    private long lastSelectionTick = Long.MIN_VALUE;

    public enum SelectionResult { CHANGED, UNCHANGED, NOT_OWNED, COOLDOWN }
    public enum MutationResult { CHANGED, UNCHANGED, NOT_OWNED, FULL, ACTIVE }
    public enum AttributeMergePolicy { KEEP_EXISTING, MAX_BASE }

    public static MorphCollection restore(int version, Collection<String> forms, String active) {
        if (version != 1) throw new IllegalArgumentException("Unsupported species-only Morph schema: " + version);
        if (forms.size() > MAX_FORMS) throw new IllegalArgumentException("Too many saved forms");
        MorphCollection collection = new MorphCollection();
        for (String form : forms) if (!isFormId(form)) throw new IllegalArgumentException("Invalid saved form ID");
        for (String form : forms.stream().distinct().sorted().toList()) {
            var descriptor = FormDescriptor.species(form);
            var id = descriptor.entryId();
            collection.owned.put(id, new CollectionEntry(id, descriptor, 0, false, collection.owned.size()));
        }
        // An orphaned active form must never grant ownership on load.
        collection.active = collection.resolveSpecies(active);
        return collection;
    }

    public static MorphCollection restore(CollectionSnapshot snapshot) {
        var collection = new MorphCollection();
        for (var entry : snapshot.entries()) collection.owned.put(entry.id(), entry);
        collection.active = snapshot.activeEntryId();
        collection.revision = snapshot.revision();
        return collection;
    }

    public static boolean isFormId(String id) {
        return id != null && id.length() <= MAX_ID_LENGTH && ID.matcher(id).matches()
                && !id.equals("minecraft:player");
    }

    public List<String> ownedForms() { return owned.values().stream().map(e -> e.descriptor().species()).distinct().sorted().toList(); }
    public String activeForm() { return active == null ? "" : owned.get(active).descriptor().species(); }
    public EntryId activeEntryId() { return active; }
    public FormDescriptor activeDescriptor() { return active == null ? null : owned.get(active).descriptor(); }
    public long revision() { return revision; }
    public CollectionEntry entry(EntryId id) { return owned.get(id); }
    public List<CollectionEntry> entries() { return owned.values().stream().sorted(Comparator.comparingInt(CollectionEntry::order)).toList(); }
    public CollectionSnapshot snapshot() { return new CollectionSnapshot(CURRENT_SCHEMA, revision, entries(), active); }

    public boolean unlock(String form) {
        if (!isFormId(form)) throw new IllegalArgumentException("Invalid Morph form ID");
        return acquire(FormDescriptor.species(form), AttributeMergePolicy.KEEP_EXISTING) == MutationResult.CHANGED;
    }

    public MutationResult acquire(FormDescriptor descriptor, AttributeMergePolicy policy) {
        if (policy == null) throw new IllegalArgumentException("Missing attribute merge policy");
        EntryId id = descriptor.entryId();
        CollectionEntry previous = owned.get(id);
        if (previous == null) {
            if (owned.size() >= MAX_FORMS) return MutationResult.FULL;
            int order = 0;
            var usedOrders = owned.values().stream().map(CollectionEntry::order).collect(java.util.stream.Collectors.toSet());
            while (usedOrders.contains(order)) order++;
            var entry = new CollectionEntry(id, descriptor, 0, false, order);
            if (!fitsCollection(entry)) return MutationResult.FULL;
            long nextRevision = Math.incrementExact(revision);
            owned.put(id, entry);
            revision = nextRevision;
            return MutationResult.CHANGED;
        }
        if (!previous.descriptor().identityJson().equals(descriptor.identityJson()))
            throw new IllegalArgumentException("Appearance hash collision");
        Map<String, Double> attributes = previous.descriptor().attributes();
        if (policy == AttributeMergePolicy.MAX_BASE) {
            attributes = new HashMap<>(attributes);
            for (var value : descriptor.attributes().entrySet()) attributes.merge(value.getKey(), value.getValue(), Math::max);
        }
        FormDescriptor updated = descriptor.withAttributes(attributes);
        if (updated.equals(previous.descriptor())) return MutationResult.UNCHANGED;
        var replacement = new CollectionEntry(id, updated, Math.incrementExact(previous.revision()), previous.favorite(), previous.order());
        if (!fitsCollection(replacement)) return MutationResult.FULL;
        long nextRevision = Math.incrementExact(revision);
        owned.put(id, replacement);
        revision = nextRevision;
        return MutationResult.CHANGED;
    }

    private boolean fitsCollection(CollectionEntry candidate) {
        var entries = new java.util.ArrayList<>(owned.values());
        entries.removeIf(entry -> entry.id().equals(candidate.id()));
        entries.add(candidate);
        try { new CollectionSnapshot(CURRENT_SCHEMA, revision, entries, active); return true; }
        catch (IllegalArgumentException overLimit) { return false; }
    }

    public SelectionResult select(String form, long tick) {
        return select(resolveSpecies(form), tick);
    }

    /** Species convenience selects its default entry first, then the smallest appearance ID. */
    private EntryId resolveSpecies(String form) {
        if (!isFormId(form)) return null;
        EntryId defaultId = FormDescriptor.species(form).entryId();
        if (owned.containsKey(defaultId)) return defaultId;
        return owned.values().stream().filter(entry -> entry.descriptor().species().equals(form))
                .map(CollectionEntry::id).min(Comparator.naturalOrder()).orElse(null);
    }

    public SelectionResult select(EntryId id, long tick) {
        if (id == null || !owned.containsKey(id)) return SelectionResult.NOT_OWNED;
        if (id.equals(active)) return SelectionResult.UNCHANGED;
        if (lastSelectionTick != Long.MIN_VALUE && tick >= lastSelectionTick
                && tick - lastSelectionTick < COOLDOWN_TICKS) return SelectionResult.COOLDOWN;
        long nextRevision = Math.incrementExact(revision);
        active = id;
        lastSelectionTick = tick;
        revision = nextRevision;
        return SelectionResult.CHANGED;
    }

    /** Returning to the player is always available, but does not bypass selection cooldown. */
    public boolean reset() {
        if (active == null) return false;
        long nextRevision = Math.incrementExact(revision);
        active = null;
        revision = nextRevision;
        return true;
    }

    public MutationResult favorite(EntryId id, boolean value) {
        var entry = owned.get(id);
        if (entry == null) return MutationResult.NOT_OWNED;
        if (entry.favorite() == value) return MutationResult.UNCHANGED;
        var replacement = new CollectionEntry(id, entry.descriptor(), entry.revision(), value, entry.order());
        long nextRevision = Math.incrementExact(revision);
        owned.put(id, replacement);
        revision = nextRevision;
        return MutationResult.CHANGED;
    }

    /** Active deletion requires the service to validate returning to self before committing. */
    public MutationResult delete(EntryId id, boolean allowActiveReset) {
        if (!owned.containsKey(id)) return MutationResult.NOT_OWNED;
        if (id.equals(active) && !allowActiveReset) return MutationResult.ACTIVE;
        long nextRevision = Math.incrementExact(revision);
        owned.remove(id);
        if (id.equals(active)) active = null;
        revision = nextRevision;
        return MutationResult.CHANGED;
    }
}
