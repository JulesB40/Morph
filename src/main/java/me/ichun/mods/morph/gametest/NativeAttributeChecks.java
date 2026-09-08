package me.ichun.mods.morph.gametest;

import me.ichun.mods.morph.ability.MorphAttributes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;

/** Registry-wide checks against real native entities, run by both loader suites. */
public final class NativeAttributeChecks {
    private NativeAttributeChecks() {}

    public static void verify(GameTestHelper helper, ServerPlayer player, me.ichun.mods.morph.model.MorphCollection forms) {
        MorphAttributes.cleanup(player);
        player.setHealth(player.getMaxHealth() / 2);
        int checked = 0;
        var rows = new StringBuilder("form,max_health,movement_speed,armor,armor_toughness,knockback_resistance\n");
        for (var type : BuiltInRegistries.ENTITY_TYPE) {
            var id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (!id.getNamespace().equals("minecraft") || !DefaultAttributes.hasSupplier(type)) continue;
            var entity = type.create(helper.getLevel(), EntitySpawnReason.LOAD);
            if (!(entity instanceof LivingEntity nativeMob) || nativeMob instanceof Avatar) continue;
            if (nativeMob instanceof net.minecraft.world.entity.monster.cubemob.AbstractCubeMob cube)
                cube.setSize(cube.getSize(), false);
            if (nativeMob instanceof net.minecraft.world.entity.monster.Phantom phantom)
                phantom.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(6 + phantom.getPhantomSize());
            if (nativeMob instanceof net.minecraft.world.entity.monster.skeleton.WitherSkeleton)
                nativeMob.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(4);
            forms.unlock(id.toString());
            forms.select(id.toString(), checked * 40L);
            helper.assertValueEqual(forms.activeForm(), id.toString(), "Audit form selected");
            MorphAttributes.tick(player, id.toString());
            for (var attribute : MorphAttributes.copiedAttributes()) {
                var actual = player.getAttribute(attribute);
                if (actual == null) continue;
                var nativeAttribute = nativeMob.getAttribute(attribute);
                double expected = nativeAttribute == null ? actual.getBaseValue() : nativeAttribute.getBaseValue();
                if (nativeMob instanceof net.minecraft.world.entity.monster.Shulker && attribute == Attributes.ARMOR)
                    expected += 20; // Native covered-shell modifier, applied by its AI closing the shell.
                helper.assertTrue(Double.isFinite(actual.getValue()) && Math.abs(actual.getValue() - expected) < .0001,
                        id + " differs from native " + attribute + ": " + actual.getValue() + " vs " + expected);
            }
            helper.assertTrue(Math.abs(player.getHealth() / player.getMaxHealth() - .5) < .0001,
                    id + " lost the health ratio");
            helper.assertValueEqual(player.isInvertedHealAndHarm(), nativeMob.isInvertedHealAndHarm(),
                    id + " healing/harming inversion");
            helper.assertValueEqual(player.canFreeze(), nativeMob.canFreeze(), id + " freeze immunity");
            helper.assertValueEqual(player.canBreatheUnderwater(), nativeMob.canBreatheUnderwater(), id + " underwater breathing");
            helper.assertValueEqual(me.ichun.mods.morph.ability.MorphTraits.fireImmune(id.toString()), nativeMob.fireImmune(),
                    id + " fire immunity");
            for (var effectType : BuiltInRegistries.MOB_EFFECT) {
                var effect = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effectType);
                var instance = new net.minecraft.world.effect.MobEffectInstance(effect, 200);
                helper.assertValueEqual(player.canBeAffected(instance), nativeMob.canBeAffected(instance),
                        id + " immunity for " + effect);
            }
            rows.append(id);
            for (var attribute : java.util.List.of(Attributes.MAX_HEALTH, Attributes.MOVEMENT_SPEED,
                    Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS, Attributes.KNOCKBACK_RESISTANCE))
                rows.append(',').append(player.getAttributeValue(attribute));
            rows.append('\n');
            checked++;
        }
        helper.assertTrue(checked >= 85, "Registry audit unexpectedly skipped mobs: " + checked);
        // Explicit spawn-derived regression anchors, independent of adapter defaults.
        MorphAttributes.tick(player, "minecraft:magma_cube");
        helper.assertValueEqual(player.getMaxHealth(), 1F, "Small magma cube health");
        helper.assertValueEqual(player.getAttributeValue(Attributes.ARMOR), 3.0, "Small magma cube natural armor");
        MorphAttributes.tick(player, "minecraft:phantom");
        helper.assertValueEqual(player.getAttributeValue(Attributes.ATTACK_DAMAGE), 6.0, "Default phantom attack");
        forms.select("minecraft:witch", 10000);
        MorphAttributes.tick(player, "minecraft:witch");
        player.setHealth(player.getMaxHealth());
        player.invulnerableTime = 0;
        player.hurtServer(helper.getLevel(), player.damageSources().magic(), 10F);
        helper.assertTrue(Math.abs(player.getHealth() - (player.getMaxHealth() - 1.5F)) < .0001,
                "Witch must resist 85% of actual magic damage");
        var arrow = net.minecraft.world.entity.EntityTypes.ARROW.create(helper.getLevel(), EntitySpawnReason.LOAD);
        helper.assertTrue(arrow != null, "Arrow fixture exists");
        forms.select("minecraft:wither", 10040);
        MorphAttributes.tick(player, "minecraft:wither");
        player.setHealth(player.getMaxHealth());
        player.invulnerableTime = 0;
        helper.assertTrue(player.hurtServer(helper.getLevel(), player.damageSources().arrow(arrow, null), 10),
                "Wither above half health takes arrow damage");
        player.setHealth(player.getMaxHealth() * .5F);
        player.invulnerableTime = 0;
        helper.assertFalse(player.hurtServer(helper.getLevel(), player.damageSources().arrow(arrow, null), 10),
                "Powered wither rejects arrows");
        forms.select("minecraft:shulker", 10080);
        MorphAttributes.tick(player, "minecraft:shulker");
        player.invulnerableTime = 0;
        helper.assertValueEqual(player.getArmorValue(), 20, "Closed shulker shell armor");
        helper.assertFalse(player.hurtServer(helper.getLevel(), player.damageSources().arrow(arrow, null), 10),
                "Closed shulker rejects arrows");
        MorphAttributes.cleanup(player);
        forms.reset();
        for (var attribute : MorphAttributes.copiedAttributes()) {
            var instance = player.getAttribute(attribute);
            helper.assertTrue(instance == null || !instance.hasModifier(MorphAttributes.MODIFIER),
                    "Reset left a modifier on " + attribute);
        }
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of("morph-native-attributes.csv"), rows);
        } catch (java.io.IOException failure) { throw new AssertionError("Could not write attribute audit", failure); }
        com.mojang.logging.LogUtils.getLogger().info("Morph native attribute audit passed for {} living forms", checked);
    }
}
