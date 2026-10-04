package dev.vanta.core.cosmetics;

import com.google.gson.JsonObject;
import dev.vanta.core.settings.SettingsStore;
import dev.vanta.core.settings.VantaSettings;
import java.util.Objects;

/**
 * The cosmetic choices a player made. The settings store is the source of truth; this record is the snapshot carried
 * by profiles.
 *
 * @param themeId         UI theme id
 * @param menuBackground  main menu background
 * @param menuParticles   main menu particles
 * @param hudTheme        HUD widget style
 * @param badge           visual badge
 * @param crosshairPreset crosshair preset id
 */
public record CosmeticsSelection(String themeId, MenuBackground menuBackground, MenuParticles menuParticles,
                                 HudTheme hudTheme, Badge badge, String crosshairPreset) {
    /** Factory defaults. */
    public static final CosmeticsSelection DEFAULT = new CosmeticsSelection(UiThemeDefinition.DEFAULT_ID,
            VantaSettings.MENU_BACKGROUND.defaultValue(), VantaSettings.MENU_PARTICLES.defaultValue(),
            HudTheme.CLEAN, Badge.NONE, "default");

    public CosmeticsSelection {
        Objects.requireNonNull(themeId, "themeId");
        Objects.requireNonNull(menuBackground, "menuBackground");
        Objects.requireNonNull(menuParticles, "menuParticles");
        Objects.requireNonNull(hudTheme, "hudTheme");
        Objects.requireNonNull(badge, "badge");
        Objects.requireNonNull(crosshairPreset, "crosshairPreset");
    }

    /** Reads the selection from the settings. */
    public static CosmeticsSelection fromSettings(SettingsStore settings) {
        return new CosmeticsSelection(settings.get(VantaSettings.GENERAL_THEME_ID),
                settings.get(VantaSettings.MENU_BACKGROUND), settings.get(VantaSettings.MENU_PARTICLES),
                settings.get(VantaSettings.COSMETICS_HUD_THEME), settings.get(VantaSettings.COSMETICS_BADGE),
                settings.get(VantaSettings.COSMETICS_CROSSHAIR_PRESET));
    }

    /** Writes the selection into the settings. */
    public void applyTo(SettingsStore settings) {
        settings.set(VantaSettings.GENERAL_THEME_ID, themeId);
        settings.set(VantaSettings.MENU_BACKGROUND, menuBackground);
        settings.set(VantaSettings.MENU_PARTICLES, menuParticles);
        settings.set(VantaSettings.COSMETICS_HUD_THEME, hudTheme);
        settings.set(VantaSettings.COSMETICS_BADGE, badge);
        settings.set(VantaSettings.COSMETICS_CROSSHAIR_PRESET, crosshairPreset);
    }

    /** JSON form. */
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("theme", themeId);
        o.addProperty("menuBackground", menuBackground.id());
        o.addProperty("menuParticles", menuParticles.id());
        o.addProperty("hudTheme", hudTheme.id());
        o.addProperty("badge", badge.id());
        o.addProperty("crosshairPreset", crosshairPreset);
        return o;
    }

    /** Tolerant JSON read (invalid fields fall back to defaults). */
    public static CosmeticsSelection fromJson(JsonObject o) {
        if (o == null) {
            return DEFAULT;
        }
        String theme = str(o, "theme", DEFAULT.themeId);
        if (!UiThemeDefinition.ID_PATTERN.matcher(theme).matches()) {
            theme = DEFAULT.themeId;
        }
        String crosshair = str(o, "crosshairPreset", DEFAULT.crosshairPreset);
        if (crosshair.length() > 32) {
            crosshair = DEFAULT.crosshairPreset;
        }
        return new CosmeticsSelection(theme,
                enumOrDefault(MenuBackground.values(), str(o, "menuBackground", ""), DEFAULT.menuBackground),
                enumOrDefault(MenuParticles.values(), str(o, "menuParticles", ""), DEFAULT.menuParticles),
                HudTheme.fromId(str(o, "hudTheme", DEFAULT.hudTheme.id())),
                Badge.fromId(str(o, "badge", DEFAULT.badge.id())), crosshair);
    }

    private static String str(JsonObject o, String key, String fallback) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : fallback;
    }

    private static <E extends Enum<E>> E enumOrDefault(E[] values, String id, E fallback) {
        for (E value : values) {
            if (value.name().equalsIgnoreCase(id)) {
                return value;
            }
        }
        return fallback;
    }
}
