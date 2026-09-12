package me.ichun.mods.morph.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Shared descriptor/save JSON primitives. Wire parsing rejects duplicate fields before making a tree. */
public final class FormDescriptorJson {
    private FormDescriptorJson() {}

    public static JsonElement parse(String input, int maximumBytes) {
        if (input == null || input.length() > maximumBytes || input.getBytes(StandardCharsets.UTF_8).length > maximumBytes)
            throw new IllegalArgumentException("JSON exceeds byte limit");
        try (var reader = new JsonReader(new StringReader(input))) {
            reader.setStrictness(Strictness.STRICT);
            JsonElement result = read(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Trailing JSON data");
            return result;
        } catch (IOException | IllegalStateException error) { throw new IllegalArgumentException("Invalid descriptor JSON", error); }
    }

    private static JsonElement read(JsonReader reader, int depth) throws IOException {
        if (depth > 8) throw new IllegalArgumentException("JSON nesting exceeds limit");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                var object = new JsonObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if (object.has(key)) throw new IllegalArgumentException("Duplicate JSON field");
                    if (object.size() >= 64) throw new IllegalArgumentException("Too many JSON fields");
                    object.add(key, read(reader, depth + 1));
                }
                reader.endObject();
                yield object;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NUMBER -> {
                String number = reader.nextString();
                if (!number.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"))
                    throw new IllegalArgumentException("Invalid JSON number");
                yield new JsonPrimitive(new BigDecimal(number));
            }
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw new IllegalArgumentException("Unexpected descriptor JSON token");
        };
    }

    public static JsonObject object(JsonElement value, Set<String> required) { return object(value, required, Set.of()); }

    public static JsonObject object(JsonElement value, Set<String> required, Set<String> optional) {
        var object = map(value, required.size() + optional.size());
        if (!object.keySet().containsAll(required)) throw new IllegalArgumentException("Missing JSON fields");
        var allowed = new HashSet<>(required);
        allowed.addAll(optional);
        if (!allowed.containsAll(object.keySet())) throw new IllegalArgumentException("Unknown JSON fields");
        return object;
    }

    public static JsonObject map(JsonElement value, int maximumSize) {
        if (value == null || !value.isJsonObject() || value.getAsJsonObject().size() > maximumSize)
            throw new IllegalArgumentException("Invalid JSON object or field count");
        return value.getAsJsonObject();
    }

    public static JsonArray array(JsonElement value, int maximumSize) {
        if (value == null || !value.isJsonArray() || value.getAsJsonArray().size() > maximumSize)
            throw new IllegalArgumentException("Invalid JSON array or element count");
        return value.getAsJsonArray();
    }

    public static String string(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Expected JSON string");
        return value.getAsString();
    }

    public static String nullableString(JsonElement value) { return value != null && value.isJsonNull() ? null : string(value); }

    public static boolean bool(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
            throw new IllegalArgumentException("Expected JSON Boolean");
        return value.getAsBoolean();
    }

    public static int integer(JsonElement value) {
        try { return number(value).intValueExact(); }
        catch (ArithmeticException error) { throw new IllegalArgumentException("Expected 32-bit integer", error); }
    }

    public static long longInteger(JsonElement value) {
        try { return number(value).longValueExact(); }
        catch (ArithmeticException error) { throw new IllegalArgumentException("Expected 64-bit integer", error); }
    }

    private static BigDecimal number(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Expected JSON number");
        try { return value.getAsBigDecimal(); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("Invalid JSON number", error); }
    }

    public static UUID uuid(JsonElement value) {
        String text = string(value);
        if (!text.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new IllegalArgumentException("Invalid UUID");
        return UUID.fromString(text);
    }

    public static String canonical(JsonElement value) {
        var result = new StringBuilder();
        append(value, result);
        return result.toString();
    }

    private static void append(JsonElement value, StringBuilder output) {
        if (value.isJsonNull()) output.append("null");
        else if (value.isJsonObject()) {
            output.append('{');
            boolean first = true;
            for (var entry : new TreeMap<>(value.getAsJsonObject().asMap()).entrySet()) {
                if (!first) output.append(',');
                first = false;
                quote(entry.getKey(), output);
                output.append(':');
                append(entry.getValue(), output);
            }
            output.append('}');
        } else if (value.isJsonArray()) {
            output.append('[');
            boolean first = true;
            for (var item : value.getAsJsonArray()) {
                if (!first) output.append(',');
                first = false;
                append(item, output);
            }
            output.append(']');
        } else if (value.getAsJsonPrimitive().isString()) quote(value.getAsString(), output);
        else output.append(value);
    }

    private static void quote(String text, StringBuilder output) {
        output.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\\') output.append('\\').append(c);
            else if (c < 0x20) output.append("\\u00").append(Character.forDigit(c >>> 4, 16)).append(Character.forDigit(c & 15, 16));
            else output.append(c);
        }
        output.append('"');
    }
}
