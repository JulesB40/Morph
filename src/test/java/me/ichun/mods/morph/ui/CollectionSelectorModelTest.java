package me.ichun.mods.morph.ui;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CollectionSelectorModelTest {
    private final CollectionSelectorModel model = new CollectionSelectorModel();
    private static CollectionSelectorModel.Row row(String id, String name, String details, boolean favorite, int order) {
        return new CollectionSelectorModel.Row(id, "minecraft:sheep", name, details, favorite, order);
    }
    @Test void searchUsesLocalizedNamesVariantTermsAndIdsWithoutAccentsOrCase() {
        model.replace(List.of(row("a", "Mouton", "Bébé bleu", false, 0), row("b", "Mouton", "Adulte rouge", true, 1)));
        model.query("BEBE mouton");
        assertEquals(List.of("a"), model.visible().stream().map(CollectionSelectorModel.Row::id).toList());
        model.query("minecraft:sheep rouge");
        assertEquals("b", model.selected().id());
        model.query("missing");
        assertNull(model.selected());
        assertEquals(List.of(), model.pageRows());
        assertEquals(1, model.pages());
    }
    @Test void variantsStayDistinctAndFavoriteFilterOnlyUsesTheReplacementSnapshot() {
        var blue = row("blue", "Sheep", "Blue", false, 0);
        var red = row("red", "Sheep", "Red", true, 1);
        model.replace(List.of(blue, red));
        model.select("red");
        model.favoritesOnly(true);
        assertEquals(List.of(red), model.visible());
        model.replace(List.of(blue, row("red", "Sheep", "Red", false, 1)));
        assertTrue(model.visible().isEmpty());
        model.favoritesOnly(false);
        assertEquals(2, model.visibleCount());
    }
    @Test void keyboardNavigationCrossesPagesAndSnapshotRemovalClampsSelection() {
        var a = row("a", "A", "", false, 0);
        var b = row("b", "B", "", false, 1);
        var c = row("c", "C", "", false, 2);
        model.pageSize(1);
        model.replace(List.of(c, a, b));
        model.move(1);
        assertEquals("b", model.selected().id());
        assertEquals(1, model.page());
        model.replace(List.of(c, a, b));
        assertEquals("b", model.selected().id());
        model.page(1);
        assertEquals("c", model.selected().id());
        model.replace(List.of(a));
        assertEquals("a", model.selected().id());
        assertEquals(0, model.page());
        model.move(-100);
        assertEquals("a", model.selected().id());
    }
    @Test void queryAndFavoritesComposeAndInputIsDefensivelyCopied() {
        var source = new java.util.ArrayList<>(List.of(row("a", "Cow", "", true, 0), row("b", "Sheep", "", false, 1)));
        model.replace(source);
        source.clear();
        model.query("cow");
        model.favoritesOnly(true);
        assertEquals("a", model.selected().id());
        assertEquals(2, model.count());
    }
}
