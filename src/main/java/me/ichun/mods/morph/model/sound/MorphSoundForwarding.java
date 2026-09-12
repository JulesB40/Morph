package me.ichun.mods.morph.model.sound;

import com.mojang.logging.LogUtils;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import me.ichun.mods.morph.model.sound.mixin.EntitySoundAccess;
import me.ichun.mods.morph.shape.ShapeHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.block.state.BlockState;

/** Native sound queries on detached entities. No entity is spawned or ticked. */
public final class MorphSoundForwarding {
    public record Answer<T>(boolean replaced, T value) {}
    private record Cached(String form, WeakReference<LivingEntity> entity) {}
    private record Emission(SoundEvent sound, float volume, float pitch) {}
    private record Capture(Entity entity, ArrayList<Emission> sounds) {}
    private static final Map<Player, Cached> ADAPTERS = java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Capture> CAPTURE = new ThreadLocal<>();

    private MorphSoundForwarding() {}

    public static <T> Answer<T> query(Player player, Function<LivingEntity, T> query) {
        try {
            var adapter = adapter(player);
            return adapter == null ? new Answer<>(false, null) : new Answer<>(true, query.apply(adapter));
        } catch (RuntimeException failure) {
            LogUtils.getLogger().debug("Morph sound query failed; retaining player sound", failure);
            return new Answer<>(false, null);
        }
    }

    private static LivingEntity adapter(Player player) {
        String form = ShapeHooks.form(player);
        if (form == null || form.isEmpty()) return null;
        var id = Identifier.tryParse(form);
        if (id == null || !id.getNamespace().equals("minecraft")) return null;
        var cached = ADAPTERS.get(player);
        var entity = cached == null || !cached.form.equals(form) ? null : cached.entity.get();
        if (entity == null || entity.level() != player.level()) {
            var type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
            if (type == null) return null;
            var created = type.create(player.level(), EntitySpawnReason.LOAD);
            if (!(created instanceof LivingEntity living) || living instanceof Avatar) return null;
            entity = living;
            ADAPTERS.put(player, new Cached(form, new WeakReference<>(entity)));
        }
        entity.setPos(player.position());
        entity.setDeltaMovement(player.getDeltaMovement());
        entity.setSilent(player.isSilent());
        return entity;
    }

    /** Capture first, so a failed native override cannot emit half a step and then a fallback. */
    public static boolean step(Player player, BlockPos pos, BlockState block) {
        var answer = query(player, entity -> {
            var sounds = new ArrayList<Emission>();
            var previous = CAPTURE.get();
            CAPTURE.set(new Capture(entity, sounds));
            try {
                ((EntitySoundAccess) entity).morph$playStepSound(pos, block);
            } finally {
                if (previous == null) CAPTURE.remove(); else CAPTURE.set(previous);
            }
            return sounds;
        });
        if (!answer.replaced) return false;
        for (var sound : answer.value) player.playSound(sound.sound, sound.volume, sound.pitch);
        return true;
    }

    public static boolean capture(Entity entity, SoundEvent sound, float volume, float pitch) {
        var capture = CAPTURE.get();
        if (capture == null || capture.entity != entity) return false;
        if (capture.sounds.size() >= 32) throw new IllegalStateException("Too many native step sounds");
        capture.sounds.add(new Emission(sound, volume, pitch));
        return true;
    }

    public static SoundEvent consume(LivingEntity consumer, ItemStack stack, SoundEvent vanilla) {
        if (!(consumer instanceof Player player)) return vanilla;
        var answer = query(player, entity -> entity instanceof Consumable.OverrideConsumeSound override
                ? override.getConsumeSound(stack) : vanilla);
        return answer.replaced ? answer.value : vanilla;
    }
}
