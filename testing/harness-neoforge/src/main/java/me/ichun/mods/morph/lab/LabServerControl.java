package me.ichun.mods.morph.lab;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.ichun.mods.morph.server.MorphService;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Test-only dedicated-server control. An absent controlDir leaves this inert.
 * Requests use the client bridge envelope and must be atomically renamed to *.json.
 * A restart needs a fresh controlDir but may reuse the server's own game directory.
 */
@EventBusSubscriber(modid = "morph_lab")
public final class LabServerControl {
    private static final Gson JSON = new Gson();
    private static LabServerControl active;
    private final MinecraftServer server;
    private final Path directory;
    private final Set<Path> consumed = new HashSet<>();
    private final Set<String> identifiers = new HashSet<>();
    private boolean stopped;

    private LabServerControl(MinecraftServer server, String controlDirectory) throws IOException {
        this.server = server;
        Path requested = Path.of(controlDirectory);
        if (!requested.isAbsolute()) throw new IllegalArgumentException("morph.lab.controlDir must be absolute");
        directory = requested.normalize();
        Files.createDirectories(directory.resolve("requests"));
        // Do not append to stale success evidence from an earlier process.
        Files.createFile(directory.resolve("events.ndjson"));
        emit("ready", null, state());
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        String controlDirectory = System.getProperty("morph.lab.controlDir");
        if (controlDirectory == null) return;
        MinecraftServer server = event.getServer();
        if (!server.isDedicatedServer()) return;
        try {
            active = new LabServerControl(server, controlDirectory);
        } catch (Exception error) {
            server.halt(false);
            throw new IllegalStateException("Cannot initialize Morph Lab server control", error);
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        LabServerControl control = active;
        if (control == null || control.stopped || control.server != event.getServer()) return;
        try (var files = Files.list(control.directory.resolve("requests"))) {
            Path next = files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .filter(path -> !control.consumed.contains(path)).sorted().findFirst().orElse(null);
            if (next != null) {
                control.consumed.add(next);
                control.run(next);
            }
        } catch (Exception error) {
            control.fail(null, error);
        }
    }

    private void run(Path file) {
        String id = null;
        try {
            if (Files.size(file) > 16_384) throw new IllegalArgumentException("Request exceeds 16 KiB");
            JsonObject request = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            id = request.get("id").getAsString();
            if (!id.matches("[a-zA-Z0-9_-]{1,80}") || !identifiers.add(id)) {
                throw new IllegalArgumentException("Invalid or repeated request id");
            }
            switch (request.get("op").getAsString()) {
                case "state" -> emit("completed", id, state());
                case "command" -> {
                    // Native command processing retains normal permission and command semantics.
                    // Completion means dispatched; inspect state to establish the requested effect.
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                            request.get("command").getAsString());
                    emit("completed", id, state());
                }
                case "save" -> {
                    boolean saved = server.saveEverything(false, true, true);
                    Map<String, Object> detail = new LinkedHashMap<>(state());
                    detail.put("saved", saved);
                    emit("completed", id, detail);
                }
                case "stop" -> {
                    // The controller must await process exit; this acknowledgement is not proof
                    // that shutdown saves have finished successfully.
                    emit("completed", id, Map.of("status", "stopping"));
                    stopped = true;
                    server.halt(false);
                }
                default -> throw new IllegalArgumentException("Unknown server operation");
            }
        } catch (Exception error) {
            fail(id, error);
        }
    }

    private Map<String, Object> state() {
        return Map.of("players", server.getPlayerList().getPlayers().stream().map(player -> {
            String selected = MorphService.collection(player).activeForm();
            Map<String, Object> observed = new LinkedHashMap<>();
            observed.put("uuid", player.getUUID().toString());
            observed.put("name", player.getName().getString());
            observed.put("form", selected == null || selected.isEmpty() ? "minecraft:player" : selected);
            observed.put("showNameTag", MorphService.showNametag(player));
            observed.put("health", player.getHealth());
            observed.put("position", List.of(player.getX(), player.getY(), player.getZ()));
            return observed;
        }).toList());
    }

    private void fail(String id, Exception error) {
        stopped = true;
        try {
            emit("failed", id, Map.of("error", error.toString()));
        } finally {
            server.halt(false);
        }
    }

    private void emit(String event, String id, Map<String, ?> detail) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("schema", 1);
        row.put("loader", "neoforge");
        row.put("role", "server");
        row.put("event", event);
        if (id != null) row.put("id", id);
        row.put("tick", server.getTickCount());
        row.put("detail", detail);
        try {
            Files.writeString(directory.resolve("events.ndjson"), JSON.toJson(row) + "\n",
                    StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot write Morph Lab server evidence", error);
        }
    }
}
