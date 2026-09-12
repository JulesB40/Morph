package me.ichun.mods.morph.client.equipment;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import me.ichun.mods.morph.model.FormDescriptor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.component.DyedItemColor;

/** Equipment belongs only to an unspawned client rendering adapter, never a game entity. */
public final class MorphEquipmentRendering {
    private static final EquipmentSlot[] PLAYER_SLOTS = {
        EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
        EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private MorphEquipmentRendering() {}

    public static void prepare(Avatar player, LivingEntity adapter, FormDescriptor descriptor) {
        prepare(player, adapter);
        prepareCapturedSlot(adapter, EquipmentSlot.BODY, descriptor == null ? null : descriptor.equipment().get("body"));
        prepareCapturedSlot(adapter, EquipmentSlot.SADDLE, descriptor == null ? null : descriptor.equipment().get("saddle"));
    }

    private static void prepareCapturedSlot(LivingEntity adapter, EquipmentSlot slot, FormDescriptor.CapturedEquipment captured) {
        ItemStack stack = capturedStack(captured, slot, adapter.getType());
        if (!ItemStack.matches(stack, adapter.getItemBySlot(slot))) adapter.setItemSlot(slot, stack);
    }

    /** Rebuilds only display components; captured gear is never added to player inventory. */
    static ItemStack capturedStack(FormDescriptor.CapturedEquipment captured, EquipmentSlot slot, EntityType<?> type) {
        if (captured == null || (slot != EquipmentSlot.BODY && slot != EquipmentSlot.SADDLE)) return ItemStack.EMPTY;
        var id = Identifier.tryParse(captured.item());
        var item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null) return ItemStack.EMPTY;
        var stack = new ItemStack(item);
        var equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null || equippable.slot() != slot || !equippable.canBeEquippedBy(type.builtInRegistryHolder()))
            return ItemStack.EMPTY;
        if (captured.damage() != null && stack.isDamageableItem())
            stack.setDamageValue(Math.clamp(captured.damage(), 0, stack.getMaxDamage()));
        if (captured.dyedRgb() != null) stack.set(DataComponents.DYED_COLOR, new DyedItemColor(captured.dyedRgb()));
        if (captured.enchanted()) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    public static void prepare(Avatar player, LivingEntity adapter) {
        // Unchanged equipment needs no allocation. Empty replacements also clear cached equipment.
        // Vanilla layers decide which slots a particular model can actually display.
        for (EquipmentSlot slot : PLAYER_SLOTS) {
            ItemStack current = player.getItemBySlot(slot);
            if (!ItemStack.matches(current, adapter.getItemBySlot(slot))) {
                adapter.setItemSlot(slot, current.copy());
            }
        }
        if (adapter instanceof Mob mob) mob.setLeftHanded(player.getMainArm() == HumanoidArm.LEFT);
        adapter.swinging = player.swinging;
        adapter.swingingArm = player.swingingArm;
        adapter.swingTime = player.swingTime;
        adapter.attackAnim = player.attackAnim;
        adapter.oAttackAnim = player.oAttackAnim;
    }

    public static void finish(Avatar avatar, AvatarRenderState player, LivingEntityRenderState target) {
        me.ichun.mods.morph.client.animation.MorphPlayerPoses.apply(player, target);
        if (target instanceof ArmedEntityRenderState armed) {
            // Pull/charge item-model properties query the using entity, not just the arm pose.
            // Resolve against the real player during use without mutating or ticking the adapter.
            if (player.isUsingItem) {
                var resolver = Minecraft.getInstance().getItemModelResolver();
                resolver.updateForLiving(armed.rightHandItemState, avatar.getItemHeldByArm(HumanoidArm.RIGHT),
                    ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, avatar);
                resolver.updateForLiving(armed.leftHandItemState, avatar.getItemHeldByArm(HumanoidArm.LEFT),
                    ItemDisplayContext.THIRD_PERSON_LEFT_HAND, avatar);
            }
            armed.mainArm = player.mainArm;
            armed.attackArm = player.attackArm;
            armed.attackTime = player.attackTime;
            armed.swingAnimationType = player.swingAnimationType;
            armed.leftArmPose = player.leftArmPose;
            armed.rightArmPose = player.rightArmPose;
        }
        if (target instanceof IllagerRenderState illager) applyIllagerPose(player, illager);
        if (target instanceof HumanoidRenderState humanoid) {
            // Use state directly: starting or ticking item use on adapters would trigger item hooks.
            humanoid.useItemHand = player.useItemHand;
            humanoid.maxCrossbowChargeDuration = player.maxCrossbowChargeDuration;
            humanoid.ticksUsingItem = player.ticksUsingItem;
            humanoid.isUsingItem = player.isUsingItem;
            humanoid.leftArmPose = player.leftArmPose;
            humanoid.rightArmPose = player.rightArmPose;
            humanoid.elytraRotX = player.elytraRotX;
            humanoid.elytraRotY = player.elytraRotY;
            humanoid.elytraRotZ = player.elytraRotZ;
        }
    }

    static void applyIllagerPose(AvatarRenderState player, IllagerRenderState illager) {
        illager.mainArm = player.mainArm;
        illager.attackAnim = player.attackTime;
        illager.maxCrossbowChargeDuration = (int) player.maxCrossbowChargeDuration;
        illager.ticksUsingItem = player.ticksUsingItem;
        var activeArm = player.isUsingItem && player.useItemHand == net.minecraft.world.InteractionHand.OFF_HAND
            ? player.mainArm.getOpposite() : player.mainArm;
        var pose = activeArm == HumanoidArm.RIGHT ? player.rightArmPose : player.leftArmPose;
        // Native vindicator layers require aggression. Only actual combat poses enable it.
        // Evoker casting remains native state; holding food or gear cannot fabricate a spell.
        illager.isAggressive = player.attackTime > 0 || pose == HumanoidModel.ArmPose.BOW_AND_ARROW
            || pose == HumanoidModel.ArmPose.CROSSBOW_CHARGE || pose == HumanoidModel.ArmPose.CROSSBOW_HOLD;
        if (pose == HumanoidModel.ArmPose.CROSSBOW_CHARGE) {
            illager.armPose = AbstractIllager.IllagerArmPose.CROSSBOW_CHARGE;
        } else if (pose == HumanoidModel.ArmPose.CROSSBOW_HOLD) {
            illager.armPose = AbstractIllager.IllagerArmPose.CROSSBOW_HOLD;
        } else if (pose == HumanoidModel.ArmPose.BOW_AND_ARROW) {
            illager.armPose = AbstractIllager.IllagerArmPose.BOW_AND_ARROW;
        } else if (player.attackTime > 0) {
            illager.armPose = AbstractIllager.IllagerArmPose.ATTACKING;
        }
    }
}
