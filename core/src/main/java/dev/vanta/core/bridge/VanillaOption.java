package dev.vanta.core.bridge;

import dev.vanta.core.i18n.LangKeyed;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Vanilla Minecraft options VANTA can read and write through {@link OptionsBridge}.
 * <p>
 * Each constant documents its value {@link Kind}, its range (for numbers) or allowed values (for enums, as the
 * upper-case constant names of the vanilla enum) and the translation key of its display name. The Fabric client maps
 * every constant to the matching {@code OptionInstance} on {@code net.minecraft.client.Options}; options the running
 * game does not have are reported through {@link OptionsBridge#supports(VanillaOption)}.
 */
public enum VanillaOption implements LangKeyed {
    // ---- video ------------------------------------------------------------------------------------------------
    /** {@code framerateLimit}: 10–260, where 260 means unlimited. */
    FRAMERATE_LIMIT(Group.VIDEO, Kind.INT, 10, 260, 10),
    /** {@code renderDistance} in chunks (2–32). */
    RENDER_DISTANCE(Group.VIDEO, Kind.INT, 2, 32, 1),
    /** {@code simulationDistance} in chunks (5–32). */
    SIMULATION_DISTANCE(Group.VIDEO, Kind.INT, 5, 32, 1),
    /** {@code particles}: ALL, DECREASED, MINIMAL. */
    PARTICLES(Group.VIDEO, List.of("ALL", "DECREASED", "MINIMAL")),
    /** {@code cloudStatus}: OFF, FAST, FANCY. */
    CLOUDS(Group.VIDEO, List.of("OFF", "FAST", "FANCY")),
    /** {@code ambientOcclusion} (smooth lighting). */
    SMOOTH_LIGHTING(Group.VIDEO, Kind.BOOL),
    /** {@code entityShadows}. */
    ENTITY_SHADOWS(Group.VIDEO, Kind.BOOL),
    /** {@code entityDistanceScaling}: 0.5–5.0 (50 %–500 %). */
    ENTITY_DISTANCE_SCALING(Group.VIDEO, Kind.DOUBLE, 0.5, 5.0, 0.25),
    /** {@code enableVsync}. */
    VSYNC(Group.VIDEO, Kind.BOOL),
    /** {@code guiScale}: 0 (auto) – 6. */
    GUI_SCALE(Group.VIDEO, Kind.INT, 0, 6, 1),
    /** {@code biomeBlendRadius}: 0–7. */
    BIOME_BLEND(Group.VIDEO, Kind.INT, 0, 7, 1),
    /** {@code mipmapLevels}: 0–4. */
    MIPMAP_LEVELS(Group.VIDEO, Kind.INT, 0, 4, 1),
    /** {@code bobView}. */
    VIEW_BOBBING(Group.VIDEO, Kind.BOOL),
    /** {@code fov}: 30–110. */
    FOV(Group.VIDEO, Kind.INT, 30, 110, 1),
    /** {@code menuBackgroundBlurriness}: 0–10. */
    MENU_BLUR(Group.VIDEO, Kind.INT, 0, 10, 1),
    /** {@code graphicsMode}: FAST, FANCY, FABULOUS. */
    GRAPHICS_MODE(Group.VIDEO, List.of("FAST", "FANCY", "FABULOUS")),
    /** {@code inactivityFpsLimit}: MINIMIZED, AFK. */
    INACTIVITY_FPS_LIMIT(Group.VIDEO, List.of("MINIMIZED", "AFK")),
    /** {@code screenEffectScale}: 0–1. */
    SCREEN_EFFECT_SCALE(Group.VIDEO, Kind.DOUBLE, 0.0, 1.0, 0.05),
    /** {@code fovEffectScale}: 0–1. */
    FOV_EFFECT_SCALE(Group.VIDEO, Kind.DOUBLE, 0.0, 1.0, 0.05),
    /** {@code glintSpeed}: 0–1. */
    GLINT_SPEED(Group.VIDEO, Kind.DOUBLE, 0.0, 1.0, 0.05),
    /** {@code glintStrength}: 0–1. */
    GLINT_STRENGTH(Group.VIDEO, Kind.DOUBLE, 0.0, 1.0, 0.05),
    /** {@code showAutosaveIndicator}. */
    AUTOSAVE_INDICATOR(Group.VIDEO, Kind.BOOL),
    /** {@code panoramaSpeed}: 0–2 (1 = vanilla). */
    PANORAMA_SPEED(Group.VIDEO, Kind.DOUBLE, 0.0, 2.0, 0.1),
    /** {@code darkMojangStudiosBackground}. */
    DARK_LOADING_SCREEN(Group.VIDEO, Kind.BOOL),

    // ---- audio ------------------------------------------------------------------------------------------------
    MASTER_VOLUME(Group.AUDIO, Kind.DOUBLE, 0.0, 1.0, 0.01),
    MUSIC_VOLUME(Group.AUDIO, Kind.DOUBLE, 0.0, 1.0, 0.01),
    RECORDS_VOLUME(Group.AUDIO, Kind.DOUBLE, 0.0, 1.0, 0.01),
    WEATHER_VOLUME(Group.AUDIO, Kind.DOUBLE, 0.0, 1.0, 0.01),
    BLOCKS_VOLUME(Group.AUDIO, Kind.DOUBLE, 0.0, 1.0, 0.01),
    HOSTILE_VOLUME(Group.AUDIO, Kind.DOUBLE, 0.0, 1.0, 0.01),
    NEUTRAL_VOLUME(Group.AUDIO, Kind.DOUBLE, 0.0, 1.0, 0.01),
    PLAYERS_VOLUME(Group.AUDIO, Kind.DOUBLE, 0.0, 1.0, 0.01),
    AMBIENT_VOLUME(Group.AUDIO, Kind.DOUBLE, 0.0, 1.0, 0.01),
    VOICE_VOLUME(Group.AUDIO, Kind.DOUBLE, 0.0, 1.0, 0.01),
    /** {@code showSubtitles}. */
    SHOW_SUBTITLES(Group.AUDIO, Kind.BOOL),

    // ---- controls ---------------------------------------------------------------------------------------------
    /** {@code sensitivity}: 0–1 (0.5 = 100 %). */
    MOUSE_SENSITIVITY(Group.CONTROLS, Kind.DOUBLE, 0.0, 1.0, 0.01),
    /** {@code invertYMouse}. */
    INVERT_MOUSE(Group.CONTROLS, Kind.BOOL),
    /** {@code mouseWheelSensitivity}: 0.01–10. */
    MOUSE_WHEEL_SENSITIVITY(Group.CONTROLS, Kind.DOUBLE, 0.01, 10.0, 0.01),
    /** {@code discreteMouseScroll}. */
    DISCRETE_SCROLL(Group.CONTROLS, Kind.BOOL),
    /** {@code rawMouseInput}. */
    RAW_MOUSE_INPUT(Group.CONTROLS, Kind.BOOL),
    /** {@code autoJump}. */
    AUTO_JUMP(Group.CONTROLS, Kind.BOOL),
    /** {@code toggleCrouch}. */
    TOGGLE_SNEAK(Group.CONTROLS, Kind.BOOL),
    /** {@code toggleSprint}. */
    TOGGLE_SPRINT(Group.CONTROLS, Kind.BOOL),

    // ---- accessibility ----------------------------------------------------------------------------------------
    /** {@code highContrast}. */
    HIGH_CONTRAST(Group.ACCESSIBILITY, Kind.BOOL),
    /** {@code reducedDebugInfo}. */
    REDUCED_DEBUG_INFO(Group.ACCESSIBILITY, Kind.BOOL),
    /** {@code textBackgroundOpacity}: 0–1. */
    TEXT_BACKGROUND_OPACITY(Group.ACCESSIBILITY, Kind.DOUBLE, 0.0, 1.0, 0.05),
    /** {@code chatOpacity}: 0–1. */
    CHAT_OPACITY(Group.ACCESSIBILITY, Kind.DOUBLE, 0.0, 1.0, 0.05);

    /** Value type of an option. */
    public enum Kind {
        INT, DOUBLE, BOOL, ENUM
    }

    /** Vanilla settings screen the option belongs to. */
    public enum Group {
        VIDEO, AUDIO, CONTROLS, ACCESSIBILITY
    }

    private final Group group;
    private final Kind kind;
    private final double min;
    private final double max;
    private final double step;
    private final List<String> enumValues;

    VanillaOption(Group group, Kind kind) {
        this(group, kind, 0, 0, 0, List.of());
    }

    VanillaOption(Group group, Kind kind, double min, double max, double step) {
        this(group, kind, min, max, step, List.of());
    }

    VanillaOption(Group group, List<String> enumValues) {
        this(group, Kind.ENUM, 0, 0, 0, enumValues);
    }

    VanillaOption(Group group, Kind kind, double min, double max, double step, List<String> enumValues) {
        this.group = group;
        this.kind = kind;
        this.min = min;
        this.max = max;
        this.step = step;
        this.enumValues = enumValues;
    }

    /** Vanilla settings group. */
    public Group group() {
        return group;
    }

    /** Value type. */
    public Kind kind() {
        return kind;
    }

    /** Lower bound (numbers only). */
    public double min() {
        return min;
    }

    /** Upper bound (numbers only). */
    public double max() {
        return max;
    }

    /** Suggested slider step (numbers only). */
    public double step() {
        return step;
    }

    /** Allowed values for {@link Kind#ENUM} options (upper-case vanilla constant names). */
    public List<String> enumValues() {
        return enumValues;
    }

    /** Stable lower-case identifier used in JSON and lang keys, e.g. {@code render_distance}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String langKey() {
        return "vanta.option." + id();
    }

    /** Translation key of one enum value label. */
    public String enumValueLangKey(String value) {
        return "vanta.option." + id() + "." + value.toLowerCase(Locale.ROOT);
    }

    /**
     * Validates and normalises a value for this option: ints/doubles are clamped to the range, enum values are
     * upper-cased and checked, booleans pass through. Returns empty when the value has the wrong type.
     */
    public Optional<Object> normalize(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        switch (kind) {
            case INT -> {
                if (value instanceof Number n) {
                    long clamped = Math.round(Math.max(min, Math.min(max, n.doubleValue())));
                    return Optional.of((int) clamped);
                }
                return Optional.empty();
            }
            case DOUBLE -> {
                if (value instanceof Number n) {
                    return Optional.of(Math.max(min, Math.min(max, n.doubleValue())));
                }
                return Optional.empty();
            }
            case BOOL -> {
                if (value instanceof Boolean b) {
                    return Optional.of(b);
                }
                return Optional.empty();
            }
            case ENUM -> {
                if (value instanceof String s) {
                    String upper = s.toUpperCase(Locale.ROOT);
                    return enumValues.contains(upper) ? Optional.of(upper) : Optional.empty();
                }
                if (value instanceof Enum<?> e) {
                    return normalize(e.name());
                }
                return Optional.empty();
            }
            default -> {
                return Optional.empty();
            }
        }
    }

    /**
     * The value vanilla Minecraft 1.21.11 uses on a fresh installation. Used when no {@link OptionsBridge} is
     * available (unit tests, screen previews) and as the "reset" target of vanilla-bound settings.
     */
    public Object vanillaDefault() {
        return switch (this) {
            case FRAMERATE_LIMIT -> 120;
            case RENDER_DISTANCE -> 12;
            case SIMULATION_DISTANCE -> 12;
            case PARTICLES -> "ALL";
            case CLOUDS -> "FANCY";
            case SMOOTH_LIGHTING, ENTITY_SHADOWS, VSYNC, VIEW_BOBBING, AUTOSAVE_INDICATOR, RAW_MOUSE_INPUT -> true;
            case ENTITY_DISTANCE_SCALING, PANORAMA_SPEED, SCREEN_EFFECT_SCALE, FOV_EFFECT_SCALE, CHAT_OPACITY,
                 MOUSE_WHEEL_SENSITIVITY -> 1.0;
            case GUI_SCALE -> 0;
            case BIOME_BLEND -> 2;
            case MIPMAP_LEVELS -> 4;
            case FOV -> 70;
            case MENU_BLUR -> 5;
            case GRAPHICS_MODE -> "FANCY";
            case INACTIVITY_FPS_LIMIT -> "AFK";
            case GLINT_SPEED, MOUSE_SENSITIVITY, TEXT_BACKGROUND_OPACITY -> 0.5;
            case GLINT_STRENGTH -> 0.75;
            case DARK_LOADING_SCREEN, SHOW_SUBTITLES, INVERT_MOUSE, DISCRETE_SCROLL, AUTO_JUMP, TOGGLE_SNEAK,
                 TOGGLE_SPRINT, HIGH_CONTRAST, REDUCED_DEBUG_INFO -> false;
            case MASTER_VOLUME, MUSIC_VOLUME, RECORDS_VOLUME, WEATHER_VOLUME, BLOCKS_VOLUME, HOSTILE_VOLUME,
                 NEUTRAL_VOLUME, PLAYERS_VOLUME, AMBIENT_VOLUME, VOICE_VOLUME -> 1.0;
        };
    }

    /** Finds an option by {@link #id()} (case-insensitive). */
    public static Optional<VanillaOption> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (VanillaOption option : values()) {
            if (option.id().equalsIgnoreCase(id)) {
                return Optional.of(option);
            }
        }
        return Optional.empty();
    }
}
