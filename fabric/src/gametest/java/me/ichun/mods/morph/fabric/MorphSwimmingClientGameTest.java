package me.ichun.mods.morph.fabric;

import me.ichun.mods.morph.client.MorphRenderSnapshots;
import me.ichun.mods.morph.client.animation.MorphSwimState;
import me.ichun.mods.morph.fabric.client.MorphFabricClient;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** Live integrated-world movement and rendering, kept outside the release artifact. */
public final class MorphSwimmingClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            world.getConnection().waitForChunksDownload();
            server.runCommand("tp @a 0 68 0");
            world.getConnection().waitForChunksDownload();
            server.runCommand("fill -24 63 -24 24 73 24 minecraft:stone hollow");
            server.runCommand("fill -23 64 -23 23 72 23 minecraft:water");
            server.runCommand("gamemode survival @a");
            server.runCommand("effect give @a minecraft:water_breathing infinite 0 true");
            server.runCommand("effect give @a minecraft:night_vision infinite 0 true");
            context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            String filter = System.getenv("MORPH_CLIENT_TEST_FORMS");
            String[] forms = filter == null || filter.isBlank()
                ? new String[] {"pig", "cow", "villager", "pillager", "cat", "wolf", "horse", "spider", "drowned", "dolphin", "turtle", "guardian", "squid", "axolotl", "zombie"}
                : filter.split(",");
            for (String form : forms) {
                server.runCommand("morph grant @p minecraft:" + form);
                server.runCommand("execute as @a run morph select minecraft:" + form);
                context.waitFor(client -> client.player != null && ("minecraft:" + form).equals(MorphFabricClient.FORMS.get(client.player.getUUID())), 200);
                context.waitTicks(120);
                server.runCommand("tp @a 0 68 0 0 0");
                world.getConnection().waitForClientboundPackets();
                context.getInput().lookAt(0F, 20F);
                context.getInput().holdKey(options -> options.keyUp);
                context.getInput().holdKey(options -> options.keySprint);
                try {
                    if (!form.equals("zombie")) context.waitFor(client -> client.player != null && client.player.isVisuallySwimming(), 160);
                    context.waitTicks(8);
                    context.runOnClient(client -> {
                        var player = client.player;
                        if (form.equals("zombie") && player.isSwimming()) throw new AssertionError("Ordinary zombie entered live swimming pose");
                        var original = (AvatarRenderState) client.getEntityRenderDispatcher().getRenderer(player).createRenderState(player, .5F);
                        if (me.ichun.mods.morph.client.MorphTransitions.extract(player, original, "minecraft:" + form) != null)
                            throw new AssertionError("Transformation still active for " + form);
                        var extracted = MorphRenderSnapshots.extract(player, original, "minecraft:" + form);
                        if (!(extracted instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState snapshot))
                            throw new AssertionError("Missing living swimming renderer for " + form);
                        if (form.equals("pillager") || form.equals("villager")) {
                            try {
                                // Check the actual renderer rotation, independently of the marker fields.
                                var rotations = net.minecraft.client.renderer.entity.LivingEntityRenderer.class.getDeclaredMethod(
                                    "setupRotations", net.minecraft.client.renderer.entity.state.LivingEntityRenderState.class,
                                    com.mojang.blaze3d.vertex.PoseStack.class, float.class, float.class);
                                rotations.setAccessible(true);
                                var poses = new com.mojang.blaze3d.vertex.PoseStack();
                                rotations.invoke(client.getEntityRenderDispatcher().getRenderer(snapshot), snapshot, poses, snapshot.bodyRot, 1F);
                                var up = poses.last().pose().transformDirection(new org.joml.Vector3f(0, 1, 0));
                                if (Math.abs(up.y) > .65F) throw new AssertionError("Swimming body is not horizontal for " + form + ": " + up);
                            } catch (ReflectiveOperationException failure) { throw new AssertionError("Cannot inspect swimming renderer rotation", failure); }
                        }
                        boolean fast = ((MorphSwimState) snapshot).morph$fastSwimming();
                        if (fast == form.equals("zombie")) throw new AssertionError("Incorrect fast swim state for " + form);
                        if (snapshot instanceof HumanoidRenderState humanoid && ((humanoid.swimAmount > 0) == form.equals("zombie")))
                            throw new AssertionError("Incorrect humanoid swim stroke for " + form);
                    });
                    if (!form.equals("zombie")) context.takeScreenshot("morph-fast-swim-" + form);
                } finally {
                    context.getInput().releaseKey(options -> options.keySprint);
                    context.getInput().releaseKey(options -> options.keyUp);
                }
            }
        }
    }
}
