package me.ichun.mods.morph.shape;

import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;
import me.ichun.mods.morph.model.FormDescriptor;
import me.ichun.mods.morph.server.FormCapture;
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
    private static final Map<Level, Map<String, Map<Pose, EntityDimensions>>> CACHE = new WeakHashMap<>();

    private MorphDimensions() {}

    public static boolean supports(Level level, String form) {
        return me.ichun.mods.morph.model.MorphCollection.isFormId(form)
                && poseDimensions(level, FormDescriptor.species(form)).containsKey(Pose.STANDING);
    }

    public static EntityDimensions forPose(Level level, String form, Pose pose, EntityDimensions vanilla) {
        if (!me.ichun.mods.morph.model.MorphCollection.isFormId(form)) return vanilla;
        return forPose(level, FormDescriptor.species(form), pose, vanilla);
    }

    public static EntityDimensions forPose(Level level, FormDescriptor descriptor, Pose pose, EntityDimensions vanilla) {
        if (descriptor == null) return vanilla;
        return poseDimensions(level, descriptor).getOrDefault(pose, vanilla);
    }

    private static synchronized Map<Pose, EntityDimensions> poseDimensions(Level level, FormDescriptor descriptor) {
        String form = descriptor.species();
        String cacheKey = descriptor.entryId().value();
        var shapes = CACHE.get(level);
        if (shapes != null && shapes.containsKey(cacheKey)) return shapes.get(cacheKey);
        Identifier id = Identifier.tryParse(form);
        if (id == null || !id.getNamespace().equals("minecraft")) return Map.of();
        var type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        if (type == null) return Map.of();
        if (shapes == null) {
            shapes = new java.util.LinkedHashMap<>(128, 0.75F, true);
            CACHE.put(level, shapes);
        }
        Map<Pose, EntityDimensions> dimensions = new EnumMap<>(Pose.class);
        try {
            // Match the renderer's LOAD adapter, including default slime/pufferfish dimensions.
            if (type.create(level, EntitySpawnReason.LOAD) instanceof LivingEntity entity && !(entity instanceof Avatar)) {
                if (!FormCapture.applyVariant(entity, descriptor)) return Map.of();
                for (Pose pose : Pose.values()) {
                    var nativeShape = entity.getDimensions(pose);
                    if (Float.isFinite(nativeShape.width()) && Float.isFinite(nativeShape.height())
                            && Float.isFinite(nativeShape.eyeHeight())
                            && nativeShape.width() > 0 && nativeShape.height() > 0) {
                        dimensions.put(pose, nativeShape);
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // A failed vanilla adapter retains player geometry rather than breaking a tick.
        }
        Map<Pose, EntityDimensions> result = Map.copyOf(dimensions);
        if (shapes.size() >= 1024) shapes.remove(shapes.keySet().iterator().next());
        shapes.put(cacheKey, result);
        return result;
    }
}
