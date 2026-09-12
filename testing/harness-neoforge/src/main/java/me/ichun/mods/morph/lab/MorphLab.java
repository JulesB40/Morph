package me.ichun.mods.morph.lab;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Developer-only bridge; deliberately has no hooks into production Morph state. */
@Mod(value = "morph_lab", dist = Dist.CLIENT)
public final class MorphLab {
    private static final Gson JSON = new Gson();
    private final Path directory;
    private final String server;
    private final String role;
    private final long deadline;
    private final Set<Path> consumed = new HashSet<>();
    private final Set<String> identifiers = new HashSet<>();
    private final Set<KeyMapping> heldKeys = new HashSet<>();
    private int tick;
    private int loadedTicks;
    private boolean connected;
    private boolean ready;
    private boolean stopped;
    private boolean initialized;
    private boolean explicitlyDisconnected;
    private String reconnectId;
    private String inputId;
    private int releaseTick;
    private String captureId;
    private boolean captureSubmitted;
    private boolean injectCaptureFailure;

    public MorphLab() throws IOException {
        if (!Boolean.getBoolean("morph.lab.enabled")) {
            throw new IllegalStateException("Morph Lab requires -Dmorph.lab.enabled=true");
        }
        directory = Path.of(requiredProperty("morph.lab.runDir")).toAbsolutePath().normalize();
        server = requiredProperty("morph.lab.server");
        role = System.getProperty("morph.lab.role", "actor");
        ServerAddress address = ServerAddress.parseString(server);
        if (!Set.of("localhost", "127.0.0.1", "::1").contains(address.getHost())) {
            throw new IllegalArgumentException("Morph Lab connects only to a loopback server");
        }
        int seconds = Integer.getInteger("morph.lab.timeoutSeconds", 180);
        if (seconds < 10 || seconds > 3600) throw new IllegalArgumentException("Invalid lab timeout");
        deadline = System.nanoTime() + seconds * 1_000_000_000L;
        Files.createDirectories(directory.resolve("requests"));
        Files.createDirectories(directory.resolve("captures"));
        // A stale output directory must never be mistaken for a fresh successful run.
        Files.createFile(directory.resolve("events.ndjson"));
        NeoForge.EVENT_BUS.addListener(this::beforeTick);
        NeoForge.EVENT_BUS.addListener(this::afterFrame);
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + name);
        return value;
    }

    private void beforeTick(ClientTickEvent.Pre ignored) {
        if (stopped) return;
        Minecraft client = Minecraft.getInstance();
        tick++;
        try {
            if (!initialized) {
                initialized = true;
                if (Boolean.getBoolean("morph.lab.hidden")) {
                    GLFW.glfwHideWindow(client.getWindow().handle());
                }
                emit("started", null, runtime(client));
            }
            if (System.nanoTime() > deadline) throw new IllegalStateException("Client bridge timed out");
            if (!connected && client.isGameLoadFinished() && client.gui.overlay() == null) {
                connected = true;
                client.options.pauseOnLostFocus = false;
                ConnectScreen.startConnecting(new TitleScreen(), client, ServerAddress.parseString(server),
                        new ServerData("Morph Lab", server, ServerData.Type.OTHER), false, null);
                emit("connecting", null, Map.of());
            }
            if (client.player == null || client.level == null) {
                if (ready) throw new IllegalStateException("Client disconnected during lab session");
                if (explicitlyDisconnected) pollRequest(client);
                return;
            }
            if (!ready) {
                if (client.gui.screen() != null || ++loadedTicks < 40) return;
                ready = true;
                emit("ready", null, state(client));
                if (reconnectId != null) {
                    emit("completed", reconnectId, state(client));
                    reconnectId = null;
                }
            }
            if (inputId != null) {
                if (tick < releaseTick) {
                    heldKeys.forEach(key -> key.setDown(true));
                    return;
                }
                release();
                emit("completed", inputId, state(client));
                inputId = null;
            }
            if (captureId != null) return;
            pollRequest(client);
        } catch (Exception error) {
            fail(client, reconnectId, error);
        }
    }

    private void pollRequest(Minecraft client) throws IOException {
        try (var files = Files.list(directory.resolve("requests"))) {
                Path next = files.filter(p -> p.getFileName().toString().endsWith(".json"))
                        .filter(p -> !consumed.contains(p)).sorted().findFirst().orElse(null);
                if (next != null) {
                    consumed.add(next);
                    runRequest(client, next);
                }
        }
    }

    private void runRequest(Minecraft client, Path file) {
        String id = null;
        try {
            if (Files.size(file) > 16_384) throw new IllegalArgumentException("Request exceeds 16 KiB");
            JsonObject request = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            id = request.get("id").getAsString();
            if (!id.matches("[a-zA-Z0-9_-]{1,80}") || !identifiers.add(id)) {
                throw new IllegalArgumentException("Invalid or repeated request id");
            }
            String operation = request.get("op").getAsString();
            if (explicitlyDisconnected && !Set.of("reconnect", "state", "exit").contains(operation)) {
                throw new IllegalStateException("Only reconnect/state/exit are available while disconnected");
            }
            switch (operation) {
                case "disconnect" -> {
                    release();
                    ready = false;
                    loadedTicks = 0;
                    explicitlyDisconnected = true;
                    client.disconnect(new TitleScreen(), false);
                }
                case "reconnect" -> {
                    if (!explicitlyDisconnected) throw new IllegalStateException("Disconnect before reconnecting");
                    explicitlyDisconnected = false;
                    connected = false;
                    reconnectId = id;
                    return;
                }
                case "input" -> {
                    if (client.gui.screen() != null) throw new IllegalStateException("Cannot apply movement with a screen open");
                    int duration = request.get("ticks").getAsInt();
                    if (duration < 1 || duration > 1200) throw new IllegalArgumentException("Input ticks must be 1..1200");
                    for (var key : request.getAsJsonArray("keys")) heldKeys.add(key(client, key.getAsString()));
                    inputId = id;
                    releaseTick = tick + duration;
                    heldKeys.forEach(key -> key.setDown(true));
                    emit("input_started", id, Map.of("ticks", duration, "keys", request.get("keys")));
                    return;
                }
                case "release" -> release();
                case "look" -> {
                    float yaw = request.get("yaw").getAsFloat();
                    float pitch = request.get("pitch").getAsFloat();
                    if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || Math.abs(pitch) > 90) {
                        throw new IllegalArgumentException("Invalid look angles");
                    }
                    client.player.setYRot(yaw);
                    client.player.setXRot(pitch);
                }
                case "command" -> client.player.connection.sendCommand(request.get("command").getAsString());
                case "state" -> { }
                case "probe" -> {
                    String name = request.get("name").getAsString();
                    if (name.equals("capture-failure")) {
                        captureId = id;
                        captureSubmitted = false;
                        injectCaptureFailure = true;
                    } else {
                        Map<String, Object> result = new LinkedHashMap<>(LabComponentProbes.run(client, name));
                        boolean passed = Boolean.TRUE.equals(result.get("passed"));
                        if (!passed) result.put("recoverable", true);
                        emit(passed ? "completed" : "failed", id, result);
                    }
                    return;
                }
                case "capture" -> {
                    captureId = id;
                    captureSubmitted = false;
                    return;
                }
                case "exit" -> {
                    release();
                    emit("completed", id, state(client));
                    emit("finished", null, Map.of("status", "completed", "meaning", "bridge actions completed; no gameplay oracle evaluated"));
                    stopped = true;
                    client.stop();
                    return;
                }
                default -> throw new IllegalArgumentException("Unknown operation");
            }
            // A command acknowledgement means sent, not accepted by the server.
            emit("completed", id, state(client));
        } catch (Exception error) {
            fail(client, id, error);
        }
    }

    private void afterFrame(RenderFrameEvent.Post ignored) {
        if (stopped || captureId == null || captureSubmitted) return;
        captureSubmitted = true;
        Minecraft client = Minecraft.getInstance();
        String id = captureId;
        try {
            if (injectCaptureFailure) {
                injectCaptureFailure = false;
                throw new InjectedCaptureFailure();
            }
            Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(), image -> {
                try (image) {
                    Path target = directory.resolve("captures").resolve(id + ".png");
                    image.writeToFile(target);
                    client.execute(() -> {
                        emit("completed", id, Map.of("png", "captures/" + id + ".png"));
                        captureId = null;
                    });
                } catch (Exception error) {
                    client.execute(() -> fail(client, id, error));
                }
            });
        } catch (InjectedCaptureFailure error) {
            captureId = null;
            captureSubmitted = false;
            emit("failed", id, Map.of("probe", "capture-failure", "injected", true,
                    "recoverable", true, "error", error.toString(), "stage", "before_framebuffer_readback"));
        } catch (Exception error) {
            fail(client, id, error);
        }
    }

    private static final class InjectedCaptureFailure extends IOException {
        private InjectedCaptureFailure() { super("Deliberate test-only capture failure"); }
    }

    private KeyMapping key(Minecraft client, String name) {
        return switch (name) {
            case "forward" -> client.options.keyUp;
            case "back" -> client.options.keyDown;
            case "left" -> client.options.keyLeft;
            case "right" -> client.options.keyRight;
            case "jump" -> client.options.keyJump;
            case "sneak" -> client.options.keyShift;
            case "sprint" -> client.options.keySprint;
            default -> throw new IllegalArgumentException("Unsupported input key: " + name);
        };
    }

    private void release() {
        heldKeys.forEach(key -> key.setDown(false));
        heldKeys.clear();
    }

    private Map<String, Object> state(Minecraft client) {
        var player = client.player;
        Map<String, Object> state = new LinkedHashMap<>(runtime(client));
        state.put("connected", player != null && client.level != null);
        if (player == null || client.level == null) return state;
        state.put("uuid", player.getUUID().toString());
        state.put("name", player.getName().getString());
        state.put("position", java.util.List.of(player.getX(), player.getY(), player.getZ()));
        state.put("velocity", java.util.List.of(player.getDeltaMovement().x, player.getDeltaMovement().y, player.getDeltaMovement().z));
        state.put("onGround", player.onGround());
        state.put("inWater", player.isInWater());
        state.put("swimming", player.isSwimming());
        state.put("flying", player.getAbilities().flying);
        state.put("pose", player.getPose().toString());
        state.put("players", client.level.players().stream().map(other -> Map.of(
                "uuid", other.getUUID().toString(), "name", other.getName().getString(),
                "form", java.util.Objects.requireNonNullElse(
                        me.ichun.mods.morph.client.ClientMorphState.lookup(other.getUUID()), "minecraft:player"),
                "showNameTag", me.ichun.mods.morph.client.nametag.MorphNameTags.visible(other.getUUID()),
                "position", java.util.List.of(other.getX(), other.getY(), other.getZ()))).toList());
        return state;
    }

    private Map<String, Object> runtime(Minecraft client) {
        var device = RenderSystem.getDevice().getDeviceInfo();
        return Map.of("server", server,
                "gpuVendor", device.vendorName(), "gpuRenderer", device.name(),
                "gpuBackend", device.backendName(), "gpuDriver", device.driverInfo(),
                "hiddenRequested", Boolean.getBoolean("morph.lab.hidden"),
                "windowHidden", GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE);
    }

    private void fail(Minecraft client, String id, Exception error) {
        release();
        stopped = true;
        try {
            emit("failed", id, Map.of("error", error.toString()));
        } finally {
            client.stop();
        }
    }

    private synchronized void emit(String event, String id, Map<String, ?> detail) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("schema", 1);
        row.put("loader", "neoforge");
        row.put("role", role);
        row.put("event", event);
        if (id != null) row.put("id", id);
        row.put("tick", tick);
        row.put("detail", detail);
        try {
            Files.writeString(directory.resolve("events.ndjson"), JSON.toJson(row) + "\n",
                    StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot write Morph Lab evidence", error);
        }
    }
}
