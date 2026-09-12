package me.ichun.mods.morph.client;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import me.ichun.mods.morph.model.*;
import me.ichun.mods.morph.network.CollectionProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClientCollectionStateTest {
    @BeforeEach void clear() { ClientCollectionState.clear(); }
    private static CollectionEntry entry(String species, int order) {
        var descriptor = FormDescriptor.species(species);
        return new CollectionEntry(descriptor.entryId(), descriptor, 0, false, order);
    }
    @Test void publishesOnlyWhenAllPagesArriveAndKeepsOldSnapshotWhilePending() {
        var cow = entry("minecraft:cow", 0); var sheep = entry("minecraft:sheep", 1);
        assertFalse(ClientCollectionState.hasSnapshot());
        assertTrue(ClientCollectionState.accept(new CollectionProtocol.Page(1, 0, 1, cow.id(), List.of(cow)), 0));
        assertTrue(ClientCollectionState.hasSnapshot());
        assertFalse(ClientCollectionState.accept(new CollectionProtocol.Page(2, 1, 2, sheep.id(), List.of(sheep)), 1));
        assertEquals(cow.id(), ClientCollectionState.snapshot().activeEntryId());
        assertTrue(ClientCollectionState.accept(new CollectionProtocol.Page(2, 0, 2, sheep.id(), List.of(cow)), 2));
        assertEquals(List.of(cow, sheep), ClientCollectionState.snapshot().entries());
        assertEquals(sheep.id(), ClientCollectionState.snapshot().activeEntryId());
    }
    @Test void conflictingHeadersAndDuplicatePagesDoNotPublish() {
        var cow = entry("minecraft:cow", 0);
        var first = new CollectionProtocol.Page(1, 0, 2, cow.id(), List.of(cow));
        assertFalse(ClientCollectionState.accept(first, 0));
        assertFalse(ClientCollectionState.accept(first, 1));
        assertFalse(ClientCollectionState.hasSnapshot());
        assertFalse(ClientCollectionState.accept(first, 2));
        assertFalse(ClientCollectionState.accept(new CollectionProtocol.Page(1, 1, 2, null, List.of(entry("minecraft:pig", 1))), 3));
        assertFalse(ClientCollectionState.hasSnapshot());
    }
    @Test void rejectsDuplicateIdsAndUnownedActiveAcrossPages() {
        var cow = entry("minecraft:cow", 0);
        assertFalse(ClientCollectionState.accept(new CollectionProtocol.Page(1, 0, 2, null, List.of(cow)), 0));
        assertFalse(ClientCollectionState.accept(new CollectionProtocol.Page(1, 1, 2, null, List.of(cow)), 1));
        assertFalse(ClientCollectionState.accept(new CollectionProtocol.Page(2, 0, 1, entry("minecraft:pig", 1).id(), List.of(cow)), 2));
        assertFalse(ClientCollectionState.hasSnapshot());
    }
    @Test void expirationAndOlderRevisionNeverReplacePublishedState() {
        var cow = entry("minecraft:cow", 0); var sheep = entry("minecraft:sheep", 1);
        assertTrue(ClientCollectionState.accept(new CollectionProtocol.Page(4, 0, 1, null, List.of(cow)), 0));
        assertFalse(ClientCollectionState.accept(new CollectionProtocol.Page(5, 0, 2, null, List.of(cow)), 1));
        assertFalse(ClientCollectionState.accept(new CollectionProtocol.Page(5, 1, 2, null, List.of(sheep)), 6_000_000_000L));
        assertFalse(ClientCollectionState.accept(new CollectionProtocol.Page(3, 0, 1, null, List.of(cow)), 6_000_000_001L));
        assertEquals(4, ClientCollectionState.snapshot().revision());
    }
    @Test void acknowledgementDoesNotChangeOwnership() {
        var cow = entry("minecraft:cow", 0);
        ClientCollectionState.acknowledge(new CollectionProtocol.Ack(4, CollectionProtocol.Code.CHANGED, 5, cow.id()));
        assertFalse(ClientCollectionState.hasSnapshot());
        assertTrue(ClientCollectionState.snapshot().entries().isEmpty());
        ClientCollectionState.acknowledge(new CollectionProtocol.Ack(3, CollectionProtocol.Code.DENIED, 0, null));
        assertEquals(4, ClientCollectionState.lastAck().sequence());
    }
}
