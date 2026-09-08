package me.ichun.mods.morph.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FormFlightTest {
    @Test
    void NativeFlyingFormsUseSustainedFlightWithoutFlapImpulses() {
        for (String form : java.util.List.of("minecraft:allay", "minecraft:bat", "minecraft:bee",
                "minecraft:blaze", "minecraft:ender_dragon", "minecraft:ghast", "minecraft:happy_ghast",
                "minecraft:parrot", "minecraft:phantom", "minecraft:vex", "minecraft:wither")) {
            assertTrue(FormTraits.forForm(form).flight(), form);
            assertTrue(FormTraits.forForm(form).fallImmunity(), form);
        }
    }

    @Test
    void OrdinaryFormsAndUnknownFormsHaveNeitherFlightMode() {
        assertFalse(FormTraits.forForm("minecraft:chicken").flight());
        assertFalse(FormTraits.forForm("").flight());
        assertEquals(0.0, MorphActions.flapImpulse(""));
    }
}
