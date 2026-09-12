package me.ichun.mods.morph.fabric;

import me.ichun.mods.morph.network.AppearanceProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MorphDescriptorTransition(AppearanceProtocol.Transition value) implements CustomPacketPayload {
    public static final Type<MorphDescriptorTransition> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morph", "transition_v3"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MorphDescriptorTransition> CODEC = new StreamCodec<>() {
        public MorphDescriptorTransition decode(RegistryFriendlyByteBuf buf) { return new MorphDescriptorTransition(AppearanceProtocol.readTransition(buf)); }
        public void encode(RegistryFriendlyByteBuf buf, MorphDescriptorTransition value) { AppearanceProtocol.writeTransition(buf, value.value()); }
    };
    public Type<MorphDescriptorTransition> type() { return TYPE; }
}
