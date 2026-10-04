package dev.vanta.core.profiles;

import com.google.gson.JsonElement;
import dev.vanta.core.bridge.KeyRef;
import dev.vanta.core.cosmetics.CosmeticsSelection;
import dev.vanta.core.crosshair.CrosshairStyle;
import dev.vanta.core.hud.HudLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A complete, switchable configuration: settings snapshot, HUD layout, VANTA key overrides, crosshair and cosmetics.
 * Profiles are plain JSON files in {@code config/vanta/profiles/} and can be exported and imported.
 *
 * @param schemaVersion file schema version
 * @param id            file stem ({@code [a-z0-9_-]}), unique
 * @param name          display name (≤ 64 chars)
 * @param icon          icon id for the UI (≤ 32 chars)
 * @param createdAt     epoch millis
 * @param updatedAt     epoch millis
 * @param settings      setting id → JSON value
 * @param hud           HUD layout
 * @param keybinds      VANTA key mapping id → key
 * @param crosshair     crosshair style
 * @param cosmetics     cosmetic selection
 */
public record Profile(int schemaVersion, String id, String name, String icon, long createdAt, long updatedAt,
                      Map<String, JsonElement> settings, HudLayout hud, Map<String, KeyRef> keybinds,
                      CrosshairStyle crosshair, CosmeticsSelection cosmetics) {
    /** Current profile schema version. */
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_NAME = 64;
    public static final int MAX_ICON = 32;
    public static final int MAX_SETTINGS = 512;
    public static final int MAX_KEYBINDS = 64;
    /** Default icon id. */
    public static final String DEFAULT_ICON = "profile";

    public Profile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        icon = icon == null || icon.isBlank() ? DEFAULT_ICON : icon;
        if (name.isBlank() || name.length() > MAX_NAME) {
            throw new IllegalArgumentException("Profile name must be 1–" + MAX_NAME + " characters");
        }
        if (icon.length() > MAX_ICON) {
            throw new IllegalArgumentException("Profile icon id too long");
        }
        settings = Map.copyOf(new LinkedHashMap<>(settings));
        keybinds = Map.copyOf(new LinkedHashMap<>(keybinds));
        Objects.requireNonNull(hud, "hud");
        Objects.requireNonNull(crosshair, "crosshair");
        Objects.requireNonNull(cosmetics, "cosmetics");
        if (settings.size() > MAX_SETTINGS) {
            throw new IllegalArgumentException("Profile has too many settings");
        }
        if (keybinds.size() > MAX_KEYBINDS) {
            throw new IllegalArgumentException("Profile has too many key bindings");
        }
    }

    public Profile withId(String newId) {
        return new Profile(schemaVersion, newId, name, icon, createdAt, updatedAt, settings, hud, keybinds, crosshair,
                cosmetics);
    }

    public Profile withName(String newName, long now) {
        return new Profile(schemaVersion, id, newName, icon, createdAt, now, settings, hud, keybinds, crosshair,
                cosmetics);
    }

    public Profile withIcon(String newIcon, long now) {
        return new Profile(schemaVersion, id, name, newIcon, createdAt, now, settings, hud, keybinds, crosshair,
                cosmetics);
    }

    /** Copy with new content and an updated timestamp. */
    public Profile withContent(Map<String, JsonElement> newSettings, HudLayout newHud, Map<String, KeyRef> newKeys,
                               CrosshairStyle newCrosshair, CosmeticsSelection newCosmetics, long now) {
        return new Profile(schemaVersion, id, name, icon, createdAt, now, newSettings, newHud, newKeys, newCrosshair,
                newCosmetics);
    }
}
