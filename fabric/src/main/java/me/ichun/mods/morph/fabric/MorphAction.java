package me.ichun.mods.morph.fabric;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Empty flap intent; the server determines eligibility and strength. */
public record MorphAction() implements CustomPacketPayload {
    public static final Type<MorphAction> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morph", "flap"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MorphAction> CODEC = StreamCodec.unit(new MorphAction());
    @Override public Type<MorphAction> type() { return TYPE; }
}
