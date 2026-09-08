package me.ichun.mods.morph.fabric;

import me.ichun.mods.morph.ability.HealthSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MorphHealth(HealthSnapshot value) implements CustomPacketPayload {
    public static final Type<MorphHealth> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morph", "health"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MorphHealth> CODEC = new StreamCodec<>() {
        public MorphHealth decode(RegistryFriendlyByteBuf buf) {
            return new MorphHealth(new HealthSnapshot(buf.readFloat(), buf.readFloat()));
        }
        public void encode(RegistryFriendlyByteBuf buf, MorphHealth health) {
            buf.writeFloat(health.value().before()); buf.writeFloat(health.value().after());
        }
    };
    public Type<MorphHealth> type() { return TYPE; }
}
