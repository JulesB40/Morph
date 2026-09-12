package me.ichun.mods.morph.network;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.Unpooled;
import java.util.List;
import me.ichun.mods.morph.model.*;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class CollectionProtocolTest {
    @Test void exactEntryFavoriteAndRevisionRoundTrip() {
        var descriptor = FormDescriptor.species("minecraft:sheep");
        var entry = new CollectionEntry(descriptor.entryId(), descriptor, 7, true, 12);
        var page = new CollectionProtocol.Page(91, 0, 1, entry.id(), List.of(entry));
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            CollectionProtocol.writePage(buf, page);
            assertEquals(page, CollectionProtocol.readPage(buf));
        } finally { buf.release(); }
    }
    @Test void actionCannotCarryDescriptorOrTrailingData() {
        var descriptor = FormDescriptor.species("minecraft:cow");
        var action = new CollectionProtocol.Action(12, 21, CollectionProtocol.Opcode.DELETE, descriptor.entryId(), false);
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            CollectionProtocol.writeAction(buf, action); assertEquals(action, CollectionProtocol.readAction(buf));
            buf.clear(); CollectionProtocol.writeAction(buf, action); buf.writeByte(0);
            assertThrows(IllegalArgumentException.class, () -> CollectionProtocol.readAction(buf));
        } finally { buf.release(); }
    }
    @Test void invalidLengthsAndOpcodesAreRejectedBeforeAllocation() {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeVarInt(Integer.MAX_VALUE);
            assertThrows(IllegalArgumentException.class, () -> CollectionProtocol.readDescriptor(buf));
            buf.clear(); buf.writeLong(0); buf.writeLong(0); buf.writeByte(255);
            assertThrows(IllegalArgumentException.class, () -> CollectionProtocol.readAction(buf));
        } finally { buf.release(); }
    }
    @Test void malformedUtf8AndNonBooleanPresenceAreRejected() {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeVarInt(2); buf.writeByte(0xc0); buf.writeByte(0xaf);
            assertThrows(IllegalArgumentException.class, () -> CollectionProtocol.readDescriptor(buf));
            buf.clear(); buf.writeByte(2);
            assertThrows(IllegalArgumentException.class, () -> CollectionProtocol.readNullableId(buf));
        } finally { buf.release(); }
    }
    @Test void emptySnapshotAndActionShapeAreBounded() {
        assertEquals(1, CollectionProtocol.pages(new CollectionSnapshot(2, 0, List.of(), null)).size());
        assertThrows(IllegalArgumentException.class, () -> new CollectionProtocol.Page(0, 0, 87, null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new CollectionProtocol.Action(0, 0, CollectionProtocol.Opcode.SELECT, null, false));
        assertThrows(IllegalArgumentException.class, () -> new CollectionProtocol.Action(-1, 0, CollectionProtocol.Opcode.RESET, null, false));
    }
}
