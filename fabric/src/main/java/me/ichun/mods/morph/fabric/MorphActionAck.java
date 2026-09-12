package me.ichun.mods.morph.fabric;

import me.ichun.mods.morph.network.CollectionProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MorphActionAck(CollectionProtocol.Ack value) implements CustomPacketPayload {
    public static final Type<MorphActionAck> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morph", "action_ack_v3"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MorphActionAck> CODEC = new StreamCodec<>() {
        public MorphActionAck decode(RegistryFriendlyByteBuf buf) { return new MorphActionAck(CollectionProtocol.readAck(buf)); }
        public void encode(RegistryFriendlyByteBuf buf, MorphActionAck value) { CollectionProtocol.writeAck(buf, value.value()); }
    };
    public Type<MorphActionAck> type() { return TYPE; }
}
