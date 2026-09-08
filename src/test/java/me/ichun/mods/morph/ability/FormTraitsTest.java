package me.ichun.mods.morph.ability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FormTraitsTest {
    @Test
    void waterBreathingMatchesAquaticAndUndeadFamilies() {
        for (String form : java.util.List.of("minecraft:axolotl", "minecraft:copper_golem",
                "minecraft:frog", "minecraft:guardian", "minecraft:nautilus", "minecraft:tadpole",
                "minecraft:turtle", "minecraft:zombie", "minecraft:zombie_nautilus")) {
            assertTrue(FormTraits.forForm(form).waterBreathing(), form);
        }
        assertFalse(FormTraits.forForm("minecraft:iron_golem").waterBreathing());
        assertFalse(FormTraits.forForm("minecraft:pig").waterBreathing());
    }

    @Test
    void nativePassiveMappingsIncludeNew26_2Families() {
        assertTrue(MorphTraits.isUndead("minecraft:camel_husk"));
        assertTrue(MorphTraits.isUndead("minecraft:parched"));
        assertTrue(MorphTraits.isUndead("minecraft:zombie_nautilus"));
        assertTrue(MorphTraits.fireImmune("minecraft:wither_skeleton"));
        assertFalse(MorphTraits.fireImmune("minecraft:camel_husk"));
        assertEquals(3.0, MorphTraits.swimMultiplier("minecraft:tadpole"));
        assertEquals(1.2, MorphTraits.swimMultiplier("minecraft:nautilus"));
        assertEquals(1.0, MorphTraits.landMultiplier("minecraft:guardian"));
    }
}
