package me.ichun.mods.morph.client;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import me.ichun.mods.morph.model.*;
import me.ichun.mods.morph.network.AppearanceProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DescriptorStateTest {
    @BeforeEach void clear() { DescriptorState.clear(); }
    @Test void staleAppearanceAndPreviousConnectionCannotReplaceNewState() {
        var player = UUID.randomUUID(); var oldEpoch = UUID.randomUUID(); var epoch = UUID.randomUUID();
        var descriptor = FormDescriptor.species("minecraft:cow");
        var entry = new CollectionEntry(descriptor.entryId(), descriptor, 0, false, 0);
        assertTrue(DescriptorState.accept(new AppearanceProtocol.Appearance(player, oldEpoch, 15, true, entry)));
        assertFalse(DescriptorState.accept(new AppearanceProtocol.Appearance(player, oldEpoch, 14, true, null)));
        assertEquals(entry, DescriptorState.active(player));
        assertTrue(DescriptorState.accept(new AppearanceProtocol.Appearance(player, epoch, 0, true, null)));
        assertFalse(DescriptorState.accept(new AppearanceProtocol.Appearance(player, oldEpoch, 16, true, entry)));
        assertNull(DescriptorState.active(player));
    }
    @Test void transitionNeedsCurrentAppearanceEpochAndNewGeneration() {
        var player = UUID.randomUUID(); var epoch = UUID.randomUUID();
        var transition = new AppearanceProtocol.Transition(player, epoch, 0, 100, 100, null, null);
        assertFalse(DescriptorState.accept(transition));
        assertTrue(DescriptorState.accept(new AppearanceProtocol.Appearance(player, epoch, 0, true, null)));
        assertTrue(DescriptorState.accept(transition));
        assertFalse(DescriptorState.accept(transition));
        assertFalse(DescriptorState.accept(new AppearanceProtocol.Transition(player, UUID.randomUUID(), 1, 100, 100, null, null)));
    }
}
