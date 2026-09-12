package me.ichun.mods.morph.network;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import me.ichun.mods.morph.model.*;
import net.minecraft.network.FriendlyByteBuf;

/** One bounded wire format shared by both loader adapters. */
public final class CollectionProtocol {
    public static final int PAGE_SIZE = 3;
    public static final int MAX_PAGES = 86;
    public static final int MAX_PACKET_BYTES = 60 * 1024;
    private CollectionProtocol() {}
    public enum Opcode { SELECT, RESET, DELETE, FAVORITE, REQUEST_SNAPSHOT }
    public enum Code { CHANGED, UNCHANGED, NOT_OWNED, UNSUPPORTED, INVALID, FULL, COOLDOWN, DENIED, NO_SPACE, STALE, CANCELED }
    public record Action(long sequence, long expectedRevision, Opcode opcode, EntryId entryId, boolean favorite) {
        public Action {
            nonnegative(sequence); nonnegative(expectedRevision); Objects.requireNonNull(opcode);
            boolean needsEntry = opcode == Opcode.SELECT || opcode == Opcode.DELETE || opcode == Opcode.FAVORITE;
            if (needsEntry != (entryId != null)) throw new IllegalArgumentException("Invalid action entry");
            if (favorite && opcode != Opcode.FAVORITE) throw new IllegalArgumentException("Unexpected favorite flag");
        }
    }
    public record Ack(long sequence, Code code, long revision, EntryId activeEntryId) {
        public Ack { nonnegative(sequence); nonnegative(revision); Objects.requireNonNull(code); }
    }
    public record Page(long revision, int index, int count, EntryId activeEntryId, List<CollectionEntry> entries) {
        public Page {
            nonnegative(revision); entries = List.copyOf(entries);
            if (count < 1 || count > MAX_PAGES || index < 0 || index >= count || entries.size() > PAGE_SIZE
                    || (entries.isEmpty() && (count != 1 || index != 0 || activeEntryId != null)))
                throw new IllegalArgumentException("Invalid snapshot page");
        }
    }
    public static List<Page> pages(CollectionSnapshot snapshot) {
        var pages = new ArrayList<Page>();
        int count = Math.max(1, (snapshot.entries().size() + PAGE_SIZE - 1) / PAGE_SIZE);
        for (int index = 0; index < count; index++) {
            int start = index * PAGE_SIZE;
            pages.add(new Page(snapshot.revision(), index, count, snapshot.activeEntryId(),
                    snapshot.entries().subList(start, Math.min(start + PAGE_SIZE, snapshot.entries().size()))));
        }
        return List.copyOf(pages);
    }
    public static Action readAction(FriendlyByteBuf buf) {
        packetLimit(buf, 256);
        long sequence = buf.readLong(), revision = buf.readLong();
        var opcode = enumeration(Opcode.values(), buf.readUnsignedByte());
        EntryId id = opcode == Opcode.SELECT || opcode == Opcode.DELETE || opcode == Opcode.FAVORITE ? readId(buf) : null;
        boolean favorite = opcode == Opcode.FAVORITE && readBoolean(buf);
        finished(buf);
        return new Action(sequence, revision, opcode, id, favorite);
    }
    public static void writeAction(FriendlyByteBuf buf, Action value) {
        buf.writeLong(value.sequence()); buf.writeLong(value.expectedRevision()); buf.writeByte(value.opcode().ordinal());
        if (value.entryId() != null) writeId(buf, value.entryId());
        if (value.opcode() == Opcode.FAVORITE) buf.writeBoolean(value.favorite());
    }
    public static Ack readAck(FriendlyByteBuf buf) {
        packetLimit(buf, 256);
        var value = new Ack(buf.readLong(), enumeration(Code.values(), buf.readUnsignedByte()), buf.readLong(), readNullableId(buf));
        finished(buf); return value;
    }
    public static void writeAck(FriendlyByteBuf buf, Ack value) {
        buf.writeLong(value.sequence()); buf.writeByte(value.code().ordinal()); buf.writeLong(value.revision()); writeNullableId(buf, value.activeEntryId());
    }
    public static Page readPage(FriendlyByteBuf buf) {
        packetLimit(buf, MAX_PACKET_BYTES);
        long revision = buf.readLong(); int index = buf.readVarInt(), count = buf.readVarInt();
        EntryId active = readNullableId(buf); int size = buf.readVarInt();
        if (size < 0 || size > PAGE_SIZE) throw new IllegalArgumentException("Invalid page entry count");
        var entries = new ArrayList<CollectionEntry>(size);
        for (int i = 0; i < size; i++) entries.add(readEntry(buf));
        finished(buf); return new Page(revision, index, count, active, entries);
    }
    public static void writePage(FriendlyByteBuf buf, Page value) {
        int start = buf.writerIndex();
        buf.writeLong(value.revision()); buf.writeVarInt(value.index()); buf.writeVarInt(value.count());
        writeNullableId(buf, value.activeEntryId()); buf.writeVarInt(value.entries().size());
        for (var entry : value.entries()) writeEntry(buf, entry);
        if (buf.writerIndex() - start > MAX_PACKET_BYTES) throw new IllegalArgumentException("Snapshot page exceeds packet limit");
    }
    public static CollectionEntry readEntry(FriendlyByteBuf buf) {
        var id = readId(buf); long revision = buf.readLong(); boolean favorite = readBoolean(buf); int order = buf.readVarInt();
        return new CollectionEntry(id, readDescriptor(buf), revision, favorite, order);
    }
    public static void writeEntry(FriendlyByteBuf buf, CollectionEntry entry) {
        writeId(buf, entry.id()); buf.writeLong(entry.revision()); buf.writeBoolean(entry.favorite()); buf.writeVarInt(entry.order());
        writeDescriptor(buf, entry.descriptor());
    }
    public static FormDescriptor readDescriptor(FriendlyByteBuf buf) { return FormDescriptor.fromJson(readText(buf, 16384)); }
    public static void writeDescriptor(FriendlyByteBuf buf, FormDescriptor descriptor) { writeText(buf, descriptor.canonicalJson(), 16384); }
    public static EntryId readId(FriendlyByteBuf buf) { return new EntryId(readText(buf, 67)); }
    public static void writeId(FriendlyByteBuf buf, EntryId id) { writeText(buf, id.value(), 67); }
    public static EntryId readNullableId(FriendlyByteBuf buf) { return readBoolean(buf) ? readId(buf) : null; }
    public static void writeNullableId(FriendlyByteBuf buf, EntryId id) { buf.writeBoolean(id != null); if (id != null) writeId(buf, id); }
    public static boolean readBoolean(FriendlyByteBuf buf) {
        int value = buf.readUnsignedByte();
        if (value > 1) throw new IllegalArgumentException("Invalid boolean");
        return value == 1;
    }
    private static String readText(FriendlyByteBuf buf, int max) {
        int size = buf.readVarInt();
        if (size < 0 || size > max || size > buf.readableBytes()) throw new IllegalArgumentException("Invalid UTF-8 length");
        byte[] bytes = new byte[size]; buf.readBytes(bytes);
        try { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (CharacterCodingException e) { throw new IllegalArgumentException("Invalid UTF-8", e); }
    }
    private static void writeText(FriendlyByteBuf buf, String text, int max) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > max) throw new IllegalArgumentException("UTF-8 value exceeds limit");
        buf.writeVarInt(bytes.length); buf.writeBytes(bytes);
    }
    public static void packetLimit(FriendlyByteBuf buf, int max) {
        if (buf.readableBytes() > max) throw new IllegalArgumentException("Packet exceeds limit");
    }
    public static void finished(FriendlyByteBuf buf) { if (buf.isReadable()) throw new IllegalArgumentException("Trailing packet data"); }
    public static void nonnegative(long value) { if (value < 0) throw new IllegalArgumentException("Negative sequence or revision"); }
    private static <T> T enumeration(T[] values, int ordinal) {
        if (ordinal < 0 || ordinal >= values.length) throw new IllegalArgumentException("Unknown action code");
        return values[ordinal];
    }
}
