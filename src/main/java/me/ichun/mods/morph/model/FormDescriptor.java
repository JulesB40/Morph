package me.ichun.mods.morph.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Immutable bounded appearance data. A valid descriptor does not establish adapter support. */
public record FormDescriptor(int version, String species, String adapter, int adapterVersion,
        Map<String, Object> variant, String customName, Map<String, CapturedEquipment> equipment,
        Map<String, Double> attributes, PlayerProfile profile) {
    public static final int VERSION = 1;
    public static final int MAX_BYTES = 16_384;
    public static final int MAX_FIELDS = 32;
    private static final Set<String> EQUIPMENT_SLOTS = Set.of("mainhand", "offhand", "head", "chest",
            "legs", "feet", "body", "saddle");
    // JSON text retains Boolean/integer distinctions across NBT's byte representation.
    public static final Codec<FormDescriptor> CODEC = Codec.STRING.comapFlatMap(input -> {
        try { return DataResult.success(fromJson(input)); }
        catch (IllegalArgumentException error) { return DataResult.error(error::getMessage); }
    }, FormDescriptor::canonicalJson);

    public FormDescriptor {
        if (version != VERSION) throw new IllegalArgumentException("Unsupported descriptor version");
        requireIdentifier(species);
        requireIdentifier(adapter);
        if (adapterVersion < 1 || adapterVersion > 65_535)
            throw new IllegalArgumentException("Invalid adapter version");
        if (variant == null || variant.size() > MAX_FIELDS) throw new IllegalArgumentException("Too many variant fields");
        var copiedVariant = new TreeMap<String, Object>();
        variant.forEach((key, value) -> {
            if (key == null || !key.matches("[a-zA-Z0-9_.-]{1,64}"))
                throw new IllegalArgumentException("Invalid variant field");
            if (value instanceof String text) value = normalizedText(text, 256, false);
            else if (!(value instanceof Boolean) && !(value instanceof Integer))
                throw new IllegalArgumentException("Variant values must be Boolean, integer or text");
            copiedVariant.put(key, value);
        });
        variant = Map.copyOf(copiedVariant);
        if (customName != null) customName = normalizedText(customName, 256, true);
        if (equipment == null || equipment.size() > EQUIPMENT_SLOTS.size()
                || equipment.keySet().stream().anyMatch(key -> key == null || !EQUIPMENT_SLOTS.contains(key))
                || equipment.values().stream().anyMatch(java.util.Objects::isNull))
            throw new IllegalArgumentException("Invalid captured equipment slots");
        equipment = Map.copyOf(equipment);
        if (attributes == null || attributes.size() > MAX_FIELDS)
            throw new IllegalArgumentException("Too many attributes");
        var copiedAttributes = new TreeMap<String, Double>();
        attributes.forEach((key, value) -> {
            requireIdentifier(key);
            if (value == null || !Double.isFinite(value) || Math.abs(value) > 1_000_000)
                throw new IllegalArgumentException("Invalid captured attribute value");
            copiedAttributes.put(key, value == 0 ? 0.0 : value);
        });
        attributes = Map.copyOf(copiedAttributes);
        if (species.equals("minecraft:player") != (profile != null))
            throw new IllegalArgumentException("Player descriptors require a profile; other forms cannot have one");
        validateKnownAdapter(species, adapter, adapterVersion, variant, customName, equipment, profile);
        // A compact constructor cannot call instance methods until its fields have been assigned.
        if (FormDescriptorJson.canonical(json(version, species, adapter, adapterVersion, variant,
                customName, equipment, attributes, profile, false)).getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IllegalArgumentException("Morph descriptor exceeds byte limit");
    }

    public static FormDescriptor species(String species) {
        if (!MorphCollection.isFormId(species)) throw new IllegalArgumentException("Invalid species form ID");
        return new FormDescriptor(VERSION, species, "morph:species", 1, Map.of(), null, Map.of(), Map.of(), null);
    }

    public FormDescriptor withAttributes(Map<String, Double> values) {
        return new FormDescriptor(version, species, adapter, adapterVersion, variant, customName, equipment, values, profile);
    }

    public String identityJson() {
        return FormDescriptorJson.canonical(json(version, species, adapter, adapterVersion, variant,
                customName, equipment, attributes, profile, true));
    }

    public EntryId entryId() {
        try {
            return new EntryId("v1:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(identityJson().getBytes(StandardCharsets.UTF_8))));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public JsonObject toJson() {
        return json(version, species, adapter, adapterVersion, variant, customName, equipment, attributes, profile, false);
    }

    public String canonicalJson() { return FormDescriptorJson.canonical(toJson()); }
    public static FormDescriptor fromJson(String input) { return fromJson(FormDescriptorJson.parse(input, MAX_BYTES)); }

    public static FormDescriptor fromJson(byte[] input) {
        if (input == null || input.length > MAX_BYTES) throw new IllegalArgumentException("Descriptor exceeds byte limit");
        try {
            var decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
            return fromJson(decoder.decode(java.nio.ByteBuffer.wrap(input)).toString());
        } catch (java.nio.charset.CharacterCodingException invalid) { throw new IllegalArgumentException("Invalid UTF-8 descriptor", invalid); }
    }

    public static FormDescriptor fromJson(JsonElement input) {
        var root = FormDescriptorJson.object(input, Set.of("version", "species", "adapter", "adapterVersion",
                "variant", "customName", "equipment", "attributes", "profile"));
        var variants = new TreeMap<String, Object>();
        var variantJson = FormDescriptorJson.map(root.get("variant"), MAX_FIELDS);
        variantJson.entrySet().forEach(e -> {
            if (!e.getValue().isJsonPrimitive()) throw new IllegalArgumentException("Invalid variant value");
            var value = e.getValue().getAsJsonPrimitive();
            variants.put(e.getKey(), value.isBoolean() ? value.getAsBoolean()
                    : value.isString() ? value.getAsString() : FormDescriptorJson.integer(value));
        });
        var gear = new TreeMap<String, CapturedEquipment>();
        FormDescriptorJson.map(root.get("equipment"), EQUIPMENT_SLOTS.size()).entrySet().forEach(e -> {
            var item = FormDescriptorJson.object(e.getValue(), Set.of("item", "enchanted"), Set.of("damage", "dyedRgb"));
            gear.put(e.getKey(), new CapturedEquipment(FormDescriptorJson.string(item.get("item")),
                    item.has("damage") ? FormDescriptorJson.integer(item.get("damage")) : null,
                    item.has("dyedRgb") ? FormDescriptorJson.integer(item.get("dyedRgb")) : null,
                    FormDescriptorJson.bool(item.get("enchanted"))));
        });
        var attrs = new TreeMap<String, Double>();
        FormDescriptorJson.map(root.get("attributes"), MAX_FIELDS).entrySet().forEach(e -> {
            if (!e.getValue().isJsonPrimitive() || !e.getValue().getAsJsonPrimitive().isNumber())
                throw new IllegalArgumentException("Attribute must be a number");
            attrs.put(e.getKey(), e.getValue().getAsDouble());
        });
        PlayerProfile profile = null;
        if (!root.get("profile").isJsonNull()) {
            var value = FormDescriptorJson.object(root.get("profile"), Set.of("uuid", "name", "skinHash", "model"));
            profile = new PlayerProfile(FormDescriptorJson.uuid(value.get("uuid")),
                    FormDescriptorJson.string(value.get("name")), FormDescriptorJson.nullableString(value.get("skinHash")),
                    SkinModel.valueOf(FormDescriptorJson.string(value.get("model"))));
        }
        return new FormDescriptor(FormDescriptorJson.integer(root.get("version")), FormDescriptorJson.string(root.get("species")),
                FormDescriptorJson.string(root.get("adapter")), FormDescriptorJson.integer(root.get("adapterVersion")),
                variants, FormDescriptorJson.nullableString(root.get("customName")), gear, attrs, profile);
    }

    private static void validateKnownAdapter(String species, String adapter, int adapterVersion, Map<String, Object> variant,
            String name, Map<String, CapturedEquipment> equipment, PlayerProfile profile) {
        if (adapterVersion != 1) return; // Preserve future adapters as unavailable, without interpreting their fields.
        switch (adapter) {
            case "morph:species" -> {
                if (!variant.isEmpty() || name != null || !equipment.isEmpty() || profile != null)
                    throw new IllegalArgumentException("Species adapter cannot carry appearance state");
            }
            case "morph:sheep" -> {
                if (!species.equals("minecraft:sheep") || !variant.keySet().equals(Set.of("baby", "color"))
                        || !(variant.get("baby") instanceof Boolean))
                    throw new IllegalArgumentException("Invalid sheep variant");
                requireInteger(variant.get("color"), 0, 15);
            }
            case "morph:slime" -> {
                if (!species.equals("minecraft:slime") || !variant.keySet().equals(Set.of("size")))
                    throw new IllegalArgumentException("Invalid slime variant");
                requireInteger(variant.get("size"), 1, 16);
            }
            default -> { } // Adapter availability is a separate service/registry decision.
        }
    }

    private static void requireInteger(Object value, int minimum, int maximum) {
        if (!(value instanceof Integer number) || number < minimum || number > maximum)
            throw new IllegalArgumentException("Variant integer is out of range");
    }

    public static void requireIdentifier(String value) {
        if (value == null || value.length() > 256 || !value.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))
            throw new IllegalArgumentException("Invalid namespaced identifier");
    }

    public static String normalizedText(String value, int maximumBytes, boolean nonempty) {
        if (value == null) throw new IllegalArgumentException("Missing text");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i)))
                    throw new IllegalArgumentException("Unpaired surrogate");
            } else if (Character.isLowSurrogate(c)) throw new IllegalArgumentException("Unpaired surrogate");
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
        if ((nonempty && normalized.isEmpty()) || normalized.getBytes(StandardCharsets.UTF_8).length > maximumBytes)
            throw new IllegalArgumentException("Text exceeds byte limit or is empty");
        return normalized;
    }

    private static JsonObject json(int version, String species, String adapter, int adapterVersion,
            Map<String, Object> variant, String customName, Map<String, CapturedEquipment> equipment,
            Map<String, Double> attributes, PlayerProfile profile, boolean identity) {
        var root = new JsonObject();
        root.addProperty("version", version);
        root.addProperty("species", species);
        root.addProperty("adapter", adapter);
        root.addProperty(identity ? "adapter_version" : "adapterVersion", adapterVersion);
        root.add(identity ? "custom_name" : "customName", customName == null ? JsonNull.INSTANCE : new JsonPrimitive(customName));
        var variants = new JsonObject();
        variant.forEach((key, value) -> variants.add(key, value instanceof Boolean b ? new JsonPrimitive(b)
                : value instanceof Integer n ? new JsonPrimitive(n) : new JsonPrimitive((String) value)));
        root.add("variant", variants);
        var gear = new JsonObject();
        equipment.forEach((key, value) -> {
            var item = new JsonObject();
            item.addProperty("item", value.item());
            item.addProperty("enchanted", value.enchanted());
            if (value.damage() != null) item.addProperty("damage", value.damage());
            if (value.dyedRgb() != null) item.addProperty("dyedRgb", value.dyedRgb());
            gear.add(key, item);
        });
        root.add("equipment", gear);
        if (identity) root.add("profile_uuid", profile == null ? JsonNull.INSTANCE : new JsonPrimitive(profile.uuid().toString()));
        else {
            var attrs = new JsonObject();
            attributes.forEach(attrs::addProperty);
            root.add("attributes", attrs);
            JsonElement player = JsonNull.INSTANCE;
            if (profile != null) {
                var object = new JsonObject();
                object.addProperty("uuid", profile.uuid().toString());
                object.addProperty("name", profile.name());
                object.add("skinHash", profile.skinHash() == null ? JsonNull.INSTANCE : new JsonPrimitive(profile.skinHash()));
                object.addProperty("model", profile.model().name());
                player = object;
            }
            root.add("profile", player);
        }
        return root;
    }

    public record CapturedEquipment(String item, Integer damage, Integer dyedRgb, boolean enchanted) {
        public CapturedEquipment {
            requireIdentifier(item);
            if (damage != null && (damage < 0 || damage > 1_000_000)) throw new IllegalArgumentException("Invalid item damage");
            if (dyedRgb != null && (dyedRgb < 0 || dyedRgb > 0xffffff)) throw new IllegalArgumentException("Invalid item color");
        }
    }

    public enum SkinModel { WIDE, SLIM }

    public record PlayerProfile(UUID uuid, String name, String skinHash, SkinModel model) {
        public PlayerProfile {
            if (uuid == null || name == null || !name.matches("[A-Za-z0-9_]{1,16}") || model == null
                    || (skinHash != null && !skinHash.matches("[0-9a-f]{64}")))
                throw new IllegalArgumentException("Invalid player profile");
        }
    }
}
