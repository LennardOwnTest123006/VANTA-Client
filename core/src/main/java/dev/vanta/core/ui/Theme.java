package dev.vanta.core.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The in-game design system: every color token from {@code shared/design/tokens.json} as packed ARGB plus the
 * in-game spacing and radius scale, the UI scale factor and the accessibility flags.
 * <p>
 * Themes are immutable; use the {@code with*} methods to derive variants. Token names use the dotted form of
 * the JSON file ({@code "accent.violet"}, {@code "surface.1"}, {@code "state.danger"}, {@code "gradient.accent.0"}).
 *
 * <h2>JSON shapes accepted by {@link #fromTokensJson(Reader)}</h2>
 * <ol>
 *   <li>The canonical token file: {@code {"color": {"bg": {"void": "#07070A", ...}, "surface": {"1": "#111118"},
 *       ..., "gradient": {"accent": {"stops": ["#7C5CFF", "#4F8DFF"]}}}}}. Missing groups fall back to the default
 *       theme, so partial files are fine.</li>
 *   <li>A cosmetic theme: {@code {"id": "midnight", "name": "Midnight", "colors": {"accent.violet": "#...",
 *       "surface.1": "#..."}}} — flat dotted token names under {@code "colors"} (or {@code "tokens"}).</li>
 * </ol>
 * Colors use the CSS convention of the token file: {@code #RRGGBB} or {@code #RRGGBBAA} (alpha last).
 */
public final class Theme {

    /** Duration token {@code motion.duration.fast} in ms. */
    public static final long MOTION_FAST = 120L;
    /** Duration token {@code motion.duration.base} in ms. */
    public static final long MOTION_BASE = 200L;
    /** Duration token {@code motion.duration.slow} in ms. */
    public static final long MOTION_SLOW = 320L;

    /** In-game spacing scale ({@code ingame.spacing} 1..7). */
    public static final int SPACE_1 = 2;
    public static final int SPACE_2 = 4;
    public static final int SPACE_3 = 6;
    public static final int SPACE_4 = 8;
    public static final int SPACE_5 = 12;
    public static final int SPACE_6 = 16;
    public static final int SPACE_7 = 24;

    /** In-game radius scale ({@code ingame.radius}). */
    public static final int RADIUS_SM = 2;
    public static final int RADIUS_MD = 3;
    public static final int RADIUS_LG = 4;

    /** Default translucency of panels over the game world ({@code ingame.panelAlpha}). */
    public static final float PANEL_ALPHA = 0.82f;

    private static final String[] TOKEN_ORDER = {
            "bg.void", "bg.base",
            "surface.1", "surface.2", "surface.3", "surface.4",
            "border.subtle", "border.strong", "border.focus",
            "text.primary", "text.secondary", "text.muted", "text.inverse",
            "accent.violet", "accent.violetHover", "accent.violetPressed", "accent.violetSoft",
            "accent.blue", "accent.blueSoft",
            "state.success", "state.warning", "state.danger", "state.info",
            "gradient.accent.0", "gradient.accent.1",
    };

    private static final Map<String, Integer> DEFAULT_TOKENS = defaultTokens();

    /** The VANTA dark theme built from the canonical token values. */
    public static final Theme DEFAULT = new Theme("vanta", "VANTA", DEFAULT_TOKENS, 1f, false, false, false, false);

    private final String id;
    private final String name;
    private final Map<String, Integer> tokens;
    private final float scale;
    private final boolean reducedMotion;
    private final boolean reducedTransparency;
    private final boolean largeText;
    private final boolean highContrast;

    private Theme(String id, String name, Map<String, Integer> tokens, float scale, boolean reducedMotion,
                  boolean reducedTransparency, boolean largeText, boolean highContrast) {
        this.id = id;
        this.name = name;
        this.tokens = Collections.unmodifiableMap(new LinkedHashMap<>(tokens));
        this.scale = scale;
        this.reducedMotion = reducedMotion;
        this.reducedTransparency = reducedTransparency;
        this.largeText = largeText;
        this.highContrast = highContrast;
    }

    private static Map<String, Integer> defaultTokens() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("bg.void", 0xFF07070A);
        m.put("bg.base", 0xFF0B0B10);
        m.put("surface.1", 0xFF111118);
        m.put("surface.2", 0xFF17171F);
        m.put("surface.3", 0xFF1F1F29);
        m.put("surface.4", 0xFF272733);
        m.put("border.subtle", 0xFF232330);
        m.put("border.strong", 0xFF2C2C3A);
        m.put("border.focus", 0xFF9B82FF);
        m.put("text.primary", 0xFFF5F5F7);
        m.put("text.secondary", 0xFFA1A1AA);
        m.put("text.muted", 0xFF6B6B78);
        m.put("text.inverse", 0xFF0B0B10);
        m.put("accent.violet", 0xFF7C5CFF);
        m.put("accent.violetHover", 0xFF9B82FF);
        m.put("accent.violetPressed", 0xFF5B3DE0);
        m.put("accent.violetSoft", 0x337C5CFF);
        m.put("accent.blue", 0xFF4F8DFF);
        m.put("accent.blueSoft", 0x334F8DFF);
        m.put("state.success", 0xFF3DDC97);
        m.put("state.warning", 0xFFF5B942);
        m.put("state.danger", 0xFFFF5C7A);
        m.put("state.info", 0xFF4F8DFF);
        m.put("gradient.accent.0", 0xFF7C5CFF);
        m.put("gradient.accent.1", 0xFF4F8DFF);
        return m;
    }

    /** Names of all color tokens in canonical order. */
    public static String[] tokenNames() {
        return TOKEN_ORDER.clone();
    }

    // ---------------------------------------------------------------- factories

    /**
     * A theme with stronger borders, pure white text and a brighter muted tone for users who enabled
     * high contrast. Accent colors are unchanged so the brand stays recognisable.
     */
    public static Theme highContrast() {
        return DEFAULT.withHighContrast(true);
    }

    /**
     * Parses a theme from JSON (see the class documentation for the accepted shapes). Unknown tokens are ignored,
     * missing tokens keep their default value.
     *
     * @throws JsonParseException when the document is not valid JSON or a color is malformed
     */
    public static Theme fromTokensJson(Reader reader) {
        JsonElement rootElement = JsonParser.parseReader(Objects.requireNonNull(reader, "reader"));
        if (!rootElement.isJsonObject()) {
            throw new JsonParseException("Theme JSON must be an object");
        }
        JsonObject root = rootElement.getAsJsonObject();
        Map<String, Integer> overrides = new LinkedHashMap<>();
        if (root.has("color") && root.get("color").isJsonObject()) {
            flattenColors("", root.getAsJsonObject("color"), overrides);
        }
        for (String key : new String[] {"colors", "tokens"}) {
            if (root.has(key) && root.get(key).isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject(key).entrySet()) {
                    if (e.getValue().isJsonPrimitive()) {
                        overrides.put(e.getKey(), parseColor(e.getKey(), e.getValue().getAsString()));
                    }
                }
            }
        }
        String id = root.has("id") && root.get("id").isJsonPrimitive() ? root.get("id").getAsString() : "custom";
        String name = root.has("name") && root.get("name").isJsonPrimitive() ? root.get("name").getAsString() : id;
        return DEFAULT.withOverrides(overrides).withIdentity(id, name);
    }

    private static void flattenColors(String prefix, JsonObject object, Map<String, Integer> out) {
        for (Map.Entry<String, JsonElement> e : object.entrySet()) {
            String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            JsonElement value = e.getValue();
            if (value.isJsonPrimitive()) {
                out.put(key, parseColor(key, value.getAsString()));
            } else if (value.isJsonArray()) {
                JsonArray array = value.getAsJsonArray();
                for (int i = 0; i < array.size(); i++) {
                    if (array.get(i).isJsonPrimitive()) {
                        out.put(key + "." + i, parseColor(key + "." + i, array.get(i).getAsString()));
                    }
                }
            } else if (value.isJsonObject()) {
                JsonObject child = value.getAsJsonObject();
                if (child.has("stops") && child.get("stops").isJsonArray()) {
                    JsonArray stops = child.getAsJsonArray("stops");
                    for (int i = 0; i < stops.size(); i++) {
                        out.put(key + "." + i, parseColor(key + "." + i, stops.get(i).getAsString()));
                    }
                } else {
                    flattenColors(key, child, out);
                }
            }
        }
    }

    private static int parseColor(String key, String hex) {
        try {
            return Colors.fromCssHex(hex);
        } catch (IllegalArgumentException e) {
            throw new JsonParseException("Invalid color for token '" + key + "': " + hex, e);
        }
    }

    // ---------------------------------------------------------------- derivation

    /** Applies token overrides (dotted names → ARGB). Unknown names are ignored. */
    public Theme withOverrides(Map<String, Integer> overrides) {
        Map<String, Integer> merged = new LinkedHashMap<>(tokens);
        for (Map.Entry<String, Integer> e : overrides.entrySet()) {
            if (merged.containsKey(e.getKey())) {
                merged.put(e.getKey(), e.getValue());
            }
        }
        return new Theme(id, name, merged, scale, reducedMotion, reducedTransparency, largeText, highContrast);
    }

    /** Renames the theme (used by cosmetic themes). */
    public Theme withIdentity(String newId, String newName) {
        return new Theme(Objects.requireNonNull(newId), Objects.requireNonNull(newName), tokens, scale, reducedMotion,
                reducedTransparency, largeText, highContrast);
    }

    /** UI scale factor; clamped to 0.5..3. */
    public Theme withScale(float newScale) {
        float clamped = Math.max(0.5f, Math.min(3f, newScale));
        return new Theme(id, name, tokens, clamped, reducedMotion, reducedTransparency, largeText, highContrast);
    }

    /** Toggles reduced motion (animations snap). */
    public Theme withReducedMotion(boolean on) {
        return new Theme(id, name, tokens, scale, on, reducedTransparency, largeText, highContrast);
    }

    /** Toggles reduced transparency (opaque panels, no blur). */
    public Theme withReducedTransparency(boolean on) {
        return new Theme(id, name, tokens, scale, reducedMotion, on, largeText, highContrast);
    }

    /** Toggles large text (adds 15% to the effective scale). */
    public Theme withLargeText(boolean on) {
        return new Theme(id, name, tokens, scale, reducedMotion, reducedTransparency, on, highContrast);
    }

    /** Toggles high contrast: brighter borders and text, opaque panels. */
    public Theme withHighContrast(boolean on) {
        if (!on) {
            return new Theme(id, name, tokens, scale, reducedMotion, reducedTransparency, largeText, false);
        }
        Map<String, Integer> merged = new LinkedHashMap<>(tokens);
        merged.put("border.subtle", 0xFF4A4A5C);
        merged.put("border.strong", 0xFF6E6E86);
        merged.put("border.focus", 0xFFFFFFFF);
        merged.put("text.primary", 0xFFFFFFFF);
        merged.put("text.secondary", 0xFFD4D4DC);
        merged.put("text.muted", 0xFFA6A6B4);
        merged.put("surface.1", 0xFF0E0E14);
        merged.put("surface.2", 0xFF15151D);
        merged.put("surface.3", 0xFF1F1F2A);
        merged.put("surface.4", 0xFF2A2A38);
        return new Theme(id, name, merged, scale, reducedMotion, reducedTransparency, largeText, true);
    }

    // ---------------------------------------------------------------- identity & flags

    /** Theme id (e.g. {@code vanta}). */
    public String id() {
        return id;
    }

    /** Display name. */
    public String name() {
        return name;
    }

    /** UI scale factor requested by the user. */
    public float scale() {
        return scale;
    }

    /** Scale including the large-text bonus; the factor the screen actually renders with. */
    public float effectiveScale() {
        return largeText ? scale * 1.15f : scale;
    }

    /** Whether animations should snap. */
    public boolean reducedMotion() {
        return reducedMotion;
    }

    /** Whether translucency and blur are disabled. */
    public boolean reducedTransparency() {
        return reducedTransparency;
    }

    /** Whether large text is requested. */
    public boolean largeText() {
        return largeText;
    }

    /** Whether the high-contrast palette is active. */
    public boolean isHighContrast() {
        return highContrast;
    }

    /** Alpha for panels drawn over the game world. */
    public float panelAlpha() {
        return reducedTransparency || highContrast ? 1f : PANEL_ALPHA;
    }

    /** Whether the host should blur the world behind screens. */
    public boolean wantsBlur() {
        return !reducedTransparency;
    }

    /** Copy of all color tokens. */
    public Map<String, Integer> tokens() {
        return new LinkedHashMap<>(tokens);
    }

    /** A token by name. */
    public int token(String tokenName) {
        Integer v = tokens.get(tokenName);
        if (v == null) {
            throw new IllegalArgumentException("Unknown theme token: " + tokenName);
        }
        return v;
    }

    // ---------------------------------------------------------------- color accessors

    public int bgVoid() {
        return tokens.get("bg.void");
    }

    public int bgBase() {
        return tokens.get("bg.base");
    }

    public int surface1() {
        return tokens.get("surface.1");
    }

    public int surface2() {
        return tokens.get("surface.2");
    }

    public int surface3() {
        return tokens.get("surface.3");
    }

    public int surface4() {
        return tokens.get("surface.4");
    }

    public int borderSubtle() {
        return tokens.get("border.subtle");
    }

    public int borderStrong() {
        return tokens.get("border.strong");
    }

    public int borderFocus() {
        return tokens.get("border.focus");
    }

    public int textPrimary() {
        return tokens.get("text.primary");
    }

    public int textSecondary() {
        return tokens.get("text.secondary");
    }

    public int textMuted() {
        return tokens.get("text.muted");
    }

    public int textInverse() {
        return tokens.get("text.inverse");
    }

    public int accent() {
        return tokens.get("accent.violet");
    }

    public int accentHover() {
        return tokens.get("accent.violetHover");
    }

    public int accentPressed() {
        return tokens.get("accent.violetPressed");
    }

    public int accentSoft() {
        return tokens.get("accent.violetSoft");
    }

    public int accentBlue() {
        return tokens.get("accent.blue");
    }

    public int accentBlueSoft() {
        return tokens.get("accent.blueSoft");
    }

    public int success() {
        return tokens.get("state.success");
    }

    public int warning() {
        return tokens.get("state.warning");
    }

    public int danger() {
        return tokens.get("state.danger");
    }

    public int info() {
        return tokens.get("state.info");
    }

    /** First stop of the accent gradient (violet). */
    public int gradientStart() {
        return tokens.get("gradient.accent.0");
    }

    /** Second stop of the accent gradient (blue). */
    public int gradientEnd() {
        return tokens.get("gradient.accent.1");
    }

    // ---------------------------------------------------------------- derived colors

    /** Panel background: surface.1 with the panel alpha. */
    public int panelBackground() {
        return Colors.withAlpha(surface1(), panelAlpha());
    }

    /** Dim layer drawn over the world behind a screen. */
    public int screenDim() {
        return Colors.withAlpha(bgVoid(), reducedTransparency ? 1f : 0.72f);
    }

    /** Dim layer drawn behind modal dialogs. */
    public int modalDim() {
        return Colors.withAlpha(bgVoid(), 0.6f);
    }

    /** Scrollbar thumb color. */
    public int scrollThumb() {
        return Colors.withAlpha(textMuted(), 0.75f);
    }

    /** Scrollbar thumb while hovered or dragged. */
    public int scrollThumbActive() {
        return textSecondary();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Theme other)) {
            return false;
        }
        return id.equals(other.id) && tokens.equals(other.tokens) && scale == other.scale
                && reducedMotion == other.reducedMotion && reducedTransparency == other.reducedTransparency
                && largeText == other.largeText && highContrast == other.highContrast;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tokens, scale, reducedMotion, reducedTransparency, largeText, highContrast);
    }

    @Override
    public String toString() {
        return "Theme[" + id + ", scale=" + scale + "]";
    }
}
