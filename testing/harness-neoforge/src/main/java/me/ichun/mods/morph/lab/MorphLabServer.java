package me.ichun.mods.morph.lab;

import java.util.function.Consumer;
import me.ichun.mods.morph.server.MorphSavedData;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

@Mod(value = "morph_lab", dist = Dist.DEDICATED_SERVER)
@EventBusSubscriber(modid = "morph_lab")
public final class MorphLabServer {
    private static final HuskDamageProbes.Adapter ADAPTER = new HuskDamageProbes.Adapter() {
        private me.ichun.mods.morph.model.MorphCollection forms(ServerPlayer player) {
            return player.level().getServer().overworld().getDataStorage().computeIfAbsent(MorphSavedData.TYPE).collection(player.getUUID());
        }
        public void selectForm(ServerPlayer player, String form) { forms(player).unlock(form); forms(player).select(form, 0); }
        public String activeForm(ServerPlayer player) { return forms(player).activeForm(); }
        public void resetForm(ServerPlayer player) { forms(player).reset(); }
    };
    private static Identifier id(HuskDamageProbes.Case scenario) {
        return Identifier.fromNamespaceAndPath("morph_lab", scenario.name().toLowerCase(java.util.Locale.ROOT));
    }
    @SubscribeEvent public static void functions(RegisterEvent event) {
        if (!Boolean.getBoolean("morph.lab.serverTests")) return;
        event.register(Registries.TEST_FUNCTION, registry -> {
            for (var scenario : HuskDamageProbes.Case.values()) registry.register(id(scenario), helper -> HuskDamageProbes.run(helper, scenario, ADAPTER));
        });
    }
    @SubscribeEvent public static void tests(RegisterGameTestsEvent event) {
        if (!Boolean.getBoolean("morph.lab.serverTests")) return;
        var environment = event.registerEnvironment(Identifier.fromNamespaceAndPath("morph_lab", "baseline"));
        for (var scenario : HuskDamageProbes.Case.values()) {
            ResourceKey<Consumer<GameTestHelper>> function = ResourceKey.create(Registries.TEST_FUNCTION, id(scenario));
            event.registerTest(id(scenario), new FunctionGameTestInstance(function,
                    new TestData<>(environment, Identifier.withDefaultNamespace("empty"), 100, 0, true)));
        }
    }
}
