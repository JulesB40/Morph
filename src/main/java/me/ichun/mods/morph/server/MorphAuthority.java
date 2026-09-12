package me.ichun.mods.morph.server;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.UUID;
import me.ichun.mods.morph.model.*;
import me.ichun.mods.morph.config.MorphPolicies;
import me.ichun.mods.morph.api.MorphEvents;
import me.ichun.mods.morph.network.CollectionProtocol;
import me.ichun.mods.morph.network.CollectionProtocol.Code;
import me.ichun.mods.morph.network.AppearanceProtocol;
import me.ichun.mods.morph.shape.ShapeHooks;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Shared server-thread validation, mutation and publication ordering for both loaders. */
public final class MorphAuthority {
    private MorphAuthority() {}
    public interface Transport {
        void collection(ServerPlayer player);
        void appearance(ServerPlayer player);
        void transition(ServerPlayer player, CollectionEntry previous, CollectionEntry next);
    }
    private static Transport transport;
    private static final MorphEvents events = new MorphEvents();
    public static MorphEvents events() { return events; }
    private static boolean allowed(MorphEvents.Action action, UUID actor, ServerPlayer target, String species) {
        return events.before(new MorphEvents.BeforeAction(action, actor, target.getUUID(), species, MorphPolicies.current().revision())).allowed();
    }
    private static final Map<ServerPlayer, Session> sessions = new WeakHashMap<>();
    private static final class Session {
        final UUID epoch = UUID.randomUUID();
        long appearance, generation, lastSequence = -1, window = Long.MIN_VALUE, lastSnapshot = Long.MIN_VALUE;
        int requests;
    }
    public static void startConfiguration(Object server, java.nio.file.Path directory) {
        var result = me.ichun.mods.morph.config.MorphConfiguration.start(server, directory);
        if (!result.accepted()) com.mojang.logging.LogUtils.getLogger().warn("Morph configuration rejected: {}", result.errors());
    }
    public static void setTransport(Transport value) {
        transport = java.util.Objects.requireNonNull(value);
        MorphPolicies.registerMode(me.ichun.mods.morph.config.MorphPolicySnapshot.ServerMode.BIOMASS);
    }
    private static Session session(ServerPlayer player) { return sessions.computeIfAbsent(player, ignored -> new Session()); }
    private static long tick(ServerPlayer player) { return player.level().getServer().overworld().getGameTime(); }
    public static MorphSavedData data(ServerPlayer player) {
        return MorphSavedData.load(player.level().getServer());
    }
    public static MorphCollection collection(ServerPlayer player) { return data(player).collection(player.getUUID()); }
    public static CollectionEntry active(ServerPlayer player) { return collection(player).entry(collection(player).activeEntryId()); }
    public static boolean showNametag(ServerPlayer player) { return data(player).showNametag(player.getUUID()); }
    public static AppearanceProtocol.Appearance appearance(ServerPlayer player) {
        var session = session(player);
        return new AppearanceProtocol.Appearance(player.getUUID(), session.epoch, session.appearance++, showNametag(player), active(player));
    }
    public static AppearanceProtocol.Appearance departure(ServerPlayer player) {
        var session = session(player);
        return new AppearanceProtocol.Appearance(player.getUUID(), session.epoch, session.appearance++, true, null);
    }
    public static AppearanceProtocol.Transition transition(ServerPlayer player, CollectionEntry from, CollectionEntry to) {
        var session = session(player);
        return new AppearanceProtocol.Transition(player.getUUID(), session.epoch, session.generation++, tick(player),
                MorphPolicies.current().durationTicks(), from, to);
    }
    public static void disconnected(ServerPlayer player) { sessions.remove(player); }
    public static int nametag(ServerPlayer player, Boolean visible) {
        data(player).setShowNametag(player.getUUID(), visible == null ? !showNametag(player) : visible);
        publish(() -> transport.appearance(player));
        player.sendSystemMessage(Component.translatable(showNametag(player) ? "morph.nametag.shown" : "morph.nametag.hidden"));
        return 1;
    }
    private static boolean validForm(ServerPlayer player, String form) {
        return MorphCollection.isFormId(form) && me.ichun.mods.morph.shape.MorphDimensions.supports(player.level(), form);
    }
    private static boolean supported(ServerPlayer player, FormDescriptor descriptor) {
        if (!validForm(player, descriptor.species()) || descriptor.adapterVersion() != 1) return false;
        return switch (descriptor.adapter()) {
            case "morph:species" -> descriptor.variant().isEmpty() && descriptor.profile() == null;
            case "morph:sheep" -> descriptor.species().equals("minecraft:sheep");
            case "morph:slime" -> descriptor.species().equals("minecraft:slime") || descriptor.species().equals("minecraft:magma_cube");
            default -> false;
        };
    }
    private static boolean canChange(ServerPlayer player) { return player.isAlive() && !player.isRemoved() && !player.isSpectator(); }
    public static boolean select(ServerPlayer player, String form) { return select(player.getUUID(), player, form); }
    public static boolean select(UUID actor, ServerPlayer player, String form) {
        if (!validForm(player, form)) return report(player, Code.UNSUPPORTED);
        var forms = collection(player);
        var species = FormDescriptor.species(form).entryId();
        var id = forms.entry(species) != null ? species : forms.entries().stream().filter(e -> e.descriptor().species().equals(form))
                .map(CollectionEntry::id).min(EntryId::compareTo).orElse(null);
        return report(player, selectEntry(actor, player, id));
    }
    public static Code selectEntry(ServerPlayer player, EntryId id) { return selectEntry(player.getUUID(), player, id); }
    public static Code selectEntry(UUID actor, ServerPlayer player, EntryId id) {
        if (!canChange(player)) return Code.DENIED;
        var forms = collection(player); var entry = forms.entry(id);
        if (entry == null) return Code.NOT_OWNED;
        if (!supported(player, entry.descriptor())) return Code.UNSUPPORTED;
        if (!MorphPolicies.current().canMorph(player.getUUID(), entry.descriptor().species())) return Code.DENIED;
        if (id.equals(forms.activeEntryId())) return Code.UNCHANGED;
        if (!ShapeHooks.canFit(player, entry.descriptor())) return Code.NO_SPACE;
        if (!allowed(MorphEvents.Action.SELECT, actor, player, entry.descriptor().species())) return Code.CANCELED;
        var previous = active(player);
        var charge = data(player).biomass(player.getUUID()).charge(me.ichun.mods.morph.progression.BiomassRuntime.DEFAULTS,
                biomassEnabled(player), me.ichun.mods.morph.progression.BiomassDefinitions.Action.MORPH);
        if (!charge.accepted()) return Code.DENIED;
        var result = forms.select(id, tick(player));
        if (result == MorphCollection.SelectionResult.CHANGED) {
            if (charge.status() == me.ichun.mods.morph.progression.BiomassLedger.Status.CHANGED
                    && !data(player).commitBiomass(player.getUUID(), charge.before().revision(), charge.after()))
                throw new IllegalStateException("Biomass revision changed inside server-thread selection commit");
            changedForm(player, previous); return Code.CHANGED;
        }
        return Code.valueOf(result.name());
    }
    public static boolean reset(ServerPlayer player) { return reset(player.getUUID(), player); }
    public static boolean reset(UUID actor, ServerPlayer player) { return report(player, resetEntry(actor, player)); }
    public static Code resetEntry(ServerPlayer player) { return resetEntry(player.getUUID(), player); }
    public static Code resetEntry(UUID actor, ServerPlayer player) {
        if (collection(player).activeEntryId() == null) return Code.UNCHANGED;
        if (player.isAlive() && !ShapeHooks.canFit(player, (FormDescriptor) null)) return Code.NO_SPACE;
        if (!allowed(MorphEvents.Action.RESET, actor, player, "")) return Code.CANCELED;
        var previous = active(player);
        if (!collection(player).reset()) return Code.UNCHANGED;
        changedForm(player, previous); return Code.CHANGED;
    }
    public static Code deleteEntry(ServerPlayer player, EntryId id) { return deleteEntry(player.getUUID(), player, id); }
    public static Code deleteEntry(UUID actor, ServerPlayer player, EntryId id) {
        if (!canChange(player)) return Code.DENIED;
        var forms = collection(player); var previous = active(player);
        if (forms.entry(id) == null) return Code.NOT_OWNED;
        boolean deletingActive = id.equals(forms.activeEntryId());
        if (deletingActive && !ShapeHooks.canFit(player, (FormDescriptor) null)) return Code.NO_SPACE;
        if (!allowed(MorphEvents.Action.DELETE, actor, player, forms.entry(id).descriptor().species())) return Code.CANCELED;
        if (deletingActive && !allowed(MorphEvents.Action.RESET, actor, player, "")) return Code.CANCELED;
        var result = forms.delete(id, deletingActive);
        if (result == MorphCollection.MutationResult.CHANGED) {
            if (deletingActive) changedForm(player, previous);
            else { data(player).setDirty(); requestCollection(player); }
        }
        return Code.valueOf(result.name());
    }
    public static Code favoriteEntry(ServerPlayer player, EntryId id, boolean favorite) {
        if (!canChange(player)) return Code.DENIED;
        var result = collection(player).favorite(id, favorite);
        if (result == MorphCollection.MutationResult.CHANGED) { data(player).setDirty(); requestCollection(player); }
        return Code.valueOf(result.name());
    }
    /** Expected revisions and sequence numbers apply to the authenticated connection, never a supplied target UUID. */
    public static CollectionProtocol.Ack action(ServerPlayer player, CollectionProtocol.Action action) {
        var session = session(player); var forms = collection(player); long now = tick(player);
        Code code;
        if (action.opcode() != CollectionProtocol.Opcode.RESET && !MorphPolicies.current().canUseSelector(player.getUUID())) code = Code.DENIED;
        else if (action.sequence() <= session.lastSequence) code = Code.STALE;
        else {
            session.lastSequence = action.sequence();
            if (session.window == Long.MIN_VALUE || now < session.window || now - session.window >= 20) { session.window = now; session.requests = 0; }
            if (++session.requests > 20) code = Code.COOLDOWN;
            else if (action.opcode() == CollectionProtocol.Opcode.REQUEST_SNAPSHOT) {
                if (session.lastSnapshot != Long.MIN_VALUE && now >= session.lastSnapshot && now - session.lastSnapshot < 20) code = Code.COOLDOWN;
                else { session.lastSnapshot = now; requestCollection(player); code = Code.UNCHANGED; }
            } else if (action.expectedRevision() != forms.revision()) code = Code.STALE;
            else code = switch (action.opcode()) {
                case SELECT -> selectEntry(player, action.entryId());
                case RESET -> resetEntry(player);
                case DELETE -> deleteEntry(player, action.entryId());
                case FAVORITE -> favoriteEntry(player, action.entryId(), action.favorite());
                case REQUEST_SNAPSHOT -> throw new AssertionError();
            };
        }
        if (code == Code.STALE && (session.lastSnapshot == Long.MIN_VALUE || now < session.lastSnapshot || now - session.lastSnapshot >= 20)) {
            session.lastSnapshot = now; requestCollection(player);
        }
        return new CollectionProtocol.Ack(action.sequence(), code, forms.revision(), forms.activeEntryId());
    }
    private static void changedForm(ServerPlayer player, CollectionEntry previous) {
        data(player).setDirty();
        if (player.isAlive()) me.ichun.mods.morph.ability.MorphAttributes.begin(player, collection(player).activeForm());
        sync(player);
        if (player.isAlive()) {
            publish(() -> transport.transition(player, previous, active(player)));
            if (MorphPolicies.current().morphSounds()) me.ichun.mods.morph.model.MorphSounds.schedule(player, MorphPolicies.current().durationTicks());
            else me.ichun.mods.morph.model.MorphSounds.cancel(player);
        }
    }
    public static void requestCollection(ServerPlayer player) {
        if (MorphPolicies.current().canUseSelector(player.getUUID())) publish(() -> transport.collection(player));
    }
    public static void sync(ServerPlayer player) {
        var forms = collection(player);
        if (forms.activeDescriptor() != null && !supported(player, forms.activeDescriptor())) {
            forms.reset(); data(player).setDirty();
        }
        ShapeHooks.refresh(player);
        me.ichun.mods.morph.ability.MorphAbilities.tick(player, forms.activeForm());
        publish(() -> transport.appearance(player)); requestCollection(player);
    }
    public static boolean grant(ServerPlayer player, String form) { return grant(player.getUUID(), player, form); }
    public static boolean grant(UUID actor, ServerPlayer player, String form) {
        if (!validForm(player, form)) return report(player, Code.UNSUPPORTED);
        return report(player, grantDescriptor(actor, player, FormDescriptor.species(form)));
    }
    /** Trusted server capture/admin boundary. No serverbound payload accepts a descriptor. */
    public static Code grantDescriptor(ServerPlayer player, FormDescriptor descriptor) { return grantDescriptor(player.getUUID(), player, descriptor); }
    public static Code grantDescriptor(UUID actor, ServerPlayer player, FormDescriptor descriptor) {
        if (!supported(player, descriptor)) return Code.UNSUPPORTED;
        if (!MorphPolicies.current().forms().allows(descriptor.species())) return Code.DENIED;
        if (!allowed(MorphEvents.Action.ACQUIRE, actor, player, descriptor.species())) return Code.CANCELED;
        var result = collection(player).acquire(descriptor, MorphCollection.AttributeMergePolicy.KEEP_EXISTING);
        if (result == MorphCollection.MutationResult.CHANGED) { data(player).setDirty(); requestCollection(player); }
        return Code.valueOf(result.name());
    }
    public static Code capture(ServerPlayer player, net.minecraft.world.entity.LivingEntity source) {
        if (!canAcquire(player)) return Code.DENIED;
        String species = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(source.getType()).toString();
        if (!MorphPolicies.current().canAcquireByKill(player.getUUID(), species)) return Code.DENIED;
        var result = FormCapture.capture(source);
        if (!result.succeeded()) return Code.UNSUPPORTED;
        var code = grantDescriptor(player, result.descriptor());
        if (code == Code.CHANGED || code == Code.UNCHANGED || code == Code.FULL) {
            double width = source.getBbWidth();
            biomass(player).gain(player.getUUID(), biomassEnabled(player), true,
                    source.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).getBaseValue(),
                    width * width * source.getBbHeight(), player.distanceTo(source));
        }
        return code;
    }
    public static boolean biomassEnabled(ServerPlayer player) {
        return MorphPolicies.current().mode() == me.ichun.mods.morph.config.MorphPolicySnapshot.ServerMode.BIOMASS
                && MorphPolicies.current().morphPlayers().allows(player.getUUID());
    }
    public static me.ichun.mods.morph.progression.BiomassRuntime biomass(ServerPlayer player) {
        var saved = data(player);
        return new me.ichun.mods.morph.progression.BiomassRuntime(new me.ichun.mods.morph.progression.BiomassRuntime.Store() {
            public me.ichun.mods.morph.progression.BiomassLedger read(UUID id) { return saved.biomass(id); }
            public boolean commit(UUID id, long revision, me.ichun.mods.morph.progression.BiomassLedger ledger) {
                return saved.commitBiomass(id, revision, ledger);
            }
        }, me.ichun.mods.morph.progression.BiomassRuntime.DEFAULTS);
    }
    public static boolean canAcquire(ServerPlayer player) {
        return canChange(player) && player.connection != null
                && player.level().getServer().getPlayerList().getPlayer(player.getUUID()) == player;
    }
    public static void died(ServerPlayer player) {
        me.ichun.mods.morph.model.MorphSounds.cancel(player);
        if (collection(player).reset()) data(player).setDirty();
        sync(player);
    }
    public static boolean report(ServerPlayer player, Code code) {
        if (code == Code.CHANGED || code == Code.UNCHANGED) { requestCollection(player); return true; }
        player.sendSystemMessage(Component.literal(switch (code) {
            case NOT_OWNED -> "Acquire that form before selecting it.";
            case NO_SPACE -> "There is not enough room for that form here.";
            case COOLDOWN -> "Wait one second between morph selections.";
            case UNSUPPORTED -> "That is not a supported living entity form.";
            case FULL -> "Your morph collection is full.";
            default -> "Morph action rejected: " + code.name().toLowerCase(java.util.Locale.ROOT);
        }));
        return false;
    }
    private static void publish(Runnable send) {
        try { send.run(); }
        catch (RuntimeException exception) {
            // Committed ownership/attributes must not be rolled back by one failed observer delivery.
            com.mojang.logging.LogUtils.getLogger().warn("Morph synchronization failed; a new snapshot can be requested", exception);
        }
    }
}
