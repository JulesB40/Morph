package me.ichun.mods.morph.definition;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;
import me.ichun.mods.morph.config.MorphPolicySnapshot;
import me.ichun.mods.morph.config.MorphPolicySnapshot.*;

/** Strict bounded document parser. Legacy support JSON is deliberately not this format. */
public final class DefinitionParser {
    public static final int MAX_BYTES = 65536;
    private DefinitionParser() {}

    public static DefinitionSnapshot parse(byte[] bytes, long revision) {
        if (bytes == null || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Definition document exceeds 65536 bytes");
        String source;
        try {
            source = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException error) { throw new IllegalArgumentException("Invalid UTF-8", error); }
        JsonElement root;
        try (JsonReader reader = new JsonReader(new StringReader(source))) {
            reader.setLenient(false);
            root = read(reader, 0, new int[]{0});
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Trailing JSON data");
        } catch (IOException error) { throw new IllegalArgumentException("Invalid definition JSON", error); }
        JsonObject object = object(root, Set.of("schema_version", "policy", "mobs", "traits"));
        if (integer(object, "schema_version", -1) != 1) throw new IllegalArgumentException("Unsupported definition schema");
        MorphPolicySnapshot policy = policy(object.has("policy") ? object.get("policy") : new JsonObject(), revision);
        Map<String, DefinitionSnapshot.TraitDefinition> traits = new LinkedHashMap<>();
        for (JsonElement value : array(object, "traits", 256)) {
            JsonObject item = object(value, Set.of("id", "enabled", "cooldown_ticks", "terrain_harm"));
            String id = string(item, "id", null);
            var definition = new DefinitionSnapshot.TraitDefinition(id, bool(item, "enabled", true),
                    integer(item, "cooldown_ticks", 0), bool(item, "terrain_harm", false));
            if (traits.putIfAbsent(id, definition) != null) throw new IllegalArgumentException("Duplicate trait ID");
        }
        Map<String, DefinitionSnapshot.MobDefinition> mobs = new LinkedHashMap<>();
        for (JsonElement value : array(object, "mobs", 256)) {
            JsonObject item = object(value, Set.of("species", "enabled", "traits", "adapter", "adapter_version"));
            String species = string(item, "species", null);
            var definition = new DefinitionSnapshot.MobDefinition(species, bool(item, "enabled", true),
                    strings(item, "traits", 32), string(item, "adapter", "morph:species"), integer(item, "adapter_version", 1));
            if (mobs.putIfAbsent(species, definition) != null) throw new IllegalArgumentException("Duplicate mob species");
        }
        return new DefinitionSnapshot(revision, policy, mobs, traits);
    }

    private static MorphPolicySnapshot policy(JsonElement element, long revision) {
        JsonObject o = object(element, Set.of("mode", "biomass_opt_in", "duration_ticks", "morph_sounds",
                "morph_players", "selector_players", "forms", "abilities"));
        JsonObject forms = object(o.has("forms") ? o.get("forms") : new JsonObject(), Set.of("allow", "deny"));
        JsonObject abilities = object(o.has("abilities") ? o.get("abilities") : new JsonObject(), Set.of("enabled", "terrain_harm", "disabled"));
        return new MorphPolicySnapshot(revision, ServerMode.valueOf(string(o, "mode", "CLASSIC")),
                bool(o, "biomass_opt_in", false), integer(o, "duration_ticks", 100), bool(o, "morph_sounds", true),
                players(o, "morph_players"), players(o, "selector_players"),
                new FormFilter(strings(forms, "allow", 256), strings(forms, "deny", 256)),
                new AbilityPolicy(bool(abilities, "enabled", true), bool(abilities, "terrain_harm", false), strings(abilities, "disabled", 256)));
    }
    private static PlayerFilter players(JsonObject parent, String key) {
        JsonObject o = object(parent.has(key) ? parent.get(key) : new JsonObject(), Set.of("allow", "deny"));
        return new PlayerFilter(uuids(strings(o, "allow", 256)), uuids(strings(o, "deny", 256)));
    }
    private static Set<UUID> uuids(Set<String> values) {
        Set<UUID> result = new HashSet<>();
        for (String value : values) {
            UUID uuid = UUID.fromString(value);
            if (!uuid.toString().equals(value)) throw new IllegalArgumentException("UUID must be canonical lowercase");
            result.add(uuid);
        }
        return result;
    }
    private static JsonObject object(JsonElement value, Set<String> keys) {
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException("Expected JSON object");
        JsonObject result = value.getAsJsonObject();
        if (!keys.containsAll(result.keySet())) throw new IllegalArgumentException("Unknown definition field");
        return result;
    }
    private static JsonArray array(JsonObject o, String key, int maximum) {
        if (!o.has(key)) return new JsonArray();
        if (!o.get(key).isJsonArray() || o.getAsJsonArray(key).size() > maximum)
            throw new IllegalArgumentException("Invalid array: " + key);
        return o.getAsJsonArray(key);
    }
    private static Set<String> strings(JsonObject o, String key, int maximum) {
        Set<String> result = new LinkedHashSet<>();
        for (JsonElement value : array(o, key, maximum)) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                    || !result.add(value.getAsString())) throw new IllegalArgumentException("Expected unique strings: " + key);
        }
        return result;
    }
    private static String string(JsonObject o, String key, String fallback) {
        if (!o.has(key)) return fallback;
        JsonElement value = o.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Expected string: " + key);
        return value.getAsString();
    }
    private static boolean bool(JsonObject o, String key, boolean fallback) {
        if (!o.has(key)) return fallback;
        JsonElement value = o.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("Expected boolean: " + key);
        return value.getAsBoolean();
    }
    private static int integer(JsonObject o, String key, int fallback) {
        if (!o.has(key)) return fallback;
        JsonElement value = o.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Expected integer: " + key);
        try { return value.getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException | NumberFormatException error) { throw new IllegalArgumentException("Invalid integer: " + key); }
    }
    private static JsonElement read(JsonReader reader, int depth, int[] count) throws IOException {
        if (depth > 5 || ++count[0] > 8192) throw new IllegalArgumentException("Definition structure exceeds bounds");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject(); JsonObject result = new JsonObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if (key.length() > 64 || result.has(key)) throw new IllegalArgumentException("Invalid or duplicate field");
                    result.add(key, read(reader, depth + 1, count));
                }
                reader.endObject(); yield result;
            }
            case BEGIN_ARRAY -> {
                reader.beginArray(); JsonArray result = new JsonArray();
                while (reader.hasNext()) {
                    if (result.size() >= 256) throw new IllegalArgumentException("Array exceeds 256 entries");
                    result.add(read(reader, depth + 1, count));
                }
                reader.endArray(); yield result;
            }
            case STRING -> {
                String value = reader.nextString();
                if (value.getBytes(StandardCharsets.UTF_8).length > 256) throw new IllegalArgumentException("String exceeds 256 bytes");
                for (int i = 0; i < value.length(); i++) {
                    char ch = value.charAt(i);
                    if (Character.isHighSurrogate(ch)) {
                        if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) throw new IllegalArgumentException("Unpaired surrogate");
                    } else if (Character.isLowSurrogate(ch)) throw new IllegalArgumentException("Unpaired surrogate");
                }
                yield new JsonPrimitive(value);
            }
            case NUMBER -> {
                String value = reader.nextString();
                if (!value.matches("-?(0|[1-9][0-9]*)") || value.length() > 11) throw new IllegalArgumentException("Expected bounded integer token");
                try { yield new JsonPrimitive(Integer.parseInt(value)); }
                catch (NumberFormatException error) { throw new IllegalArgumentException("Integer overflow"); }
            }
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            default -> throw new IllegalArgumentException("Unexpected JSON token");
        };
    }
}
