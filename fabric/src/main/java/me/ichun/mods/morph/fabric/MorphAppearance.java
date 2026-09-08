package me.ichun.mods.morph.fabric;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Only the server publishes appearance; clients cannot grant or select forms with a payload. */
public record MorphAppearance(UUID player, String form, boolean showNametag) implements CustomPacketPayload {
    public MorphAppearance(UUID player, String form) { this(player, form, true); }
    public static final Type<MorphAppearance> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morph", "appearance_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MorphAppearance> CODEC = new StreamCodec<>() {
        public MorphAppearance decode(RegistryFriendlyByteBuf buf) { return new MorphAppearance(buf.readUUID(), buf.readUtf(256), buf.readBoolean()); }
        public void encode(RegistryFriendlyByteBuf buf, MorphAppearance value) { buf.writeUUID(value.player()); buf.writeUtf(value.form(), 256); buf.writeBoolean(value.showNametag()); }
    };
    @Override public Type<MorphAppearance> type() { return TYPE; }
}
