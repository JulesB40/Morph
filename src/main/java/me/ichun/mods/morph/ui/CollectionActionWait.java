package me.ichun.mods.morph.ui;

/** An acknowledgement alone never replaces the visible authoritative collection. */
public final class CollectionActionWait {
    private long sequence = -1;
    private long requiredRevision = Long.MAX_VALUE;
    private long snapshotRevision = -1;

    public void start(long sequence) {
        if (sequence < 0 || pending()) throw new IllegalStateException("Invalid or overlapping collection request");
        this.sequence = sequence;
        requiredRevision = Long.MAX_VALUE;
    }
    public boolean acknowledge(long sequence, long revision) {
        if (this.sequence != sequence || sequence < 0) return false;
        requiredRevision = revision;
        finish();
        return true;
    }
    public void snapshot(long revision) { snapshotRevision = Math.max(snapshotRevision, revision); finish(); }
    public boolean pending() { return sequence >= 0; }
    private void finish() {
        if (pending() && requiredRevision <= snapshotRevision) sequence = -1;
    }
}
