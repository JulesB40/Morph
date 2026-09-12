package me.ichun.mods.morph.fabric;

import me.ichun.mods.morph.network.AppearanceProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MorphDescriptorAppearance(AppearanceProtocol.Appearance value) implements CustomPacketPayload {
    public static final Type<MorphDescriptorAppearance> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morph", "appearance_v3"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MorphDescriptorAppearance> CODEC = new StreamCodec<>() {
        public MorphDescriptorAppearance decode(RegistryFriendlyByteBuf buf) { return new MorphDescriptorAppearance(AppearanceProtocol.readAppearance(buf)); }
        public void encode(RegistryFriendlyByteBuf buf, MorphDescriptorAppearance value) { AppearanceProtocol.writeAppearance(buf, value.value()); }
    };
    public Type<MorphDescriptorAppearance> type() { return TYPE; }
}
