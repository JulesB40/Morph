package me.ichun.mods.morph.fabric.lab;

import com.google.gson.GsonBuilder;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.ichun.mods.morph.client.MorphRenderSnapshots;
import me.ichun.mods.morph.client.animation.MorphOtherSwimming;
import me.ichun.mods.morph.client.animation.MorphSwimState;
import me.ichun.mods.morph.fabric.MorphFlyingClientGameTest;
import me.ichun.mods.morph.fabric.MorphNametagClientGameTest;
import me.ichun.mods.morph.fabric.client.MorphFabricClient;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.model.animal.sniffer.SnifferModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.SnifferRenderState;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;

/** Developer-only probes. Assertions are evidence, never an instruction to change production behavior. */
public final class MorphLabClientGameTest implements FabricClientGameTest {
    private static final List<String> DEFAULTS = List.of("dragon-renderer", "sniffer-middle-legs",
            "bat-bee-flight", "nametag", "transformation-clip", "intentional-assertion");
    private Path runDir;
    private long sequence;
    private final List<Map<String, Object>> results = new ArrayList<>();

    @Override public void runTest(ClientGameTestContext context) {
        String directory = System.getProperty("morph.lab.runDir");
        if (directory == null || directory.isBlank()) return;
        runDir = Path.of(directory);
        if (!runDir.isAbsolute()) throw new IllegalArgumentException("morph.lab.runDir must be absolute");
        runDir = runDir.normalize();
        try {
            Files.createDirectories(runDir.resolve("scenarios"));
            String selection = System.getProperty("morph.lab.scenarios", String.join(",", DEFAULTS));
            List<String> cases = Arrays.stream(selection.split(",")).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
            if (cases.isEmpty()) throw new IllegalArgumentException("No Morph Lab scenarios selected");
            for (String scenario : cases) {
                if (!DEFAULTS.contains(scenario)) throw new IllegalArgumentException("Unknown Morph Lab scenario: " + scenario);
            }
            for (String scenario : cases) runCase(context, scenario);
            boolean infrastructureFailure = results.stream().anyMatch(r -> "infrastructure_failure".equals(r.get("status")));
            boolean assertionFailure = results.stream().anyMatch(r -> "fail".equals(r.get("status")));
            write(runDir.resolve("result.json"), Map.of("schema_version", 1, "loader", "fabric",
                    "status", infrastructureFailure ? "infrastructure_failure" : assertionFailure ? "fail" : "pass",
                    "role", System.getProperty("morph.lab.role", "baseline"),
                    "run_id", runDir.getParent().getFileName().toString(), "cases", results,
                    "artifacts", List.of("events.ndjson", "scenarios")));
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot write Morph Lab evidence", failure);
        }
    }

    private void runCase(ClientGameTestContext context, String scenario) throws java.io.IOException {
        Path directory = runDir.resolve("scenarios").resolve(scenario);
        Files.createDirectories(directory);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scenario", scenario);
        result.put("loader", "fabric");
        result.put("role", System.getProperty("morph.lab.role", "baseline"));
        result.put("artifacts", List.of("scenarios/" + scenario + "/result.json"));
        result.put("started_at", java.time.Instant.now().toString());
        event(scenario, "start", Map.of());
        try {
            switch (scenario) {
                case "dragon-renderer" -> dragon(context);
                case "sniffer-middle-legs" -> sniffer(context);
                case "bat-bee-flight" -> new MorphFlyingClientGameTest().runTest(context);
                case "nametag" -> new MorphNametagClientGameTest().runTest(context);
                case "transformation-clip" -> clip(context);
                case "intentional-assertion" -> throw new DeliberateAssertion("Morph Lab intentionally wrong oracle: 2 + 2 must equal 5");
                default -> throw new IllegalArgumentException(scenario);
            }
            result.put("status", "pass");
        } catch (DeliberateAssertion expected) {
            result.put("status", "fail");
            result.put("expected_failure", true);
            result.put("failure_kind", "intentional_assertion");
            addFailure(result, expected);
        } catch (AssertionError failure) {
            result.put("status", "fail");
            result.put("expected_failure", false);
            result.put("failure_kind", "assertion");
            addFailure(result, failure);
        } catch (Exception failure) {
            result.put("status", "infrastructure_failure");
            result.put("expected_failure", false);
            result.put("failure_kind", "exception");
            addFailure(result, failure);
        } finally {
            try {
                context.restoreDefaultGameOptions();
            } catch (Exception | AssertionError cleanupFailure) {
                result.put("cleanup_failure", cleanupFailure.toString());
                result.put("status", "infrastructure_failure");
            }
        }
        result.put("finished_at", java.time.Instant.now().toString());
        event(scenario, "result", result);
        write(directory.resolve("result.json"), result);
        results.add(result);
    }

    private void dragon(ClientGameTestContext context) throws Exception {
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksDownload();
            context.runOnClient(client -> {
                var dragon = EntityTypes.ENDER_DRAGON.create(client.level, EntitySpawnReason.LOAD);
                if (dragon == null) throw new IllegalStateException("Native dragon creation failed");
                dragon.setId(client.player.getId());
                var renderer = client.getEntityRenderDispatcher().getRenderer(dragon);
                var nativeState = renderer.createRenderState(dragon, .5F);
                var playerState = (AvatarRenderState) client.getEntityRenderDispatcher().getRenderer(client.player).createRenderState(client.player, .5F);
                Object morphState = MorphRenderSnapshots.extract(client.player, playerState, "minecraft:ender_dragon");
                event("dragon-renderer", "renderer_acceptance", Map.of("scope", "extraction_component",
                        "native_renderer", renderer.getClass().getName(), "native_state", nativeState.getClass().getName(),
                        "morph_state", morphState == null ? "null" : morphState.getClass().getName()));
                if (morphState == null || !nativeState.getClass().isInstance(morphState))
                    throw new AssertionError("Native dragon renderer exists but Morph rejects its render-state type; visible replacement cannot proceed");
            });
        }
    }

    private void sniffer(ClientGameTestContext context) throws Exception {
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksDownload();
            context.runOnClient(client -> {
                var entity = EntityTypes.SNIFFER.create(client.level, EntitySpawnReason.LOAD);
                if (entity == null) throw new IllegalStateException("Native Sniffer creation failed");
                var renderer = client.getEntityRenderDispatcher().getRenderer(entity);
                if (!(renderer instanceof LivingEntityRenderer<?, ?, ?> living) || !(living.getModel() instanceof SnifferModel model))
                    throw new IllegalStateException("Expected actual native SnifferModel");
                String[] names = {"right_front_leg", "left_front_leg", "right_mid_leg", "left_mid_leg", "right_hind_leg", "left_hind_leg"};
                float[][] samples = new float[4][names.length];
                var lookup = model.root().createPartLookup();
                for (int frame = 0; frame < samples.length; frame++) {
                    var state = new SnifferRenderState();
                    state.entityType = EntityTypes.SNIFFER;
                    state.ageInTicks = frame * 7F;
                    state.isInWater = true;
                    ((MorphSwimState) state).morph$setSwimBlend(1F);
                    model.setupAnim(state);
                    MorphOtherSwimming.apply(model, state);
                    Map<String, Object> angles = new LinkedHashMap<>();
                    for (int part = 0; part < names.length; part++) {
                        var limb = lookup.apply(names[part]);
                        if (limb == null) throw new IllegalStateException("Missing native model part " + names[part]);
                        samples[frame][part] = limb.xRot;
                        angles.put(names[part], limb.xRot);
                    }
                    event("sniffer-middle-legs", "posed_model", Map.of("scope", "native_model_component", "sample_age", state.ageInTicks, "x_rot", angles));
                }
                List<String> staticParts = new ArrayList<>();
                for (int part = 0; part < names.length; part++) {
                    float min = samples[0][part], max = min;
                    for (float[] sample : samples) { min = Math.min(min, sample[part]); max = Math.max(max, sample[part]); }
                    if (!Float.isFinite(min) || !Float.isFinite(max) || max - min < .01F) staticParts.add(names[part]);
                }
                if (!staticParts.isEmpty()) throw new AssertionError("Swimming must move all six native Sniffer legs; static/nonfinite parts: " + staticParts);
            });
        }
    }

    private void clip(ClientGameTestContext context) throws Exception {
        String scenario = "transformation-clip";
        Path media = runDir.resolve("scenarios").resolve(scenario).resolve("frames");
        Files.createDirectories(media);
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksDownload();
            var server = world.getServer();
            // Short capture ends long before daylight changes affect the scene.
            server.runCommand("time set noon");
            server.runCommand("weather clear");
            server.runCommand("fill -8 99 -8 8 99 8 minecraft:stone");
            server.runCommand("tp @a 0 100 0 180 0");
            server.runCommand("gamemode creative @a");
            world.getConnection().waitForClientboundPackets();
            context.runOnClient(client -> { client.options.setCameraType(CameraType.THIRD_PERSON_FRONT); client.gui.setScreen(null); });
            context.waitTicks(10);
            server.runCommand("morph grant @p minecraft:pig");
            server.runCommand("execute as @a run morph select minecraft:pig");
            context.waitFor(client -> client.player != null && "minecraft:pig".equals(MorphFabricClient.FORMS.get(client.player.getUUID())), 200);
            List<Map<String, Object>> frames = new ArrayList<>();
            for (int index = 0; index < 26; index++) {
                long tickBefore = context.computeOnClient(client -> client.level.getGameTime());
                Path source = context.takeScreenshot("morph-lab-transform-" + index);
                Path target = media.resolve(String.format("frame-%04d.png", index));
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                long tickAfter = context.computeOnClient(client -> client.level.getGameTime());
                Map<String, Object> frame = Map.of("frame_index", index, "client_tick_before", tickBefore,
                        "client_tick_after", tickAfter, "requested_tick_offset", index * 4,
                        "path", runDir.relativize(target).toString().replace('\\', '/'));
                frames.add(frame);
                event(scenario, "framebuffer", frame);
                if (index < 25) context.waitTicks(4);
            }
            write(runDir.resolve("scenarios").resolve(scenario).resolve("media.json"), Map.of("frames", frames,
                    "capture", "Fabric client framebuffer", "timing", "tick-sampled; actual bounds recorded; not real-time FPS",
                    "audio", "not captured", "frame_count", frames.size()));
        }
    }

    private synchronized void event(String scenario, String type, Map<String, ?> payload) throws java.io.IOException {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("schema_version", 1);
        event.put("scenario", scenario);
        event.put("loader", "fabric");
        event.put("sequence", sequence++);
        event.put("monotonic_ns", System.nanoTime());
        event.put("event", type);
        event.put("data", payload);
        Files.writeString(runDir.resolve("events.ndjson"), new GsonBuilder().create().toJson(event) + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static void write(Path path, Object value) throws java.io.IOException {
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(value) + "\n");
    }

    private static void addFailure(Map<String, Object> result, Throwable failure) {
        StringWriter trace = new StringWriter();
        failure.printStackTrace(new PrintWriter(trace));
        result.put("message", String.valueOf(failure.getMessage()));
        result.put("exception", failure.getClass().getName());
        result.put("stack_trace", trace.toString());
    }

    private static final class DeliberateAssertion extends AssertionError {
        DeliberateAssertion(String message) { super(message); }
    }
}
