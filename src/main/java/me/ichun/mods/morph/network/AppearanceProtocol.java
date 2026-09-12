package me.ichun.mods.morph.network;

import java.util.Objects;
import java.util.UUID;
import me.ichun.mods.morph.model.CollectionEntry;
import net.minecraft.network.FriendlyByteBuf;

/** Descriptor endpoints remain available even when an active entry is deleted. */
public final class AppearanceProtocol {
    private AppearanceProtocol() {}
    public record Appearance(UUID subject, UUID epoch, long sequence, boolean showNametag, CollectionEntry active) {
        public Appearance { Objects.requireNonNull(subject); Objects.requireNonNull(epoch); CollectionProtocol.nonnegative(sequence); }
    }
    public record Transition(UUID subject, UUID epoch, long generation, long startTick, int duration, CollectionEntry from, CollectionEntry to) {
        public Transition {
            Objects.requireNonNull(subject); Objects.requireNonNull(epoch); CollectionProtocol.nonnegative(generation);
            CollectionProtocol.nonnegative(startTick);
            if (duration < 1 || duration > 1200) throw new IllegalArgumentException("Invalid transition duration");
        }
    }
    public static Appearance readAppearance(FriendlyByteBuf buf) {
        CollectionProtocol.packetLimit(buf, CollectionProtocol.MAX_PACKET_BYTES);
        var result = new Appearance(buf.readUUID(), buf.readUUID(), buf.readLong(), CollectionProtocol.readBoolean(buf), readEndpoint(buf));
        CollectionProtocol.finished(buf); return result;
    }
    public static void writeAppearance(FriendlyByteBuf buf, Appearance value) {
        buf.writeUUID(value.subject()); buf.writeUUID(value.epoch()); buf.writeLong(value.sequence()); buf.writeBoolean(value.showNametag());
        writeEndpoint(buf, value.active());
    }
    public static Transition readTransition(FriendlyByteBuf buf) {
        CollectionProtocol.packetLimit(buf, CollectionProtocol.MAX_PACKET_BYTES);
        var result = new Transition(buf.readUUID(), buf.readUUID(), buf.readLong(), buf.readLong(), buf.readVarInt(), readEndpoint(buf), readEndpoint(buf));
        CollectionProtocol.finished(buf); return result;
    }
    public static void writeTransition(FriendlyByteBuf buf, Transition value) {
        buf.writeUUID(value.subject()); buf.writeUUID(value.epoch()); buf.writeLong(value.generation()); buf.writeLong(value.startTick()); buf.writeVarInt(value.duration());
        writeEndpoint(buf, value.from()); writeEndpoint(buf, value.to());
    }
    private static CollectionEntry readEndpoint(FriendlyByteBuf buf) { return CollectionProtocol.readBoolean(buf) ? CollectionProtocol.readEntry(buf) : null; }
    private static void writeEndpoint(FriendlyByteBuf buf, CollectionEntry entry) {
        buf.writeBoolean(entry != null); if (entry != null) CollectionProtocol.writeEntry(buf, entry);
    }
}
