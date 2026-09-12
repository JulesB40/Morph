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
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.glfw.GLFW;

/** Developer-only probes. Assertions are evidence, never an instruction to change production behavior. */
public final class MorphLabClientGameTest implements FabricClientGameTest {
    private static final List<String> DEFAULTS = List.of("dragon-renderer", "sniffer-middle-legs",
            "bat-bee-flight", "nametag", "transformation-clip", "transformation-sound", "intentional-assertion");
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
            configureOwnWindow(context);
            for (String scenario : cases) runCase(context, scenario);
            boolean infrastructureFailure = results.stream().anyMatch(r -> "infrastructure_failure".equals(r.get("status")));
            boolean assertionFailure = results.stream().anyMatch(r -> "fail".equals(r.get("status")));
            write(runDir.resolve("result.json"), Map.of("schema_version", 1, "loader", "fabric",
                    "status", infrastructureFailure ? "infrastructure_failure" : assertionFailure ? "fail" : "pass",
                    "role", System.getProperty("morph.lab.role", "baseline"),
                    "run_id", runDir.getParent().getFileName().toString(), "cases", results,
                    "artifacts", List.of("events.ndjson", "environment.json", "scenarios")));
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
            configureOwnWindow(context);
            switch (scenario) {
                case "descriptor-rendering", "captured-equipment" -> featureComponents(context, scenario);
                case "dragon-renderer" -> dragon(context);
                case "sniffer-middle-legs" -> sniffer(context);
                case "bat-bee-flight" -> new MorphFlyingClientGameTest().runTest(context);
                case "nametag" -> new MorphNametagClientGameTest().runTest(context);
                case "transformation-clip" -> clip(context);
                case "transformation-sound" -> sound(context);
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
        try (var files = Files.walk(directory)) {
            List<String> artifacts = new ArrayList<>(files.filter(Files::isRegularFile)
                    .map(path -> runDir.relativize(path).toString().replace('\\', '/')).sorted().toList());
            artifacts.add("scenarios/" + scenario + "/result.json");
            result.put("artifacts", artifacts);
        }
        result.put("finished_at", java.time.Instant.now().toString());
        event(scenario, "result", result);
        write(directory.resolve("result.json"), result);
        results.add(result);
    }

    private void featureComponents(ClientGameTestContext context, String scenario) throws Exception {
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksDownload();
            context.runOnClient(client -> {
                Object checks;
                if (scenario.equals("captured-equipment")) {
                    var result = me.ichun.mods.morph.lab.MorphCapturedEquipmentProbes.run(client.player);
                    if (!Boolean.TRUE.equals(result.get("passed"))) throw new AssertionError(result.toString());
                    checks = result;
                } else {
                    var source = (AvatarRenderState) client.getEntityRenderDispatcher().getRenderer(client.player).createRenderState(client.player, .5F);
                    checks = me.ichun.mods.morph.lab.NativeRenderDescriptorChecks.verify(client.player, source);
                }
                event(scenario, "native_components", Map.of("checks", checks));
            });
            checkpoint(context, scenario, "completed-components");
        }
    }

    private void dragon(ClientGameTestContext context) throws Exception {
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksDownload();
            selectedScene(context, world.getServer(), "minecraft:ender_dragon");
            checkpoint(context, "dragon-renderer", "selected-form");
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
            selectedScene(context, world.getServer(), "minecraft:sniffer");
            checkpoint(context, "sniffer-middle-legs", "selected-form");
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

    private void sound(ClientGameTestContext context) throws Exception {
        String scenario = "transformation-sound";
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksDownload();
            var server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("morph grant @p minecraft:pig");
            world.getConnection().waitForClientboundPackets();
            context.runOnClient(client -> client.gui.setScreen(null));
            List<Map<String, Object>> sounds = new ArrayList<>();
            SoundEventListener listener = context.computeOnClient(client ->
                    (SoundEventListener) (instance, soundEvent, range) -> {
                        if (!instance.getIdentifier().toString().startsWith("morph:")) return;
                        Map<String, Object> sample = new LinkedHashMap<>();
                        sample.put("sound_id", instance.getIdentifier().toString());
                        sample.put("category", instance.getSource().name());
                        sample.put("volume", instance.getVolume());
                        sample.put("pitch", instance.getPitch());
                        sample.put("position", List.of(instance.getX(), instance.getY(), instance.getZ()));
                        sample.put("client_tick", client.level == null ? -1L : client.level.getGameTime());
                        sample.put("monotonic_ns", System.nanoTime());
                        sample.put("relative", instance.isRelative());
                        sounds.add(sample);
                    });
            context.runOnClient(client -> client.getSoundManager().addListener(listener));
            try {
                server.runCommand("execute as @a run morph select minecraft:pig");
                context.waitFor(client -> client.player != null && "minecraft:pig".equals(MorphFabricClient.FORMS.get(client.player.getUUID())), 200);
                // The contract schedules the sound twenty server ticks after selection.
                context.waitTicks(60);
                world.getConnection().waitForClientboundPackets();
                List<Map<String, Object>> selected = context.computeOnClient(client -> List.copyOf(sounds));
                for (Map<String, Object> sample : selected) event(scenario, "sound_event", sample);
                write(runDir.resolve("scenarios").resolve(scenario).resolve("sounds.json"), Map.of(
                        "scope", "client SoundEventListener notification; no audible-output assertion",
                        "audio", "not captured", "expected_sound_id", "morph:morph", "expected_category", "PLAYERS",
                        "events", selected));
                if (selected.size() != 1 || !"morph:morph".equals(selected.getFirst().get("sound_id"))
                        || !"PLAYERS".equals(selected.getFirst().get("category"))) {
                    throw new AssertionError("Selecting pig must emit exactly one morph:morph event in PLAYERS; observed " + selected);
                }
            } finally {
                context.runOnClient(client -> client.getSoundManager().removeListener(listener));
            }
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

    private void configureOwnWindow(ClientGameTestContext context) throws java.io.IOException {
        Map<String, Object> environment = context.computeOnClient(client -> {
            boolean hidden = Boolean.parseBoolean(System.getProperty("morph.lab.hidden", "true"));
            long handle = client.getWindow().handle();
            if (hidden) GLFW.glfwHideWindow(handle);
            client.options.pauseOnLostFocus = false;
            var device = RenderSystem.getDevice().getDeviceInfo();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("run_id", runDir.getParent().getFileName().toString());
            data.put("role", System.getProperty("morph.lab.role", "baseline"));
            data.put("loader", "fabric");
            data.put("pid", ProcessHandle.current().pid());
            data.put("hidden_requested", hidden);
            data.put("own_window_handle", Long.toUnsignedString(handle));
            data.put("own_window_visible", GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_VISIBLE) == GLFW.GLFW_TRUE);
            data.put("own_window_focused", GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE);
            data.put("gpu_vendor", device.vendorName());
            data.put("gpu_renderer", device.name());
            data.put("gpu_backend", device.backendName());
            data.put("gpu_driver", device.driverInfo());
            data.put("os", System.getProperty("os.name"));
            data.put("focus_isolation", "unproven; visibility sampled only after GameTest entrypoint starts");
            return data;
        });
        write(runDir.resolve("environment.json"), environment);
        event("harness", "environment", environment);
    }

    private void selectedScene(ClientGameTestContext context, TestServerContext server, String form) {
        server.runCommand("time set noon");
        server.runCommand("weather clear");
        server.runCommand("fill -8 99 -8 8 99 8 minecraft:stone");
        server.runCommand("gamemode creative @a");
        server.runCommand("tp @a 0 100 0 180 0");
        server.runCommand("morph grant @p " + form);
        server.runCommand("execute as @a run morph select " + form);
        context.waitFor(client -> client.player != null && form.equals(MorphFabricClient.FORMS.get(client.player.getUUID())), 200);
        context.runOnClient(client -> {
            client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            client.gui.setScreen(null);
        });
        context.waitTicks(120);
    }

    private void checkpoint(ClientGameTestContext context, String scenario, String name) throws java.io.IOException {
        long before = context.computeOnClient(client -> client.level.getGameTime());
        Path captured = context.takeScreenshot("morph-lab-" + scenario + "-" + name);
        Path target = runDir.resolve("scenarios").resolve(scenario).resolve(name + ".png");
        Files.copy(captured, target, StandardCopyOption.REPLACE_EXISTING);
        Map<String, Object> evidence = context.computeOnClient(client -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("path", runDir.relativize(target).toString().replace('\\', '/'));
            data.put("client_tick_before", before);
            data.put("client_tick_after", client.level.getGameTime());
            data.put("selected_form", String.valueOf(MorphFabricClient.FORMS.get(client.player.getUUID())));
            data.put("own_window_visible", GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_TRUE);
            data.put("own_window_focused", GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE);
            data.put("scope", "framebuffer supporting evidence; no automated pixel oracle");
            return data;
        });
        event(scenario, "checkpoint", evidence);
        write(target.resolveSibling(name + ".json"), evidence);
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
