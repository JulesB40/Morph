package me.ichun.mods.morph.ui;

import java.util.ArrayList;
import java.util.List;

/** Keeps adjacent favorite cards apart at the minimum supported GUI size. */
public final class FavoriteWheelLayout {
    private FavoriteWheelLayout() {}
    public record Slot(int x, int y, int width, int height) {}
    public static List<Slot> slots(int width, int height, int count) {
        if (count < 0 || count > 8) throw new IllegalArgumentException("A wheel page holds eight entries");
        int card = Math.min(110, Math.max(64, width / 4));
        int radiusX = Math.max(48, Math.min(140, (width - card - 16) / 2));
        int radiusY = Math.max(44, Math.min(86, (height - 144) / 2));
        int diagonalX = radiusX * 3 / 4;
        int diagonalY = radiusY / 2;
        int[][] offsets = {{0, -radiusY}, {diagonalX, -diagonalY}, {radiusX, 0},
                {diagonalX, diagonalY}, {0, radiusY}, {-diagonalX, diagonalY},
                {-radiusX, 0}, {-diagonalX, -diagonalY}};
        var result = new ArrayList<Slot>();
        for (int i = 0; i < count; i++)
            result.add(new Slot(width / 2 + offsets[i][0] - card / 2,
                    (height - 32) / 2 + offsets[i][1] - 10, card, 20));
        return List.copyOf(result);
    }
}
