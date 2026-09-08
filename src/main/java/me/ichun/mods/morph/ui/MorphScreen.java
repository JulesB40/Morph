package me.ichun.mods.morph.ui;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Native vanilla screen; the loader supplies the authoritative selection request. */
public final class MorphScreen extends Screen {
    private final Consumer<String> select;
    private List<String> forms = List.of();
    private String active = "";
    private boolean loaded;
    private int page;
    private int pageSize = 6;

    public MorphScreen(Consumer<String> select) {
        super(Component.translatable("morph.selector.title"));
        this.select = select;
    }

    public void update(List<String> forms, String active) {
        this.forms = List.copyOf(forms);
        this.active = active;
        loaded = true;
        if (width > 0) rebuildWidgets();
    }

    @Override
    protected void init() {
        pageSize = Math.max(1, Math.min(10, (height - 130) / 24));
        int pages = Math.max(1, (forms.size() + pageSize - 1) / pageSize);
        page = Math.min(page, pages - 1);
        int left = Math.max(4, width / 2 - 140);
        int buttonWidth = Math.min(280, width - 8);
        for (int i = page * pageSize; i < Math.min(forms.size(), (page + 1) * pageSize); i++) {
            String id = forms.get(i);
            String label = (id.equals(active) ? "✓ " : "") + id;
            addRenderableWidget(Button.builder(Component.literal(label), b -> select.accept(id))
                    .bounds(left, 55 + (i % pageSize) * 24, buttonWidth, 20).build());
        }
        int footer = Math.max(80, height - 65);
        var previous = addRenderableWidget(Button.builder(Component.translatable("morph.selector.previous"), b -> {
            page--; rebuildWidgets();
        }).bounds(left, footer, buttonWidth / 2 - 2, 20).build());
        previous.active = page > 0;
        var next = addRenderableWidget(Button.builder(Component.translatable("morph.selector.next"), b -> {
            page++; rebuildWidgets();
        }).bounds(left + buttonWidth / 2 + 2, footer, buttonWidth / 2 - 2, 20).build());
        next.active = page + 1 < pages;
        addRenderableWidget(Button.builder(Component.translatable("morph.selector.reset"), b -> select.accept(""))
                .bounds(left, footer + 25, buttonWidth / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + buttonWidth / 2 + 2, footer + 25, buttonWidth / 2 - 2, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 15, 0xFFFFFFFF);
        Component status = !loaded ? Component.translatable("morph.selector.loading")
                : forms.isEmpty() ? Component.translatable("morph.selector.empty")
                : Component.translatable("morph.selector.count", forms.size(), page + 1,
                    Math.max(1, (forms.size() + pageSize - 1) / pageSize));
        graphics.centeredText(font, status, width / 2, 34, 0xFFBBBBBB);
    }

    @Override public boolean isPauseScreen() { return false; }
}
