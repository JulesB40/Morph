package me.ichun.mods.morph.network;

import java.util.List;
import java.util.UUID;
import me.ichun.mods.morph.server.MorphService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Direction-specific protocol. Only the server can publish forms or collections. */
public final class MorphNetwork {
    public static final int MAX_FORMS = me.ichun.mods.morph.model.MorphCollection.MAX_FORMS;
    public static final int MAX_ID_LENGTH = me.ichun.mods.morph.model.MorphCollection.MAX_ID_LENGTH;

    private MorphNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        registrar.playToClient(State.TYPE, State.CODEC);
        registrar.playToClient(Transition.TYPE, Transition.CODEC);
        registrar.playToClient(Collection.TYPE, Collection.CODEC);
        registrar.playToServer(Select.TYPE, Select.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                if (payload.formId().isEmpty()) MorphService.reset(player);
                else MorphService.select(player, payload.formId());
            }
        });
        registrar.playToServer(RequestCollection.TYPE, RequestCollection.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) MorphService.requestCollection(player);
        });
    }

    public static void sendState(ServerPlayer recipient, UUID subject, String formId) {
        if (recipient.connection != null && recipient.connection.hasChannel(State.TYPE))
            PacketDistributor.sendToPlayer(recipient, new State(subject, formId));
    }

    public static void broadcastState(ServerPlayer subject, String formId) {
        PacketDistributor.sendToPlayersTrackingEntity(subject, new State(subject.getUUID(), formId));
        sendState(subject, subject.getUUID(), formId);
    }

    public static void sendCollection(ServerPlayer recipient, List<String> forms, String activeForm) {
        if (recipient.connection != null && recipient.connection.hasChannel(Collection.TYPE))
            PacketDistributor.sendToPlayer(recipient, new Collection(forms, activeForm));
    }

    public static void broadcastTransition(ServerPlayer subject, String fromForm, String toForm) {
        var payload = new Transition(subject.getUUID(), fromForm, toForm,
                me.ichun.mods.morph.model.MorphSounds.DURATION_TICKS);
        PacketDistributor.sendToPlayersTrackingEntity(subject, payload);
        if (subject.connection != null && subject.connection.hasChannel(Transition.TYPE))
            PacketDistributor.sendToPlayer(subject, payload);
    }

    public record Transition(UUID playerId, String fromForm, String toForm, int durationTicks) implements CustomPacketPayload {
        public Transition {
            if (durationTicks < 1 || durationTicks > 1200) throw new IllegalArgumentException("Invalid morph duration");
        }
        public static final Type<Transition> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morph", "transition"));
        public static final StreamCodec<FriendlyByteBuf, Transition> CODEC = new StreamCodec<>() {
            public Transition decode(FriendlyByteBuf buf) {
                return new Transition(buf.readUUID(), buf.readUtf(MAX_ID_LENGTH), buf.readUtf(MAX_ID_LENGTH), buf.readVarInt());
            }
            public void encode(FriendlyByteBuf buf, Transition value) {
                buf.writeUUID(value.playerId());
                buf.writeUtf(value.fromForm(), MAX_ID_LENGTH);
                buf.writeUtf(value.toForm(), MAX_ID_LENGTH);
                buf.writeVarInt(value.durationTicks());
            }
        };
        public Type<Transition> type() { return TYPE; }
    }

    public record State(UUID playerId, String formId) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morph", "state"));
        public static final StreamCodec<FriendlyByteBuf, State> CODEC = new StreamCodec<>() {
            public State decode(FriendlyByteBuf buf) { return new State(buf.readUUID(), buf.readUtf(MAX_ID_LENGTH)); }
            public void encode(FriendlyByteBuf buf, State value) {
                buf.writeUUID(value.playerId());
                buf.writeUtf(value.formId(), MAX_ID_LENGTH);
            }
        };
        public Type<State> type() { return TYPE; }
    }

    public record Collection(List<String> forms, String activeForm) implements CustomPacketPayload {
        public Collection {
            forms = List.copyOf(forms);
            if (forms.size() > MAX_FORMS) throw new IllegalArgumentException("Too many morph forms");
        }
        public static final Type<Collection> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morph", "collection"));
        public static final StreamCodec<FriendlyByteBuf, Collection> CODEC = new StreamCodec<>() {
            public Collection decode(FriendlyByteBuf buf) {
                int count = buf.readVarInt();
                if (count < 0 || count > MAX_FORMS) throw new IllegalArgumentException("Invalid morph collection size");
                var forms = new java.util.ArrayList<String>(count);
                for (int i = 0; i < count; i++) forms.add(buf.readUtf(MAX_ID_LENGTH));
                return new Collection(forms, buf.readUtf(MAX_ID_LENGTH));
            }
            public void encode(FriendlyByteBuf buf, Collection value) {
                buf.writeVarInt(value.forms().size());
                for (String id : value.forms()) buf.writeUtf(id, MAX_ID_LENGTH);
                buf.writeUtf(value.activeForm(), MAX_ID_LENGTH);
            }
        };
        public Type<Collection> type() { return TYPE; }
    }

    public record Select(String formId) implements CustomPacketPayload {
        public static final Type<Select> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morph", "select"));
        public static final StreamCodec<FriendlyByteBuf, Select> CODEC = new StreamCodec<>() {
            public Select decode(FriendlyByteBuf buf) { return new Select(buf.readUtf(MAX_ID_LENGTH)); }
            public void encode(FriendlyByteBuf buf, Select value) { buf.writeUtf(value.formId(), MAX_ID_LENGTH); }
        };
        public Type<Select> type() { return TYPE; }
    }

    public record RequestCollection() implements CustomPacketPayload {
        public static final Type<RequestCollection> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morph", "request_collection"));
        public static final StreamCodec<FriendlyByteBuf, RequestCollection> CODEC = StreamCodec.unit(new RequestCollection());
        public Type<RequestCollection> type() { return TYPE; }
    }
}
