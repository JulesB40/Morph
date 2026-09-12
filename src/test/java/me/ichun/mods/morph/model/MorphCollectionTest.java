package me.ichun.mods.morph.model;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static me.ichun.mods.morph.model.MorphCollection.SelectionResult.*;

class MorphCollectionTest {
    @Test void selectionRequiresOwnershipAndDoesNotMutateOnFailure() {
        MorphCollection forms = new MorphCollection();
        assertEquals(NOT_OWNED, forms.select("minecraft:pig", 0));
        assertEquals("", forms.activeForm());
        assertTrue(forms.unlock("minecraft:pig"));
        assertFalse(forms.unlock("minecraft:pig"));
        assertEquals(CHANGED, forms.select("minecraft:pig", 0));
    }
    @Test void resetIsAlwaysAvailableButCannotBypassCooldown() {
        MorphCollection forms = new MorphCollection();
        forms.unlock("minecraft:pig"); forms.unlock("minecraft:cow");
        assertEquals(CHANGED, forms.select("minecraft:pig", 100));
        assertTrue(forms.reset());
        assertEquals(COOLDOWN, forms.select("minecraft:cow", 119));
        assertEquals(CHANGED, forms.select("minecraft:cow", 120));
        assertEquals(UNCHANGED, forms.select("minecraft:cow", 121));
    }
    @Test void persistedStateRestoresOwnershipButRejectsFutureSchema() {
        MorphCollection forms = MorphCollection.restore(1, List.of("minecraft:pig"), "minecraft:pig");
        assertEquals("minecraft:pig", forms.activeForm());
        assertEquals(List.of("minecraft:pig"), forms.ownedForms());
        assertEquals("", MorphCollection.restore(1, List.of("minecraft:pig"), "minecraft:cow").activeForm());
        assertThrows(IllegalArgumentException.class, () -> MorphCollection.restore(2, List.of(), ""));
    }
    @Test void malformedPlayerAndOversizedIdsAreRejected() {
        MorphCollection forms = new MorphCollection();
        for (String invalid : List.of("minecraft:player", "pig", "minecraft:Pig", "minecraft:pig{Health:999}", "a:" + "x".repeat(256))) {
            assertThrows(IllegalArgumentException.class, () -> forms.unlock(invalid));
        }
    }
    @Test void collectionIsBoundedAndReturnedViewCannotMutateOwnership() {
        MorphCollection forms = new MorphCollection();
        for (int i = 0; i < MorphCollection.MAX_FORMS; i++) assertTrue(forms.unlock("example:mob_" + i));
        assertFalse(forms.unlock("example:extra"));
        assertThrows(UnsupportedOperationException.class, () -> forms.ownedForms().clear());
        ArrayList<String> oversized = new ArrayList<>(forms.ownedForms()); oversized.add("example:extra");
        assertThrows(IllegalArgumentException.class, () -> MorphCollection.restore(1, oversized, ""));
    }

    @Test void duplicateAppearanceMergesAttributesWithoutChangingIdentityOrFavorite() {
        var forms = new MorphCollection();
        var red = FormDescriptorTest.sheep(14, false, null).withAttributes(java.util.Map.of("minecraft:max_health", 8.0));
        assertEquals(MorphCollection.MutationResult.CHANGED, forms.acquire(red, MorphCollection.AttributeMergePolicy.KEEP_EXISTING));
        assertEquals(MorphCollection.MutationResult.CHANGED, forms.favorite(red.entryId(), true));
        long revision = forms.revision();
        assertEquals(MorphCollection.MutationResult.UNCHANGED, forms.acquire(red.withAttributes(java.util.Map.of("minecraft:max_health", 10.0)),
                MorphCollection.AttributeMergePolicy.KEEP_EXISTING));
        assertEquals(revision, forms.revision());
        assertEquals(MorphCollection.MutationResult.CHANGED, forms.acquire(red.withAttributes(java.util.Map.of("minecraft:max_health", 10.0)),
                MorphCollection.AttributeMergePolicy.MAX_BASE));
        var entry = forms.entry(red.entryId());
        assertTrue(entry.favorite());
        assertEquals(1, entry.revision());
        assertEquals(10.0, entry.descriptor().attributes().get("minecraft:max_health"));
        assertEquals(revision + 1, forms.revision());
        assertEquals(1, forms.entries().size());
        forms.acquire(FormDescriptorTest.sheep(0, false, null), MorphCollection.AttributeMergePolicy.KEEP_EXISTING);
        assertEquals(2, forms.entries().size());
        assertEquals(List.of("minecraft:sheep"), forms.ownedForms());
    }

    @Test void failedActiveDeletionLeavesWholeSnapshotUntouchedAndCommittedDeletionOnlyIncrementsOnce() {
        var forms = new MorphCollection();
        forms.unlock("minecraft:pig");
        forms.select("minecraft:pig", 100);
        var before = forms.snapshot();
        assertEquals(MorphCollection.MutationResult.ACTIVE, forms.delete(forms.activeEntryId(), false));
        assertEquals(before, forms.snapshot());
        assertEquals(MorphCollection.MutationResult.CHANGED, forms.delete(forms.activeEntryId(), true));
        assertEquals(before.revision() + 1, forms.revision());
        assertNull(forms.activeDescriptor());
        assertTrue(forms.entries().isEmpty());
        assertEquals(MorphCollection.MutationResult.NOT_OWNED, forms.delete(null, true));
    }

    @Test void speciesConveniencePrefersMigratedDefaultThenSmallestDetailedId() {
        var red = FormDescriptorTest.sheep(14, false, null);
        var white = FormDescriptorTest.sheep(0, false, null);
        var forms = new MorphCollection();
        forms.acquire(red, MorphCollection.AttributeMergePolicy.KEEP_EXISTING);
        forms.acquire(white, MorphCollection.AttributeMergePolicy.KEEP_EXISTING);
        forms.select("minecraft:sheep", 0);
        assertEquals(List.of(red.entryId(), white.entryId()).stream().min(EntryId::compareTo).orElseThrow(), forms.activeEntryId());
        forms.unlock("minecraft:sheep");
        forms.select("minecraft:sheep", 20);
        assertEquals(FormDescriptor.species("minecraft:sheep").entryId(), forms.activeEntryId());
        assertEquals(3, forms.entries().size());
    }
}
