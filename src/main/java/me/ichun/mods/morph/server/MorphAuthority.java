package me.ichun.mods.morph.server;

import me.ichun.mods.morph.model.MorphCollection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Both loaders invoke these operations on the server thread. */
public final class MorphAuthority {
    private MorphAuthority() {}
    public interface Transport {
        void collection(ServerPlayer player);
        void appearance(ServerPlayer player);
        void transition(ServerPlayer player, String previous, String next);
    }
    private static Transport transport;
    public static void setTransport(Transport value) { transport = java.util.Objects.requireNonNull(value); }

    public static MorphSavedData data(ServerPlayer player) {
        return player.level().getServer().overworld().getDataStorage().computeIfAbsent(MorphSavedData.TYPE);
    }

    public static boolean showNametag(ServerPlayer player) { return data(player).showNametag(player.getUUID()); }
    public static int nametag(ServerPlayer player, Boolean visible) {
        data(player).setShowNametag(player.getUUID(), visible == null ? !showNametag(player) : visible);
        transport.appearance(player);
        player.sendSystemMessage(Component.translatable(showNametag(player) ? "morph.nametag.shown" : "morph.nametag.hidden"));
        return 1;
    }

    public static MorphCollection collection(ServerPlayer player) { return data(player).collection(player.getUUID()); }

    private static boolean validForm(ServerPlayer player, String form) {
        return MorphCollection.isFormId(form)
                && me.ichun.mods.morph.shape.MorphDimensions.supports(player.level(), form);
    }

    public static boolean select(ServerPlayer player, String form) {
        if (!player.isAlive() || player.isSpectator()) return fail(player, "You cannot morph right now.");
        if (!validForm(player, form)) return fail(player, "That is not a supported living entity form.");
        if (!collection(player).activeForm().equals(form) && !me.ichun.mods.morph.shape.ShapeHooks.canFit(player, form))
            return fail(player, "There is not enough room for that form here.");
        MorphCollection forms = collection(player);
        String previous = forms.activeForm();
        return switch (forms.select(form, player.level().getServer().overworld().getGameTime())) {
            case NOT_OWNED -> fail(player, "Acquire that form before selecting it.");
            case COOLDOWN -> fail(player, "Wait one second between morph selections.");
            case UNCHANGED -> { requestCollection(player); yield true; }
            case CHANGED -> {
                me.ichun.mods.morph.ability.MorphAttributes.begin(player, form);
                data(player).setDirty(); sync(player); transform(player, previous); yield true;
            }
        };
    }

    public static boolean reset(ServerPlayer player) {
        if (player.isAlive() && !me.ichun.mods.morph.shape.ShapeHooks.canFit(player, ""))
            return fail(player, "Move somewhere with enough room to return to player form.");
        String previous = collection(player).activeForm();
        boolean changed = collection(player).reset();
        if (changed && player.isAlive()) me.ichun.mods.morph.ability.MorphAttributes.begin(player, "");
        if (changed) data(player).setDirty();
        sync(player);
        if (changed && player.isAlive()) transform(player, previous);
        return true;
    }

    private static void transform(ServerPlayer player, String previous) {
        transport.transition(player, previous, collection(player).activeForm());
        me.ichun.mods.morph.model.MorphSounds.schedule(player);
    }

    public static void requestCollection(ServerPlayer player) {
        MorphCollection forms = collection(player);
        transport.collection(player);
    }

    public static void sync(ServerPlayer player) {
        MorphCollection forms = collection(player);
        if (!forms.activeForm().isEmpty() && !validForm(player, forms.activeForm())) {
            forms.reset();
            data(player).setDirty();
        }
        me.ichun.mods.morph.shape.ShapeHooks.refresh(player);
        me.ichun.mods.morph.ability.MorphAbilities.tick(player, forms.activeForm());
        transport.appearance(player);
        requestCollection(player);
    }

    private static boolean fail(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message));
        return false;
    }

    public static boolean grant(ServerPlayer player, String form) {
        if (!validForm(player, form)) return fail(player, "That is not a supported living entity form.");
        if (collection(player).unlock(form)) {
            data(player).setDirty();
            requestCollection(player);
            player.sendSystemMessage(Component.literal("Acquired morph: " + form));
            return true;
        }
        return fail(player, "You already own that form, or your collection is full (256 forms).");
    }


    public static boolean canAcquire(ServerPlayer player) {
        return player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && player.connection != null
                && player.level().getServer().getPlayerList().getPlayer(player.getUUID()) == player;
    }
    public static void died(ServerPlayer player) {
        me.ichun.mods.morph.model.MorphSounds.cancel(player);
        if (collection(player).reset()) data(player).setDirty();
        sync(player);
    }
}
