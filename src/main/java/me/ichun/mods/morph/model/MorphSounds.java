package me.ichun.mods.morph.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Server-only timing: center the original three-second samples within the five-second morph. */
public final class MorphSounds {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("morph", "morph");
    public static final SoundEvent EVENT = SoundEvent.createVariableRangeEvent(ID);
    public static final int DURATION_TICKS = 100;
    private static final Map<UUID, Long> PENDING = new HashMap<>();
    private MorphSounds() {}

    public static void schedule(ServerPlayer player) {
        PENDING.put(player.getUUID(), player.level().getServer().overworld().getGameTime() + 20);
    }

    public static void cancel(ServerPlayer player) { PENDING.remove(player.getUUID()); }

    /** Read-only server timing diagnostic; absent after playback or cancellation. */
    public static java.util.OptionalLong scheduledTick(ServerPlayer player) {
        Long tick = PENDING.get(player.getUUID());
        return tick == null ? java.util.OptionalLong.empty() : java.util.OptionalLong.of(tick);
    }

    public static void tick(ServerPlayer player) {
        Long due = PENDING.get(player.getUUID());
        if (due == null) return;
        if (!player.isAlive() || player.isRemoved()) { cancel(player); return; }
        if (player.level().getServer().overworld().getGameTime() >= due) {
            cancel(player);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), EVENT,
                    SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }
}
