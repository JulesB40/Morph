package me.ichun.mods.morph.server;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.Map;
import java.util.UUID;
import me.ichun.mods.morph.progression.BiomassDefinitions;
import me.ichun.mods.morph.progression.BiomassLedger;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MorphBiomassSavedDataTest {
    @Test void oldSavesDefaultToLockedAndNbtRoundTripPreservesProgress() {
        var id = UUID.randomUUID();
        var old = MorphSavedData.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"schema_version\":1,\"players\":{}}")).getOrThrow();
        assertEquals(BiomassLedger.locked(), old.biomass(id));
        assertTrue(old.commitBiomass(id, 0, old.biomass(id).unlock()));
        var progressed = new BiomassLedger(2, true, 80, Map.of(BiomassDefinitions.Upgrade.CAPACITY, 1));
        assertTrue(old.commitBiomass(id, 1, progressed));
        var restored = MorphSavedData.CODEC.parse(NbtOps.INSTANCE,
                MorphSavedData.CODEC.encodeStart(NbtOps.INSTANCE, old).getOrThrow()).getOrThrow();
        assertEquals(progressed, restored.biomass(id));
        assertEquals(BiomassLedger.locked(), restored.biomass(UUID.randomUUID()));
        var jsonRestored = MorphSavedData.CODEC.parse(JsonOps.INSTANCE,
                MorphSavedData.CODEC.encodeStart(JsonOps.INSTANCE, restored).getOrThrow()).getOrThrow();
        assertEquals(progressed, jsonRestored.biomass(id));
    }

    @Test void staleInvalidAndNonsequentialCommitsCannotChangeState() {
        var data = new MorphSavedData();
        var id = UUID.randomUUID();
        var unlocked = BiomassLedger.locked().unlock();
        assertTrue(data.commitBiomass(id, 0, unlocked));
        assertFalse(data.commitBiomass(id, 0, unlocked));
        assertFalse(data.commitBiomass(id, 1, new BiomassLedger(3, true, 1, Map.of())));
        assertThrows(IllegalArgumentException.class, () ->
                data.commitBiomass(id, 1, new BiomassLedger(2, true, 101, Map.of())));
        assertEquals(unlocked, data.biomass(id));
        data.setDirty(false);
        assertTrue(data.commitBiomass(id, 1, unlocked));
        assertFalse(data.isDirty());
    }
}
