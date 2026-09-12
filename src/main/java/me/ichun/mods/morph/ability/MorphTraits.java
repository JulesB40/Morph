package me.ichun.mods.morph.ability;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import me.ichun.mods.morph.shape.ShapeHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Original passive traits applied without permanent potion effects or entity mutation. */
public final class MorphTraits {
    /** Mirrors the vanilla undead, skeletons and zombies entity tags in 26.2. */
    private static final Set<String> UNDEAD = ids("zombie", "zombie_villager", "husk", "drowned", "skeleton",
            "stray", "bogged", "wither_skeleton", "phantom", "wither", "zoglin", "zombified_piglin",
            "skeleton_horse", "zombie_horse", "camel_husk", "parched", "zombie_nautilus");
    private static final Set<String> FIRE_IMMUNE = ids("blaze", "ender_dragon", "ghast", "magma_cube",
            "shulker", "strider", "vex", "warden", "wither", "wither_skeleton", "zoglin", "zombified_piglin");
    private static final Set<String> WATER_SENSITIVE = ids("blaze", "enderman", "snow_golem", "strider");
    /** Exact members of minecraft:burn_in_daylight in the 26.2 source data. */
    private static final Set<String> SUN_SENSITIVE = ids("drowned", "phantom", "skeleton", "stray", "bogged",
            "zombie", "zombie_villager", "zombie_horse", "zombie_nautilus");
    /** Water animals which actually lose air and drown when kept on land. */
    private static final Set<String> WATER_ONLY = ids("cod", "salmon", "pufferfish", "tropical_fish",
            "squid", "glow_squid", "tadpole", "nautilus");
    private static final Set<String> SPIDER_POISON_IMMUNE = ids("spider", "cave_spider");
    private static final Set<String> NAUTILUS_POISON_IMMUNE = ids("nautilus", "zombie_nautilus");
    private static final Set<String> WEAKNESS_IMMUNE = ids("parched");
    private static final Map<ServerPlayer, DryTime> DRY_TIME = new WeakHashMap<>();
    private static final Map<Player, Boolean> SINK_WATER = new WeakHashMap<>();
    private static final Map<ServerPlayer, Integer> BEE_WATER_TICKS = new WeakHashMap<>();
    private MorphTraits() {}

    private static Set<String> ids(String... names) {
        return java.util.Arrays.stream(names).map(name -> "minecraft:" + name).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public static boolean isUndead(String form) { return form != null && UNDEAD.contains(form); }
    public static boolean climbs(String form) { return "minecraft:spider".equals(form) || "minecraft:cave_spider".equals(form); }
    public static boolean fireImmune(String form) { return form != null && FIRE_IMMUNE.contains(form); }

    public static boolean preventsDamage(ServerPlayer player, String form, DamageSource source) {
        if ("minecraft:shulker".equals(form)
                && source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow) return true;
        if ("minecraft:wither".equals(form)) {
            var attacker = source.getEntity();
            if (source.is(DamageTypeTags.WITHER_IMMUNE_TO)
                    || attacker instanceof net.minecraft.world.entity.boss.wither.WitherBoss
                    || attacker != null && attacker.is(net.minecraft.tags.EntityTypeTags.WITHER_FRIENDS)) return true;
            if (player.getHealth() <= player.getMaxHealth() * .5F
                    && (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow
                    || source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge)) return true;
        }
        return fireImmune(form) && source.is(DamageTypeTags.IS_FIRE)
                || FormTraits.forForm(form).fallImmunity() && source.is(DamageTypeTags.IS_FALL);
    }

    public static boolean rejectsEffect(Player player, MobEffectInstance effect) {
        String form = ShapeHooks.form(player);
        if (form == null || form.isEmpty()) return false;
        return (isUndead(form) && (effect.is(MobEffects.POISON) || effect.is(MobEffects.REGENERATION)))
                || SPIDER_POISON_IMMUNE.contains(form) && effect.is(MobEffects.POISON)
                || NAUTILUS_POISON_IMMUNE.contains(form) && effect.is(MobEffects.POISON)
                || WEAKNESS_IMMUNE.contains(form) && effect.is(MobEffects.WEAKNESS)
                || "minecraft:silverfish".equals(form) && effect.is(MobEffects.INFESTED)
                || "minecraft:slime".equals(form) && effect.is(MobEffects.OOZING)
                || ("minecraft:wither".equals(form) || "minecraft:wither_skeleton".equals(form))
                && effect.is(MobEffects.WITHER);
    }

    public static void afterDamage(LivingEntity target, DamageSource source) {
        if (!(source.getEntity() instanceof ServerPlayer attacker) || source.getDirectEntity() != attacker
                || source.is(DamageTypeTags.IS_PROJECTILE) || !target.isAlive()) return;
        String form = ShapeHooks.form(attacker);
        if ("minecraft:bee".equals(form)) {
            target.setStingerCount(target.getStingerCount() + 1);
            int poisonSeconds = attacker.level().getDifficulty() == net.minecraft.world.Difficulty.NORMAL ? 10
                    : attacker.level().getDifficulty() == net.minecraft.world.Difficulty.HARD ? 18 : 0;
            if (poisonSeconds > 0) target.addEffect(new MobEffectInstance(MobEffects.POISON, poisonSeconds * 20), attacker);
        } else if ("minecraft:cave_spider".equals(form)) {
            int poisonSeconds = attacker.level().getDifficulty() == net.minecraft.world.Difficulty.NORMAL ? 7
                    : attacker.level().getDifficulty() == net.minecraft.world.Difficulty.HARD ? 15 : 0;
            if (poisonSeconds > 0) target.addEffect(new MobEffectInstance(MobEffects.POISON, poisonSeconds * 20), attacker);
        }
        else if ("minecraft:pufferfish".equals(form)) target.addEffect(new MobEffectInstance(MobEffects.POISON, 60), attacker);
        else if ("minecraft:husk".equals(form) && attacker.getMainHandItem().isEmpty()) {
            int duration = 140 * (int)attacker.level().getCurrentDifficultyAt(attacker.blockPosition()).getEffectiveDifficulty();
            if (duration > 0) target.addEffect(new MobEffectInstance(MobEffects.HUNGER, duration), attacker);
        }
        else if ("minecraft:wither_skeleton".equals(form)) target.addEffect(new MobEffectInstance(MobEffects.WITHER, 200), attacker);
    }

    public static void tick(ServerPlayer player, String form) {
        if (form == null || form.isEmpty() || !player.isAlive() || player.isSpectator()) { cleanup(player); return; }
        MorphInteractions.tick(player, form);
        MorphActions.tick(player, form);
        MorphSwimmingRules.beforeTravel(player);
        if (FormTraits.forForm(form).waterBreathing() && player.isUnderWater()) {
            player.setAirSupply(Math.min(player.getMaxAirSupply(), player.getAirSupply() + 4));
        }
        if (fireImmune(form)) player.clearFire();
        for (MobEffectInstance effect : java.util.List.copyOf(player.getActiveEffects())) {
            if (rejectsEffect(player, effect)) player.removeEffect(effect.getEffect());
        }
        if (player.isCreative()) { DRY_TIME.remove(player); BEE_WATER_TICKS.remove(player); return; }
        if ("minecraft:bee".equals(form) && player.isInWater()) {
            int wetTicks = Math.min(21, BEE_WATER_TICKS.getOrDefault(player, 0) + 1);
            BEE_WATER_TICKS.put(player, wetTicks);
            if (wetTicks > 20) player.hurtServer(player.level(), player.damageSources().drown(), 1);
            if (!player.isAlive()) return;
        } else BEE_WATER_TICKS.remove(player);
        boolean waterDamage = "minecraft:bee".equals(form) ? player.isInWater() : player.isInWaterOrRain();
        if (WATER_SENSITIVE.contains(form) && waterDamage) {
            player.hurtServer(player.level(), player.damageSources().drown(), 1);
            if (!player.isAlive()) return;
        }
        if ("minecraft:snow_golem".equals(form)
                && player.level().environmentAttributes().getValue(EnvironmentAttributes.SNOW_GOLEM_MELTS, player.position())) {
            player.hurtServer(player.level(), player.damageSources().onFire(), 1);
            if (!player.isAlive()) return;
        }
        int dryLimit = WATER_ONLY.contains(form) ? 300 : "minecraft:dolphin".equals(form) ? 2400
                : "minecraft:axolotl".equals(form) ? 6000 : 0;
        if (dryLimit > 0) {
            DryTime dry = DRY_TIME.get(player);
            if (dry == null || !dry.form.equals(form)) { dry = new DryTime(form); DRY_TIME.put(player, dry); }
            if (refillsDryAir(form, player)) dry.ticks = 0;
            else if (usesDryAir(player, form) && ++dry.ticks >= dryLimit && (WATER_ONLY.contains(form) ? (dry.ticks - dryLimit) % 20 == 0 : true)) {
                player.hurtServer(player.level(), WATER_ONLY.contains(form) ? player.damageSources().drown() : player.damageSources().dryOut(), WATER_ONLY.contains(form) ? 2 : 1);
                if (!player.isAlive()) return;
            }
        } else DRY_TIME.remove(player);
        if (SUN_SENSITIVE.contains(form)) burnInSun(player);
    }

    private static boolean usesDryAir(ServerPlayer player, String form) {
        if (!WATER_ONLY.contains(form)) return true;
        double bonus = player.getAttributeValue(Attributes.OXYGEN_BONUS);
        return bonus <= 0 || player.getRandom().nextDouble() < 1.0 / (bonus + 1.0);
    }

    private static boolean refillsDryAir(String form, ServerPlayer player) {
        // WaterAnimal and Nautilus use isInWater(); Axolotl and Dolphin intentionally
        // use isInWaterOrRain() in their native air/moisture handlers.
        return WATER_ONLY.contains(form) || "minecraft:nautilus".equals(form)
                ? player.isInWater() : player.isInWaterOrRain();
    }

    private static void burnInSun(ServerPlayer player) {
        if (!player.level().environmentAttributes().getValue(EnvironmentAttributes.MONSTERS_BURN, player.position())
                || player.isInWaterOrRain() || player.isInPowderSnow || player.wasInPowderSnow) return;
        float brightness = player.getLightLevelDependentMagicValue();
        if (brightness <= 0.5F || player.getRandom().nextFloat() * 30 >= (brightness - 0.4F) * 2
                || !player.level().canSeeSky(BlockPos.containing(player.getX(), player.getEyeY(), player.getZ()))) return;
        var helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        if (helmet.isEmpty()) player.igniteForSeconds(8);
        else if (helmet.isDamageableItem()) helmet.hurtAndBreak(player.getRandom().nextInt(2), player, EquipmentSlot.HEAD);
    }

    /** Shared client prediction and server movement: no persistent player base changes. */
    public static void movement(Player player) {
        String form = ShapeHooks.form(player);
        if (player.isSpectator() || player.getAbilities().flying) return;
        if (player.level().isClientSide() && MorphActions.flapImpulse(form) > 0
                && player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER))
            player.setDeltaMovement(player.getDeltaMovement().multiply(0.65, 0.2, 0.65));
        if ("minecraft:chicken".equals(form) && !player.isShiftKeyDown() && !player.onGround() && !player.isInWater()
                && player.getDeltaMovement().y < 0) {
            player.setDeltaMovement(player.getDeltaMovement().multiply(1, 0.6, 1));
            player.resetFallDistance();
        }
        if (("minecraft:slime".equals(form) || "minecraft:magma_cube".equals(form)) && (player.isInWater() || player.isInLava()))
            player.setDeltaMovement(player.getDeltaMovement().add(0, 0.07, 0));
        if ("minecraft:iron_golem".equals(form)) {
            if (player.isInWater()) {
                var velocity = player.getDeltaMovement();
                if (player.horizontalCollision) player.setDeltaMovement(velocity.x, 0.07, velocity.z);
                else if (velocity.y > -0.07) player.setDeltaMovement(velocity.add(0, -0.07, 0));
            } else if (Boolean.TRUE.equals(SINK_WATER.get(player))) player.setDeltaMovement(player.getDeltaMovement().add(0, 0.32, 0));
            SINK_WATER.put(player, player.isInWater());
        } else SINK_WATER.remove(player);
        if (!player.isInWater() && player.onGround()) {
            double multiplier = landMultiplier(form);
            if (multiplier != 1) player.setDeltaMovement(player.getDeltaMovement().scale(multiplier));
        }
    }

    public static double swimMultiplier(String form) {
        if (form == null) return 1;
        return switch (form) {
            case "minecraft:cod", "minecraft:guardian", "minecraft:elder_guardian" -> 3;
            case "minecraft:salmon" -> 3.5;
            case "minecraft:dolphin", "minecraft:axolotl" -> 4;
            case "minecraft:turtle" -> 2.5;
            case "minecraft:squid", "minecraft:glow_squid", "minecraft:pufferfish", "minecraft:tropical_fish" -> 2;
            case "minecraft:nautilus", "minecraft:zombie_nautilus" -> 1.2;
            case "minecraft:tadpole" -> 3;
            default -> 1;
        };
    }

    public static double landMultiplier(String form) {
        if (form == null) return 1;
        return switch (form) {
            case "minecraft:cod", "minecraft:salmon", "minecraft:dolphin", "minecraft:axolotl" -> 0.1;
            case "minecraft:pufferfish", "minecraft:tropical_fish", "minecraft:turtle" -> 0.05;
            case "minecraft:squid", "minecraft:glow_squid" -> 0.025;
            case "minecraft:tadpole" -> 0.05;
            case "minecraft:nautilus", "minecraft:zombie_nautilus" -> 0.5;
            default -> 1;
        };
    }

    public static void cleanup(ServerPlayer player) {
        MorphActions.cleanup(player);
        DRY_TIME.remove(player);
        BEE_WATER_TICKS.remove(player);
        SINK_WATER.remove(player);
        MorphInteractions.cleanup(player);
    }

    private static final class DryTime {
        final String form;
        int ticks;
        DryTime(String form) { this.form = form; }
    }
}
