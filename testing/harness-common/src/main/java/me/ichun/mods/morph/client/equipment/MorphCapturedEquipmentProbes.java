package me.ichun.mods.morph.client.equipment;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import me.ichun.mods.morph.model.FormDescriptor;
import me.ichun.mods.morph.model.FormDescriptor.CapturedEquipment;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Run after a real client has joined a loaded world; native equipment uses loaded entity tags. */
public final class MorphCapturedEquipmentProbes {
    private MorphCapturedEquipmentProbes() {}

    public static Map<String, Object> run(Avatar player) {
        List<Map<String, Object>> checks = new ArrayList<>();
        var captured = new CapturedEquipment("minecraft:wolf_armor", 10, 0x123456, true);
        var stack = MorphEquipmentRendering.capturedStack(captured, EquipmentSlot.BODY, EntityTypes.WOLF);
        check(checks, "wolf_body_item", stack.is(Items.WOLF_ARMOR), stack.getItem().toString());
        check(checks, "captured_count_one", stack.getCount() == 1, stack.getCount());
        check(checks, "captured_damage", stack.getDamageValue() == 10, stack.getDamageValue());
        var dye = stack.get(DataComponents.DYED_COLOR);
        check(checks, "captured_dye", dye != null && dye.rgb() == 0x123456, String.valueOf(dye));
        check(checks, "captured_glint", Boolean.TRUE.equals(stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)),
            String.valueOf(stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)));
        check(checks, "wrong_species_empty", MorphEquipmentRendering.capturedStack(captured, EquipmentSlot.BODY, EntityTypes.PIG).isEmpty(), "wolf armor on pig");
        check(checks, "wrong_slot_empty", MorphEquipmentRendering.capturedStack(captured, EquipmentSlot.SADDLE, EntityTypes.WOLF).isEmpty(), "body armor in saddle slot");
        check(checks, "live_slot_rejected", MorphEquipmentRendering.capturedStack(captured, EquipmentSlot.MAINHAND, EntityTypes.WOLF).isEmpty(), "captured main hand");
        check(checks, "missing_gear_empty", MorphEquipmentRendering.capturedStack(null, EquipmentSlot.BODY, EntityTypes.WOLF).isEmpty(), "null capture");
        for (var id : List.of("minecraft:missing_item", "minecraft:air", "minecraft:diamond")) {
            var invalid = MorphEquipmentRendering.capturedStack(new CapturedEquipment(id, null, null, false), EquipmentSlot.BODY, EntityTypes.WOLF);
            check(checks, "invalid_gear_" + id, invalid.isEmpty(), invalid.toString());
        }
        var damaged = MorphEquipmentRendering.capturedStack(new CapturedEquipment("minecraft:wolf_armor", 1_000_000, null, false),
            EquipmentSlot.BODY, EntityTypes.WOLF);
        check(checks, "native_damage_bound", damaged.getDamageValue() == damaged.getMaxDamage(), damaged.getDamageValue());
        check(checks, "absent_dye_and_glint", damaged.get(DataComponents.DYED_COLOR) == null
            && damaged.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE) == null, damaged.toString());

        var saddle = new CapturedEquipment("minecraft:saddle", null, null, false);
        var pigSaddle = MorphEquipmentRendering.capturedStack(saddle, EquipmentSlot.SADDLE, EntityTypes.PIG);
        check(checks, "tag_allows_pig_saddle", pigSaddle.is(Items.SADDLE), pigSaddle.toString());
        check(checks, "tag_rejects_wolf_saddle", MorphEquipmentRendering.capturedStack(saddle, EquipmentSlot.SADDLE, EntityTypes.WOLF).isEmpty(), "wolf saddle");
        var horseArmor = new CapturedEquipment("minecraft:diamond_horse_armor", null, null, false);
        check(checks, "tag_allows_horse_armor", MorphEquipmentRendering.capturedStack(horseArmor, EquipmentSlot.BODY, EntityTypes.HORSE)
            .is(Items.DIAMOND_HORSE_ARMOR), "horse body armor");
        check(checks, "tag_rejects_pig_horse_armor", MorphEquipmentRendering.capturedStack(horseArmor, EquipmentSlot.BODY, EntityTypes.PIG).isEmpty(), "pig horse armor");

        // A direct equipment fixture, not a claim that this adapter supports acquisition.
        var descriptor = new FormDescriptor(FormDescriptor.VERSION, "minecraft:wolf", "morph:equipment_fixture", 1,
            Map.of(), null, Map.of("body", captured), Map.of(), null);
        var adapter = EntityTypes.WOLF.create(player.level(), EntitySpawnReason.LOAD);
        if (adapter == null) throw new IllegalStateException("Native wolf adapter creation failed");
        var inventory = new EnumMap<EquipmentSlot, ItemStack>(EquipmentSlot.class);
        for (var slot : EquipmentSlot.values()) inventory.put(slot, player.getItemBySlot(slot).copy());
        MorphEquipmentRendering.prepare(player, adapter, descriptor);
        check(checks, "adapter_receives_body_gear", ItemStack.matches(stack, adapter.getItemBySlot(EquipmentSlot.BODY)),
            adapter.getItemBySlot(EquipmentSlot.BODY).toString());
        check(checks, "live_hands_stay_authoritative", ItemStack.matches(player.getMainHandItem(), adapter.getMainHandItem())
            && ItemStack.matches(player.getOffhandItem(), adapter.getOffhandItem()), "player hands copied");
        MorphEquipmentRendering.prepare(player, adapter, null);
        check(checks, "missing_descriptor_clears_body_and_saddle", adapter.getItemBySlot(EquipmentSlot.BODY).isEmpty()
            && adapter.getItemBySlot(EquipmentSlot.SADDLE).isEmpty(), "cleared captured gear");
        check(checks, "player_equipment_unchanged", inventory.entrySet().stream()
            .allMatch(entry -> ItemStack.matches(entry.getValue(), player.getItemBySlot(entry.getKey()))), "all player equipment slots compared");
        return Map.of("passed", checks.stream().allMatch(check -> Boolean.TRUE.equals(check.get("passed"))), "checks", checks,
            "scope", "native client component with loaded registries/tags; not a framebuffer or acquisition oracle");
    }

    private static void check(List<Map<String, Object>> checks, String name, boolean passed, Object observed) {
        checks.add(Map.of("name", name, "passed", passed, "observed", observed));
    }
}
