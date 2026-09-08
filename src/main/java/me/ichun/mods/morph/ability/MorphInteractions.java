package me.ichun.mods.morph.ability;

import java.util.List;
import java.util.Set;
import me.ichun.mods.morph.shape.ShapeHooks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;

/** Original Classic hostility, avoidance, and unsaddled passenger rules. */
public final class MorphInteractions {
    private static final Set<String> HOSTILE = Set.of(
        "blaze", "cave_spider", "creeper", "drowned", "elder_guardian", "ender_dragon", "enderman",
        "endermite", "evoker", "ghast", "giant", "guardian", "hoglin", "husk", "illusioner",
        "magma_cube", "phantom", "piglin", "piglin_brute", "pillager", "ravager", "shulker",
        "silverfish", "skeleton", "slime", "spider", "stray", "vex", "vindicator", "witch",
        "wither", "wither_skeleton", "zoglin", "zombie", "zombie_villager", "zombified_piglin");
    private static final Set<String> RIDEABLE = Set.of("horse", "llama", "skeleton_horse", "trader_llama", "zombie_horse");
    private static final java.util.Map<Player, Boolean> MOUNTS = new java.util.WeakHashMap<>();
    private MorphInteractions() {}
    public static void mounted(Player mount) { MOUNTS.put(mount, Boolean.TRUE); }
    public static void cleanup(Player player) {
        if (MOUNTS.remove(player) != null) player.ejectPassengers();
    }
    private static String vanilla(String form) {
        return form != null && form.startsWith("minecraft:") ? form.substring(10) : "";
    }
    public static boolean rideable(Player player) {
        return player.isAlive() && !player.isSpectator() && RIDEABLE.contains(vanilla(ShapeHooks.form(player)));
    }
    public static boolean shouldIgnoreTarget(Mob mob, LivingEntity target) {
        if (!(target instanceof Player player) || mob.level().isClientSide()
                || mob.getLastHurtByMob() == player || mob.getLastHurtMob() == player) return false;
        String form = vanilla(ShapeHooks.form(player));
        return HOSTILE.contains(form) || fears(form).stream().anyMatch(fear -> fear.matches(mob));
    }
    private record Fear(String target, double range, double far, double near) {
        boolean matches(Mob mob) {

            if (target.equals("villager")) return mob instanceof net.minecraft.world.entity.npc.villager.AbstractVillager;
            if (target.equals("skeletons")) return mob instanceof net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
            var id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
            return id.getNamespace().equals("minecraft") && id.getPath().equals(target);
        }
    }
    private static final java.util.Map<String, List<Fear>> FEARS = createFears();
    private static java.util.Map<String, List<Fear>> createFears() {
        var rules = new java.util.HashMap<String, List<Fear>>();
        rules.put("cat", List.of(new Fear("creeper", 6, 1, 1.2)));
        rules.put("ocelot", List.of(new Fear("creeper", 6, 1, 1.2)));
        rules.put("guardian", List.of(new Fear("dolphin", 8, 1, 1)));
        rules.put("elder_guardian", List.of(new Fear("dolphin", 8, 1, 1)));
        rules.put("llama", List.of(new Fear("wolf", 24, 1.5, 1.5)));
        rules.put("trader_llama", List.of(new Fear("wolf", 24, 1.5, 1.5)));
        rules.put("polar_bear", List.of(new Fear("fox", 8, 1.6, 1.4)));
        rules.put("wolf", List.of(new Fear("skeletons", 6, 1, 1.2), new Fear("fox", 8, 1.6, 1.4), new Fear("rabbit", 10, 2.2, 2.2)));
        rules.put("drowned", List.of(new Fear("villager", 8, .5, .5)));
        rules.put("husk", List.of(new Fear("villager", 8, .5, .5)));
        rules.put("vex", List.of(new Fear("villager", 8, .5, .5)));
        rules.put("zombie", List.of(new Fear("villager", 8, .5, .5)));
        rules.put("zombie_villager", List.of(new Fear("villager", 8, .5, .5)));
        rules.put("vindicator", List.of(new Fear("villager", 10, .5, .5)));
        rules.put("zoglin", List.of(new Fear("villager", 10, .5, .5)));
        rules.put("evoker", List.of(new Fear("villager", 12, .5, .5)));
        rules.put("illusioner", List.of(new Fear("villager", 12, .5, .5)));
        rules.put("ravager", List.of(new Fear("villager", 12, .5, .5)));
        rules.put("pillager", List.of(new Fear("villager", 15, .5, .5)));
        return java.util.Map.copyOf(rules);
    }
    private static List<Fear> fears(String form) {
        return FEARS.getOrDefault(form, List.of());
    }
    public static void tick(ServerPlayer player, String form) {
        if (MOUNTS.containsKey(player)) {
            if (!rideable(player) || player.isShiftKeyDown()) player.ejectPassengers();
            if (player.getPassengers().isEmpty()) MOUNTS.remove(player);
        }
    }

    /** One goal owned by the mob itself; no global map retains unloaded entities. */
    public static void installFearGoal(PathfinderMob mob, net.minecraft.world.entity.ai.goal.GoalSelector selector) {
        if (List.of("cat", "guardian", "llama", "polar_bear", "wolf", "zombie").stream()
                .flatMap(form -> fears(form).stream()).anyMatch(fear -> fear.matches(mob))) {
            selector.addGoal(1, new MorphFearGoal(mob));
        }
    }

    private static final class MorphFearGoal extends net.minecraft.world.entity.ai.goal.Goal {
        private final PathfinderMob mob;
        private Player threat;
        private Fear fear;
        private net.minecraft.world.level.pathfinder.Path path;
        private long nextSearch;
        MorphFearGoal(PathfinderMob mob) {
            this.mob = mob;
            setFlags(java.util.EnumSet.of(Flag.MOVE));
        }
        @Override public boolean canUse() {
            long now = mob.level().getGameTime();
            if (now < nextSearch) return false;
            nextSearch = now + 20;
            threat = null;
            double nearest = Double.MAX_VALUE;
            for (Player candidate : mob.level().getEntitiesOfClass(Player.class, mob.getBoundingBox().inflate(24, 3, 24))) {
                if (!candidate.isAlive() || candidate.isSpectator()) continue;
                for (Fear candidateFear : fears(vanilla(ShapeHooks.form(candidate)))) {
                    double distance = mob.distanceToSqr(candidate);
                    if (candidateFear.matches(mob) && distance < nearest
                            && candidate.getBoundingBox().inflate(candidateFear.range, 3, candidateFear.range).intersects(mob.getBoundingBox())) {
                        nearest = distance;
                        threat = candidate;
                        fear = candidateFear;
                    }
                }
            }
            if (threat == null) return false;
            // Flat terrain can reject most random vertical samples. Bound retries so a single
            // unlucky destination does not leave the creature idle for the whole search interval.
            for (int attempt = 0; attempt < 4; attempt++) {
                var away = DefaultRandomPos.getPosAway(mob, 16, 7, threat.position());
                if (away == null || threat.distanceToSqr(away) <= threat.distanceToSqr(mob)) continue;
                path = mob.getNavigation().createPath(away.x, away.y, away.z, 0);
                if (path != null) return true;
            }
            return false;
        }
        @Override public boolean canContinueToUse() {
            return threat != null && threat.isAlive() && !threat.isSpectator() && !mob.getNavigation().isDone()
                    && fears(vanilla(ShapeHooks.form(threat))).contains(fear);
        }
        @Override public void start() { mob.getNavigation().moveTo(path, fear.far); }
        @Override public void tick() { mob.getNavigation().setSpeedModifier(mob.distanceToSqr(threat) < 49 ? fear.near : fear.far); }
        @Override public void stop() {
            mob.getNavigation().stop();
            threat = null;
            path = null;
            fear = null;
        }
    }
}
