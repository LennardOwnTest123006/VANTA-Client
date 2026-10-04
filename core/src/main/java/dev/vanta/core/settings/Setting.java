package dev.vanta.core.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.vanta.core.bridge.KeyRef;
import dev.vanta.core.bridge.VanillaOption;
import dev.vanta.core.i18n.LangKeyed;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Immutable description of one setting: identity, category, editor kind, default, validation and search metadata.
 * <p>
 * Values live in {@link SettingsStore}; a {@code Setting} is only the schema. Settings bound to a
 * {@link VanillaOption} read and write Minecraft's own options through {@code OptionsBridge} instead of
 * {@code settings.json}, so the VIDEO / AUDIO / CONTROLS categories edit the real game options.
 * <p>
 * Translation keys are derived from the id: {@code vanta.setting.<id>.title} and
 * {@code vanta.setting.<id>.description}.
 *
 * @param <T> value type ({@link Boolean}, {@link Integer}, {@link Double}, {@link String} or an enum)
 */
public final class Setting<T> {
    /** Longest string a {@link SettingKind#STRING} setting accepts. */
    public static final int MAX_STRING_LENGTH = 256;

    private final String id;
    private final SettingCategory category;
    private final SettingKind kind;
    private final Class<T> type;
    private final T defaultValue;
    private final UnaryOperator<T> normalizer;
    private final List<String> keywords;
    private final Optional<VanillaOption> vanillaBinding;
    private final double min;
    private final double max;
    private final double step;
    private final List<T> options;
    private final boolean persisted;
    private final boolean requiresRestart;
    private final String actionId;

    private Setting(String id, SettingCategory category, SettingKind kind, Class<T> type, T defaultValue,
                    UnaryOperator<T> normalizer, List<String> keywords, Optional<VanillaOption> vanillaBinding,
                    double min, double max, double step, List<T> options, boolean persisted, boolean requiresRestart,
                    String actionId) {
        this.id = validateId(id);
        this.category = Objects.requireNonNull(category, "category");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.type = Objects.requireNonNull(type, "type");
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer");
        this.keywords = Collections.unmodifiableList(new ArrayList<>(keywords));
        this.vanillaBinding = Objects.requireNonNull(vanillaBinding, "vanillaBinding");
        this.min = min;
        this.max = max;
        this.step = step;
        this.options = Collections.unmodifiableList(new ArrayList<>(options));
        this.persisted = persisted;
        this.requiresRestart = requiresRestart;
        this.actionId = actionId;
    }

    private static String validateId(String id) {
        Objects.requireNonNull(id, "id");
        if (!id.matches("[a-z][a-zA-Z0-9]*(\\.[a-z][a-zA-Z0-9]*)+")) {
            throw new IllegalArgumentException("Setting id must look like 'group.name': " + id);
        }
        return id;
    }

    // ---- factories ---------------------------------------------------------------------------------------------

    /** Boolean toggle. */
    public static Setting<Boolean> bool(String id, SettingCategory category, boolean defaultValue) {
        return new Setting<>(id, category, SettingKind.BOOL, Boolean.class, defaultValue, v -> v, List.of(),
                Optional.empty(), 0, 0, 0, List.of(), true, false, null);
    }

    /** Integer slider; values are clamped to {@code [min, max]}. */
    public static Setting<Integer> intRange(String id, SettingCategory category, int defaultValue, int min, int max,
                                            int step) {
        if (min > max || step <= 0) {
            throw new IllegalArgumentException("Invalid range for " + id);
        }
        return new Setting<>(id, category, SettingKind.INT_RANGE, Integer.class, defaultValue,
                v -> Math.max(min, Math.min(max, v)), List.of(), Optional.empty(), min, max, step, List.of(), true,
                false, null);
    }

    /** Double slider; values are clamped to {@code [min, max]}. */
    public static Setting<Double> doubleRange(String id, SettingCategory category, double defaultValue, double min,
                                              double max, double step) {
        if (min > max || step <= 0) {
            throw new IllegalArgumentException("Invalid range for " + id);
        }
        return new Setting<>(id, category, SettingKind.DOUBLE_RANGE, Double.class, defaultValue,
                v -> Math.max(min, Math.min(max, v)), List.of(), Optional.empty(), min, max, step, List.of(), true,
                false, null);
    }

    /** Dropdown over a Java enum. */
    public static <E extends Enum<E>> Setting<E> enumOf(String id, SettingCategory category, Class<E> type,
                                                        E defaultValue) {
        return new Setting<>(id, category, SettingKind.ENUM, type, defaultValue, v -> v, List.of(), Optional.empty(),
                0, 0, 0, Arrays.asList(type.getEnumConstants()), true, false, null);
    }

    /** ARGB colour. */
    public static Setting<Integer> color(String id, SettingCategory category, int defaultArgb) {
        return new Setting<>(id, category, SettingKind.COLOR, Integer.class, defaultArgb, v -> v, List.of(),
                Optional.empty(), 0, 0, 0, List.of(), true, false, null);
    }

    /** Free text, trimmed and truncated to {@link #MAX_STRING_LENGTH}. */
    public static Setting<String> string(String id, SettingCategory category, String defaultValue) {
        return new Setting<>(id, category, SettingKind.STRING, String.class, defaultValue,
                v -> v.length() > MAX_STRING_LENGTH ? v.substring(0, MAX_STRING_LENGTH) : v, List.of(),
                Optional.empty(), 0, 0, 0, List.of(), true, false, null);
    }

    /** Key capture; the value is a vanilla key name ({@code key.keyboard.c}). */
    public static Setting<String> key(String id, SettingCategory category, String defaultKeyName) {
        return new Setting<>(id, category, SettingKind.KEY, String.class, defaultKeyName,
                v -> v.isBlank() ? KeyRef.UNKNOWN_NAME : v.trim(), List.of(), Optional.empty(), 0, 0, 0, List.of(),
                true, false, null);
    }

    /** Button row. {@code actionId} is matched against {@code ActionEntry} ids by the UI. Never persisted. */
    public static Setting<String> action(String id, SettingCategory category, String actionId) {
        Objects.requireNonNull(actionId, "actionId");
        return new Setting<>(id, category, SettingKind.ACTION, String.class, actionId, v -> v, List.of(),
                Optional.empty(), 0, 0, 0, List.of(), false, false, actionId);
    }

    /**
     * Setting bound to a vanilla option. The Java type follows {@link VanillaOption#kind()}:
     * INT → Integer slider, DOUBLE → Double slider, BOOL → toggle, ENUM → String dropdown over the vanilla names.
     */
    public static Setting<?> vanilla(String id, SettingCategory category, VanillaOption option) {
        Object def = option.vanillaDefault();
        return switch (option.kind()) {
            case INT -> new Setting<>(id, category, SettingKind.INT_RANGE, Integer.class, (Integer) def,
                    v -> (int) Math.round(Math.max(option.min(), Math.min(option.max(), v))), List.of(),
                    Optional.of(option), option.min(), option.max(), option.step(), List.of(), false, false, null);
            case DOUBLE -> new Setting<>(id, category, SettingKind.DOUBLE_RANGE, Double.class, (Double) def,
                    v -> Math.max(option.min(), Math.min(option.max(), v)), List.of(), Optional.of(option),
                    option.min(), option.max(), option.step(), List.of(), false, false, null);
            case BOOL -> new Setting<>(id, category, SettingKind.BOOL, Boolean.class, (Boolean) def, v -> v,
                    List.of(), Optional.of(option), 0, 0, 0, List.of(), false, false, null);
            case ENUM -> new Setting<>(id, category, SettingKind.ENUM, String.class, (String) def,
                    v -> option.enumValues().contains(v.toUpperCase(Locale.ROOT)) ? v.toUpperCase(Locale.ROOT)
                            : (String) def,
                    List.of(), Optional.of(option), 0, 0, 0, option.enumValues(), false, false, null);
        };
    }

    // ---- withers -----------------------------------------------------------------------------------------------

    /** Copy with additional search keywords (English words; titles and descriptions are indexed automatically). */
    public Setting<T> keywords(String... words) {
        List<String> merged = new ArrayList<>(keywords);
        merged.addAll(Arrays.asList(words));
        return new Setting<>(id, category, kind, type, defaultValue, normalizer, merged, vanillaBinding, min, max,
                step, options, persisted, requiresRestart, actionId);
    }

    /** Copy flagged as requiring a game restart. */
    public Setting<T> requiresRestart() {
        return new Setting<>(id, category, kind, type, defaultValue, normalizer, keywords, vanillaBinding, min, max,
                step, options, persisted, true, actionId);
    }

    /** Copy with a custom normaliser applied after the built-in one (validation / clamping). */
    public Setting<T> validator(UnaryOperator<T> extra) {
        Objects.requireNonNull(extra, "extra");
        UnaryOperator<T> combined = v -> extra.apply(normalizer.apply(v));
        return new Setting<>(id, category, kind, type, defaultValue, combined, keywords, vanillaBinding, min, max,
                step, options, persisted, requiresRestart, actionId);
    }

    // ---- accessors ---------------------------------------------------------------------------------------------

    /** Stable id such as {@code hud.globalScale}. */
    public String id() {
        return id;
    }

    /** Category. */
    public SettingCategory category() {
        return category;
    }

    /** Editor kind. */
    public SettingKind kind() {
        return kind;
    }

    /** Java value type. */
    public Class<T> type() {
        return type;
    }

    /** Default value. */
    public T defaultValue() {
        return defaultValue;
    }

    /** Extra search keywords. */
    public List<String> keywords() {
        return keywords;
    }

    /** Vanilla option this setting proxies, if any. */
    public Optional<VanillaOption> vanillaBinding() {
        return vanillaBinding;
    }

    /** True for vanilla-bound settings. */
    public boolean isVanilla() {
        return vanillaBinding.isPresent();
    }

    /** Lower bound (ranges only). */
    public double min() {
        return min;
    }

    /** Upper bound (ranges only). */
    public double max() {
        return max;
    }

    /** Slider step (ranges only). */
    public double step() {
        return step;
    }

    /** Allowed values (ENUM only). */
    public List<T> options() {
        return options;
    }

    /** False for actions and vanilla-bound settings, which are not written to {@code settings.json}. */
    public boolean isPersisted() {
        return persisted;
    }

    /** True when changing the value needs a restart to take effect. */
    public boolean needsRestart() {
        return requiresRestart;
    }

    /** Action id (ACTION only). */
    public Optional<String> actionId() {
        return Optional.ofNullable(actionId);
    }

    /** Translation key of the title. */
    public String titleKey() {
        return "vanta.setting." + id + ".title";
    }

    /** Translation key of the description / tooltip. */
    public String descriptionKey() {
        return "vanta.setting." + id + ".description";
    }

    /**
     * Translation key of one option label: {@link LangKeyed} enums supply their own key, vanilla enum values use
     * {@link VanillaOption#enumValueLangKey(String)}, everything else {@code vanta.setting.<id>.option.<value>}.
     */
    public String optionLangKey(T option) {
        if (option instanceof LangKeyed keyed) {
            return keyed.langKey();
        }
        if (vanillaBinding.isPresent() && option instanceof String s) {
            return vanillaBinding.get().enumValueLangKey(s);
        }
        return "vanta.setting." + id + ".option." + String.valueOf(option).toLowerCase(Locale.ROOT);
    }

    // ---- values ------------------------------------------------------------------------------------------------

    /** Applies clamping / validation. Never returns null. */
    public T normalize(T value) {
        if (value == null) {
            return defaultValue;
        }
        T normalized = normalizer.apply(value);
        return normalized == null ? defaultValue : normalized;
    }

    /** Converts a raw object (from JSON or the options bridge) to the value type, empty when incompatible. */
    @SuppressWarnings("unchecked")
    public Optional<T> coerce(Object raw) {
        if (raw == null) {
            return Optional.empty();
        }
        if (type.isInstance(raw)) {
            return Optional.of(normalize((T) raw));
        }
        if (type == Integer.class && raw instanceof Number n) {
            return Optional.of(normalize((T) Integer.valueOf(n.intValue())));
        }
        if (type == Double.class && raw instanceof Number n) {
            return Optional.of(normalize((T) Double.valueOf(n.doubleValue())));
        }
        if (type == Integer.class && kind == SettingKind.COLOR && raw instanceof String s) {
            return parseColor(s).map(c -> normalize((T) c));
        }
        if (type.isEnum() && raw instanceof String s) {
            for (T option : options) {
                if (((Enum<?>) option).name().equalsIgnoreCase(s.trim())) {
                    return Optional.of(option);
                }
            }
            return Optional.empty();
        }
        if (type == String.class && raw instanceof Enum<?> e) {
            return Optional.of(normalize((T) e.name()));
        }
        return Optional.empty();
    }

    /** Encodes a value for JSON. */
    public JsonElement encode(T value) {
        T v = normalize(value);
        if (v instanceof Boolean b) {
            return new JsonPrimitive(b);
        }
        if (kind == SettingKind.COLOR && v instanceof Integer c) {
            return new JsonPrimitive(formatColor(c));
        }
        if (v instanceof Number n) {
            return new JsonPrimitive(n);
        }
        if (v instanceof Enum<?> e) {
            return new JsonPrimitive(e.name().toLowerCase(Locale.ROOT));
        }
        return new JsonPrimitive(String.valueOf(v));
    }

    /** Decodes a JSON value; empty when the element has the wrong shape. */
    public Optional<T> decode(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return Optional.empty();
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return coerce(primitive.getAsBoolean());
        }
        if (primitive.isNumber()) {
            return coerce(primitive.getAsNumber());
        }
        if (primitive.isString()) {
            String s = primitive.getAsString();
            if (type == Integer.class && kind != SettingKind.COLOR) {
                try {
                    return coerce(Integer.parseInt(s.trim()));
                } catch (NumberFormatException e) {
                    return Optional.empty();
                }
            }
            if (type == Double.class) {
                try {
                    return coerce(Double.parseDouble(s.trim()));
                } catch (NumberFormatException e) {
                    return Optional.empty();
                }
            }
            if (type == Boolean.class) {
                if ("true".equalsIgnoreCase(s.trim())) {
                    return coerce(Boolean.TRUE);
                }
                if ("false".equalsIgnoreCase(s.trim())) {
                    return coerce(Boolean.FALSE);
                }
                return Optional.empty();
            }
            return coerce(s);
        }
        return Optional.empty();
    }

    /** Value as exchanged with {@code OptionsBridge} (Integer / Double / Boolean / upper-case String). */
    public Object toBridgeValue(T value) {
        T v = normalize(value);
        if (v instanceof Enum<?> e) {
            return e.name();
        }
        return v;
    }

    /** Parses {@code #RRGGBB} (opaque) or {@code #AARRGGBB}. */
    public static Optional<Integer> parseColor(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String hex = text.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        } else if (hex.startsWith("0x") || hex.startsWith("0X")) {
            hex = hex.substring(2);
        }
        try {
            if (hex.length() == 6) {
                return Optional.of(0xFF000000 | Integer.parseInt(hex, 16));
            }
            if (hex.length() == 8) {
                return Optional.of((int) Long.parseLong(hex, 16));
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        return Optional.empty();
    }

    /** Formats as {@code #AARRGGBB}. */
    public static String formatColor(int argb) {
        return String.format(Locale.ROOT, "#%08X", argb);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Setting<?> other && other.id.equals(id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Setting[" + id + "]";
    }
}
