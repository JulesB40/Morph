package me.ichun.mods.morph.client.equipment;

import me.ichun.mods.morph.model.FormDescriptor.CapturedEquipment;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MorphCapturedEquipmentTest {
    @BeforeAll static void nativeRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void onlyCompatibleBodyGearRestoresDisplayComponents() {
        var captured = new CapturedEquipment("minecraft:wolf_armor", 10, 0x123456, true);
        var stack = MorphEquipmentRendering.capturedStack(captured, EquipmentSlot.BODY, EntityTypes.WOLF);
        assertTrue(stack.is(Items.WOLF_ARMOR));
        assertEquals(1, stack.getCount());
        assertEquals(10, stack.getDamageValue());
        assertEquals(0x123456, stack.get(DataComponents.DYED_COLOR).rgb());
        assertEquals(Boolean.TRUE, stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
        assertTrue(MorphEquipmentRendering.capturedStack(captured, EquipmentSlot.BODY, EntityTypes.PIG).isEmpty());
        assertTrue(MorphEquipmentRendering.capturedStack(captured, EquipmentSlot.SADDLE, EntityTypes.WOLF).isEmpty());
        assertTrue(MorphEquipmentRendering.capturedStack(captured, EquipmentSlot.MAINHAND, EntityTypes.WOLF).isEmpty());
    }

    @Test void missingInvalidAndNonEquipmentValuesRemainEmpty() {
        assertTrue(MorphEquipmentRendering.capturedStack(null, EquipmentSlot.BODY, EntityTypes.WOLF).isEmpty());
        for (var id : new String[] {"minecraft:missing_item", "minecraft:air", "minecraft:diamond"}) {
            assertTrue(MorphEquipmentRendering.capturedStack(new CapturedEquipment(id, null, null, false),
                EquipmentSlot.BODY, EntityTypes.WOLF).isEmpty());
        }
        var damaged = MorphEquipmentRendering.capturedStack(new CapturedEquipment("minecraft:wolf_armor", 1_000_000, null, false),
            EquipmentSlot.BODY, EntityTypes.WOLF);
        assertEquals(damaged.getMaxDamage(), damaged.getDamageValue());
        assertNull(damaged.get(DataComponents.DYED_COLOR));
        assertNull(damaged.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
    }
}
