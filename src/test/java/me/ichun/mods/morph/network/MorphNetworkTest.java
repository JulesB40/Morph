package me.ichun.mods.morph.network;

import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MorphNetworkTest {
    @Test void healthConversionRoundTripAndBoundedDecode() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var health = new MorphNetwork.Health(new me.ichun.mods.morph.ability.HealthSnapshot(18, 5.4F));
            MorphNetwork.Health.CODEC.encode(buffer, health);
            assertEquals(8, buffer.readableBytes());
            assertEquals(health, MorphNetwork.Health.CODEC.decode(buffer));
            assertFalse(buffer.isReadable());
            for (float invalid : new float[] {Float.NaN, Float.POSITIVE_INFINITY, -1, 0, 1_000_001}) {
                buffer.clear(); buffer.writeFloat(invalid); buffer.writeFloat(6);
                assertThrows(IllegalArgumentException.class, () -> MorphNetwork.Health.CODEC.decode(buffer));
                buffer.clear(); buffer.writeFloat(20); buffer.writeFloat(invalid);
                assertThrows(IllegalArgumentException.class, () -> MorphNetwork.Health.CODEC.decode(buffer));
            }
        } finally { buffer.release(); }
    }
    @Test void transitionRoundTripIncludesBothFormsAndServerDuration() {
        for (String destination : List.of("minecraft:bat", "")) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                var transition = new MorphNetwork.Transition(UUID.randomUUID(), "minecraft:pig", destination, 100);
                MorphNetwork.Transition.CODEC.encode(buffer, transition);
                assertEquals(transition, MorphNetwork.Transition.CODEC.decode(buffer));
                assertFalse(buffer.isReadable());
            } finally { buffer.release(); }
        }
    }

    @Test void rejectsInvalidTransitionDuration() {
        for (int duration : new int[] {-1, 0, 1201, Integer.MAX_VALUE}) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buffer.writeUUID(UUID.randomUUID());
                buffer.writeUtf("");
                buffer.writeUtf("minecraft:pig");
                buffer.writeVarInt(duration);
                assertThrows(IllegalArgumentException.class, () -> MorphNetwork.Transition.CODEC.decode(buffer));
            } finally { buffer.release(); }
        }
    }
    @Test void stateRoundTripIncludesSubjectAndReset() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var state = new MorphNetwork.State(UUID.randomUUID(), "", false);
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
