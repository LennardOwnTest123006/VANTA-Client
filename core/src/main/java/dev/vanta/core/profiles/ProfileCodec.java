package dev.vanta.core.profiles;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.vanta.core.bridge.KeyRef;
import dev.vanta.core.config.FileNames;
import dev.vanta.core.config.JsonStore;
import dev.vanta.core.cosmetics.CosmeticsSelection;
import dev.vanta.core.crosshair.CrosshairStyle;
import dev.vanta.core.hud.HudLayout;
import dev.vanta.core.hud.HudLayoutCodec;
import dev.vanta.core.keybinds.VantaKeys;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON (de)serialisation and validation of {@link Profile}s.
 * <p>
 * Reading is strict about shape (an import must not crash later code) but tolerant about content: unknown keys are
 * ignored, string values are length-limited, only VANTA key ids are accepted as key overrides, and the HUD layout
 * goes through the tolerant {@link HudLayoutCodec}.
 */
public final class ProfileCodec {
    /** Longest string value accepted inside the settings snapshot. */
    public static final int MAX_STRING_VALUE = 1024;
    /** Longest setting id accepted. */
    public static final int MAX_SETTING_ID = 128;

    private ProfileCodec() {
    }

    /** Serialises a profile. */
    public static JsonObject toJson(Profile profile) {
        JsonObject o = new JsonObject();
        o.addProperty(JsonStore.SCHEMA_VERSION, profile.schemaVersion());
        o.addProperty("id", profile.id());
        o.addProperty("name", profile.name());
        o.addProperty("icon", profile.icon());
        o.addProperty("createdAt", profile.createdAt());
        o.addProperty("updatedAt", profile.updatedAt());
        JsonObject settings = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : profile.settings().entrySet()) {
            settings.add(entry.getKey(), entry.getValue());
        }
        o.add("settings", settings);
        o.add("hud", HudLayoutCodec.write(profile.hud(), new JsonObject()));
        JsonObject keys = new JsonObject();
        for (Map.Entry<String, KeyRef> entry : profile.keybinds().entrySet()) {
            keys.addProperty(entry.getKey(), entry.getValue().serialize());
        }
        o.add("keybinds", keys);
        o.add("crosshair", profile.crosshair().toJson());
        o.add("cosmetics", profile.cosmetics().toJson());
        return o;
    }

    /**
     * Parses JSON text.
     *
     * @param text      file content
     * @param idForFile id to assign (derived from the file name by the caller), overriding the stored id
     */
    public static Profile parse(String text, String idForFile) throws ProfileImportException {
        JsonElement element;
        try {
            element = JsonParser.parseString(text);
        } catch (JsonParseException e) {
            throw new ProfileImportException(ProfileImportException.Reason.NOT_JSON, "Not valid JSON", e);
        }
        if (element == null || !element.isJsonObject()) {
            throw new ProfileImportException(ProfileImportException.Reason.NOT_JSON, "Profile must be a JSON object");
        }
        return fromJson(element.getAsJsonObject(), idForFile);
    }

    /**
     * Validates and converts a JSON object.
     *
     * @param o         the object
     * @param idForFile id to assign; must already be a safe file stem
     */
    public static Profile fromJson(JsonObject o, String idForFile) throws ProfileImportException {
        if (!FileNames.isSafe(idForFile)) {
            throw new ProfileImportException(ProfileImportException.Reason.INVALID_FIELD, "Unsafe profile id");
        }
        int schema = JsonStore.schemaVersion(o, 1);
        if (schema > Profile.SCHEMA_VERSION) {
            throw new ProfileImportException(ProfileImportException.Reason.UNSUPPORTED_SCHEMA,
                    "Profile schemaVersion " + schema + " is newer than supported " + Profile.SCHEMA_VERSION);
        }
        if (schema < 1) {
            throw new ProfileImportException(ProfileImportException.Reason.INVALID_FIELD, "Invalid schemaVersion");
        }
        String name = string(o, "name");
        if (name == null || name.isBlank()) {
            throw new ProfileImportException(ProfileImportException.Reason.INVALID_FIELD, "Missing profile name");
        }
        name = name.trim();
        if (name.length() > Profile.MAX_NAME) {
            name = name.substring(0, Profile.MAX_NAME);
        }
        String icon = string(o, "icon");
        if (icon == null || icon.isBlank() || icon.length() > Profile.MAX_ICON || !icon.matches("[a-z0-9_-]+")) {
            icon = Profile.DEFAULT_ICON;
        }
        long createdAt = number(o, "createdAt");
        long updatedAt = number(o, "updatedAt");

        Map<String, JsonElement> settings = new LinkedHashMap<>();
        JsonElement settingsElement = o.get("settings");
        if (settingsElement != null) {
            if (!settingsElement.isJsonObject()) {
                throw new ProfileImportException(ProfileImportException.Reason.INVALID_FIELD,
                        "'settings' must be an object");
            }
            for (Map.Entry<String, JsonElement> entry : settingsElement.getAsJsonObject().entrySet()) {
                if (settings.size() >= Profile.MAX_SETTINGS) {
                    break;
                }
                String key = entry.getKey();
                JsonElement value = entry.getValue();
                if (key.length() > MAX_SETTING_ID || !value.isJsonPrimitive()) {
                    continue;
                }
                if (value.getAsJsonPrimitive().isString() && value.getAsString().length() > MAX_STRING_VALUE) {
                    continue;
                }
                settings.put(key, value);
            }
        }

        HudLayout hud = HudLayout.EMPTY;
        JsonElement hudElement = o.get("hud");
        if (hudElement != null) {
            if (!hudElement.isJsonObject()) {
                throw new ProfileImportException(ProfileImportException.Reason.INVALID_FIELD, "'hud' must be an object");
            }
            hud = HudLayoutCodec.read(hudElement.getAsJsonObject());
        }

        Map<String, KeyRef> keybinds = new LinkedHashMap<>();
        JsonElement keysElement = o.get("keybinds");
        if (keysElement != null && keysElement.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : keysElement.getAsJsonObject().entrySet()) {
                if (keybinds.size() >= Profile.MAX_KEYBINDS) {
                    break;
                }
                if (!VantaKeys.isVantaKey(entry.getKey()) || !entry.getValue().isJsonPrimitive()) {
                    continue;
                }
                keybinds.put(entry.getKey(), KeyRef.parse(entry.getValue().getAsString()));
            }
        }

        JsonElement crosshairElement = o.get("crosshair");
        CrosshairStyle crosshair = crosshairElement != null && crosshairElement.isJsonObject()
                ? CrosshairStyle.fromJson(crosshairElement.getAsJsonObject()) : CrosshairStyle.DEFAULT;
        JsonElement cosmeticsElement = o.get("cosmetics");
        CosmeticsSelection cosmetics = cosmeticsElement != null && cosmeticsElement.isJsonObject()
                ? CosmeticsSelection.fromJson(cosmeticsElement.getAsJsonObject()) : CosmeticsSelection.DEFAULT;

        try {
            // The file's own schema version is kept so ProfileManager.load() can tell which files still need its
            // one-time upgrade; a profile written back carries the version it was loaded with until then.
            return new Profile(schema, idForFile, name, icon, createdAt, updatedAt, settings, hud, keybinds, crosshair,
                    cosmetics);
        } catch (IllegalArgumentException e) {
            throw new ProfileImportException(ProfileImportException.Reason.INVALID_FIELD, e.getMessage(), e);
        }
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
    }

    private static long number(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsLong() : 0L;
    }
}
