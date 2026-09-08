package me.ichun.mods.morph.ability;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/** Original default attribute policy, using owned transient modifiers rather than changing player bases. */
public final class MorphAttributes {
    public static final Identifier MODIFIER = Identifier.fromNamespaceAndPath("morph", "form_attribute");
    private static final List<Holder<Attribute>> SUPPORTED = List.of(Attributes.MAX_HEALTH,
            Attributes.KNOCKBACK_RESISTANCE, Attributes.MOVEMENT_SPEED, Attributes.ATTACK_DAMAGE,
            Attributes.ATTACK_KNOCKBACK, Attributes.ATTACK_SPEED, Attributes.ARMOR, Attributes.LUCK,
            Attributes.JUMP_STRENGTH);
    private static final Map<Level, Map<String, Map<Holder<Attribute>, Double>>> DEFAULTS = new WeakHashMap<>();
    private static final Map<ServerPlayer, Transition> TRANSITIONS = new WeakHashMap<>();
    private record Transition(String form, long start, Map<Holder<Attribute>, Double> amounts) {}
    private static java.util.function.BiConsumer<ServerPlayer, HealthSnapshot> healthSync = (player, health) -> {};
    private MorphAttributes() {}

    public static void setHealthSync(java.util.function.BiConsumer<ServerPlayer, HealthSnapshot> sender) {
        healthSync = java.util.Objects.requireNonNull(sender);
    }

    /** Called before publishing an accepted select/reset. Repeated synchronization does not restart it. */
    public static void begin(ServerPlayer player, String form) {
        Map<Holder<Attribute>, Double> amounts = new HashMap<>();
        for (var attribute : SUPPORTED) {
            var instance = player.getAttribute(attribute);
            var modifier = instance == null ? null : instance.getModifier(MODIFIER);
            amounts.put(attribute, modifier == null ? 0.0 : modifier.amount());
        }
        TRANSITIONS.put(player, new Transition(form, time(player), Map.copyOf(amounts)));
    }

    public static void tick(ServerPlayer player, String form) {
        if (!player.isAlive()) { cleanup(player); return; }
        form = form == null ? "" : form;
        var target = defaults(player.level(), form);
        var transition = TRANSITIONS.get(player);
        if (transition != null && !transition.form.equals(form)) {
            TRANSITIONS.remove(player);
            transition = null;
        }
        float progress = transition == null ? 1.0F : progress(time(player) - transition.start);
        float oldMaximum = player.getMaxHealth();
        float health = player.getHealth();
        for (var attribute : SUPPORTED) {
            var instance = player.getAttribute(attribute);
            if (instance == null) continue;
            double amount = target.containsKey(attribute) ? target.get(attribute) - instance.getBaseValue() : 0.0;
            if (transition != null) {
                double start = transition.amounts.getOrDefault(attribute, 0.0);
                amount = start + (amount - start) * progress;
            }
            var current = instance.getModifier(MODIFIER);
            if (amount == 0.0) { if (current != null) instance.removeModifier(MODIFIER); }
            else if (current == null || Math.abs(current.amount() - amount) > 1.0E-9)
                instance.addOrUpdateTransientModifier(new AttributeModifier(MODIFIER, amount, AttributeModifier.Operation.ADD_VALUE));
        }
        preserveHealth(player, health, oldMaximum);
        if (progress >= 1.0F) TRANSITIONS.remove(player);
    }

    public static void cleanup(ServerPlayer player) {
        TRANSITIONS.remove(player);
        float maximum = player.getMaxHealth();
        float health = player.getHealth();
        for (var attribute : SUPPORTED) {
            var instance = player.getAttribute(attribute);
            if (instance != null) instance.removeModifier(MODIFIER);
        }
        preserveHealth(player, health, maximum);
    }

    private static void preserveHealth(ServerPlayer player, float health, float previousMax) {
        if (health > 0.0F && previousMax > 0.0F && previousMax != player.getMaxHealth()) {
            player.setHealth(Math.clamp(health / previousMax, 0.0F, 1.0F) * player.getMaxHealth());
            if (player.getHealth() > 0.0F) healthSync.accept(player, new HealthSnapshot(health, player.getHealth()));
        }
    }

    /** Autosaves use unmorphed health units because transient attribute modifiers are not serialized. */
    public static float healthForSave(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return entity.getHealth();
        var actual = player.getAttribute(Attributes.MAX_HEALTH);
        if (actual == null || !actual.hasModifier(MODIFIER) || player.getMaxHealth() <= 0.0F) return player.getHealth();
        var baseline = new AttributeInstance(actual.getAttribute(), ignored -> {});
        baseline.replaceFrom(actual);
        baseline.removeModifier(MODIFIER);
        return (float)(player.getHealth() / player.getMaxHealth() * baseline.getValue());
    }

    static float progress(long ticks) {
        double phase = Math.clamp((ticks / 100.0 - 0.125) / 0.75, 0.0, 1.0);
        return (float)((1.0 - Math.cos(Math.PI * phase)) * 0.5);
    }

    private static long time(ServerPlayer player) { return player.level().getServer().overworld().getGameTime(); }

    private static Map<Holder<Attribute>, Double> defaults(Level level, String form) {
        if (form.isEmpty()) return Map.of();
        return DEFAULTS.computeIfAbsent(level, ignored -> new HashMap<>()).computeIfAbsent(form, id -> {
            var identifier = Identifier.tryParse(id);
            var type = identifier == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(identifier).orElse(null);
            if (type == null) return Map.of();
            var entity = type.create(level, EntitySpawnReason.LOAD);
            if (!(entity instanceof LivingEntity living) || living instanceof Avatar) return Map.of();
            Map<Holder<Attribute>, Double> values = new HashMap<>();
            for (var attribute : SUPPORTED) {
                var instance = living.getAttribute(attribute);
                if (instance == null) continue;
                double value = instance.getBaseValue();
                if (attribute == Attributes.MAX_HEALTH) value = Math.min(value, 20.0);
                if (attribute == Attributes.MOVEMENT_SPEED) value = Math.min(value, 0.1);
                if (Double.isFinite(value)) values.put(attribute, value);
            }
            return Map.copyOf(values);
        });
    }
}
