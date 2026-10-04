package dev.vanta.core.bridge;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * In-memory {@link OptionsBridge} initialised with vanilla default values.
 */
public final class FakeOptionsBridge implements OptionsBridge {
    private final Map<VanillaOption, Object> values = new EnumMap<>(VanillaOption.class);
    private final Set<VanillaOption> unsupported = EnumSet.noneOf(VanillaOption.class);
    /** Number of {@link #save()} calls. */
    public int saveCount;
    /** Every option written through {@link #set}, in order. */
    public final java.util.List<VanillaOption> setOrder = new java.util.ArrayList<>();
    /**
     * When true, emulates Minecraft 1.21.11 where the graphics preset is a bundle: setting it also rewrites the
     * render and simulation distance (to 16 / 12 here) and afterwards reads back as the unknown value "CUSTOM" as
     * soon as any bundled option differs.
     */
    public boolean graphicsPresetBundle;

    public FakeOptionsBridge() {
        values.put(VanillaOption.FRAMERATE_LIMIT, 120);
        values.put(VanillaOption.RENDER_DISTANCE, 12);
        values.put(VanillaOption.SIMULATION_DISTANCE, 12);
        values.put(VanillaOption.PARTICLES, "ALL");
        values.put(VanillaOption.CLOUDS, "FANCY");
        values.put(VanillaOption.SMOOTH_LIGHTING, true);
        values.put(VanillaOption.ENTITY_SHADOWS, true);
        values.put(VanillaOption.ENTITY_DISTANCE_SCALING, 1.0);
        values.put(VanillaOption.VSYNC, true);
        values.put(VanillaOption.GUI_SCALE, 0);
        values.put(VanillaOption.BIOME_BLEND, 2);
        values.put(VanillaOption.MIPMAP_LEVELS, 4);
        values.put(VanillaOption.VIEW_BOBBING, true);
        values.put(VanillaOption.FOV, 70);
        values.put(VanillaOption.MENU_BLUR, 5);
        values.put(VanillaOption.GRAPHICS_MODE, "FANCY");
        values.put(VanillaOption.INACTIVITY_FPS_LIMIT, "AFK");
        values.put(VanillaOption.SCREEN_EFFECT_SCALE, 1.0);
        values.put(VanillaOption.FOV_EFFECT_SCALE, 1.0);
        values.put(VanillaOption.GLINT_SPEED, 0.5);
        values.put(VanillaOption.GLINT_STRENGTH, 0.75);
        values.put(VanillaOption.AUTOSAVE_INDICATOR, true);
        values.put(VanillaOption.PANORAMA_SPEED, 1.0);
        values.put(VanillaOption.DARK_LOADING_SCREEN, false);
        for (VanillaOption option : VanillaOption.values()) {
            if (option.group() == VanillaOption.Group.AUDIO && option.kind() == VanillaOption.Kind.DOUBLE) {
                values.put(option, 1.0);
            }
        }
        values.put(VanillaOption.SHOW_SUBTITLES, false);
        values.put(VanillaOption.MOUSE_SENSITIVITY, 0.5);
        values.put(VanillaOption.INVERT_MOUSE, false);
        values.put(VanillaOption.MOUSE_WHEEL_SENSITIVITY, 1.0);
        values.put(VanillaOption.DISCRETE_SCROLL, false);
        values.put(VanillaOption.RAW_MOUSE_INPUT, true);
        values.put(VanillaOption.AUTO_JUMP, false);
        values.put(VanillaOption.TOGGLE_SNEAK, false);
        values.put(VanillaOption.TOGGLE_SPRINT, false);
        values.put(VanillaOption.HIGH_CONTRAST, false);
        values.put(VanillaOption.REDUCED_DEBUG_INFO, false);
        values.put(VanillaOption.TEXT_BACKGROUND_OPACITY, 0.5);
        values.put(VanillaOption.CHAT_OPACITY, 1.0);
    }

    /** Marks an option as missing from the running game. */
    public FakeOptionsBridge withoutSupportFor(VanillaOption option) {
        unsupported.add(option);
        return this;
    }

    @Override
    public boolean supports(VanillaOption option) {
        return !unsupported.contains(option);
    }

    @Override
    public Optional<Object> get(VanillaOption option) {
        if (!supports(option)) {
            return Optional.empty();
        }
        Object raw = values.get(option);
        // Like the real bridge: a value the option does not know (e.g. the game's "CUSTOM" graphics preset) reads
        // back as empty rather than leaking through.
        return raw == null ? Optional.empty() : option.normalize(raw);
    }

    @Override
    public boolean set(VanillaOption option, Object value) {
        if (!supports(option)) {
            return false;
        }
        Optional<Object> normalized = option.normalize(value);
        if (normalized.isEmpty()) {
            return false;
        }
        setOrder.add(option);
        if (graphicsPresetBundle && option == VanillaOption.GRAPHICS_MODE) {
            values.put(VanillaOption.RENDER_DISTANCE, 16);
            values.put(VanillaOption.SIMULATION_DISTANCE, 12);
        } else if (graphicsPresetBundle && (option == VanillaOption.RENDER_DISTANCE
                || option == VanillaOption.SIMULATION_DISTANCE)) {
            values.put(VanillaOption.GRAPHICS_MODE, "CUSTOM");
        }
        values.put(option, normalized.get());
        return true;
    }

    @Override
    public void save() {
        saveCount++;
    }

    /** Raw view for assertions. */
    public Map<VanillaOption, Object> values() {
        return values;
    }
}
