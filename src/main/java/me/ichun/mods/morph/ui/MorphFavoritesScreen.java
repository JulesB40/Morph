package me.ichun.mods.morph.ui;

import java.util.ArrayList;
import java.util.List;
import me.ichun.mods.morph.model.CollectionSnapshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Eight favorites per wheel page, with keyboard navigation and explicit selection. */
public final class MorphFavoritesScreen extends Screen implements CollectionView {
    private final MorphScreen.Actions actions;
    private final CollectionSelectorModel model = new CollectionSelectorModel();
    private final CollectionActionWait waiting = new CollectionActionWait();
    private final List<Button> favorites = new ArrayList<>();
    private Button previous;
    private Button next;
    private String hovered;
    private boolean keyboardSelection;
    private boolean loaded;
    private boolean allowed = true;
    private boolean released;
    private boolean successful;
    private Component feedback;

    public MorphFavoritesScreen(MorphScreen.Actions actions) {
        super(Component.translatable("morph.radial.title"));
        this.actions = actions;
        model.pageSize(8);
        model.favoritesOnly(true);
    }
    @Override public void update(CollectionSnapshot snapshot) {
        model.replace(snapshot.entries().stream().map(entry -> new CollectionSelectorModel.Row(
                entry.id().value(), entry.descriptor().species(), MorphLabels.name(entry.descriptor()).getString(),
                MorphLabels.details(entry.descriptor()).getString(), entry.favorite(), entry.order())).toList());
        loaded = true;
        waiting.snapshot(snapshot.revision());
        if (successful && !waiting.pending()) onClose();
        else if (width > 0) rebuildFavorites();
    }
    @Override public void acknowledge(long sequence, long revision, String resultCode) {
        boolean accepted = resultCode.equals("CHANGED") || resultCode.equals("UNCHANGED");
        if (!(accepted ? waiting.acknowledge(sequence, revision) : waiting.reject(sequence))) return;
        successful = resultCode.equals("CHANGED") || resultCode.equals("UNCHANGED");
        feedback = Component.translatable("morph.selector.result." + resultCode.toLowerCase(java.util.Locale.ROOT));
        if (successful && !waiting.pending()) onClose();
        else rebuildFavorites();
    }
    @Override public void snapshotResult(String resultCode) {
        if (resultCode.equals("UNCHANGED")) { allowed = true; feedback = null; }
        else {
            if (resultCode.equals("DENIED")) allowed = false;
            feedback = Component.translatable("morph.selector.result." + resultCode.toLowerCase(java.util.Locale.ROOT));
        }
        rebuildFavorites();
    }
    @Override protected void init() {
        int center = width / 2;
        previous = addRenderableWidget(Button.builder(Component.translatable("morph.selector.previous"), button -> {
            model.page(-1); keyboardSelection = false; rebuildFavorites();
        }).bounds(center - 126, height - 53, 82, 20).build());
        next = addRenderableWidget(Button.builder(Component.translatable("morph.selector.next"), button -> {
            model.page(1); keyboardSelection = false; rebuildFavorites();
        }).bounds(center + 44, height - 53, 82, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("morph.radial.collection"), button -> CollectionClientUi.open(actions, false))
                .bounds(center - 126, height - 29, 164, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(center + 42, height - 29, 84, 20).build());
        rebuildFavorites();
    }
    private void rebuildFavorites() {
        if (previous == null) return;
        if (favorites.contains(getFocused())) setFocused(null);
        favorites.forEach(this::removeWidget);
        favorites.clear();
        hovered = null;
        var rows = model.pageRows();
        var slots = FavoriteWheelLayout.slots(width, height, rows.size());
        for (int index = 0; index < rows.size(); index++) {
            var row = rows.get(index);
            var slot = slots.get(index);
            var label = Component.literal(row.name() + (row.details().isEmpty() ? "" : " · " + row.details()));
            var button = addRenderableWidget(Button.builder(label, b -> choose(row.id()))
                    .bounds(slot.x(), slot.y(), slot.width(), slot.height()).build());
            button.setTooltip(Tooltip.create(Component.literal(row.name() + (row.details().isEmpty() ? "" : " · " + row.details()))));
            button.active = loaded && allowed && !waiting.pending();
            favorites.add(button);
        }
        previous.active = !waiting.pending() && model.page() > 0;
        next.active = !waiting.pending() && model.page() + 1 < model.pages();
    }
    private void choose(String id) {
        if (!loaded || !allowed || waiting.pending() || id == null) return;
        waiting.start(actions.select(id));
        feedback = null;
        rebuildFavorites();
    }
    /** A key release in the empty center cancels without changing the form. */
    public void releaseSelection() {
        if (released) return;
        released = true;
        if (waiting.pending()) return;
        String id = keyboardSelection && model.selected() != null ? model.selected().id() : hovered;
        if (id == null) onClose(); else choose(id);
    }
    @Override public boolean keyReleased(KeyEvent event) {
        var key = net.minecraft.client.KeyMapping.get("key.morph.favorites");
        if (key != null && key.matches(event)) { releaseSelection(); return true; }
        return super.keyReleased(event);
    }
    @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        var key = net.minecraft.client.KeyMapping.get("key.morph.favorites");
        if (key != null && key.matchesMouse(event)) { releaseSelection(); return true; }
        return super.mouseReleased(event);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        int delta = event.key() == GLFW.GLFW_KEY_LEFT || event.key() == GLFW.GLFW_KEY_UP ? -1
                : event.key() == GLFW.GLFW_KEY_RIGHT || event.key() == GLFW.GLFW_KEY_DOWN ? 1 : 0;
        if (delta != 0 && !waiting.pending()) {
            model.move(delta); keyboardSelection = true; rebuildFavorites(); return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ENTER && keyboardSelection && model.selected() != null) {
            choose(model.selected().id()); return true;
        }
        return super.keyPressed(event);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
        Component status = waiting.pending() ? Component.translatable("morph.selector.pending") : feedback != null ? feedback
                : !loaded ? Component.translatable("morph.selector.loading")
                : model.visibleCount() == 0 ? Component.translatable("morph.radial.empty") : Component.translatable("morph.radial.hint");
        graphics.centeredText(font, font.plainSubstrByWidth(status.getString(), width - 16), width / 2, 29, 0xFFCCCCCC);
        hovered = null;
        for (int index = 0; index < favorites.size(); index++) {
            Button button = favorites.get(index);
            var row = model.pageRows().get(index);
            if (button.isMouseOver(mouseX, mouseY)) hovered = row.id();
            if (keyboardSelection && model.selected() != null && row.id().equals(model.selected().id()))
                graphics.outline(button.getX() - 1, button.getY() - 1, button.getWidth() + 2, button.getHeight() + 2, 0xFFFFDD77);
        }
        graphics.centeredText(font, Component.translatable("morph.selector.page", model.page() + 1, model.pages()), width / 2, height - 47, 0xFFBBBBBB);
    }
    @Override public boolean isPauseScreen() { return false; }
}


