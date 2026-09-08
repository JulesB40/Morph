package me.ichun.mods.morph.network;

import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MorphNetworkTest {
    @Test void stateRoundTripIncludesSubjectAndReset() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var state = new MorphNetwork.State(UUID.randomUUID(), "");
            MorphNetwork.State.CODEC.encode(buffer, state);
            assertEquals(state, MorphNetwork.State.CODEC.decode(buffer));
            assertFalse(buffer.isReadable());
        } finally { buffer.release(); }
    }

    @Test void collectionRoundTripPreservesOwnershipAndActiveForm() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var collection = new MorphNetwork.Collection(List.of("minecraft:pig", "minecraft:bat"), "minecraft:pig");
            MorphNetwork.Collection.CODEC.encode(buffer, collection);
            assertEquals(collection, MorphNetwork.Collection.CODEC.decode(buffer));
            assertFalse(buffer.isReadable());
        } finally { buffer.release(); }
    }

    @Test void rejectsInvalidCollectionSizesBeforeAllocatingEntries() {
        for (int count : new int[] {-1, MorphNetwork.MAX_FORMS + 1, Integer.MAX_VALUE}) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buffer.writeVarInt(count);
                assertThrows(IllegalArgumentException.class, () -> MorphNetwork.Collection.CODEC.decode(buffer));
            } finally { buffer.release(); }
        }
    }

    @Test void rejectsOversizedClientSelection() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeUtf("x".repeat(MorphNetwork.MAX_ID_LENGTH + 1));
            assertThrows(io.netty.handler.codec.DecoderException.class,
                    () -> MorphNetwork.Select.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }
}
