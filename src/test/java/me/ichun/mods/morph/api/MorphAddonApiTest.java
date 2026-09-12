package me.ichun.mods.morph.api;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MorphAddonApiTest {
    @Test void partialAdapterCannotClaimSupport() {
        var registry = new MorphAddonRegistry();
        var partial = new MorphAddonRegistry.AdapterRegistration(new MorphAddonRegistry.Key("fixture:mob", 1),
                Set.of("fixture:mob"), true, true, false, true);
        registry.register(partial);
        assertFalse(registry.availability("fixture:mob", 1, "fixture:mob"));
        assertFalse(registry.availability("fixture:mob", 2, "fixture:mob"));
        registry.register(new MorphAddonRegistry.AdapterRegistration(new MorphAddonRegistry.Key("fixture:mob", 2),
                Set.of("fixture:mob"), true, true, true, true));
        assertTrue(registry.availability("fixture:mob", 2, "fixture:mob"));
        assertFalse(registry.availability("fixture:mob", 2, "fixture:other"));
        assertThrows(IllegalArgumentException.class, () -> registry.register(partial));
        assertThrows(UnsupportedOperationException.class, () -> registry.snapshot().clear());
    }
    @Test void cancellationStopsDispatchAndCanBeRemoved() throws Exception {
        var events = new MorphEvents();
        var event = new MorphEvents.BeforeAction(MorphEvents.Action.ACQUIRE, UUID.randomUUID(), UUID.randomUUID(), "minecraft:pig", 0);
        AtomicInteger calls = new AtomicInteger();
        var cancellation = events.register(value -> MorphEvents.Decision.cancel("fixture denial"));
        events.register(value -> { calls.incrementAndGet(); return MorphEvents.Decision.allow(); });
        assertFalse(events.before(event).allowed());
        assertEquals(0, calls.get());
        cancellation.close();
        assertTrue(events.before(event).allowed());
        assertEquals(1, calls.get());
        events.register(value -> { throw new IllegalStateException("addon bug"); });
        assertFalse(events.before(event).allowed());
    }
}
