package dev.vanta.core.modrinth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tolerant JSON accessors: a missing field, a {@code null} or a value of the wrong type yields the fallback instead of
 * an exception, so a changed API response degrades instead of breaking the screen.
 */
final class Json {
    private Json() {
    }

    static String string(JsonObject object, String key) {
        return string(object, key, "");
    }

    static String string(JsonObject object, String key, String fallback) {
        JsonElement e = object == null ? null : object.get(key);
        if (e == null || !e.isJsonPrimitive()) {
            return fallback;
        }
        JsonPrimitive p = e.getAsJsonPrimitive();
        return p.isString() || p.isNumber() || p.isBoolean() ? p.getAsString() : fallback;
    }

    /** String value or {@code null} when missing / null / blank. */
    static String nullableString(JsonObject object, String key) {
        String value = string(object, key, null);
        return value == null || value.isBlank() ? null : value;
    }

    static long longValue(JsonObject object, String key, long fallback) {
        JsonElement e = object == null ? null : object.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        try {
            return e.getAsLong();
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    static int intValue(JsonObject object, String key, int fallback) {
        long value = longValue(object, key, fallback);
        return value > Integer.MAX_VALUE || value < Integer.MIN_VALUE ? fallback : (int) value;
    }

    static boolean bool(JsonObject object, String key, boolean fallback) {
        JsonElement e = object == null ? null : object.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean()) {
            return fallback;
        }
        return e.getAsBoolean();
    }

    static List<String> strings(JsonObject object, String key) {
        JsonElement e = object == null ? null : object.get(key);
        List<String> out = new ArrayList<>();
        if (e == null || !e.isJsonArray()) {
            return out;
        }
        for (JsonElement item : e.getAsJsonArray()) {
            if (item != null && item.isJsonPrimitive()) {
                out.add(item.getAsString());
            }
        }
        return out;
    }

    static List<JsonObject> objects(JsonObject object, String key) {
        JsonElement e = object == null ? null : object.get(key);
        return e != null && e.isJsonArray() ? objects(e.getAsJsonArray()) : new ArrayList<>();
    }

    static List<JsonObject> objects(JsonArray array) {
        List<JsonObject> out = new ArrayList<>();
        for (JsonElement item : array) {
            if (item != null && item.isJsonObject()) {
                out.add(item.getAsJsonObject());
            }
        }
        return out;
    }

    static JsonObject object(JsonObject object, String key) {
        JsonElement e = object == null ? null : object.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
    }

    /** Parses an ISO-8601 timestamp (with or without offset); {@link Instant#EPOCH} when unparsable. */
    static Instant instant(String text) {
        if (text == null || text.isBlank()) {
            return Instant.EPOCH;
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            try {
                return OffsetDateTime.parse(text).toInstant();
            } catch (DateTimeParseException e2) {
                return Instant.EPOCH;
            }
        }
    }

    static JsonArray array(List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }
}
