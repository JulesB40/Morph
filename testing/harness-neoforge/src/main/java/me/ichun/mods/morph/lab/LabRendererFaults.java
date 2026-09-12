package me.ichun.mods.morph.lab;

/** Scoped to synchronous probe calls; ordinary frames have no active fault. */
public final class LabRendererFaults implements AutoCloseable {
    public enum Stage { EXTRACTION, SUBMISSION }
    private static final ThreadLocal<LabRendererFaults> ACTIVE = new ThreadLocal<>();
    private final Stage stage;
    private int remaining;
    private int callbacks;
    private int injected;

    private LabRendererFaults(Stage stage, int count) {
        this.stage = stage;
        remaining = count;
    }

    public static LabRendererFaults open(Stage stage, int count) {
        if (ACTIVE.get() != null || count < 1) throw new IllegalStateException("Invalid nested fault scope");
        var scope = new LabRendererFaults(stage, count);
        ACTIVE.set(scope);
        return scope;
    }

    public static void check(Stage stage) {
        LabRendererFaults scope = ACTIVE.get();
        if (scope == null || scope.stage != stage) return;
        scope.callbacks++;
        if (scope.remaining-- > 0) {
            scope.injected++;
            throw new IllegalStateException("Injected Morph Lab PigRenderer " + stage + " failure");
        }
    }

    public int callbacks() { return callbacks; }
    public int injected() { return injected; }
    public static boolean active() { return ACTIVE.get() != null; }
    @Override public void close() { ACTIVE.remove(); }
}
