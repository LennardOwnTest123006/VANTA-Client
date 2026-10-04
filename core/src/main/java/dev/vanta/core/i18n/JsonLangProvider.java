package dev.vanta.core.i18n;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * {@link LangProvider} backed by a Minecraft-style flat language JSON ({@code {"key": "value", …}}).
 */
public final class JsonLangProvider implements LangProvider {
    /** Classpath location of the bundled English strings (also shipped inside the mod jar). */
    public static final String EN_US_RESOURCE = "/assets/vanta/lang/en_us.json";

    private final Map<String, String> strings;

    private JsonLangProvider(Map<String, String> strings) {
        this.strings = Collections.unmodifiableMap(new LinkedHashMap<>(strings));
    }

    /** Loads the bundled {@code en_us.json}. */
    public static JsonLangProvider builtIn() {
        return fromClasspath(EN_US_RESOURCE);
    }

    /**
     * Loads a language file from the classpath.
     *
     * @throws IllegalStateException when the resource is missing or invalid
     */
    public static JsonLangProvider fromClasspath(String resource) {
        InputStream in = JsonLangProvider.class.getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalStateException("Language resource not found on the classpath: " + resource);
        }
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return fromReader(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read language resource " + resource, e);
        }
    }

    /** Parses a flat JSON object of strings. Non-string values are ignored. */
    public static JsonLangProvider fromReader(Reader reader) {
        JsonElement element;
        try {
            element = JsonParser.parseReader(reader);
        } catch (JsonParseException e) {
            throw new IllegalStateException("Language file is not valid JSON", e);
        }
        if (element == null || !element.isJsonObject()) {
            throw new IllegalStateException("Language file must contain a JSON object");
        }
        return fromJson(element.getAsJsonObject());
    }

    /** Builds a provider from an already parsed JSON object. */
    public static JsonLangProvider fromJson(JsonObject json) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            JsonElement value = entry.getValue();
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                map.put(entry.getKey(), value.getAsString());
            }
        }
        return new JsonLangProvider(map);
    }

    /** Builds a provider from a plain map (tests). */
    public static JsonLangProvider of(Map<String, String> strings) {
        return new JsonLangProvider(Objects.requireNonNull(strings, "strings"));
    }

    @Override
    public Optional<String> lookup(String key) {
        return Optional.ofNullable(strings.get(key));
    }

    /** All keys in file order. */
    public Set<String> keys() {
        return strings.keySet();
    }

    /** Number of translations. */
    public int size() {
        return strings.size();
    }
}
