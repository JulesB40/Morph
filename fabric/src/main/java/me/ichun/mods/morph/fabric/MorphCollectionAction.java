package me.ichun.mods.morph.fabric;

import me.ichun.mods.morph.network.CollectionProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MorphCollectionAction(CollectionProtocol.Action value) implements CustomPacketPayload {
    public static final Type<MorphCollectionAction> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morph", "collection_action_v3"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MorphCollectionAction> CODEC = new StreamCodec<>() {
        public MorphCollectionAction decode(RegistryFriendlyByteBuf buf) { return new MorphCollectionAction(CollectionProtocol.readAction(buf)); }
        public void encode(RegistryFriendlyByteBuf buf, MorphCollectionAction value) { CollectionProtocol.writeAction(buf, value.value()); }
    };
    public Type<MorphCollectionAction> type() { return TYPE; }
}
