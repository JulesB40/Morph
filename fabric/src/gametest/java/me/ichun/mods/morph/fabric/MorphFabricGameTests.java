package me.ichun.mods.morph.fabric;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import me.ichun.mods.morph.server.MorphSavedData;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.GameType;

public final class MorphFabricGameTests {
    @GameTest(maxTicks = 100)
    public void commandsAndGeometry(GameTestHelper helper) {
        var player = player(helper);
        var forms = MorphFabric.data(helper.getLevel().getServer()).collection(player.getUUID());
        helper.assertValueEqual(command(player, "morph select minecraft:pig"), 0, "Unowned form rejected");
        forms.unlock("minecraft:pig"); forms.unlock("minecraft:cow"); forms.unlock("minecraft:item");
        helper.assertValueEqual(command(player, "morph select minecraft:item"), 0, "Nonliving form rejected");
        helper.assertValueEqual(command(player, "morph select minecraft:pig"), 1, "Namespaced selection accepted");
        helper.assertValueEqual(forms.activeForm(), "minecraft:pig", "Selected pig");
        helper.assertValueEqual(command(player, "morph select minecraft:pig"), 1, "Selecting current form succeeds without consuming cooldown");
        helper.assertTrue(player.getBbHeight() < 1.0F, "Pig shape replaces player server hitbox");
        helper.assertValueEqual(command(player, "morph select minecraft:cow"), 0, "Cooldown enforced");
        helper.assertValueEqual(command(player, "morph reset"), 1, "Reset works");
        helper.assertTrue(player.getBbHeight() > 1.5F, "Player shape restored");
        helper.assertValueEqual(command(player, "morph select minecraft:cow"), 0, "Reset cannot bypass cooldown");
        helper.runAfterDelay(21, () -> {
            helper.assertValueEqual(command(player, "morph select minecraft:cow"), 1, "Cooldown recovers");
            player.setGameMode(GameType.SPECTATOR);
            helper.assertValueEqual(command(player, "morph select minecraft:pig"), 0, "Spectator cannot select");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 100)
    public void killAcquiresAndPersists(GameTestHelper helper) {
        var player = player(helper);
        var forms = MorphFabric.data(helper.getLevel().getServer()).collection(player.getUUID());
        var pig = helper.spawn(EntityTypes.PIG, 1, 1, 1);
        pig.hurtServer(helper.getLevel(), player.damageSources().playerAttack(player), 1000.0F);
        helper.assertTrue(pig.isDeadOrDying(), "Attack killed pig");
        helper.assertTrue(forms.ownedForms().contains("minecraft:pig"), "Fabric death event acquired pig");
        helper.assertValueEqual(command(player, "morph select minecraft:pig"), 1, "Acquired form selectable");
        var saved = MorphFabric.data(helper.getLevel().getServer());
        helper.assertTrue(saved.isDirty(), "Selection marks saved data dirty");
        var json = MorphSavedData.CODEC.encodeStart(JsonOps.INSTANCE, saved).getOrThrow();
        var restored = MorphSavedData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        helper.assertValueEqual(restored.collection(player.getUUID()).activeForm(), "minecraft:pig", "Selection persisted");
        helper.assertTrue(restored.collection(UUID.randomUUID()).ownedForms().isEmpty(), "Ownership isolated by UUID");
        helper.succeed();
    }

    @GameTest(maxTicks = 100)
    public void flightAndFallTraits(GameTestHelper helper) {
        var player = player(helper);
        var forms = MorphFabric.data(helper.getLevel().getServer()).collection(player.getUUID());
        forms.unlock("minecraft:bat");
        helper.assertFalse(player.getAbilities().mayfly, "Survival starts without flight");
        helper.assertValueEqual(command(player, "morph select minecraft:bat"), 1, "Select flying form");
        helper.assertTrue(player.getAbilities().mayfly, "Selection immediately grants flight");
        helper.assertFalse(me.ichun.mods.morph.ability.MorphAbilities.packForSave(player, player.getAbilities()).mayFly(), "Morph flight is transient when saving");
        float health = player.getHealth();
        player.hurtServer(helper.getLevel(), player.damageSources().fall(), 5.0F);
        helper.assertValueEqual(player.getHealth(), health, "Fabric damage event prevents bat fall damage");
        helper.assertValueEqual(command(player, "morph reset"), 1, "Reset flying form");
        helper.assertFalse(player.getAbilities().mayfly, "Reset removes Morph flight");
        player.getAbilities().mayfly = true;
        me.ichun.mods.morph.ability.MorphAbilities.tick(player, "minecraft:bat");
        me.ichun.mods.morph.ability.MorphAbilities.cleanup(player);
        helper.assertTrue(player.getAbilities().mayfly, "Pre-existing flight survives cleanup");
        helper.succeed();
    }

    private static int command(ServerPlayer player, String command) {
        try { return player.level().getServer().getCommands().getDispatcher().execute(command, player.createCommandSourceStack()); }
        catch (Exception failure) { throw new IllegalStateException("Command failed: " + command, failure); }
    }

    private static ServerPlayer player(GameTestHelper helper) {
        var id = UUID.randomUUID();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(id, "morph-" + id.toString().substring(0, 8)), false);
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        player.connection = new ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, player, cookie);
        player.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(0.5, 2.0, 0.5)));
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }
}
