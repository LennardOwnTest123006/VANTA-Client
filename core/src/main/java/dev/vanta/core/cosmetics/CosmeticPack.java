package dev.vanta.core.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A user-installed cosmetic pack ({@code config/vanta/cosmetics/<file>.json}): a bundle of UI themes.
 * Packs are purely visual and cannot contain code.
 * <pre>
 * {"schemaVersion": 1, "id": "my-pack", "name": "My Pack", "author": "Name",
 *  "themes": [{"id": "my-theme", "name": "My Theme", "tokens": {"accent.violet": "#FF8844"}}]}
 * </pre>
 *
 * @param id     pack id ({@code [a-z0-9-]{2,32}})
 * @param name   display name (≤ 48 chars)
 * @param author author (≤ 48 chars, may be empty)
 * @param themes themes contributed by the pack (1–16)
 */
public record CosmeticPack(String id, String name, String author, List<UiThemeDefinition> themes) {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_THEMES = 16;
    public static final int MAX_TEXT = 48;
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9-]{1,31}");

    public CosmeticPack {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        author = author == null ? "" : author;
        if (!ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid pack id: " + id);
        }
        if (name.isBlank() || name.length() > MAX_TEXT || author.length() > MAX_TEXT) {
            throw new IllegalArgumentException("Pack name/author must be 1–" + MAX_TEXT + " characters");
        }
        themes = List.copyOf(themes);
        if (themes.isEmpty() || themes.size() > MAX_THEMES) {
            throw new IllegalArgumentException("A pack must contain 1–" + MAX_THEMES + " themes");
        }
    }

    /**
     * Parses a pack.
     *
     * @throws IllegalArgumentException when the JSON is invalid
     */
    public static CosmeticPack fromJson(JsonObject o) {
        int schema = o.has("schemaVersion") && o.get("schemaVersion").isJsonPrimitive()
                ? o.get("schemaVersion").getAsInt() : 1;
        if (schema > SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported cosmetic pack schemaVersion " + schema);
        }
        String id = requireString(o, "id");
        String name = requireString(o, "name");
        String author = o.has("author") && o.get("author").isJsonPrimitive() ? o.get("author").getAsString() : "";
        JsonElement themesElement = o.get("themes");
        if (themesElement == null || !themesElement.isJsonArray()) {
            throw new IllegalArgumentException("Pack is missing 'themes'");
        }
        List<UiThemeDefinition> themes = new ArrayList<>();
        for (JsonElement element : themesElement.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("Every theme must be an object");
            }
            themes.add(UiThemeDefinition.fromJson(element.getAsJsonObject(), false));
        }
        return new CosmeticPack(id, name, author, themes);
    }

    /** JSON form. */
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("schemaVersion", SCHEMA_VERSION);
        o.addProperty("id", id);
        o.addProperty("name", name);
        o.addProperty("author", author);
        JsonArray array = new JsonArray();
        for (UiThemeDefinition theme : themes) {
            array.add(theme.toJson());
        }
        o.add("themes", array);
        return o;
    }

    private static String requireString(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Pack is missing '" + key + "'");
        }
        return e.getAsString();
    }
}
