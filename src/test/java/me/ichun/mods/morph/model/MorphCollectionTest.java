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
}
