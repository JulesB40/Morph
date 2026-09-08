package me.ichun.mods.morph;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** Entry point for the 26.2 port. Legacy implementation is retained under legacy/. */
@Mod(Morph.MOD_ID)
public final class Morph {
    public static final String MOD_ID = "morph";

    public Morph(IEventBus modBus) {
        me.ichun.mods.morph.shape.ShapeHooks.setFormResolver(player ->
                player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                        ? me.ichun.mods.morph.server.MorphService.collection(serverPlayer).activeForm() : null);
        modBus.addListener(me.ichun.mods.morph.network.MorphNetwork::register);
        me.ichun.mods.morph.server.MorphService.initialize();
        LogUtils.getLogger().info("Morph 26.2 development port loaded");
    }
}
