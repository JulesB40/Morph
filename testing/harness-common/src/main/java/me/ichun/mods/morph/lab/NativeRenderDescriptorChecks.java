package me.ichun.mods.morph.lab;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import me.ichun.mods.morph.client.MorphRenderSnapshots;
import me.ichun.mods.morph.model.CollectionEntry;
import me.ichun.mods.morph.model.FormDescriptor;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.SheepRenderState;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.DyeColor;

/** Client-only extraction probes; these inspect model state, not visible pixels or mesh submission. */
public final class NativeRenderDescriptorChecks {
    private NativeRenderDescriptorChecks() {}

    public static List<Map<String, Object>> verify(Avatar avatar, AvatarRenderState source) {
        var results = new ArrayList<Map<String, Object>>();
        var white = sheep(false, 0);
        var red = sheep(false, 14);
        var baby = sheep(true, 14);
        checkSheep(results, avatar, source, white, false, DyeColor.WHITE);
        checkSheep(results, avatar, source, red, false, DyeColor.RED);
        var named = new FormDescriptor(1, "minecraft:sheep", "morph:sheep", 1,
                Map.of("baby", false, "color", 0), "jeb_", Map.of(), Map.of(), null);
        var namedState = MorphRenderSnapshots.extract(avatar, source, entry(named));
        if (!(namedState instanceof SheepRenderState sheep) || !sheep.isJebSheep)
            throw new IllegalStateException("Captured jeb_ sheep must retain native rainbow state");
        checkSheep(results, avatar, source, baby, true, DyeColor.RED);
        // Revisit both cached adapters after the other variant was rendered.
        checkSheep(results, avatar, source, white, false, DyeColor.WHITE);
        checkSheep(results, avatar, source, red, false, DyeColor.RED);
        for (int size : new int[] {2, 16, 2}) {
            var descriptor = new FormDescriptor(1, "minecraft:slime", "morph:slime", 1,
                    Map.of("size", size), null, Map.of(), Map.of(), null);
            var extracted = MorphRenderSnapshots.extract(avatar, source, entry(descriptor));
            if (!(extracted instanceof SlimeRenderState slime) || slime.size != size)
                throw new IllegalStateException("Slime descriptor expected size " + size + "; got " + extracted);
            results.add(Map.of("species", "minecraft:slime", "expected_size", size, "actual_size", slime.size));
        }
        MorphRenderSnapshots.reload(null);
        checkSheep(results, avatar, source, red, false, DyeColor.RED);
        results.add(verifyPreview(red));
        return List.copyOf(results);
    }

    private static Map<String, Object> verifyPreview(FormDescriptor descriptor) {
        var state = new net.minecraft.client.renderer.state.gui.GuiRenderState();
        var graphics = new net.minecraft.client.gui.GuiGraphicsExtractor(
                net.minecraft.client.Minecraft.getInstance(), state, 50, 50);
        if (!MorphRenderSnapshots.preview(graphics, entry(descriptor), 10, 10, 110, 130, 50, 50))
            throw new IllegalStateException("Red sheep preview must extract");
        var pictures = new ArrayList<net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState>();
        state.forEachPictureInPicture(pictures::add);
        if (pictures.size() != 1 || !(pictures.getFirst() instanceof
                net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState picture)
                || !(picture.renderState() instanceof SheepRenderState sheep) || sheep.woolColor != DyeColor.RED)
            throw new IllegalStateException("Preview must contain one native red sheep state");
        if (picture.x0() != 10 || picture.y0() != 10 || picture.x1() != 110 || picture.y1() != 130)
            throw new IllegalStateException("Preview must retain its requested GUI bounds");
        if (MorphRenderSnapshots.preview(graphics, null, 10, 10, 110, 130, 50, 50))
            throw new IllegalStateException("Missing descriptor must use UI fallback");
        return Map.of("scenario", "preview_red_sheep", "expected_color", "red", "actual_color", sheep.woolColor.getName(),
                "scope", "submitted GUI picture state; not framebuffer output");
    }

    private static FormDescriptor sheep(boolean baby, int color) {
        return new FormDescriptor(1, "minecraft:sheep", "morph:sheep", 1,
                Map.of("baby", baby, "color", color), null, Map.of(), Map.of(), null);
    }

    private static CollectionEntry entry(FormDescriptor descriptor) {
        return new CollectionEntry(descriptor.entryId(), descriptor, 0, false, 0);
    }

    private static void checkSheep(List<Map<String, Object>> results, Avatar avatar, AvatarRenderState source,
            FormDescriptor descriptor, boolean baby, DyeColor color) {
        var extracted = MorphRenderSnapshots.extract(avatar, source, entry(descriptor));
        if (!(extracted instanceof SheepRenderState sheep) || sheep.isBaby != baby || sheep.woolColor != color)
            throw new IllegalStateException("Sheep descriptor expected baby=" + baby + ", color=" + color + "; got " + extracted);
        results.add(Map.of("species", "minecraft:sheep", "expected_baby", baby, "actual_baby", sheep.isBaby,
                "expected_color", color.getName(), "actual_color", sheep.woolColor.getName()));
    }
}
