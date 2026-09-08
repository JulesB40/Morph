package me.ichun.mods.morph.fabric;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Explicit event; ordinary appearance synchronization must never start a transformation. */
public record MorphTransition(UUID player, String fromForm, String toForm, int durationTicks) implements CustomPacketPayload {
    public MorphTransition {
        if (durationTicks < 1 || durationTicks > 1200) throw new IllegalArgumentException("Invalid morph duration");
    }
    public static final Type<MorphTransition> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morph", "transition"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MorphTransition> CODEC = new StreamCodec<>() {
        public MorphTransition decode(RegistryFriendlyByteBuf buf) {
            return new MorphTransition(buf.readUUID(), buf.readUtf(256), buf.readUtf(256), buf.readVarInt());
        }
        public void encode(RegistryFriendlyByteBuf buf, MorphTransition value) {
            buf.writeUUID(value.player()); buf.writeUtf(value.fromForm(), 256);
            buf.writeUtf(value.toForm(), 256); buf.writeVarInt(value.durationTicks());
        }
    };
    @Override public Type<MorphTransition> type() { return TYPE; }
}
