package dev.vanta.core.cosmetics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vanta.core.i18n.LangKeyed;
import dev.vanta.core.settings.Setting;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A UI theme: a set of design-token overrides applied on top of the default VANTA theme.
 * <p>
 * Token keys follow {@code shared/design/tokens.json} paths such as {@code accent.violet}, {@code surface.1} or
 * {@code text.primary}; values are ARGB colours. The UI layer resolves {@link #tokenOverrides()} into its
 * {@code Theme}, so cosmetics never touch rendering code.
 * <pre>
 * {"id": "midnight", "name": "vanta.cosmetics.theme.midnight",
 *  "tokens": {"accent.violet": "#4F8DFF", "accent.violetHover": "#7FAAFF"}}
 * </pre>
 *
 * @param id             stable id ({@code [a-z0-9-]{2,32}})
 * @param name           translation key (built-ins) or literal name (packs)
 * @param tokenOverrides token path → ARGB
 * @param builtIn        true for themes shipped with VANTA
 */
public record UiThemeDefinition(String id, String name, Map<String, Integer> tokenOverrides, boolean builtIn)
        implements LangKeyed {
    /** Id pattern. */
    public static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9-]{1,31}");
    /** Token key pattern. */
    public static final Pattern TOKEN_PATTERN = Pattern.compile("[a-z][a-zA-Z0-9]*(\\.[a-zA-Z0-9]+)*");
    /** Longest literal theme name. */
    public static final int MAX_NAME = 48;
    /** Most overrides a theme may define. */
    public static final int MAX_TOKENS = 64;
    /** Id of the default theme. */
    public static final String DEFAULT_ID = "vanta-dark";

    public UiThemeDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        if (!ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid theme id: " + id);
        }
        if (name.isBlank() || name.length() > Math.max(MAX_NAME, 80)) {
            throw new IllegalArgumentException("Invalid theme name");
        }
        if (tokenOverrides.size() > MAX_TOKENS) {
            throw new IllegalArgumentException("Theme defines too many tokens");
        }
        for (String key : tokenOverrides.keySet()) {
            if (!TOKEN_PATTERN.matcher(key).matches()) {
                throw new IllegalArgumentException("Invalid token key: " + key);
            }
        }
        tokenOverrides = Map.copyOf(new LinkedHashMap<>(tokenOverrides));
    }

    /** The default theme: no overrides. */
    public static UiThemeDefinition defaultTheme() {
        return new UiThemeDefinition(DEFAULT_ID, "vanta.cosmetics.theme.vanta-dark", Map.of(), true);
    }

    @Override
    public String langKey() {
        return name;
    }

    /** Translation key of the description (built-ins). */
    public String descriptionKey() {
        return name + ".description";
    }

    /** ARGB for a token, if overridden. */
    public java.util.Optional<Integer> token(String key) {
        return java.util.Optional.ofNullable(tokenOverrides.get(key));
    }

    /** JSON form. */
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("name", name);
        JsonObject tokens = new JsonObject();
        for (Map.Entry<String, Integer> entry : tokenOverrides.entrySet()) {
            tokens.addProperty(entry.getKey(), Setting.formatColor(entry.getValue()));
        }
        o.add("tokens", tokens);
        return o;
    }

    /**
     * Parses a theme.
     *
     * @throws IllegalArgumentException when the JSON is invalid
     */
    public static UiThemeDefinition fromJson(JsonObject o, boolean builtIn) {
        if (!o.has("id") || !o.get("id").isJsonPrimitive()) {
            throw new IllegalArgumentException("Theme is missing 'id'");
        }
        String id = o.get("id").getAsString();
        String name = o.has("name") && o.get("name").isJsonPrimitive() ? o.get("name").getAsString() : id;
        if (!builtIn && name.length() > MAX_NAME) {
            name = name.substring(0, MAX_NAME);
        }
        Map<String, Integer> tokens = new LinkedHashMap<>();
        JsonElement tokensElement = o.get("tokens");
        if (tokensElement != null && tokensElement.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : tokensElement.getAsJsonObject().entrySet()) {
                if (!entry.getValue().isJsonPrimitive()) {
                    throw new IllegalArgumentException("Token " + entry.getKey() + " must be a colour string");
                }
                int argb = Setting.parseColor(entry.getValue().getAsString())
                        .orElseThrow(() -> new IllegalArgumentException("Token " + entry.getKey()
                                + " is not a #RRGGBB / #AARRGGBB colour"));
                tokens.put(entry.getKey(), argb);
            }
        }
        return new UiThemeDefinition(id, name, tokens, builtIn);
    }
}
