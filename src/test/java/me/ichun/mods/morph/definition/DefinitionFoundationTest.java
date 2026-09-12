package me.ichun.mods.morph.definition;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import me.ichun.mods.morph.config.MorphPolicySnapshot;
import me.ichun.mods.morph.config.MorphPolicySnapshot.*;
import org.junit.jupiter.api.Test;

class DefinitionFoundationTest {
    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    @Test void shippedDefaultsAreSafeAndParseable() throws Exception {
        try (var stream = getClass().getResourceAsStream("/morph/definitions/defaults.json")) {
            assertNotNull(stream);
            var value = DefinitionParser.parse(stream.readAllBytes(), 0);
            assertEquals(MorphPolicySnapshot.defaults(), value.policy());
            assertTrue(value.mobs().isEmpty());
            assertFalse(value.policy().abilities().terrainHarm());
        }
    }
    @Test void independentFiltersAndDenyPrecedence() {
        UUID alice = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID bob = UUID.fromString("00000000-0000-0000-0000-000000000002");
        var mutable = new HashSet<>(Set.of(alice));
        var policy = new MorphPolicySnapshot(3, ServerMode.COMMAND, false, 100, false,
                new PlayerFilter(mutable, Set.of()), new PlayerFilter(Set.of(), Set.of(alice)),
                new FormFilter(Set.of("minecraft:pig", "minecraft:bat"), Set.of("minecraft:bat")),
                new AbilityPolicy(true, false, Set.of("morph:explode")));
        mutable.add(bob);
        assertTrue(policy.canMorph(alice, "minecraft:pig"));
        assertFalse(policy.canMorph(bob, "minecraft:pig"));
        assertFalse(policy.canMorph(alice, "minecraft:bat"));
        assertFalse(policy.canUseSelector(alice));
        assertTrue(policy.canUseSelector(bob));
        assertFalse(policy.canAcquireByKill(alice, "minecraft:pig"));
        assertFalse(policy.abilities().allows("morph:explode"));
        assertTrue(policy.abilities().allows("morph:teleport"));
    }
    @Test void lastGoodSnapshotSurvivesCrossReferenceAndSyntaxFailures() {
        var reload = new DefinitionReloadService(mode -> mode == ServerMode.CLASSIC);
        String valid = """
            {"schema_version":1,"policy":{"duration_ticks":1200},
             "traits":[{"id":"morph:test","cooldown_ticks":12}],
             "mobs":[{"species":"minecraft:pig","traits":["morph:test"]}]}
            """;
        assertTrue(reload.reload(bytes(valid)).accepted());
        var accepted = reload.current();
        assertEquals(1, accepted.revision());
        assertEquals(12, accepted.traits().get("morph:test").cooldownTicks());
        assertFalse(reload.reload(bytes(valid.replace("\"id\":\"morph:test\"", "\"id\":\"morph:other\""))).accepted());
        assertSame(accepted, reload.current());
        assertFalse(reload.reload(bytes("{" )).accepted());
        assertSame(accepted, reload.current());
        assertTrue(reload.reload(bytes("{\"schema_version\":1}")).accepted());
        assertEquals(2, reload.current().revision());
        assertThrows(UnsupportedOperationException.class, () -> accepted.traits().clear());
    }
    @Test void unsupportedRuntimeModesCannotActivate() {
        var reload = new DefinitionReloadService(mode -> mode == ServerMode.CLASSIC || mode == ServerMode.COMMAND);
        assertFalse(reload.reload(bytes("{\"schema_version\":1,\"policy\":{\"mode\":\"DISGUISE\"}}")).accepted());
        assertFalse(reload.reload(bytes("{\"schema_version\":1,\"policy\":{\"mode\":\"BIOMASS\",\"biomass_opt_in\":true}}")).accepted());
        assertEquals(ServerMode.CLASSIC, reload.current().policy().mode());
        assertThrows(IllegalArgumentException.class, () -> DefinitionParser.parse(bytes("{\"schema_version\":1,\"policy\":{\"mode\":\"BIOMASS\"}}"), 0));
    }
    @Test void rejectsAmbiguousOrUnboundedDocuments() {
        String[] bad = {
            "{\"schema_version\":1,\"schema_version\":1}",
            "{\"schema_version\":1,\"policy\":{\"duration_ticks\":1.5}}",
            "{\"schema_version\":1,\"policy\":{\"duration_ticks\":0}}",
            "{\"schema_version\":1,\"policy\":{\"duration_ticks\":1201}}",
            "{\"schema_version\":1,\"policy\":{\"morph_sounds\":\"false\"}}",
            "{\"schema_version\":1,\"unknown\":true}",
            "{\"schema_version\":1} {}",
            "{\"schema_version\":1,\"mobs\":[{\"species\":\"minecraft:pig\"},{\"species\":\"minecraft:pig\"}]}",
            "{\"schema_version\":1,\"policy\":{\"forms\":{\"deny\":[\"minecraft:pig\",\"minecraft:pig\"]}}}"
        };
        for (String value : bad) assertThrows(RuntimeException.class, () -> DefinitionParser.parse(bytes(value), 0), value);
        assertThrows(IllegalArgumentException.class, () -> DefinitionParser.parse(new byte[65537], 0));
        assertThrows(IllegalArgumentException.class, () -> DefinitionParser.parse(new byte[]{(byte)0xc3, (byte)0x28}, 0));
        String excess = "{\"schema_version\":1,\"traits\":[" + "{},".repeat(256) + "{}]}";
        assertThrows(IllegalArgumentException.class, () -> DefinitionParser.parse(bytes(excess), 0));
        assertEquals(1, DefinitionParser.parse(bytes("{\"schema_version\":1,\"policy\":{\"duration_ticks\":1}}"), 0).policy().durationTicks());
    }
}
