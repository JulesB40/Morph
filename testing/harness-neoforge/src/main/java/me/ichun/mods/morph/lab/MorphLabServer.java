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
            return MorphSavedData.load(player.level().getServer()).collection(player.getUUID());
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
            registry.register(Identifier.fromNamespaceAndPath("morph_lab", "native_pose_dimensions"), NativePoseProbes::verify);
            registry.register(Identifier.fromNamespaceAndPath("morph_lab", "native_variant_attributes"), NativeVariantAttributeChecks::verify);
            registry.register(Identifier.fromNamespaceAndPath("morph_lab", "native_sounds"), helper -> {
                NativeSoundChecks.verify(helper, new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "lab-sounds"), net.minecraft.server.level.ClientInformation.createDefault())); helper.succeed();
            });
            for (var scenario : NativeBiomassChecks.Case.values()) registry.register(
                    Identifier.fromNamespaceAndPath("morph_lab", "biomass_" + scenario.name().toLowerCase(java.util.Locale.ROOT)),
                    helper -> NativeBiomassChecks.run(helper, scenario));
            for (var scenario : NativeAuthorityChecks.Case.values()) registry.register(
                    Identifier.fromNamespaceAndPath("morph_lab", "authority_" + scenario.name().toLowerCase(java.util.Locale.ROOT)),
                    helper -> NativeAuthorityChecks.run(helper, scenario));
            for (var scenario : HuskDamageProbes.Case.values()) registry.register(id(scenario), helper -> HuskDamageProbes.run(helper, scenario, ADAPTER));
        });
    }
    @SubscribeEvent public static void tests(RegisterGameTestsEvent event) {
        if (!Boolean.getBoolean("morph.lab.serverTests")) return;
        var environment = event.registerEnvironment(Identifier.fromNamespaceAndPath("morph_lab", "baseline"));
        event.registerTest(Identifier.fromNamespaceAndPath("morph_lab", "native_pose_dimensions"), new FunctionGameTestInstance(
                ResourceKey.create(Registries.TEST_FUNCTION, Identifier.fromNamespaceAndPath("morph_lab", "native_pose_dimensions")),
                new TestData<>(environment, Identifier.withDefaultNamespace("empty"), 100, 0, true)));
        var extra = new java.util.ArrayList<String>();
        extra.add("native_sounds");
        extra.add("native_variant_attributes");
        for (var scenario : NativeBiomassChecks.Case.values()) extra.add("biomass_" + scenario.name().toLowerCase(java.util.Locale.ROOT));
        for (var scenario : NativeAuthorityChecks.Case.values()) extra.add("authority_" + scenario.name().toLowerCase(java.util.Locale.ROOT));
        for (String name : extra) {
            var testId = Identifier.fromNamespaceAndPath("morph_lab", name);
            event.registerTest(testId, new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, testId),
                    new TestData<>(environment, Identifier.withDefaultNamespace("empty"), 100, 0, true)));
        }
        for (var scenario : HuskDamageProbes.Case.values()) {
            ResourceKey<Consumer<GameTestHelper>> function = ResourceKey.create(Registries.TEST_FUNCTION, id(scenario));
            event.registerTest(id(scenario), new FunctionGameTestInstance(function,
                    new TestData<>(environment, Identifier.withDefaultNamespace("empty"), 100, 0, true)));
        }
    }
}
