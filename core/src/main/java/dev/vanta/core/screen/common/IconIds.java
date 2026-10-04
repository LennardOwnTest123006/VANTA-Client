package dev.vanta.core.screen.common;

import dev.vanta.core.ui.Icons;
import java.util.Locale;
import java.util.Map;

/**
 * Maps the domain layer's string icon ids ({@code SettingCategory.iconId()}, {@code Notification.iconId()}, profile
 * icons) onto the UI kit's {@link Icons}. Unknown ids fall back to the caller's default so new domain icons never
 * break rendering.
 */
public final class IconIds {
    private static final Map<String, Icons> ALIASES = Map.ofEntries(
            Map.entry("settings", Icons.GEAR),
            Map.entry("video", Icons.EYE),
            Map.entry("audio", Icons.SLIDERS),
            Map.entry("controls", Icons.KEYBOARD),
            Map.entry("hud", Icons.GRID),
            Map.entry("performance", Icons.CHART),
            Map.entry("language", Icons.LIST),
            Map.entry("cosmetics", Icons.PALETTE),
            Map.entry("privacy", Icons.LOCK),
            Map.entry("statistics", Icons.CHART),
            Map.entry("stats", Icons.CHART),
            Map.entry("profiles", Icons.PROFILE),
            Map.entry("user", Icons.PROFILE),
            Map.entry("packs", Icons.PACKAGE),
            Map.entry("resource_packs", Icons.PACKAGE),
            Map.entry("about", Icons.INFO),
            Map.entry("link", Icons.EXTERNAL_LINK),
            Map.entry("external", Icons.EXTERNAL_LINK),
            Map.entry("world", Icons.PLAY),
            Map.entry("sword", Icons.CROSSHAIR),
            Map.entry("camera", Icons.EYE),
            Map.entry("bolt", Icons.CHART));

    private IconIds() {
    }

    /** Resolves an icon id; {@code null}, blank and unknown ids yield {@code fallback}. */
    public static Icons resolve(String iconId, Icons fallback) {
        if (iconId == null || iconId.isBlank()) {
            return fallback;
        }
        String key = iconId.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        Icons alias = ALIASES.get(key);
        if (alias != null) {
            return alias;
        }
        for (Icons icon : Icons.values()) {
            if (icon.name().toLowerCase(Locale.ROOT).equals(key)) {
                return icon;
            }
        }
        return fallback;
    }
}
