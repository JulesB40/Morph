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
        var registrar = event.registrar("6");
        registrar.playToClient(SnapshotPage.TYPE, SnapshotPage.CODEC);
        registrar.playToClient(ActionAck.TYPE, ActionAck.CODEC);
        registrar.playToClient(DescriptorAppearance.TYPE, DescriptorAppearance.CODEC);
        registrar.playToClient(DescriptorTransition.TYPE, DescriptorTransition.CODEC);
        registrar.playToServer(CollectionAction.TYPE, CollectionAction.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                var ack = me.ichun.mods.morph.server.MorphAuthority.action(player, payload.value());
                PacketDistributor.sendToPlayer(player, new ActionAck(ack));
            }
        });
        registrar.playToServer(Flap.TYPE, Flap.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) me.ichun.mods.morph.ability.MorphActions.flap(player);
        });
        registrar.playToClient(State.TYPE, State.CODEC);
        registrar.playToClient(Transition.TYPE, Transition.CODEC);
        registrar.playToClient(Health.TYPE, Health.CODEC);
        registrar.playToClient(Collection.TYPE, Collection.CODEC);

    }

    public static void sendState(ServerPlayer recipient, UUID subject, String formId, boolean showNametag) {
        var player = recipient.level().getServer().getPlayerList().getPlayer(subject);
        if (player != null && recipient.connection != null && recipient.connection.hasChannel(DescriptorAppearance.TYPE))
            PacketDistributor.sendToPlayer(recipient, new DescriptorAppearance(me.ichun.mods.morph.server.MorphAuthority.appearance(player)));
    }

    public static void sendHealth(ServerPlayer recipient, me.ichun.mods.morph.ability.HealthSnapshot health) {
        if (recipient.connection == null || !recipient.connection.hasChannel(Health.TYPE)) return;
        // Ordered on the same connection: raise/lower the full maximum (including other
        // mods' modifiers) before assigning health so setHealth cannot clamp to a stale max.
        recipient.connection.send(new net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket(
                recipient.getId(), List.of(recipient.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH))));
        PacketDistributor.sendToPlayer(recipient, new Health(health));
    }

    public record Health(me.ichun.mods.morph.ability.HealthSnapshot value) implements CustomPacketPayload {
        public static final Type<Health> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morph", "health"));
        public static final StreamCodec<FriendlyByteBuf, Health> CODEC = new StreamCodec<>() {
            public Health decode(FriendlyByteBuf buf) {
                return new Health(new me.ichun.mods.morph.ability.HealthSnapshot(buf.readFloat(), buf.readFloat()));
            }
            public void encode(FriendlyByteBuf buf, Health value) {
                buf.writeFloat(value.value().before()); buf.writeFloat(value.value().after());
            }
        };
        public Type<Health> type() { return TYPE; }
    }

    public static void broadcastState(ServerPlayer subject, String formId) {
        var payload = new DescriptorAppearance(me.ichun.mods.morph.server.MorphAuthority.appearance(subject));
        PacketDistributor.sendToPlayersTrackingEntity(subject, payload);
        if (subject.connection != null && subject.connection.hasChannel(DescriptorAppearance.TYPE)) PacketDistributor.sendToPlayer(subject, payload);
    }
    public static void broadcastDeparture(ServerPlayer subject) {
        PacketDistributor.sendToPlayersTrackingEntity(subject, new DescriptorAppearance(me.ichun.mods.morph.server.MorphAuthority.departure(subject)));
    }
    public static void sendSnapshot(ServerPlayer recipient, me.ichun.mods.morph.model.CollectionSnapshot snapshot) {
        if (recipient.connection != null && recipient.connection.hasChannel(SnapshotPage.TYPE))
            for (var page : CollectionProtocol.pages(snapshot)) PacketDistributor.sendToPlayer(recipient, new SnapshotPage(page));
    }
    public static void broadcastDescriptorTransition(ServerPlayer subject, me.ichun.mods.morph.model.CollectionEntry from, me.ichun.mods.morph.model.CollectionEntry to) {
        var payload = new DescriptorTransition(me.ichun.mods.morph.server.MorphAuthority.transition(subject, from, to));
        PacketDistributor.sendToPlayersTrackingEntity(subject, payload);
        if (subject.connection != null && subject.connection.hasChannel(DescriptorTransition.TYPE)) PacketDistributor.sendToPlayer(subject, payload);
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

    public record State(UUID playerId, String formId, boolean showNametag) implements CustomPacketPayload {
        public State(UUID playerId, String formId) { this(playerId, formId, true); }
        public static final Type<State> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morph", "state"));
        public static final StreamCodec<FriendlyByteBuf, State> CODEC = new StreamCodec<>() {
            public State decode(FriendlyByteBuf buf) { return new State(buf.readUUID(), buf.readUtf(MAX_ID_LENGTH), buf.readBoolean()); }
            public void encode(FriendlyByteBuf buf, State value) {
                buf.writeUUID(value.playerId());
                buf.writeUtf(value.formId(), MAX_ID_LENGTH);
                buf.writeBoolean(value.showNametag());
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

    public record Flap() implements CustomPacketPayload {
        public static final Type<Flap> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morph", "flap"));
        public static final StreamCodec<FriendlyByteBuf, Flap> CODEC = StreamCodec.unit(new Flap());
        public Type<Flap> type() { return TYPE; }
    }

    public record RequestCollection() implements CustomPacketPayload {
        public static final Type<RequestCollection> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morph", "request_collection"));
        public static final StreamCodec<FriendlyByteBuf, RequestCollection> CODEC = StreamCodec.unit(new RequestCollection());
        public Type<RequestCollection> type() { return TYPE; }
    }

    public record CollectionAction(CollectionProtocol.Action value) implements CustomPacketPayload {
        public static final Type<CollectionAction> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morph", "collection_action"));
        public static final StreamCodec<FriendlyByteBuf, CollectionAction> CODEC = new StreamCodec<>() {
            public CollectionAction decode(FriendlyByteBuf buf) { return new CollectionAction(CollectionProtocol.readAction(buf)); }
            public void encode(FriendlyByteBuf buf, CollectionAction value) { CollectionProtocol.writeAction(buf, value.value()); }
        };
        public Type<CollectionAction> type() { return TYPE; }
    }

    public record SnapshotPage(CollectionProtocol.Page value) implements CustomPacketPayload {
        public static final Type<SnapshotPage> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morph", "snapshot_page"));
        public static final StreamCodec<FriendlyByteBuf, SnapshotPage> CODEC = new StreamCodec<>() {
            public SnapshotPage decode(FriendlyByteBuf buf) { return new SnapshotPage(CollectionProtocol.readPage(buf)); }
            public void encode(FriendlyByteBuf buf, SnapshotPage value) { CollectionProtocol.writePage(buf, value.value()); }
        };
        public Type<SnapshotPage> type() { return TYPE; }
    }

    public record ActionAck(CollectionProtocol.Ack value) implements CustomPacketPayload {
        public static final Type<ActionAck> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morph", "action_ack"));
        public static final StreamCodec<FriendlyByteBuf, ActionAck> CODEC = new StreamCodec<>() {
            public ActionAck decode(FriendlyByteBuf buf) { return new ActionAck(CollectionProtocol.readAck(buf)); }
            public void encode(FriendlyByteBuf buf, ActionAck value) { CollectionProtocol.writeAck(buf, value.value()); }
        };
        public Type<ActionAck> type() { return TYPE; }
    }

    public record DescriptorAppearance(AppearanceProtocol.Appearance value) implements CustomPacketPayload {
        public static final Type<DescriptorAppearance> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morph", "appearance"));
        public static final StreamCodec<FriendlyByteBuf, DescriptorAppearance> CODEC = new StreamCodec<>() {
            public DescriptorAppearance decode(FriendlyByteBuf buf) { return new DescriptorAppearance(AppearanceProtocol.readAppearance(buf)); }
            public void encode(FriendlyByteBuf buf, DescriptorAppearance value) { AppearanceProtocol.writeAppearance(buf, value.value()); }
        };
        public Type<DescriptorAppearance> type() { return TYPE; }
    }

    public record DescriptorTransition(AppearanceProtocol.Transition value) implements CustomPacketPayload {
        public static final Type<DescriptorTransition> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morph", "descriptor_transition"));
        public static final StreamCodec<FriendlyByteBuf, DescriptorTransition> CODEC = new StreamCodec<>() {
            public DescriptorTransition decode(FriendlyByteBuf buf) { return new DescriptorTransition(AppearanceProtocol.readTransition(buf)); }
            public void encode(FriendlyByteBuf buf, DescriptorTransition value) { AppearanceProtocol.writeTransition(buf, value.value()); }
        };
        public Type<DescriptorTransition> type() { return TYPE; }
    }
}
