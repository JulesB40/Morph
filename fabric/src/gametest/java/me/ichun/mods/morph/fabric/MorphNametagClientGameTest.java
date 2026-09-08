package me.ichun.mods.morph.fabric;

import me.ichun.mods.morph.client.MorphRenderSnapshots;
import me.ichun.mods.morph.client.nametag.MorphNameTags;
import me.ichun.mods.morph.fabric.client.MorphFabricClient;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.chat.Component;

/** Exercises actual command -> server save -> packet -> render snapshot behavior. */
public final class MorphNametagClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksDownload();
            var server = world.getServer();
            server.runCommand("morph grant @p minecraft:pig");
            server.runCommand("execute as @a run morph select minecraft:pig");
            context.waitFor(client -> client.player != null && "minecraft:pig".equals(MorphFabricClient.FORMS.get(client.player.getUUID())), 200);
            context.waitTicks(120);
            for (String command : new String[] {"off", "on", "toggle"}) {
                boolean visible = command.equals("on");
                server.runCommand("execute as @a run morph nametag " + command);
                context.waitFor(client -> MorphNameTags.visible(client.player.getUUID()) == visible, 200);
                context.runOnClient(client -> {
                    var player = client.player;
                    var source = (AvatarRenderState) client.getEntityRenderDispatcher().getRenderer(player).createRenderState(player, .5F);
                    // An observer's vanilla extraction supplies these; self-tags are normally absent.
                    source.nameTag = Component.literal("Observer-visible player name");
                    source.scoreText = Component.literal("10 points");
                    var snapshot = MorphRenderSnapshots.extract(player, source, "minecraft:pig");
                    if (snapshot == null || (snapshot.nameTag != null) != visible || (snapshot.scoreText != null) != visible)
                        throw new AssertionError("Nametag visibility failed for " + command);
                });
            }
            context.runOnClient(client -> {
                client.gui.setScreen(new me.ichun.mods.morph.ui.MorphScreen(id -> {}));
                var screen = (me.ichun.mods.morph.ui.MorphScreen) client.gui.screen();
                screen.update(java.util.List.of("minecraft:pig"), "minecraft:pig");
                boolean found = screen.children().stream().anyMatch(widget -> widget instanceof net.minecraft.client.gui.components.Button button
                        && button.getMessage().getString().equals("Morphed nametag: Hidden"));
                if (!found) throw new AssertionError("Missing synchronized selector toggle");
            });
            context.takeScreenshot("morph-nametag-toggle");
            for (boolean expected : new boolean[] {true, false}) {
                context.runOnClient(client -> {
                    var screen = client.gui.screen();
                    var toggle = screen.children().stream()
                            .filter(widget -> widget instanceof net.minecraft.client.gui.components.Button button
                                    && button.getMessage().getString().startsWith("Morphed nametag:"))
                            .map(widget -> (net.minecraft.client.gui.components.Button) widget).findFirst().orElseThrow();
                    toggle.onPress(null);
                });
                context.waitFor(client -> MorphNameTags.visible(client.player.getUUID()) == expected, 200);
            }
            context.runOnClient(client -> client.gui.setScreen(null));
            server.runCommand("execute as @a run morph reset");
            context.waitFor(client -> !MorphFabricClient.FORMS.containsKey(client.player.getUUID()), 200);
            context.waitTicks(120);
            context.runOnClient(client -> {
                var player = client.player;
                var source = (AvatarRenderState) client.getEntityRenderDispatcher().getRenderer(player).createRenderState(player, .5F);
                source.nameTag = Component.literal("Normal player name");
                MorphRenderSnapshots.extract(player, source, "");
                if (source.nameTag == null) throw new AssertionError("Normal player nametag was hidden");
            });
        }
    }
}
