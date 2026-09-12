package me.ichun.mods.morph.ui;

import me.ichun.mods.morph.model.CollectionEntry;
import me.ichun.mods.morph.model.CollectionSnapshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A full-size native model view for compact GUI sizes. */
public final class MorphPreviewScreen extends Screen implements CollectionView {
    private final MorphScreen parent;
    private final String entryId;
    private CollectionEntry entry;

    public MorphPreviewScreen(MorphScreen parent, CollectionEntry entry) {
        super(Component.translatable("morph.selector.preview"));
        this.parent = parent;
        this.entry = entry;
        entryId = entry.id().value();
    }
    @Override public void update(CollectionSnapshot snapshot) {
        parent.update(snapshot);
        entry = snapshot.entries().stream().filter(value -> value.id().value().equals(entryId)).findFirst().orElse(null);
    }
    @Override public void acknowledge(long sequence, long revision, String resultCode) {
        parent.acknowledge(sequence, revision, resultCode);
    }
    @Override public void snapshotResult(String resultCode) { parent.snapshotResult(resultCode); }
    @Override protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(width / 2 - 70, height - 29, 140, 20).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (entry == null) {
            graphics.centeredText(font, Component.translatable("morph.selector.result.not_owned"), width / 2, height / 2, 0xFFBBBBBB);
            return;
        }
        graphics.centeredText(font, font.plainSubstrByWidth(MorphLabels.name(entry.descriptor()).getString(), width - 24),
                width / 2, 13, 0xFFFFFFFF);
        graphics.centeredText(font, MorphLabels.details(entry.descriptor()), width / 2, 29, 0xFFCCCCCC);
        int left = Math.max(12, width / 2 - 180);
        int right = Math.min(width - 12, width / 2 + 180);
        graphics.fill(left, 46, right, height - 40, 0x55000000);
        if (!me.ichun.mods.morph.client.MorphRenderSnapshots.preview(graphics, entry, left + 4, 50,
                right - 4, height - 44, mouseX, mouseY))
            graphics.centeredText(font, Component.translatable("morph.selector.preview.unavailable"), width / 2, height / 2, 0xFFBBBBBB);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
