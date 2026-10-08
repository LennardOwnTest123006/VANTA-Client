package dev.vanta.core.search;

import dev.vanta.core.i18n.LangKeyed;
import dev.vanta.core.screen.ScreenId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A command the global search can offer: opening a screen or running a named action.
 * <p>
 * Actions with a {@link #screen()} are handled by the host by opening that screen; the others
 * ({@code reset_settings}, {@code open_config_folder}, …) are dispatched by id through {@code VantaServices}.
 *
 * @param id       stable id, e.g. {@code open_hud_editor}
 * @param keywords extra search words
 * @param screen   screen to open, if the action is a navigation
 */
public record ActionEntry(String id, List<String> keywords, Optional<ScreenId> screen) implements LangKeyed {
    public ActionEntry {
        Objects.requireNonNull(id, "id");
        keywords = List.copyOf(keywords);
        Objects.requireNonNull(screen, "screen");
    }

    /** Navigation action. */
    public static ActionEntry open(ScreenId screen, String... keywords) {
        return new ActionEntry("open_" + screen.id(), List.of(keywords), Optional.of(screen));
    }

    /** Command action dispatched by id. */
    public static ActionEntry command(String id, String... keywords) {
        return new ActionEntry(id, List.of(keywords), Optional.empty());
    }

    @Override
    public String langKey() {
        return "vanta.action." + id;
    }

    /** Translation key of the one-line description. */
    public String descriptionKey() {
        return "vanta.action." + id + ".description";
    }

    /** Id of the "reset every VANTA setting" command. */
    public static final String RESET_SETTINGS = "reset_settings";
    /** Id of the "open the config folder" command. */
    public static final String OPEN_CONFIG_FOLDER = "open_config_folder";
    /** Id of the "export the active profile" command. */
    public static final String EXPORT_PROFILE = "export_profile";
    /** Id of the "take a HUD-free screenshot" command. */
    public static final String SCREENSHOT_HUD_FREE = "screenshot_hud_free";
    /** Id of the "toggle the HUD" command. */
    public static final String TOGGLE_HUD = "toggle_hud";
    /** Id of the "apply balanced performance preset" command. */
    public static final String APPLY_BALANCED_PRESET = "apply_balanced_preset";
    /**
     * Id of the one-click "Boost FPS" command: Max FPS preset, no frame-rate cap, VSync off, a plain VANTA menu and
     * the Performance pack when it is missing.
     */
    public static final String BOOST_FPS = "boost_fps";
    /** Id of Smart Boost's "measure again" command (takes back the video options the player changed). */
    public static final String SMART_BOOST_RETUNE = "smart_boost_retune";
    /** Id of "Undo Smart Boost": restores the video options from before Smart Boost and turns it off. */
    public static final String SMART_BOOST_UNDO = "smart_boost_undo";
    /** Id of the "clear statistics" command. */
    public static final String CLEAR_STATISTICS = "clear_statistics";
    /** Id of the "open website" command. */
    public static final String OPEN_WEBSITE = "open_website";
    /** Id of the navigation action that opens Vanta Nexus ({@code open_} + {@link ScreenId#NEXUS}). */
    public static final String OPEN_NEXUS = "open_nexus";

    /** Every built-in action. */
    public static List<ActionEntry> builtIns() {
        return List.of(
                open(ScreenId.SETTINGS, "options", "preferences", "configure"),
                open(ScreenId.HUD_EDITOR, "widgets", "overlay", "layout", "move", "edit hud"),
                open(ScreenId.PERFORMANCE, "fps", "frame time", "memory", "lag", "optimize"),
                open(ScreenId.PROFILES, "presets", "configs", "switch", "import", "export"),
                open(ScreenId.KEYBINDS, "controls", "keys", "shortcuts", "bindings", "hotkeys"),
                open(ScreenId.CROSSHAIR, "reticle", "aim", "cursor"),
                open(ScreenId.COSMETICS, "theme", "background", "particles", "badge", "appearance"),
                open(ScreenId.STATISTICS, "stats", "playtime", "sessions", "distance"),
                open(ScreenId.RESOURCE_PACKS, "textures", "packs", "texture pack"),
                open(ScreenId.MODS, "modrinth", "mods", "shaders", "shader packs", "iris", "sodium", "fps boost",
                        "performance pack", "download", "install"),
                open(ScreenId.ACCESSIBILITY, "contrast", "motion", "large text", "scale"),
                open(ScreenId.ABOUT, "version", "credits", "license", "website"),
                open(ScreenId.NEXUS, "nexus", "ai", "assistant", "local ai", "hud designer", "waypoints", "lab"),
                command(RESET_SETTINGS, "defaults", "restore", "factory"),
                command(OPEN_CONFIG_FOLDER, "files", "directory", "settings.json"),
                command(EXPORT_PROFILE, "backup", "share", "json"),
                command(SCREENSHOT_HUD_FREE, "capture", "clean", "hide hud", "photo"),
                command(TOGGLE_HUD, "hide", "show", "overlay"),
                command(APPLY_BALANCED_PRESET, "performance", "preset", "balanced", "fps"),
                command(BOOST_FPS, "performance", "max fps", "boost", "speed", "lag", "fps boost", "unlimited",
                        "sodium"),
                command(SMART_BOOST_RETUNE, "smart boost", "re-tune", "tune", "automatic", "fps", "optimize",
                        "booster", "measure"),
                command(SMART_BOOST_UNDO, "smart boost", "undo", "restore", "revert"),
                command(CLEAR_STATISTICS, "delete", "privacy", "reset stats"),
                command(OPEN_WEBSITE, "docs", "help", "support", "documentation"));
    }
}
