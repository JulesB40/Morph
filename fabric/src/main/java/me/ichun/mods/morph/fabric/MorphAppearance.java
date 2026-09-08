package me.ichun.mods.morph.fabric;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Only the server publishes appearance; clients cannot grant or select forms with a payload. */
public record MorphAppearance(UUID player, String form) implements CustomPacketPayload {
    public static final Type<MorphAppearance> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morph", "appearance"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MorphAppearance> CODEC = new StreamCodec<>() {
        public MorphAppearance decode(RegistryFriendlyByteBuf buf) { return new MorphAppearance(buf.readUUID(), buf.readUtf(256)); }
        public void encode(RegistryFriendlyByteBuf buf, MorphAppearance value) { buf.writeUUID(value.player()); buf.writeUtf(value.form(), 256); }
    };
    @Override public Type<MorphAppearance> type() { return TYPE; }
}
