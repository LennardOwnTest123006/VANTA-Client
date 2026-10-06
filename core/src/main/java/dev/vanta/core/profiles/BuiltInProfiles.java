package dev.vanta.core.profiles;

import com.google.gson.JsonElement;
import dev.vanta.core.bridge.VanillaOption;
import dev.vanta.core.cosmetics.CosmeticsSelection;
import dev.vanta.core.cosmetics.MenuBackground;
import dev.vanta.core.cosmetics.MenuParticles;
import dev.vanta.core.crosshair.CrosshairPresets;
import dev.vanta.core.crosshair.CrosshairStyle;
import dev.vanta.core.hud.HudPresets;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.perf.FpsLimitPreset;
import dev.vanta.core.perf.PerformancePreset;
import dev.vanta.core.settings.Setting;
import dev.vanta.core.settings.SettingKind;
import dev.vanta.core.settings.SettingsRegistry;
import dev.vanta.core.settings.VantaSettings;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The five profiles created on first run. They are ordinary profiles afterwards (renamable, deletable except the last
 * one), built from the setting defaults plus a HUD preset, a performance preset and a crosshair preset each.
 */
public final class BuiltInProfiles {
    public static final String DEFAULT = "default";
    public static final String PVP = "pvp";
    public static final String BUILDING = "building";
    public static final String PERFORMANCE = "performance";
    public static final String RECORDING = "recording";

    /** Ids in creation order. */
    public static final List<String> IDS = List.of(DEFAULT, PVP, BUILDING, PERFORMANCE, RECORDING);

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
                build(registry, now, DEFAULT, "profile", HudPresets.DEFAULT, PerformancePreset.BALANCED,
                        CrosshairPresets.DEFAULT, Map.of()),
                build(registry, now, PVP, "crosshair", HudPresets.PVP, PerformancePreset.HIGH, CrosshairPresets.BOLD,
                        Map.of(VantaSettings.PERFORMANCE_FPS_LIMIT_PRESET, FpsLimitPreset.UNLIMITED,
                                VantaSettings.MENU_PARTICLES, MenuParticles.NONE,
                                VantaSettings.HUD_TEXT_SHADOW, Boolean.TRUE)),
                build(registry, now, BUILDING, "grid", HudPresets.MINIMAL, PerformancePreset.ULTRA,
                        CrosshairPresets.THIN,
                        Map.of(VantaSettings.VIDEO_FOV, 85, VantaSettings.HUD_GLOBAL_OPACITY, 0.8)),
                build(registry, now, PERFORMANCE, "chart", HudPresets.PERFORMANCE, PerformancePreset.BOOST,
                        CrosshairPresets.DEFAULT,
                        Map.of(VantaSettings.MENU_BACKGROUND, MenuBackground.SOLID,
                                VantaSettings.MENU_PARTICLES, MenuParticles.NONE,
                                VantaSettings.HUD_TEXT_SHADOW, Boolean.FALSE,
                                VantaSettings.PERFORMANCE_FPS_LIMIT_PRESET, FpsLimitPreset.UNLIMITED)),
                build(registry, now, RECORDING, "play", HudPresets.STREAMER, PerformancePreset.HIGH,
                        CrosshairPresets.DOT,
                        Map.of(VantaSettings.HUD_GLOBAL_OPACITY, 0.85,
                                VantaSettings.GENERAL_NOTIFICATION_DURATION, 2500,
                                VantaSettings.PRIVACY_STATS_TRACK_SERVERS, Boolean.FALSE,
                                VantaSettings.MENU_SHOW_VERSION_LABEL, Boolean.FALSE)));
    }

    private static Profile build(SettingsRegistry registry, long now, String id, String icon, String hudPresetId,
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
        FpsLimitPreset limit = enumOf(settings, VantaSettings.PERFORMANCE_FPS_LIMIT_PRESET);
        encodeInto(settings, VantaSettings.VIDEO_FRAMERATE_LIMIT, limit.framerateLimit());
        encodeInto(settings, VantaSettings.VIDEO_VSYNC, limit.vsync());
        CrosshairStyle crosshair = CrosshairPresets.find(crosshairPresetId).map(p -> p.style())
                .orElse(CrosshairStyle.DEFAULT);
        CosmeticsSelection cosmetics = new CosmeticsSelection(
                str(settings, VantaSettings.GENERAL_THEME_ID),
                enumOf(settings, VantaSettings.MENU_BACKGROUND),
                enumOf(settings, VantaSettings.MENU_PARTICLES),
                enumOf(settings, VantaSettings.COSMETICS_HUD_THEME),
                enumOf(settings, VantaSettings.COSMETICS_BADGE),
                crosshairPresetId);
        return new Profile(Profile.SCHEMA_VERSION, id, Lang.tr(nameKey(id)), icon, now, now, settings,
                HudPresets.find(hudPresetId).orElseThrow().layout(), Map.of(), crosshair, cosmetics);
    }

    private static Map<String, JsonElement> defaults(SettingsRegistry registry) {
        Map<String, JsonElement> out = new LinkedHashMap<>();
        for (Setting<?> setting : registry.all()) {
            if (setting.kind() != SettingKind.ACTION) {
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
