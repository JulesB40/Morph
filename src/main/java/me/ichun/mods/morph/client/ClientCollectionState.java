package me.ichun.mods.morph.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import me.ichun.mods.morph.model.*;
import me.ichun.mods.morph.network.CollectionProtocol;

/** Client-thread state. Only complete server snapshots replace the visible collection. */
public final class ClientCollectionState {
    private static CollectionSnapshot snapshot = new CollectionSnapshot(2, 0, java.util.List.of(), null);
    private static CollectionProtocol.Page[] pending;
    private static long pendingSince;
    private static long nextSequence;
    private static boolean hasSnapshot;
    private static CollectionProtocol.Ack lastAck;
    private ClientCollectionState() {}
    public static CollectionSnapshot snapshot() { return snapshot; }
    public static boolean hasSnapshot() { return hasSnapshot; }
    public static CollectionProtocol.Ack lastAck() { return lastAck; }
    public static CollectionProtocol.Action nextAction(CollectionProtocol.Opcode opcode, EntryId id, boolean favorite) {
        return new CollectionProtocol.Action(nextSequence++, snapshot.revision(), opcode, id, favorite);
    }
    public static void acknowledge(CollectionProtocol.Ack ack) {
        if (lastAck == null || ack.sequence() > lastAck.sequence()) lastAck = ack;
    }
    public static boolean accept(CollectionProtocol.Page page) { return accept(page, System.nanoTime()); }
    static boolean accept(CollectionProtocol.Page page, long now) {
        if (pending != null && now - pendingSince > 5_000_000_000L) pending = null;
        if (page.revision() < snapshot.revision()) return false;
        if (pending != null && page.revision() < firstPending().revision()) return false;
        if (pending == null || page.revision() > firstPending().revision()) {
            pending = new CollectionProtocol.Page[page.count()]; pendingSince = now;
        }
        var first = firstPending();
        if (pending.length != page.count() || first != null && !Objects.equals(first.activeEntryId(), page.activeEntryId())
                || pending[page.index()] != null) {
            pending = null; return false;
        }
        pending[page.index()] = page;
        var entries = new ArrayList<CollectionEntry>(); var ids = new HashSet<EntryId>();
        boolean complete = true;
        for (var part : pending) {
            if (part == null) { complete = false; continue; }
            for (var entry : part.entries()) {
                if (!ids.add(entry.id()) || entries.size() == 256) { pending = null; return false; }
                entries.add(entry);
            }
        }
        if (!complete) return false;
        try { snapshot = new CollectionSnapshot(2, page.revision(), entries, page.activeEntryId()); }
        catch (IllegalArgumentException invalid) { pending = null; return false; }
        pending = null; hasSnapshot = true; return true;
    }
    private static CollectionProtocol.Page firstPending() {
        for (var page : pending) if (page != null) return page;
        return null;
    }
    public static void clear() {
        snapshot = new CollectionSnapshot(2, 0, java.util.List.of(), null);
        pending = null; nextSequence = 0; lastAck = null; hasSnapshot = false;
    }
}
