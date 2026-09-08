package me.ichun.mods.morph.fabric;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
public record MorphOwned(List<String> forms, String active) implements CustomPacketPayload {
    public static final Type<MorphOwned> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morph", "owned"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MorphOwned> CODEC = new StreamCodec<>() {
        public MorphOwned decode(RegistryFriendlyByteBuf buf) {
            int count = buf.readVarInt();
            if (count < 0 || count > 256) throw new IllegalArgumentException("Invalid collection size");
            List<String> forms = new ArrayList<>(count);
            for (int i = 0; i < count; i++) forms.add(buf.readUtf(256));
            return new MorphOwned(List.copyOf(forms), buf.readUtf(256));
        }
        public void encode(RegistryFriendlyByteBuf buf, MorphOwned value) {
            buf.writeVarInt(value.forms().size());
            for (String form : value.forms()) buf.writeUtf(form, 256);
            buf.writeUtf(value.active(), 256);
        }
    };
    @Override public Type<MorphOwned> type() { return TYPE; }
}
