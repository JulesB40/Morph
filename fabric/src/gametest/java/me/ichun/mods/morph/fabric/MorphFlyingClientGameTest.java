package me.ichun.mods.morph.fabric;

import me.ichun.mods.morph.client.MorphRenderSnapshots;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.BatRenderState;
import net.minecraft.client.renderer.entity.state.BeeRenderState;

public final class MorphFlyingClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksDownload();
            var server = world.getServer();
            server.runCommand("gamemode survival @a");
            for (String form : new String[]{"bat", "bee"}) {
                server.runCommand("morph grant @p minecraft:" + form);
                server.runCommand("execute as @a run morph select minecraft:" + form);
                context.waitFor(client -> client.player != null && client.player.getAbilities().mayfly, 200);
                context.waitTicks(120);
                server.runCommand("tp @a 0 160 0");
                world.getConnection().waitForClientboundPackets();
                context.getInput().holdKey(options -> options.keyJump);
                context.waitTicks(1);
                context.getInput().releaseKey(options -> options.keyJump);
                context.waitTicks(1);
                context.getInput().holdKey(options -> options.keyJump);
                try {
                    context.waitFor(client -> client.player.getAbilities().flying, 40);
                } finally {
                    context.getInput().releaseKey(options -> options.keyJump);
                }
                double[] altitude = new double[1];
                context.waitTicks(10);
                context.runOnClient(client -> altitude[0] = client.player.getY());
                context.waitTicks(40);
                context.runOnClient(client -> {
                    var player = client.player;
                    if (!player.getAbilities().flying || Math.abs(player.getY() - altitude[0]) > .5)
                        throw new AssertionError(form + " did not sustain hovering");
                    var source = (AvatarRenderState) client.getEntityRenderDispatcher().getRenderer(player).createRenderState(player, .5F);
                    var snapshot = MorphRenderSnapshots.extract(player, source, "minecraft:" + form);
                    if (form.equals("bat") && (!(snapshot instanceof BatRenderState bat) || !bat.flyAnimationState.isStarted() || bat.isResting))
                        throw new AssertionError("Bat wing animation inactive");
                    if (form.equals("bee") && (!(snapshot instanceof BeeRenderState bee) || bee.isOnGround))
                        throw new AssertionError("Bee is not in flying pose");
                });
                server.runCommand("execute as @a run morph reset");
                context.waitFor(client -> !client.player.getAbilities().mayfly && !client.player.getAbilities().flying, 200);
            }
        }
    }
}
