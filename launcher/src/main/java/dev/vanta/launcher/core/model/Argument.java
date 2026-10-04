package dev.vanta.launcher.core.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * One entry of {@code arguments.game} / {@code arguments.jvm}. Mojang encodes entries either as a plain string or
 * as {@code {"rules": [...], "value": "x" | ["x", "y"]}}; both forms map to this record.
 *
 * @param rules  rules that must allow the argument (empty = unconditional)
 * @param values one or more argument tokens
 */
public record Argument(List<Rule> rules, List<String> values) {

    public Argument {
        rules = rules == null ? List.of() : List.copyOf(rules);
        values = values == null ? List.of() : List.copyOf(values);
    }

    /**
     * Unconditional single token.
     *
     * @param value token
     * @return argument
     */
    public static Argument of(final String value) {
        return new Argument(List.of(), List.of(value));
    }

    /** @return whether the argument has no rules */
    public boolean isUnconditional() {
        return rules.isEmpty();
    }

    /**
     * Gson adapter for the string-or-object encoding.
     */
    public static final class Adapter implements JsonDeserializer<Argument>, JsonSerializer<Argument> {

        private static final Type RULE_LIST = new TypeToken<List<Rule>>() {
        }.getType();

        @Override
        public Argument deserialize(final JsonElement json, final Type typeOfT, final JsonDeserializationContext context)
            throws JsonParseException {
            if (json.isJsonPrimitive()) {
                return Argument.of(json.getAsString());
            }
            if (!json.isJsonObject()) {
                throw new JsonParseException("Argument must be a string or an object but was " + json);
            }
            final JsonObject obj = json.getAsJsonObject();
            final List<Rule> rules = obj.has("rules") && !obj.get("rules").isJsonNull()
                ? context.deserialize(obj.get("rules"), RULE_LIST) : List.of();
            final List<String> values = new ArrayList<>();
            final JsonElement value = obj.get("value");
            if (value == null || value.isJsonNull()) {
                // nothing
            } else if (value.isJsonArray()) {
                for (JsonElement e : value.getAsJsonArray()) {
                    values.add(e.getAsString());
                }
            } else {
                values.add(value.getAsString());
            }
            return new Argument(rules, values);
        }

        @Override
        public JsonElement serialize(final Argument src, final Type typeOfSrc, final JsonSerializationContext context) {
            if (src.isUnconditional() && src.values().size() == 1) {
                return new JsonPrimitive(src.values().get(0));
            }
            final JsonObject obj = new JsonObject();
            obj.add("rules", context.serialize(src.rules(), RULE_LIST));
            if (src.values().size() == 1) {
                obj.addProperty("value", src.values().get(0));
            } else {
                final JsonArray arr = new JsonArray();
                src.values().forEach(arr::add);
                obj.add("value", arr);
            }
            return obj;
        }
    }
}
