package me.ichun.mods.morph.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import me.ichun.mods.morph.model.CollectionSnapshot;
import me.ichun.mods.morph.model.FormDescriptor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Collection presentation; only complete server snapshots change ownership or favorites. */
public final class MorphScreen extends Screen implements CollectionView {
    public interface Actions {
        long select(String entryId);
        long favorite(String entryId, boolean value);
        long delete(String entryId);
        long reset();
        void refresh();
    }

    private final Actions actions;
    private final Consumer<String> legacySelect;
    private final CollectionSelectorModel model = new CollectionSelectorModel();
    private final CollectionActionWait waiting = new CollectionActionWait();
    private final List<Button> rowWidgets = new ArrayList<>();
    private EditBox search;
    private Button nametagButton;
    private Button previous;
    private Button next;
    private Button selectButton;
    private Button deleteButton;
    private Button resetButton;
    private Button filterButton;
    private String active = "";
    private long revision = -1;
    private String deleteConfirmation;
    private Component feedback;
    private boolean loaded;
    private boolean allowed = true;
    private int left;
    private int panelWidth;
    private int footer;

    public MorphScreen(Actions actions) {
        super(Component.translatable("morph.selector.title"));
        this.actions = actions;
        legacySelect = null;
    }

    /** Transitional species-only callers can browse, but cannot mutate entry metadata. */
    public MorphScreen(Consumer<String> select) {
        super(Component.translatable("morph.selector.title"));
        legacySelect = select;
        actions = null;
    }

    public void update(CollectionSnapshot snapshot) {
        if (snapshot.revision() < revision) return;
        if (snapshot.revision() != revision) deleteConfirmation = null;
        revision = snapshot.revision();
        active = snapshot.activeEntryId() == null ? "" : snapshot.activeEntryId().value();
        model.replace(snapshot.entries().stream().map(entry -> {
            FormDescriptor descriptor = entry.descriptor();
            return new CollectionSelectorModel.Row(entry.id().value(), descriptor.species(),
                    MorphLabels.name(descriptor).getString(), MorphLabels.details(descriptor).getString(),
                    entry.favorite(), entry.order());
        }).toList());
        loaded = true;
        waiting.snapshot(revision);
        if (width > 0) refreshRows();
    }

    public void update(List<String> forms, String active) {
        if (actions != null) return;
        this.active = active;
        model.replace(forms.stream().map(id -> new CollectionSelectorModel.Row(id, id,
                MorphLabels.species(id).getString(), "", false, 0)).toList());
        loaded = true;
        if (width > 0) refreshRows();
    }

    public void acknowledge(long sequence, long revision, String resultCode) {
        if (!waiting.acknowledge(sequence, revision)) return;
        feedback = Component.translatable("morph.selector.result." + resultCode.toLowerCase(java.util.Locale.ROOT));
        refreshRows();
    }

    /** Call only with server-advertised selector policy. */
    public void access(boolean allowed) {
        this.allowed = allowed;
        if (!allowed) deleteConfirmation = null;
        if (width > 0) refreshRows();
    }

    @Override protected void init() {
        panelWidth = Math.min(420, width - 16);
        left = (width - panelWidth) / 2;
        footer = height - 99;
        model.pageSize(Math.max(1, (footer - 80) / 24));
        search = addRenderableWidget(new EditBox(font, left, 47, panelWidth - 112, 20,
                Component.translatable("morph.selector.search")));
        search.setMaxLength(128);
        search.setHint(Component.translatable("morph.selector.search"));
        search.setValue(model.query());
        search.setResponder(value -> { model.query(value); deleteConfirmation = null; refreshRows(); });
        filterButton = addRenderableWidget(Button.builder(filterLabel(), b -> {
            model.favoritesOnly(!model.favoritesOnly());
            deleteConfirmation = null;
            refreshRows();
        }).bounds(left + panelWidth - 108, 47, 108, 20).build());
        previous = addRenderableWidget(Button.builder(Component.translatable("morph.selector.previous"), b -> {
            model.page(-1); deleteConfirmation = null; refreshRows();
        }).bounds(left, footer, 84, 20).build());
        next = addRenderableWidget(Button.builder(Component.translatable("morph.selector.next"), b -> {
            model.page(1); deleteConfirmation = null; refreshRows();
        }).bounds(left + panelWidth - 84, footer, 84, 20).build());
        int half = (panelWidth - 4) / 2;
        selectButton = addRenderableWidget(Button.builder(Component.translatable("morph.selector.select"), b -> {
            if (deleteConfirmation != null) {
                String id = deleteConfirmation;
                deleteConfirmation = null;
                request(actions.delete(id));
            } else selectCurrent();
        }).bounds(left, footer + 24, half, 20).build());
        deleteButton = addRenderableWidget(Button.builder(Component.translatable("morph.selector.delete"), b -> {
            if (deleteConfirmation != null) deleteConfirmation = null;
            else if (model.selected() != null) deleteConfirmation = model.selected().id();
            refreshRows();
        }).bounds(left + half + 4, footer + 24, half, 20).build());
        nametagButton = addRenderableWidget(Button.builder(nametagLabel(), b -> {
            if (minecraft.player != null) minecraft.player.connection.sendCommand("morph nametag " +
                    (me.ichun.mods.morph.client.nametag.MorphNameTags.visible(minecraft.player.getUUID()) ? "off" : "on"));
        }).bounds(left, footer + 48, panelWidth - 88, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("morph.selector.refresh"), b -> {
            if (actions != null) actions.refresh();
        }).bounds(left + panelWidth - 84, footer + 48, 84, 20).build()).active = actions != null;
        resetButton = addRenderableWidget(Button.builder(Component.translatable("morph.selector.reset"), b -> {
            deleteConfirmation = null;
            if (actions != null) request(actions.reset()); else legacySelect.accept("");
        }).bounds(left, footer + 72, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + half + 4, footer + 72, half, 20).build());
        refreshRows();
    }

    private void refreshRows() {
        if (search == null) return;
        if (rowWidgets.contains(getFocused())) setFocused(null);
        rowWidgets.forEach(this::removeWidget);
        rowWidgets.clear();
        int index = 0;
        for (var row : model.pageRows()) {
            boolean selected = model.selected() != null && row.id().equals(model.selected().id());
            String marker = row.id().equals(active) ? "✓ " : selected ? "› " : "";
            Component label = Component.literal(marker + row.name() + (row.details().isEmpty() ? "" : " · " + row.details()));
            Button button = addRenderableWidget(Button.builder(label, b -> {
                model.select(row.id()); deleteConfirmation = null; refreshRows();
            }).bounds(left, 77 + index * 24, panelWidth - 28, 20).build());
            button.setTooltip(Tooltip.create(label));
            rowWidgets.add(button);
            Button star = addRenderableWidget(Button.builder(Component.literal(row.favorite() ? "★" : "☆"), b -> {
                deleteConfirmation = null;
                request(actions.favorite(row.id(), !row.favorite()));
            }).bounds(left + panelWidth - 24, 77 + index * 24, 24, 20).build());
            star.setTooltip(Tooltip.create(Component.translatable(row.favorite() ? "morph.selector.unfavorite" : "morph.selector.favorite")));
            star.active = canMutate() && actions != null;
            rowWidgets.add(star);
            index++;
        }
        previous.active = model.page() > 0;
        next.active = model.page() + 1 < model.pages();
        filterButton.setMessage(filterLabel());
        filterButton.active = actions != null;
        selectButton.active = canMutate() && model.selected() != null;
        deleteButton.active = canMutate() && actions != null && model.selected() != null;
        resetButton.active = loaded && !waiting.pending() && !active.isEmpty();
        selectButton.setMessage(Component.translatable(deleteConfirmation == null ? "morph.selector.select" : "morph.selector.delete.confirm"));
        deleteButton.setMessage(Component.translatable(deleteConfirmation == null ? "morph.selector.delete" : "gui.cancel"));
        if (deleteConfirmation != null) {
            Component warning = Component.translatable(deleteConfirmation.equals(active)
                    ? "morph.selector.delete.active_warning" : "morph.selector.delete.warning", model.selected().name());
            selectButton.setTooltip(Tooltip.create(warning));
        } else selectButton.setTooltip(null);
    }

    private boolean canMutate() { return loaded && allowed && !waiting.pending(); }
    private void request(long sequence) {
        waiting.start(sequence);
        feedback = null;
        refreshRows();
    }
    private void selectCurrent() {
        if (!canMutate() || model.selected() == null) return;
        if (actions != null) request(actions.select(model.selected().id()));
        else legacySelect.accept(model.selected().id());
    }
    private Component filterLabel() {
        return Component.translatable(model.favoritesOnly() ? "morph.selector.filter.favorites" : "morph.selector.filter.all");
    }
    private Component nametagLabel() {
        boolean visible = minecraft.player == null || me.ichun.mods.morph.client.nametag.MorphNameTags.visible(minecraft.player.getUUID());
        return Component.translatable(visible ? "morph.nametag.button.shown" : "morph.nametag.button.hidden");
    }
    public void refreshNametag() { if (nametagButton != null) nametagButton.setMessage(nametagLabel()); }

    @Override public boolean keyPressed(KeyEvent event) {
        if (search.isFocused() && event.key() != GLFW.GLFW_KEY_UP && event.key() != GLFW.GLFW_KEY_DOWN)
            return super.keyPressed(event);
        int delta = event.key() == GLFW.GLFW_KEY_UP ? -1 : event.key() == GLFW.GLFW_KEY_DOWN ? 1 : 0;
        if (delta != 0) {
            setFocused(null);
            model.move(delta); deleteConfirmation = null; refreshRows();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_PAGE_UP || event.key() == GLFW.GLFW_KEY_PAGE_DOWN) {
            model.page(event.key() == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1);
            deleteConfirmation = null; refreshRows(); return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ENTER && getFocused() == null && deleteConfirmation == null) {
            selectCurrent(); return true;
        }
        return super.keyPressed(event);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 13, 0xFFFFFFFF);
        Component status = !loaded ? Component.translatable("morph.selector.loading")
                : !allowed ? Component.translatable("morph.selector.denied")
                : waiting.pending() ? Component.translatable("morph.selector.pending")
                : deleteConfirmation != null ? Component.translatable("morph.selector.delete.question", model.selected().name())
                : feedback != null ? feedback
                : Component.translatable("morph.selector.match_count", model.visibleCount(), model.count());
        graphics.centeredText(font, font.plainSubstrByWidth(status.getString(), panelWidth), width / 2, 31, 0xFFCCCCCC);
        if (loaded && model.visibleCount() == 0) {
            Component empty = Component.translatable(model.count() == 0 ? "morph.selector.empty"
                    : model.favoritesOnly() && model.query().isBlank() ? "morph.selector.no_favorites" : "morph.selector.no_matches");
            graphics.centeredText(font, font.plainSubstrByWidth(empty.getString(), panelWidth), width / 2, 81, 0xFFBBBBBB);
        }
        graphics.centeredText(font, Component.translatable("morph.selector.page", model.page() + 1, model.pages()),
                width / 2, footer + 6, 0xFFBBBBBB);
    }
    @Override public boolean isPauseScreen() { return false; }
}
