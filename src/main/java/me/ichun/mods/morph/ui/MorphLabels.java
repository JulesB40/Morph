package me.ichun.mods.morph.ui;

import java.util.ArrayList;
import me.ichun.mods.morph.model.FormDescriptor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;

public final class MorphLabels {
    private MorphLabels() {}
    public static Component species(String id) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(id))
                .map(type -> type.getDescription())
                .orElseGet(() -> Component.translatable("morph.selector.unavailable", id));
    }
    public static Component name(FormDescriptor descriptor) {
        Component species = species(descriptor.species());
        return descriptor.customName() == null ? species
                : Component.translatable("morph.selector.named", descriptor.customName(), species);
    }
    public static Component details(FormDescriptor descriptor) {
        var parts = new ArrayList<Component>();
        var variant = descriptor.variant();
        if (variant.get("baby") instanceof Boolean baby)
            parts.add(Component.translatable(baby ? "morph.variant.baby" : "morph.variant.adult"));
        if (variant.get("color") instanceof Integer color && color >= 0 && color < 16)
            parts.add(Component.translatable("color.minecraft." + DyeColor.byId(color).getName()));
        if (variant.get("size") instanceof Integer size)
            parts.add(Component.translatable("morph.variant.size", size));
        var result = Component.empty();
        for (Component part : parts) {
            if (!result.getString().isEmpty()) result.append(" · ");
            result.append(part);
        }
        return result;
    }
}
