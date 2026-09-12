package me.ichun.mods.morph.lab;

import com.google.gson.Gson;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Test-only real-server damage probes; the native Husk supplies the effect oracle. */
public final class HuskDamageProbes {
    public enum Case {
        EMPTY_HAND_DURATION, HELD_ITEM, OFFHAND_ITEM, REJECTED_DAMAGE
    }

    /** Loader adapters supply authoritative form state, without replacing the global resolver. */
    public interface Adapter {
        void selectForm(ServerPlayer player, String form);
        String activeForm(ServerPlayer player);
        void resetForm(ServerPlayer player);

        /** Exercise the real damage pipeline and its installed mixin, never a trait helper. */
        default boolean damage(ServerPlayer attacker, LivingEntity target) {
            return target.hurtServer(attacker.level(), attacker.damageSources().playerAttack(attacker), 1.0F);
        }
    }

    private static final Gson JSON = new Gson();

    private HuskDamageProbes() {}

    /** Register each case as a separate GameTest so one expected baseline failure cannot hide others. */
    public static void run(GameTestHelper helper, Case scenario, Adapter adapter) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("schema", "morph-lab.husk-damage.v1");
        event.put("scenario", "husk_" + scenario.name().toLowerCase(java.util.Locale.ROOT));
        event.put("lane", "native-server");
        event.put("action_path", "server damage callback; not client input or attack packet");
        event.put("oracle", "native Husk.doHurtTarget; immediate target Hunger readback");
        event.put("server_tick", helper.getLevel().getGameTime());
        event.put("monotonic_ns", System.nanoTime());
        event.put("difficulty", helper.getLevel().getDifficulty().name());
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "lab-husk"), false);
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {};
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        player.connection = new ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        var nativeHusk = helper.spawn(EntityTypes.HUSK, 1, 2, 1);
        var nativeVictim = helper.spawn(EntityTypes.COW, 2, 2, 1);
        var morphVictim = helper.spawn(EntityTypes.COW, 2, 2, 2);
        boolean recorded = false;
        try {
            nativeHusk.setNoAi(true);
            nativeVictim.setNoAi(true);
            morphVictim.setNoAi(true);
            nativeHusk.setPos(helper.absoluteVec(new Vec3(1.5, 2, 1.5)));
            player.setPos(nativeHusk.position());
            nativeHusk.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            nativeHusk.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            if (scenario == Case.HELD_ITEM || scenario == Case.OFFHAND_ITEM) {
                var slot = scenario == Case.HELD_ITEM ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
                nativeHusk.setItemSlot(slot, new ItemStack(Items.STICK));
                player.setItemSlot(slot, new ItemStack(Items.STICK));
            }
            boolean rejected = scenario == Case.REJECTED_DAMAGE;
            nativeVictim.setInvulnerable(rejected);
            morphVictim.setInvulnerable(rejected);
            adapter.selectForm(player, "minecraft:husk");
            require("minecraft:husk".equals(adapter.activeForm(player)), "Adapter did not select the Husk form");
            require(helper.getLevel().getDifficulty() != Difficulty.PEACEFUL, "Use a non-peaceful test server");
            float localDifficulty = helper.getLevel().getCurrentDifficultyAt(player.blockPosition()).getEffectiveDifficulty();
            event.put("effective_local_difficulty", localDifficulty);
            event.put("attacker_block", player.blockPosition().toShortString());
            require(player.blockPosition().equals(nativeHusk.blockPosition()), "Attackers must share local difficulty");
            // An integer difficulty cannot distinguish truncation before versus after multiplication.
            if (scenario == Case.EMPTY_HAND_DURATION || scenario == Case.OFFHAND_ITEM) {
                require(localDifficulty != (int) localDifficulty, "Duration fixture needs fractional effective local difficulty");
            }
            require(nativeVictim.getEffect(MobEffects.HUNGER) == null && morphVictim.getEffect(MobEffects.HUNGER) == null,
                    "Fresh victims must have no pre-existing Hunger");
            event.put("native_before", snapshot(nativeVictim));
            event.put("morph_before", snapshot(morphVictim));
            float nativeHealth = nativeVictim.getHealth();
            float morphHealth = morphVictim.getHealth();
            boolean nativeAccepted = nativeHusk.doHurtTarget(helper.getLevel(), nativeVictim);
            boolean morphAccepted = adapter.damage(player, morphVictim);
            // Read both immediately, without an intervening world tick or potion-duration decrement.
            event.put("native_after", snapshot(nativeVictim));
            event.put("morph_after", snapshot(morphVictim));
            event.put("native_damage_accepted", nativeAccepted);
            event.put("morph_damage_accepted", morphAccepted);
            event.put("native_health_loss", nativeHealth - nativeVictim.getHealth());
            event.put("morph_health_loss", morphHealth - morphVictim.getHealth());
            require(nativeAccepted == !rejected && (rejected ? nativeVictim.getHealth() == nativeHealth : nativeVictim.getHealth() < nativeHealth),
                    "Native reference attack did not meet the damage precondition");
            require(morphAccepted == !rejected && (rejected ? morphVictim.getHealth() == morphHealth : morphVictim.getHealth() < morphHealth),
                    "Morph damage action did not meet the damage precondition");
            var nativeEffect = nativeVictim.getEffect(MobEffects.HUNGER);
            var morphEffect = morphVictim.getEffect(MobEffects.HUNGER);
            if (scenario == Case.HELD_ITEM || rejected) {
                require(nativeEffect == null, "Native control unexpectedly applied Hunger");
            } else {
                require(nativeEffect != null, "Native empty-hand reference did not produce a Hunger instance");
            }
            var failures = new ArrayList<String>();
            if ((nativeEffect == null) != (morphEffect == null)) failures.add("Hunger presence differs from native Husk");
            if (nativeEffect != null && morphEffect != null) {
                if (nativeEffect.getDuration() != morphEffect.getDuration()) failures.add("Hunger duration: native=" + nativeEffect.getDuration() + ", morph=" + morphEffect.getDuration());
                if (nativeEffect.getAmplifier() != morphEffect.getAmplifier()) failures.add("Hunger amplifier differs from native Husk");
            }
            event.put("status", failures.isEmpty() ? "pass" : "fail");
            event.put("failures", failures);
            emit(event);
            recorded = true;
            helper.assertTrue(failures.isEmpty(), scenario + ": " + String.join("; ", failures));
            helper.succeed();
        } catch (RuntimeException | AssertionError failure) {
            if (!recorded) {
                event.put("status", "infrastructure_failure");
                event.put("reason", failure.toString());
                emit(event);
            }
            throw failure;
        } finally {
            try {
                adapter.resetForm(player);
            } finally {
                nativeHusk.discard();
                nativeVictim.discard();
                morphVictim.discard();
                player.discard();
                channel.finishAndReleaseAll();
            }
        }
    }

    private static Map<String, Object> snapshot(LivingEntity victim) {
        var effect = victim.getEffect(MobEffects.HUNGER);
        return Map.of("health", victim.getHealth(), "hunger_present", effect != null,
                "hunger_duration_ticks", effect == null ? -1 : effect.getDuration(),
                "hunger_amplifier", effect == null ? -1 : effect.getAmplifier());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static synchronized void emit(Map<String, Object> event) {
        String line = JSON.toJson(event);
        System.out.println("MORPH_LAB_EVENT " + line);
        String destination = System.getProperty("morph.lab.events", "");
        if (destination.isBlank()) return;
        try {
            Path file = Path.of(destination).toAbsolutePath();
            Files.createDirectories(file.getParent());
            Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot write lab event to " + destination, failure);
        }
    }
}
