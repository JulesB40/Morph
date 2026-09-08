package me.ichun.mods.morph.shape;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;

/** Vanilla-only shape calculation, independent of either loader and client classes. */
public final class MorphDimensions {
    // Cache values do not retain entities/worlds. Access is synchronized for integrated servers.
    private static final Map<Level, Map<String, EntityDimensions>> CACHE = new WeakHashMap<>();

    private MorphDimensions() {}

    public static boolean supports(Level level, String form) {
        return form != null && !form.isEmpty() && baseDimensions(level, form) != null;
    }

    public static EntityDimensions forPose(Level level, String form, Pose pose, EntityDimensions vanilla) {
        if (form == null || form.isEmpty() || (pose != Pose.STANDING && pose != Pose.CROUCHING)) return vanilla;
        EntityDimensions dimensions = baseDimensions(level, form);
        if (dimensions == null) return vanilla;
        return pose == Pose.CROUCHING ? dimensions.scale(1.0F, 5.0F / 6.0F) : dimensions;
    }

    private static synchronized EntityDimensions baseDimensions(Level level, String form) {
        var shapes = CACHE.get(level);
        if (shapes != null && shapes.containsKey(form)) return shapes.get(form);
        Identifier id = Identifier.tryParse(form);
        if (id == null || !id.getNamespace().equals("minecraft")) return null;
        var type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        if (type == null) return null;
        if (shapes == null) {
            shapes = new HashMap<>();
            CACHE.put(level, shapes);
        }
        EntityDimensions dimensions = null;
        try {
            // Match the renderer's LOAD adapter, including default slime/pufferfish dimensions.
            if (type.create(level, EntitySpawnReason.LOAD) instanceof LivingEntity entity && !(entity instanceof Avatar)) {
                var base = entity.getDimensions(Pose.STANDING);
                if (Float.isFinite(base.width()) && Float.isFinite(base.height())
                        && base.width() > 0 && base.height() > 0) {
                    dimensions = EntityDimensions.scalable(base.width(), base.height()).withEyeHeight(base.eyeHeight());
                }
            }
        } catch (RuntimeException ignored) {
            // A failed vanilla adapter retains player geometry rather than breaking a tick.
        }
        shapes.put(form, dimensions);
        return dimensions;
    }
}
