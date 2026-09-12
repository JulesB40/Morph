package me.ichun.mods.morph.lab;

import com.google.gson.Gson;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import me.ichun.mods.morph.model.sound.mixin.EntitySoundAccess;
import me.ichun.mods.morph.model.sound.mixin.LivingSoundAccess;
import me.ichun.mods.morph.server.MorphSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** Test-only native entrypoint probes. Sound emissions are recorded before network/mixer delivery. */
public final class NativeSoundChecks {
    private record Emission(String sound, float volume, float pitch) {}
    private NativeSoundChecks() {}

    public static void verify(GameTestHelper helper, ServerPlayer fixture) {
        List<Emission> emitted = new ArrayList<>();
        // Reuse only the fixture's authoritative UUID. This recorder never joins the level.
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(fixture.getUUID(), "lab-sound"), fixture.clientInformation()) {
            @Override public void playSound(SoundEvent sound, float volume, float pitch) {
                if (!isSilent()) emitted.add(new Emission(sound.location().toString(), volume, pitch));
            }
        };
        var forms = helper.getLevel().getServer().overworld().getDataStorage()
                .computeIfAbsent(MorphSavedData.TYPE).collection(player.getUUID());
        try {
            forms.unlock("minecraft:pig");
            forms.select("minecraft:pig", 0);
            var sounds = (LivingSoundAccess) player;
            check(helper, "pig_hurt", "minecraft:entity.pig.hurt", sounds.morph$getHurtSound(player.damageSources().generic()));
            check(helper, "pig_death", "minecraft:entity.pig.death", sounds.morph$getDeathSound());
            check(helper, "pig_swim", "minecraft:entity.generic.swim", ((EntitySoundAccess) player).morph$getSwimSound());
            check(helper, "pig_small_fall", "minecraft:entity.generic.small_fall", player.getFallSounds().small());
            check(helper, "pig_big_fall", "minecraft:entity.generic.big_fall", player.getFallSounds().big());
            ((EntitySoundAccess) player).morph$playStepSound(BlockPos.ZERO, Blocks.STONE.defaultBlockState());
            helper.assertValueEqual(emitted.size(), 1, "One native pig step emission");
            helper.assertValueEqual(emitted.getFirst(), new Emission("minecraft:entity.pig.step", .15F, 1F), "Pig step ID/volume/pitch");
            emit("pig_step", "minecraft:entity.pig.step", emitted.getFirst().sound);
            player.setSilent(true);
            ((EntitySoundAccess) player).morph$playStepSound(BlockPos.ZERO, Blocks.STONE.defaultBlockState());
            helper.assertValueEqual(emitted.size(), 1, "Silent player suppresses forwarded step");
            player.setSilent(false);

            // 26.2's only native OverrideConsumeSound implementation is WanderingTrader.
            // It chooses milk for a milk bucket and potion for every other stack, including food.
            forms.unlock("minecraft:wandering_trader");
            forms.select("minecraft:wandering_trader", 20);
            consume(helper, player, emitted, Items.MILK_BUCKET.getDefaultInstance(), "minecraft:entity.wandering_trader.drink_milk");
            consume(helper, player, emitted, Items.POTION.getDefaultInstance(), "minecraft:entity.wandering_trader.drink_potion");
            consume(helper, player, emitted, Items.APPLE.getDefaultInstance(), "minecraft:entity.wandering_trader.drink_potion");

            forms.reset();
            check(helper, "self_hurt", "minecraft:entity.player.hurt", sounds.morph$getHurtSound(player.damageSources().generic()));
            consume(helper, player, emitted, Items.APPLE.getDefaultInstance(), "minecraft:entity.generic.eat");
        } finally {
            forms.reset();
            player.discard();
        }
    }

    private static void consume(GameTestHelper helper, ServerPlayer player, List<Emission> emitted,
            ItemStack stack, String expected) {
        emitted.clear();
        var consumable = stack.get(DataComponents.CONSUMABLE);
        helper.assertTrue(consumable != null, "Fixture has a consumable component");
        consumable.emitParticlesAndSounds(player.getRandom(), player, stack, 0);
        helper.assertValueEqual(emitted.size(), 1, "Exactly one consume sound before network delivery");
        helper.assertValueEqual(emitted.getFirst().sound, expected, "Native consumable override");
        emit("consume_" + stack.getItem(), expected, emitted.getFirst().sound);
    }

    private static void check(GameTestHelper helper, String scenario, String expected, SoundEvent actual) {
        String value = actual == null ? "silent" : actual.location().toString();
        emit(scenario, expected, value);
        helper.assertValueEqual(value, expected, scenario);
    }

    private static void emit(String scenario, String expected, String actual) {
        System.out.println("MORPH_LAB_EVENT " + new Gson().toJson(Map.of(
                "schema", "morph-lab.native-sounds.v1", "scenario", scenario,
                "expected", expected, "actual", actual, "scope", "native getters and recorded playSound call; no mixer/network assertion")));
    }
}
