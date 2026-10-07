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
     * When true, emulates Minecraft 1.21.11 where the graphics preset is a bundle ({@code Options.applyGraphicsPreset}
     * / {@code GraphicsPreset.apply}): setting FAST / FANCY / FABULOUS also rewrites every bundled option VANTA knows
     * (render and simulation distance, clouds, particles, smooth lighting, …) with the game's own values, and changing
     * any bundled option to a different value outside a preset switches the preset to "CUSTOM", exactly like
     * {@code Options.setGraphicsPresetToCustom}. "CUSTOM" itself cannot be selected (the real bridge refuses it).
     */
    public boolean graphicsPresetBundle;
    /** Options whose writes the fake refuses (the game rejected the value), for the "refused write" cases. */
    public final Set<VanillaOption> refusing = EnumSet.noneOf(VanillaOption.class);

    /** Options inside Minecraft 1.21.11's graphics preset bundle. */
    public static final Set<VanillaOption> BUNDLED = EnumSet.of(VanillaOption.RENDER_DISTANCE,
            VanillaOption.SIMULATION_DISTANCE, VanillaOption.PARTICLES, VanillaOption.CLOUDS,
            VanillaOption.SMOOTH_LIGHTING, VanillaOption.ENTITY_SHADOWS, VanillaOption.ENTITY_DISTANCE_SCALING,
            VanillaOption.BIOME_BLEND, VanillaOption.MIPMAP_LEVELS, VanillaOption.MENU_BLUR);

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
        // Like the real bridge: a value the option does not know reads back as empty rather than leaking through.
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
        if (refusing.contains(option)
                || (option == VanillaOption.GRAPHICS_MODE && "CUSTOM".equals(normalized.get()))) {
            return false;
        }
        setOrder.add(option);
        if (graphicsPresetBundle && option == VanillaOption.GRAPHICS_MODE) {
            values.putAll(bundle((String) normalized.get()));
        } else if (graphicsPresetBundle && BUNDLED.contains(option)
                && !java.util.Objects.equals(values.get(option), normalized.get())) {
            values.put(VanillaOption.GRAPHICS_MODE, "CUSTOM");
        }
        values.put(option, normalized.get());
        return true;
    }

    /**
     * Writes an option the way something outside VANTA does (a vanilla slider, Sodium's Apply, Iris): no VANTA code
     * runs, but in {@link #graphicsPresetBundle} mode the game's own preset rules still apply.
     */
    public void setFromGame(VanillaOption option, Object value) {
        if (option == VanillaOption.GRAPHICS_MODE && "CUSTOM".equals(value)) {
            values.put(option, "CUSTOM"); // what Iris does with shaders on: graphicsPreset().set(CUSTOM)
            return;
        }
        int before = setOrder.size();
        set(option, value);
        while (setOrder.size() > before) {
            setOrder.remove(setOrder.size() - 1);
        }
    }

    /** The options Minecraft 1.21.11's {@code GraphicsPreset.apply} writes for one preset (VANTA-known ones). */
    public static Map<VanillaOption, Object> bundle(String preset) {
        Map<VanillaOption, Object> out = new EnumMap<>(VanillaOption.class);
        switch (preset) {
            case "FAST" -> {
                out.put(VanillaOption.BIOME_BLEND, 1);
                out.put(VanillaOption.RENDER_DISTANCE, 8);
                out.put(VanillaOption.SIMULATION_DISTANCE, 6);
                out.put(VanillaOption.SMOOTH_LIGHTING, false);
                out.put(VanillaOption.CLOUDS, "FAST");
                out.put(VanillaOption.PARTICLES, "DECREASED");
                out.put(VanillaOption.MIPMAP_LEVELS, 2);
                out.put(VanillaOption.ENTITY_SHADOWS, false);
                out.put(VanillaOption.ENTITY_DISTANCE_SCALING, 0.75);
                out.put(VanillaOption.MENU_BLUR, 2);
            }
            case "FANCY", "FABULOUS" -> {
                boolean fabulous = "FABULOUS".equals(preset);
                out.put(VanillaOption.BIOME_BLEND, 2);
                out.put(VanillaOption.RENDER_DISTANCE, fabulous ? 32 : 16);
                out.put(VanillaOption.SIMULATION_DISTANCE, 12);
                out.put(VanillaOption.SMOOTH_LIGHTING, true);
                out.put(VanillaOption.CLOUDS, "FANCY");
                out.put(VanillaOption.PARTICLES, "ALL");
                out.put(VanillaOption.MIPMAP_LEVELS, 4);
                out.put(VanillaOption.ENTITY_SHADOWS, true);
                out.put(VanillaOption.ENTITY_DISTANCE_SCALING, fabulous ? 1.25 : 1.0);
                out.put(VanillaOption.MENU_BLUR, 5);
            }
            default -> {
            }
        }
        return out;
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
