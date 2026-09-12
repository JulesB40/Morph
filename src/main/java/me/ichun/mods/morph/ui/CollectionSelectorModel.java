package me.ichun.mods.morph.ui;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Presentation state only: collection mutations always come from a server snapshot. */
public final class CollectionSelectorModel {
    public record Row(String id, String species, String name, String details, boolean favorite, int order) {
        public Row {
            Objects.requireNonNull(id);
            Objects.requireNonNull(species);
            Objects.requireNonNull(name);
            Objects.requireNonNull(details);
        }
    }

    private List<Row> rows = List.of();
    private List<Row> visible = List.of();
    private String query = "";
    private String selected;
    private boolean favoritesOnly;
    private int page;
    private int pageSize = 6;

    public void replace(List<Row> rows) {
        this.rows = List.copyOf(rows);
        filter();
    }
    public void query(String query) { this.query = query; page = 0; filter(); }
    public String query() { return query; }
    public void favoritesOnly(boolean value) { favoritesOnly = value; page = 0; filter(); }
    public boolean favoritesOnly() { return favoritesOnly; }
    public void pageSize(int value) { pageSize = Math.max(1, value); revealSelection(); }
    public int page() { return page; }
    public int pages() { return Math.max(1, (visible.size() + pageSize - 1) / pageSize); }
    public int count() { return rows.size(); }
    public int visibleCount() { return visible.size(); }
    public List<Row> visible() { return visible; }
    public List<Row> pageRows() {
        int start = Math.min(page * pageSize, visible.size());
        return visible.subList(start, Math.min(start + pageSize, visible.size()));
    }
    public Row selected() { return visible.stream().filter(row -> row.id().equals(selected)).findFirst().orElse(null); }
    public void select(String id) {
        if (visible.stream().anyMatch(row -> row.id().equals(id))) { selected = id; revealSelection(); }
    }
    public void move(int delta) {
        if (visible.isEmpty()) return;
        int index = indexOfSelection();
        select(visible.get(Math.clamp(index + delta, 0, visible.size() - 1)).id());
    }
    public void page(int delta) {
        page = Math.clamp(page + delta, 0, pages() - 1);
        if (!pageRows().isEmpty()) selected = pageRows().getFirst().id();
    }
    private int indexOfSelection() {
        for (int i = 0; i < visible.size(); i++) if (visible.get(i).id().equals(selected)) return i;
        return 0;
    }
    private void filter() {
        String[] terms = normalize(query).strip().split("\\s+");
        visible = rows.stream().filter(row -> !favoritesOnly || row.favorite())
                .filter(row -> {
                    String text = normalize(row.name() + " " + row.details() + " " + row.species());
                    for (String term : terms) if (!text.contains(term)) return false;
                    return true;
                })
                .sorted(Comparator.comparing((Row row) -> normalize(row.name()))
                        .thenComparingInt(Row::order).thenComparing(Row::id))
                .toList();
        if (selected() == null) selected = visible.isEmpty() ? null : visible.getFirst().id();
        revealSelection();
    }
    private void revealSelection() {
        page = visible.isEmpty() ? 0 : indexOfSelection() / pageSize;
    }
    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }
}
