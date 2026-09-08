package me.ichun.mods.morph.fabric;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import me.ichun.mods.morph.ability.MorphAttributes;
import me.ichun.mods.morph.shape.MorphDimensions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;

public final class StabilityGameTests {
    @net.fabricmc.fabric.api.gametest.v1.GameTest(maxTicks = 100)

    public void allVanillaAttributes(GameTestHelper helper) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "stability-test"), false);
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {};
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        player.connection = new ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        for (String invalid : new String[] {"", "BAD ID", "minecraft:missing_form", "other:pig", "minecraft:player", "minecraft:item"}) {
            helper.assertTrue(!MorphDimensions.supports(helper.getLevel(), invalid), "Unsupported form: " + invalid);
        }
        int checked = 0;
        for (var type : BuiltInRegistries.ENTITY_TYPE) {
            var id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (!id.getNamespace().equals("minecraft") || !MorphDimensions.supports(helper.getLevel(), id.toString())) continue;
            MorphAttributes.tick(player, id.toString());
            helper.assertTrue(Float.isFinite(player.getHealth()) && player.getHealth() > 0, "Finite living health for " + id);
            for (var attribute : java.util.List.of(Attributes.MAX_HEALTH, Attributes.MOVEMENT_SPEED, Attributes.ATTACK_DAMAGE, Attributes.ARMOR)) {
                helper.assertTrue(Double.isFinite(player.getAttributeValue(attribute)), "Finite attribute for " + id);
            }
            MorphAttributes.cleanup(player);
            helper.assertTrue(player.getMaxHealth() == 20.0F, "Health restored after " + id);
            helper.assertTrue(!player.getAttribute(Attributes.MAX_HEALTH).hasModifier(MorphAttributes.MODIFIER), "No residual health modifier for " + id);
            checked++;
        }
        helper.assertTrue(checked > 50, "Exercised vanilla living adapters: " + checked);
        helper.succeed();
    }
}
