package me.ichun.mods.morph.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import me.ichun.mods.morph.model.FormDescriptor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.item.DyeColor;

/** Initial typed capture adapters. Neither capture nor application saves, spawns or ticks an entity. */
public final class FormCapture {
    private FormCapture() {}

    public enum Rejection { UNSUPPORTED_SPECIES, INVALID_STATE }

    public record CaptureResult(FormDescriptor descriptor, Rejection rejection, List<String> omittedFields) {
        public CaptureResult {
            if ((descriptor == null) == (rejection == null) || omittedFields == null || omittedFields.size() > 32)
                throw new IllegalArgumentException("Invalid capture result");
            omittedFields = omittedFields.stream().map(value -> FormDescriptor.normalizedText(value, 256, true)).toList();
        }
        public boolean succeeded() { return descriptor != null; }
    }

    public static CaptureResult capture(LivingEntity entity) {
        String species = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        if (!species.startsWith("minecraft:") || species.equals("minecraft:player"))
            return new CaptureResult(null, Rejection.UNSUPPORTED_SPECIES, List.of());
        try {
            String name = entity.getCustomName() == null ? null : entity.getCustomName().getString();
            if (name != null && name.isEmpty()) name = null;
            var omitted = new ArrayList<String>();
            FormDescriptor descriptor;
            if (entity instanceof Sheep sheep) {
                descriptor = new FormDescriptor(1, species, "morph:sheep", 1,
                        Map.of("baby", sheep.isBaby(), "color", sheep.getColor().getId()), name, Map.of(), Map.of(), null);
                if (sheep.isSheared()) omitted.add("sheared");
            } else if (entity instanceof Slime slime && species.equals("minecraft:slime")) {
                descriptor = new FormDescriptor(1, species, "morph:slime", 1,
                        Map.of("size", slime.getSize()), name, Map.of(), Map.of(), null);
            } else {
                descriptor = FormDescriptor.species(species);
                omitted.add("individual_variant");
                if (name != null) omitted.add("custom_name");
            }
            // Hands and ordinary armor remain the player's live equipment; this slice captures no gear.
            if (EquipmentSlot.VALUES.stream().anyMatch(slot -> !entity.getItemBySlot(slot).isEmpty())) omitted.add("equipment");
            return new CaptureResult(descriptor, null, omitted);
        } catch (IllegalArgumentException invalid) {
            return new CaptureResult(null, Rejection.INVALID_STATE, List.of());
        }
    }

    /** Returns false without applying state if the descriptor/target adapter is unavailable. */
    public static boolean applyVariant(LivingEntity target, FormDescriptor descriptor) {
        if (descriptor.adapterVersion() != 1
                || !BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString().equals(descriptor.species())) return false;
        switch (descriptor.adapter()) {
            case "morph:species" -> { }
            case "morph:sheep" -> {
                if (!(target instanceof Sheep sheep)) return false;
                boolean baby = (Boolean) descriptor.variant().get("baby");
                DyeColor color = DyeColor.byId((Integer) descriptor.variant().get("color"));
                if (sheep.isBaby() != baby) sheep.setBaby(baby);
                if (sheep.getColor() != color) sheep.setColor(color);
            }
            case "morph:slime" -> {
                if (!(target instanceof Slime slime)) return false;
                int size = (Integer) descriptor.variant().get("size");
                // LOAD constructors report size one before initializing its health/speed/damage.
                slime.setSize(size, false);
            }
            default -> { return false; }
        }
        var name = descriptor.customName() == null ? null : Component.literal(descriptor.customName());
        if (!java.util.Objects.equals(target.getCustomName(), name)) target.setCustomName(name);
        return true;
    }
}
