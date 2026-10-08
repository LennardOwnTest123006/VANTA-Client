package dev.vanta.core.profiles;

import com.google.gson.JsonElement;
import dev.vanta.core.bridge.VanillaOption;
import dev.vanta.core.cosmetics.CosmeticsSelection;
import dev.vanta.core.cosmetics.MenuBackground;
import dev.vanta.core.cosmetics.MenuParticles;
import dev.vanta.core.crosshair.CrosshairPresets;
import dev.vanta.core.crosshair.CrosshairStyle;
import dev.vanta.core.ai.NexusHudPreset;
import dev.vanta.core.hud.HudLayout;
import dev.vanta.core.hud.HudPresets;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.perf.FpsLimitPreset;
import dev.vanta.core.perf.PerformancePreset;
import dev.vanta.core.settings.Setting;
import dev.vanta.core.settings.SettingKind;
import dev.vanta.core.settings.SettingsRegistry;
import dev.vanta.core.settings.VantaSettings;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The seven profiles created on first run. They are ordinary profiles afterwards (renamable, deletable except the last
 * one), built from the VANTA setting defaults plus a HUD layout, a performance preset and a crosshair preset each.
 * From 1.4.0 on the HUD layouts of PvP, Survival, Building, Recording and Minimal are the matching
 * {@link NexusHudPreset} applied to the Default layout, so a profile and the assistant's {@code hud.preset} action
 * produce the same HUD.
 * <p>
 * Of the vanilla options a built-in profile only carries what it is about: the options of its performance preset, the
 * frame-rate limit and VSync of its frame-rate choice and its explicit overrides (Building's FOV). Volumes, mouse
 * sensitivity, GUI scale, chat and every other vanilla option stay as the player set them when a built-in profile is
 * activated (releases before 1.3.0 replayed vanilla's defaults for all of them).
 */
public final class BuiltInProfiles {
    public static final String DEFAULT = "default";
    public static final String PVP = "pvp";
    public static final String BUILDING = "building";
    public static final String PERFORMANCE = "performance";
    public static final String RECORDING = "recording";
    public static final String SURVIVAL = "survival";
    public static final String MINIMAL = "minimal";

    /** Ids in creation order. */
    public static final List<String> IDS = List.of(DEFAULT, PVP, SURVIVAL, BUILDING, RECORDING, PERFORMANCE, MINIMAL);

    /**
     * Version of the built-in profile content. {@code ProfileManager} re-seeds built-ins the player never touched
     * once when the version recorded in {@code profiles/state.json} is older (2: client 1.3.0, vanilla options limited
     * to the ones each profile is about; 3: client 1.4.0, HUD layouts from the Nexus presets plus the new Survival and
     * Minimal profiles, which are added to existing installs once).
     */
    public static final int CONTENT_VERSION = 3;

    /** The content version a built-in profile first shipped with (new ones are added to older installs once). */
    public static int addedInVersion(String id) {
        return switch (id) {
            case SURVIVAL, MINIMAL -> 3;
            default -> 1;
        };
    }

    private BuiltInProfiles() {
    }

    /** Translation key of a built-in profile name. */
    public static String nameKey(String id) {
        return "vanta.profile.builtin." + id;
    }

    /** Translation key of a built-in profile description. */
    public static String descriptionKey(String id) {
        return "vanta.profile.builtin." + id + ".description";
    }

    /** Creates all built-in profiles with {@code now} as timestamp. */
    public static List<Profile> create(SettingsRegistry registry, long now) {
        return List.of(
                build(registry, now, DEFAULT, "profile", HudPresets.defaultPreset().layout(), PerformancePreset.BALANCED,
                        CrosshairPresets.DEFAULT, Map.of()),
                build(registry, now, PVP, "crosshair", nexusHud(NexusHudPreset.PVP), PerformancePreset.HIGH,
                        CrosshairPresets.BOLD,
                        Map.of(VantaSettings.PERFORMANCE_FPS_LIMIT_PRESET, FpsLimitPreset.UNLIMITED,
                                VantaSettings.MENU_PARTICLES, MenuParticles.NONE,
                                VantaSettings.HUD_TEXT_SHADOW, Boolean.TRUE)),
                build(registry, now, SURVIVAL, "world", nexusHud(NexusHudPreset.SURVIVAL), PerformancePreset.BALANCED,
                        CrosshairPresets.DEFAULT,
                        Map.of(VantaSettings.HUD_TEXT_SHADOW, Boolean.TRUE,
                                VantaSettings.VIDEO_FOV, 75)),
                build(registry, now, BUILDING, "grid", nexusHud(NexusHudPreset.BUILDING), PerformancePreset.ULTRA,
                        CrosshairPresets.THIN,
                        Map.of(VantaSettings.VIDEO_FOV, 85, VantaSettings.HUD_GLOBAL_OPACITY, 0.8)),
                build(registry, now, RECORDING, "play", nexusHud(NexusHudPreset.RECORDING), PerformancePreset.HIGH,
                        CrosshairPresets.DOT,
                        Map.of(VantaSettings.HUD_GLOBAL_OPACITY, 0.85,
                                VantaSettings.GENERAL_NOTIFICATION_DURATION, 2500,
                                VantaSettings.PRIVACY_STATS_TRACK_SERVERS, Boolean.FALSE,
                                VantaSettings.MENU_SHOW_VERSION_LABEL, Boolean.FALSE)),
                build(registry, now, PERFORMANCE, "chart", HudPresets.find(HudPresets.PERFORMANCE).orElseThrow().layout(),
                        PerformancePreset.BOOST, CrosshairPresets.DEFAULT,
                        Map.of(VantaSettings.MENU_BACKGROUND, MenuBackground.SOLID,
                                VantaSettings.MENU_PARTICLES, MenuParticles.NONE,
                                VantaSettings.HUD_TEXT_SHADOW, Boolean.FALSE,
                                VantaSettings.PERFORMANCE_FPS_LIMIT_PRESET, FpsLimitPreset.UNLIMITED)),
                build(registry, now, MINIMAL, "eye", nexusHud(NexusHudPreset.MINIMAL), PerformancePreset.BALANCED,
                        CrosshairPresets.THIN,
                        Map.of(VantaSettings.HUD_GLOBAL_OPACITY, 0.9,
                                VantaSettings.MENU_PARTICLES, MenuParticles.NONE,
                                VantaSettings.GENERAL_NOTIFICATION_DURATION, 2500)));
    }

    /** The Default HUD layout with a Nexus preset applied: the layout the assistant's {@code hud.preset} produces. */
    public static HudLayout nexusHud(NexusHudPreset preset) {
        return preset.apply(HudPresets.defaultPreset().layout());
    }

    private static Profile build(SettingsRegistry registry, long now, String id, String icon, HudLayout hud,
                                 PerformancePreset perf, String crosshairPresetId, Map<Setting<?>, Object> overrides) {
        Map<String, JsonElement> settings = defaults(registry);
        for (Map.Entry<VanillaOption, Object> entry : perf.optionValues().entrySet()) {
            Optional<Setting<?>> bound = registry.forVanillaOption(entry.getKey());
            bound.ifPresent(s -> encodeInto(settings, s, entry.getValue()));
        }
        encodeInto(settings, VantaSettings.PERFORMANCE_PRESET, perf);
        encodeInto(settings, VantaSettings.COSMETICS_CROSSHAIR_PRESET, crosshairPresetId);
        for (Map.Entry<Setting<?>, Object> entry : overrides.entrySet()) {
            encodeInto(settings, entry.getKey(), entry.getValue());
        }
        // A profile's frame-rate choice is only real once the vanilla limit and VSync it stands for are in the
        // profile too: activation replays every setting, and the registry defaults would otherwise re-apply
        // vanilla's 120 FPS cap with VSync on. The performance preset deliberately leaves both alone.
        Map<String, JsonElement> complete = withFrameRateFromPreset(settings);
        CrosshairStyle crosshair = CrosshairPresets.find(crosshairPresetId).map(p -> p.style())
                .orElse(CrosshairStyle.DEFAULT);
        CosmeticsSelection cosmetics = new CosmeticsSelection(
                str(complete, VantaSettings.GENERAL_THEME_ID),
                enumOf(complete, VantaSettings.MENU_BACKGROUND),
                enumOf(complete, VantaSettings.MENU_PARTICLES),
                enumOf(complete, VantaSettings.COSMETICS_HUD_THEME),
                enumOf(complete, VantaSettings.COSMETICS_BADGE),
                crosshairPresetId);
        return new Profile(Profile.SCHEMA_VERSION, id, Lang.tr(nameKey(id)), icon, now, now, complete, hud, Map.of(),
                crosshair, cosmetics);
    }

    /**
     * The snapshot with {@code video.framerateLimit} and {@code video.vsync} derived from
     * {@code performance.fpsLimitPreset}, so the frame-rate choice is the single source of truth whenever a profile is
     * applied: a profile saying "unlimited" can never re-apply a 120 FPS cap that an older release or a vanilla edit
     * left in the file. Snapshots without the preset are returned unchanged.
     */
    public static Map<String, JsonElement> withFrameRateFromPreset(Map<String, JsonElement> settings) {
        JsonElement element = settings.get(VantaSettings.PERFORMANCE_FPS_LIMIT_PRESET.id());
        if (element == null) {
            return settings;
        }
        Optional<FpsLimitPreset> preset = VantaSettings.PERFORMANCE_FPS_LIMIT_PRESET.decode(element);
        if (preset.isEmpty()) {
            return settings;
        }
        Map<String, JsonElement> out = new LinkedHashMap<>(settings);
        encodeInto(out, VantaSettings.VIDEO_FRAMERATE_LIMIT, preset.get().framerateLimit());
        encodeInto(out, VantaSettings.VIDEO_VSYNC, preset.get().vsync());
        return out;
    }

    /**
     * The inverse of {@link #withFrameRateFromPreset} for a snapshot of the live settings: the frame-rate choice is set
     * to the one the snapshot's {@code video.framerateLimit} and {@code video.vsync} stand for, so a profile saved
     * after the player changed Max Framerate in vanilla Video Settings or Sodium restores that limit, not a stale
     * choice from settings.json. When no choice matches (e.g. 75 FPS) the choice is dropped from the snapshot, and the
     * profile then applies the vanilla values as captured. Snapshots without both vanilla values are returned
     * unchanged.
     */
    public static Map<String, JsonElement> withPresetFromFrameRate(Map<String, JsonElement> settings) {
        Optional<?> limit = Optional.ofNullable(settings.get(VantaSettings.VIDEO_FRAMERATE_LIMIT.id()))
                .flatMap(VantaSettings.VIDEO_FRAMERATE_LIMIT::decode);
        Optional<?> vsync = Optional.ofNullable(settings.get(VantaSettings.VIDEO_VSYNC.id()))
                .flatMap(VantaSettings.VIDEO_VSYNC::decode);
        if (limit.isEmpty() || vsync.isEmpty() || !(limit.get() instanceof Integer fps)
                || !(vsync.get() instanceof Boolean on)) {
            return settings;
        }
        Map<String, JsonElement> out = new LinkedHashMap<>(settings);
        Optional<FpsLimitPreset> match = Arrays.stream(FpsLimitPreset.values())
                .filter(p -> p.framerateLimit() == fps && p.vsync() == on).findFirst();
        if (match.isPresent()) {
            encodeInto(out, VantaSettings.PERFORMANCE_FPS_LIMIT_PRESET, match.get());
        } else {
            out.remove(VantaSettings.PERFORMANCE_FPS_LIMIT_PRESET.id());
        }
        return out;
    }

    /** The built-in profile with an id, freshly built from the current defaults. */
    public static Optional<Profile> shipped(SettingsRegistry registry, long now, String id) {
        return create(registry, now).stream().filter(p -> p.id().equals(id)).findFirst();
    }

    /** Defaults of every VANTA setting; vanilla-bound ones are added only where a profile defines them. */
    private static Map<String, JsonElement> defaults(SettingsRegistry registry) {
        Map<String, JsonElement> out = new LinkedHashMap<>();
        for (Setting<?> setting : registry.all()) {
            if (setting.kind() != SettingKind.ACTION && !setting.isVanilla()) {
                out.put(setting.id(), encodeDefault(setting));
            }
        }
        return out;
    }

    private static <T> JsonElement encodeDefault(Setting<T> setting) {
        return setting.encode(setting.defaultValue());
    }

    @SuppressWarnings("unchecked")
    private static void encodeInto(Map<String, JsonElement> target, Setting<?> setting, Object value) {
        Setting<Object> erased = (Setting<Object>) setting;
        erased.coerce(value).ifPresent(v -> target.put(setting.id(), erased.encode(v)));
    }

    private static <T> T decode(Map<String, JsonElement> settings, Setting<T> setting) {
        JsonElement element = settings.get(setting.id());
        return element == null ? setting.defaultValue() : setting.decode(element).orElse(setting.defaultValue());
    }

    private static String str(Map<String, JsonElement> settings, Setting<String> setting) {
        return decode(settings, setting);
    }

    private static <E extends Enum<E>> E enumOf(Map<String, JsonElement> settings, Setting<E> setting) {
        return decode(settings, setting);
    }
}
