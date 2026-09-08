package me.ichun.mods.morph.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FormFlightTest {
    @Test
    void batAndBeeUseSustainedFlightWithoutFlapImpulses() {
        for (String form : java.util.List.of("minecraft:bat", "minecraft:bee")) {
            assertTrue(FormTraits.forForm(form).flight(), form);
            assertTrue(FormTraits.forForm(form).fallImmunity(), form);
            assertEquals(0.0, MorphActions.flapImpulse(form), form);
        }
    }

    @Test
    void parrotRetainsFlapAndHumanHasNeitherFlightMode() {
        assertFalse(FormTraits.forForm("minecraft:parrot").flight());
        assertEquals(0.42, MorphActions.flapImpulse("minecraft:parrot"));
        assertFalse(FormTraits.forForm("").flight());
        assertEquals(0.0, MorphActions.flapImpulse(""));
    }
}
