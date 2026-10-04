package dev.vanta.launcher.core.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import dev.vanta.launcher.core.model.Argument;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Shared Gson instance and small helpers. All launcher JSON is pretty printed and written atomically.
 */
public final class Json {

    /** Configured Gson instance (pretty printing, no HTML escaping, union type adapters registered). */
    public static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .registerTypeAdapter(Argument.class, new Argument.Adapter())
        .create();

    private Json() {
    }

    /**
     * Parses JSON text.
     *
     * @param json text
     * @param type target type
     * @param <T>  target type
     * @return parsed object, never {@code null}
     * @throws JsonParseException when the text is not valid JSON for the type or is the literal {@code null}
     */
    public static <T> T parse(final String json, final Class<T> type) {
        final T value;
        try {
            value = GSON.fromJson(json, type);
        } catch (JsonSyntaxException e) {
            throw new JsonParseException("Malformed JSON for " + type.getSimpleName() + ": " + e.getMessage(), e);
        }
        if (value == null) {
            throw new JsonParseException("Empty JSON document for " + type.getSimpleName());
        }
        return value;
    }

    /**
     * Parses JSON text into a tree.
     *
     * @param json text
     * @return element
     */
    public static JsonElement tree(final String json) {
        return JsonParser.parseString(json);
    }

    /**
     * Reads and parses a JSON file.
     *
     * @param file file
     * @param type target type
     * @param <T>  target type
     * @return parsed object
     * @throws IOException on I/O failure or malformed content
     */
    public static <T> T read(final Path file, final Class<T> type) throws IOException {
        final String text = Files.readString(file, StandardCharsets.UTF_8);
        try {
            return parse(text, type);
        } catch (JsonParseException e) {
            throw new IOException("Cannot parse " + file + ": " + e.getMessage(), e);
        }
    }

    /**
     * Serialises an object to pretty printed JSON.
     *
     * @param value object
     * @return JSON text
     */
    public static String toJson(final Object value) {
        return GSON.toJson(Objects.requireNonNull(value, "value"));
    }

    /**
     * Serialises an object and writes it atomically.
     *
     * @param file  destination
     * @param value object
     * @throws IOException on failure
     */
    public static void write(final Path file, final Object value) throws IOException {
        AtomicFiles.writeString(file, toJson(value) + System.lineSeparator());
    }
}
