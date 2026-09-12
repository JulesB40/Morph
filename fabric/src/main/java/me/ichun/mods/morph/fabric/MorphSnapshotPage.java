package me.ichun.mods.morph.fabric;

import me.ichun.mods.morph.network.CollectionProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MorphSnapshotPage(CollectionProtocol.Page value) implements CustomPacketPayload {
    public static final Type<MorphSnapshotPage> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morph", "snapshot_page_v3"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MorphSnapshotPage> CODEC = new StreamCodec<>() {
        public MorphSnapshotPage decode(RegistryFriendlyByteBuf buf) { return new MorphSnapshotPage(CollectionProtocol.readPage(buf)); }
        public void encode(RegistryFriendlyByteBuf buf, MorphSnapshotPage value) { CollectionProtocol.writePage(buf, value.value()); }
    };
    public Type<MorphSnapshotPage> type() { return TYPE; }
}
