package dev.vanta.client.bridge;

import dev.vanta.core.bridge.OptionsBridge;
import dev.vanta.core.bridge.VanillaOption;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.sounds.SoundSource;

/**
 * {@link OptionsBridge} on top of {@link Options}. Every {@link VanillaOption} maps to the matching
 * {@link OptionInstance}; writing through {@code OptionInstance.set} runs the game's own side effects (a render
 * distance change reloads chunks, vsync toggles the window, …), so changing an option here is identical to moving the
 * slider in Video Settings.
 * <p>
 * The map is built lazily on first use because {@link Options} does not exist yet while mods initialise.
 */
public final class MinecraftOptionsBridge implements OptionsBridge {

    /** Read/write access to one option with values in the bridge's exchange types. */
    private record Access(Supplier<Object> getter, Predicate<Object> setter) {
    }

    private Map<VanillaOption, Access> accessors;

    private static Options options() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? null : minecraft.options;
    }

    private Map<VanillaOption, Access> accessors() {
        if (accessors == null) {
            Options options = options();
            if (options == null) {
                return Map.of();
            }
            accessors = build(options);
        }
        return accessors;
    }

    private static Map<VanillaOption, Access> build(Options o) {
        Map<VanillaOption, Access> map = new EnumMap<>(VanillaOption.class);
        // video
        map.put(VanillaOption.FRAMERATE_LIMIT, number(o.framerateLimit(), Number::intValue));
        map.put(VanillaOption.RENDER_DISTANCE, number(o.renderDistance(), Number::intValue));
        map.put(VanillaOption.SIMULATION_DISTANCE, number(o.simulationDistance(), Number::intValue));
        map.put(VanillaOption.PARTICLES, enumeration(o.particles(), ParticleStatus.class));
        map.put(VanillaOption.CLOUDS, enumeration(o.cloudStatus(), CloudStatus.class));
        map.put(VanillaOption.SMOOTH_LIGHTING, bool(o.ambientOcclusion()));
        map.put(VanillaOption.ENTITY_SHADOWS, bool(o.entityShadows()));
        map.put(VanillaOption.ENTITY_DISTANCE_SCALING, number(o.entityDistanceScaling(), Number::doubleValue));
        map.put(VanillaOption.VSYNC, bool(o.enableVsync()));
        map.put(VanillaOption.GUI_SCALE, number(o.guiScale(), Number::intValue));
        map.put(VanillaOption.BIOME_BLEND, number(o.biomeBlendRadius(), Number::intValue));
        map.put(VanillaOption.MIPMAP_LEVELS, number(o.mipmapLevels(), Number::intValue));
        map.put(VanillaOption.VIEW_BOBBING, bool(o.bobView()));
        map.put(VanillaOption.FOV, number(o.fov(), Number::intValue));
        map.put(VanillaOption.MENU_BLUR, number(o.menuBackgroundBlurriness(), Number::intValue));
        map.put(VanillaOption.GRAPHICS_MODE, graphicsPreset(o));
        map.put(VanillaOption.INACTIVITY_FPS_LIMIT, enumeration(o.inactivityFpsLimit(), InactivityFpsLimit.class));
        map.put(VanillaOption.SCREEN_EFFECT_SCALE, number(o.screenEffectScale(), Number::doubleValue));
        map.put(VanillaOption.FOV_EFFECT_SCALE, number(o.fovEffectScale(), Number::doubleValue));
        map.put(VanillaOption.GLINT_SPEED, number(o.glintSpeed(), Number::doubleValue));
        map.put(VanillaOption.GLINT_STRENGTH, number(o.glintStrength(), Number::doubleValue));
        map.put(VanillaOption.AUTOSAVE_INDICATOR, bool(o.showAutosaveIndicator()));
        map.put(VanillaOption.PANORAMA_SPEED, number(o.panoramaSpeed(), Number::doubleValue));
        map.put(VanillaOption.DARK_LOADING_SCREEN, bool(o.darkMojangStudiosBackground()));
        // audio
        map.put(VanillaOption.MASTER_VOLUME, volume(o, SoundSource.MASTER));
        map.put(VanillaOption.MUSIC_VOLUME, volume(o, SoundSource.MUSIC));
        map.put(VanillaOption.RECORDS_VOLUME, volume(o, SoundSource.RECORDS));
        map.put(VanillaOption.WEATHER_VOLUME, volume(o, SoundSource.WEATHER));
        map.put(VanillaOption.BLOCKS_VOLUME, volume(o, SoundSource.BLOCKS));
        map.put(VanillaOption.HOSTILE_VOLUME, volume(o, SoundSource.HOSTILE));
        map.put(VanillaOption.NEUTRAL_VOLUME, volume(o, SoundSource.NEUTRAL));
        map.put(VanillaOption.PLAYERS_VOLUME, volume(o, SoundSource.PLAYERS));
        map.put(VanillaOption.AMBIENT_VOLUME, volume(o, SoundSource.AMBIENT));
        map.put(VanillaOption.VOICE_VOLUME, volume(o, SoundSource.VOICE));
        map.put(VanillaOption.SHOW_SUBTITLES, bool(o.showSubtitles()));
        // controls
        map.put(VanillaOption.MOUSE_SENSITIVITY, number(o.sensitivity(), Number::doubleValue));
        map.put(VanillaOption.INVERT_MOUSE, bool(o.invertMouseY()));
        map.put(VanillaOption.MOUSE_WHEEL_SENSITIVITY, number(o.mouseWheelSensitivity(), Number::doubleValue));
        map.put(VanillaOption.DISCRETE_SCROLL, bool(o.discreteMouseScroll()));
        map.put(VanillaOption.RAW_MOUSE_INPUT, bool(o.rawMouseInput()));
        map.put(VanillaOption.AUTO_JUMP, bool(o.autoJump()));
        map.put(VanillaOption.TOGGLE_SNEAK, bool(o.toggleCrouch()));
        map.put(VanillaOption.TOGGLE_SPRINT, bool(o.toggleSprint()));
        // accessibility
        map.put(VanillaOption.HIGH_CONTRAST, bool(o.highContrast()));
        map.put(VanillaOption.REDUCED_DEBUG_INFO, bool(o.reducedDebugInfo()));
        map.put(VanillaOption.TEXT_BACKGROUND_OPACITY, number(o.textBackgroundOpacity(), Number::doubleValue));
        map.put(VanillaOption.CHAT_OPACITY, number(o.chatOpacity(), Number::doubleValue));
        return map;
    }

    // ---- accessor factories ------------------------------------------------------------------------------------

    private static <T extends Number> Access number(OptionInstance<T> instance, Function<Number, T> convert) {
        return new Access(instance::get, value -> {
            if (!(value instanceof Number number)) {
                return false;
            }
            instance.set(convert.apply(number));
            return true;
        });
    }

    private static Access bool(OptionInstance<Boolean> instance) {
        return new Access(instance::get, value -> {
            if (!(value instanceof Boolean flag)) {
                return false;
            }
            instance.set(flag);
            return true;
        });
    }

    private static <E extends Enum<E>> Access enumeration(OptionInstance<E> instance, Class<E> type) {
        return new Access(() -> instance.get().name(), value -> {
            Optional<E> constant = constant(type, value);
            if (constant.isEmpty()) {
                return false;
            }
            instance.set(constant.get());
            return true;
        });
    }

    private static Access volume(Options options, SoundSource source) {
        return number(options.getSoundSourceOptionInstance(source), Number::doubleValue);
    }

    /**
     * Graphics preset: FAST / FANCY / FABULOUS. {@code Options.applyGraphicsPreset} writes every individual graphics
     * option the preset implies (clouds, leaves, transparency, …) exactly like the Video Settings button.
     */
    private static Access graphicsPreset(Options options) {
        return new Access(() -> options.graphicsPreset().get().name(), value -> {
            Optional<GraphicsPreset> preset = constant(GraphicsPreset.class, value);
            if (preset.isEmpty() || preset.get() == GraphicsPreset.CUSTOM) {
                return false;
            }
            options.applyGraphicsPreset(preset.get());
            if (options.graphicsPreset().get() != preset.get()) {
                options.graphicsPreset().set(preset.get());
            }
            return true;
        });
    }

    private static <E extends Enum<E>> Optional<E> constant(Class<E> type, Object value) {
        if (value instanceof Enum<?> e) {
            value = e.name();
        }
        if (!(value instanceof String name)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Enum.valueOf(type, name.trim().toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    // ---- OptionsBridge -----------------------------------------------------------------------------------------

    @Override
    public boolean supports(VanillaOption option) {
        return accessors().containsKey(option);
    }

    @Override
    public Optional<Object> get(VanillaOption option) {
        Access access = accessors().get(option);
        if (access == null) {
            return Optional.empty();
        }
        Object raw = access.getter().get();
        return raw == null ? Optional.empty() : option.normalize(raw);
    }

    @Override
    public boolean set(VanillaOption option, Object value) {
        Access access = accessors().get(option);
        if (access == null) {
            return false;
        }
        Optional<Object> normalized = option.normalize(value);
        return normalized.isPresent() && access.setter().test(normalized.get());
    }

    @Override
    public void save() {
        Options options = options();
        if (options != null) {
            options.save();
        }
    }
}
