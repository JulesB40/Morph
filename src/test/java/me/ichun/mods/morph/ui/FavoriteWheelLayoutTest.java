package me.ichun.mods.morph.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FavoriteWheelLayoutTest {
    @Test void allEightCardsFitWithoutOverlapOrCoveringFooterAtSupportedGuiSizes() {
        for (int[] size : new int[][] {{320, 216}, {320, 240}, {427, 240}, {640, 360}, {854, 480}}) {
            var slots = FavoriteWheelLayout.slots(size[0], size[1], 8);
            for (var slot : slots) {
                assertTrue(slot.x() >= 8 && slot.x() + slot.width() <= size[0] - 8);
                assertTrue(slot.y() >= 38 && slot.y() + slot.height() < size[1] - 53);
                for (var other : slots) if (slot != other)
                    assertFalse(slot.x() < other.x() + other.width() && slot.x() + slot.width() > other.x()
                            && slot.y() < other.y() + other.height() && slot.y() + slot.height() > other.y());
            }
        }
    }
    @Test void partialAndEmptyPagesAreBounded() {
        assertTrue(FavoriteWheelLayout.slots(320, 240, 0).isEmpty());
        assertEquals(3, FavoriteWheelLayout.slots(320, 240, 3).size());
        assertThrows(IllegalArgumentException.class, () -> FavoriteWheelLayout.slots(320, 240, 9));
    }
}
