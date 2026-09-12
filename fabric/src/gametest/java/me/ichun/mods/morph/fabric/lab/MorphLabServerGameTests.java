package me.ichun.mods.morph.fabric.lab;

import me.ichun.mods.morph.lab.HuskDamageProbes;
import me.ichun.mods.morph.server.MorphSavedData;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

public final class MorphLabServerGameTests {
    @GameTest(maxTicks = 100) public void nativeVariantAttributes(GameTestHelper helper) { me.ichun.mods.morph.lab.NativeVariantAttributeChecks.verify(helper); }
    @GameTest(maxTicks = 100) public void nativePoseDimensions(GameTestHelper helper) { me.ichun.mods.morph.lab.NativePoseProbes.verify(helper); }
    private static final HuskDamageProbes.Adapter ADAPTER = new HuskDamageProbes.Adapter() {
        private me.ichun.mods.morph.model.MorphCollection forms(ServerPlayer player) {
            return MorphSavedData.load(player.level().getServer()).collection(player.getUUID());
        }
        public void selectForm(ServerPlayer player, String form) { forms(player).unlock(form); forms(player).select(form, 0); }
        public String activeForm(ServerPlayer player) { return forms(player).activeForm(); }
        public void resetForm(ServerPlayer player) { forms(player).reset(); }
    };
    @GameTest(maxTicks = 100) public void huskEmptyHand(GameTestHelper helper) { HuskDamageProbes.run(helper, HuskDamageProbes.Case.EMPTY_HAND_DURATION, ADAPTER); }
    @GameTest(maxTicks = 100) public void huskHeldItem(GameTestHelper helper) { HuskDamageProbes.run(helper, HuskDamageProbes.Case.HELD_ITEM, ADAPTER); }
    @GameTest(maxTicks = 100) public void huskOffhandItem(GameTestHelper helper) { HuskDamageProbes.run(helper, HuskDamageProbes.Case.OFFHAND_ITEM, ADAPTER); }
    @GameTest(maxTicks = 100) public void huskRejectedDamage(GameTestHelper helper) { HuskDamageProbes.run(helper, HuskDamageProbes.Case.REJECTED_DAMAGE, ADAPTER); }
    @GameTest(maxTicks = 100) public void huskEasyZeroDuration(GameTestHelper helper) { HuskDamageProbes.run(helper, HuskDamageProbes.Case.EASY_ZERO_DURATION, ADAPTER); }
    @GameTest(maxTicks = 100) public void authorityStaleActions(GameTestHelper helper) { me.ichun.mods.morph.lab.NativeAuthorityChecks.run(helper, me.ichun.mods.morph.lab.NativeAuthorityChecks.Case.STALE_ACTIONS); }
    @GameTest(maxTicks = 100) public void authorityCrampedActiveDelete(GameTestHelper helper) { me.ichun.mods.morph.lab.NativeAuthorityChecks.run(helper, me.ichun.mods.morph.lab.NativeAuthorityChecks.Case.CRAMPED_ACTIVE_DELETE); }
    @GameTest(maxTicks = 100) public void authorityCanceledActions(GameTestHelper helper) { me.ichun.mods.morph.lab.NativeAuthorityChecks.run(helper, me.ichun.mods.morph.lab.NativeAuthorityChecks.Case.CANCELED_ACTIONS); }
    @GameTest(maxTicks = 100) public void authorityRequestRate(GameTestHelper helper) { me.ichun.mods.morph.lab.NativeAuthorityChecks.run(helper, me.ichun.mods.morph.lab.NativeAuthorityChecks.Case.REQUEST_RATE); }
    @GameTest(maxTicks = 100) public void authorityMockAcquisitionDenied(GameTestHelper helper) { me.ichun.mods.morph.lab.NativeAuthorityChecks.run(helper, me.ichun.mods.morph.lab.NativeAuthorityChecks.Case.MOCK_ACQUISITION_DENIED); }
    @GameTest(maxTicks = 100) public void nativeSounds(GameTestHelper helper) { me.ichun.mods.morph.lab.NativeSoundChecks.verify(helper, new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "lab-sounds"), net.minecraft.server.level.ClientInformation.createDefault())); helper.succeed(); }
    @GameTest(maxTicks = 100) public void authorityUnavailableAttributes(GameTestHelper helper) { me.ichun.mods.morph.lab.NativeAuthorityChecks.run(helper, me.ichun.mods.morph.lab.NativeAuthorityChecks.Case.UNAVAILABLE_ATTRIBUTES); }
    @GameTest(maxTicks = 100) public void biomassClassicBypass(GameTestHelper helper) { me.ichun.mods.morph.lab.NativeBiomassChecks.run(helper, me.ichun.mods.morph.lab.NativeBiomassChecks.Case.CLASSIC_BYPASS); }
    @GameTest(maxTicks = 100) public void biomassGainPurchasePersistence(GameTestHelper helper) { me.ichun.mods.morph.lab.NativeBiomassChecks.run(helper, me.ichun.mods.morph.lab.NativeBiomassChecks.Case.GAIN_PURCHASE_PERSISTENCE); }
    @GameTest(maxTicks = 100) public void biomassSelectionConservation(GameTestHelper helper) { me.ichun.mods.morph.lab.NativeBiomassChecks.run(helper, me.ichun.mods.morph.lab.NativeBiomassChecks.Case.SELECTION_CONSERVATION); }
    @GameTest(maxTicks = 100) public void biomassCommandParse(GameTestHelper helper) { me.ichun.mods.morph.lab.NativeBiomassChecks.run(helper, me.ichun.mods.morph.lab.NativeBiomassChecks.Case.COMMAND_PARSE); }
}
