package me.ichun.mods.morph.client.equipment;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EvokerRenderState;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.monster.illager.AbstractIllager.IllagerArmPose;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MorphEquipmentRenderingTest {
    @Test void attackEnablesNativeVindicatorLayerWithoutInventingCasting() {
        var player = new AvatarRenderState();
        player.attackTime = 0.4F;
        var vindicator = new IllagerRenderState();
        MorphEquipmentRendering.applyIllagerPose(player, vindicator);
        assertTrue(vindicator.isAggressive);
        assertEquals(IllagerArmPose.ATTACKING, vindicator.armPose);
        var evoker = new EvokerRenderState();
        MorphEquipmentRendering.applyIllagerPose(player, evoker);
        assertFalse(evoker.isCastingSpell);
    }

    @Test void offHandBowAndCrossbowUseTheirActualArmPose() {
        for (var main : HumanoidArm.values()) {
            var player = new AvatarRenderState();
            player.mainArm = main;
            player.isUsingItem = true;
            player.useItemHand = InteractionHand.OFF_HAND;
            if (main == HumanoidArm.RIGHT) player.leftArmPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
            else player.rightArmPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
            var target = new IllagerRenderState();
            MorphEquipmentRendering.applyIllagerPose(player, target);
            assertEquals(IllagerArmPose.BOW_AND_ARROW, target.armPose);
            assertTrue(target.isAggressive);
        }
    }

    @Test void ordinaryItemUseKeepsNativeCrossedOrCastingPose() {
        var player = new AvatarRenderState();
        player.isUsingItem = true;
        player.rightArmPose = HumanoidModel.ArmPose.ITEM;
        var target = new EvokerRenderState();
        target.armPose = IllagerArmPose.CROSSED;
        MorphEquipmentRendering.applyIllagerPose(player, target);
        assertFalse(target.isAggressive);
        assertFalse(target.isCastingSpell);
        assertEquals(IllagerArmPose.CROSSED, target.armPose);
        target.armPose = IllagerArmPose.SPELLCASTING;
        target.isCastingSpell = true;
        MorphEquipmentRendering.applyIllagerPose(player, target);
        assertTrue(target.isCastingSpell);
        assertEquals(IllagerArmPose.SPELLCASTING, target.armPose);
    }
}
