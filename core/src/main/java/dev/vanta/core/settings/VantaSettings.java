package dev.vanta.core.settings;

import dev.vanta.core.accessibility.ColorBlindPalette;
import dev.vanta.core.bridge.VanillaOption;
import dev.vanta.core.cosmetics.Badge;
import dev.vanta.core.cosmetics.HudTheme;
import dev.vanta.core.cosmetics.MenuBackground;
import dev.vanta.core.cosmetics.MenuParticles;
import dev.vanta.core.notifications.NotificationPosition;
import dev.vanta.core.perf.FpsLimitPreset;
import dev.vanta.core.perf.PerformancePreset;
import dev.vanta.core.worlds.WorldsFolderMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static dev.vanta.core.settings.SettingCategory.ACCESSIBILITY;
import static dev.vanta.core.settings.SettingCategory.AUDIO;
import static dev.vanta.core.settings.SettingCategory.CONTROLS;
import static dev.vanta.core.settings.SettingCategory.COSMETICS;
import static dev.vanta.core.settings.SettingCategory.GENERAL;
import static dev.vanta.core.settings.SettingCategory.HUD;
import static dev.vanta.core.settings.SettingCategory.LANGUAGE;
import static dev.vanta.core.settings.SettingCategory.NEXUS;
import static dev.vanta.core.settings.SettingCategory.PERFORMANCE;
import static dev.vanta.core.settings.SettingCategory.PRIVACY;
import static dev.vanta.core.settings.SettingCategory.VIDEO;

/**
 * Every VANTA setting, defined once. Vanilla-bound entries make the VIDEO / AUDIO / CONTROLS categories edit the
 * real Minecraft options through {@code OptionsBridge}.
 * <p>
 * Ids are stable and used as JSON keys and translation keys; never rename one without a {@code Migrations} step.
 */
public final class VantaSettings {
    private static final List<Setting<?>> ALL = new ArrayList<>();

    // ---- general: main menu -----------------------------------------------------------------------------------
    /** Replace the vanilla title screen with the VANTA main menu. */
    public static final Setting<Boolean> MENU_CUSTOM_MAIN_MENU = add(Setting.bool("menu.customMainMenu", GENERAL, true)
            .keywords("title screen", "main menu", "vanilla menu"));
    /** Main menu background style. */
    public static final Setting<MenuBackground> MENU_BACKGROUND = add(Setting.enumOf("menu.background", GENERAL,
            MenuBackground.class, MenuBackground.VIOLET_HORIZON).keywords("wallpaper", "panorama", "backdrop"));
    /** Main menu particle layer. */
    public static final Setting<MenuParticles> MENU_PARTICLES = add(Setting.enumOf("menu.particles", GENERAL,
            MenuParticles.class, MenuParticles.EMBERS).keywords("embers", "dust", "ambient", "effects"));
    /** Show the version label in the main menu corner. */
    public static final Setting<Boolean> MENU_SHOW_VERSION_LABEL = add(Setting.bool("menu.showVersionLabel", GENERAL, true)
            .keywords("version", "label", "corner"));

    // ---- general: interface -------------------------------------------------------------------------------------
    /** Corner where toasts appear. */
    public static final Setting<NotificationPosition> GENERAL_NOTIFICATIONS_POSITION = add(Setting.enumOf(
            "general.notificationsPosition", GENERAL, NotificationPosition.class, NotificationPosition.TOP_RIGHT)
            .keywords("toast", "popup", "corner", "alerts"));
    /** How long a toast stays on screen, in milliseconds. */
    public static final Setting<Integer> GENERAL_NOTIFICATION_DURATION = add(Setting.intRange(
            "general.notificationDurationMs", GENERAL, 4000, 1500, 10000, 500)
            .keywords("toast", "timeout", "seconds", "alerts"));
    /** Scale multiplier applied to all VANTA screens. */
    public static final Setting<Double> GENERAL_UI_SCALE = add(Setting.doubleRange("general.uiScale", GENERAL, 1.0,
            0.75, 1.5, 0.05).keywords("size", "zoom", "interface", "gui scale"));
    /** Play click sounds in VANTA menus. */
    public static final Setting<Boolean> GENERAL_UI_SOUNDS = add(Setting.bool("general.uiSounds", GENERAL, true)
            .keywords("click", "sound", "mute"));
    /** Id of the active UI theme (see {@code CosmeticsRegistry}). */
    public static final Setting<String> GENERAL_THEME_ID = add(Setting.string("general.themeId", GENERAL, "vanta-dark")
            .keywords("theme", "colors", "accent", "dark"));
    // ---- general: worlds ---------------------------------------------------------------------------------------
    /**
     * Which saves folder Singleplayer lists (see {@code WorldsFolder}): the official Minecraft folder's worlds when it
     * exists, or the VANTA game folder's own. Minecraft opens its level storage once at start, hence the restart.
     */
    public static final Setting<WorldsFolderMode> WORLDS_FOLDER = add(Setting.enumOf("general.worldsFolder", GENERAL,
            WorldsFolderMode.class, WorldsFolderMode.MINECRAFT_FOLDER).requiresRestart()
            .keywords("worlds", "singleplayer", "saves", "minecraft folder", ".minecraft", "old worlds"));

    /** Button: reset every VANTA setting. */
    public static final Setting<String> GENERAL_RESET_ALL = add(Setting.action("general.resetAll", GENERAL,
            "reset_settings").keywords("defaults", "restore", "factory"));
    /** Button: open the config folder. */
    public static final Setting<String> GENERAL_OPEN_CONFIG_FOLDER = add(Setting.action("general.openConfigFolder",
            GENERAL, "open_config_folder").keywords("files", "directory", "settings.json"));

    // ---- hud --------------------------------------------------------------------------------------------------------
    /** Master switch for the VANTA HUD. */
    public static final Setting<Boolean> HUD_ENABLED = add(Setting.bool("hud.enabled", HUD, true)
            .keywords("overlay", "widgets", "show hud", "hide hud"));
    /** Draw the grid in the HUD editor. */
    public static final Setting<Boolean> HUD_EDITOR_GRID = add(Setting.bool("hud.editorGrid", HUD, true)
            .keywords("editor", "grid", "lines"));
    /** Snap widgets to the grid and to other widgets while dragging. */
    public static final Setting<Boolean> HUD_SNAP = add(Setting.bool("hud.snap", HUD, true)
            .keywords("editor", "snapping", "align", "magnet"));
    /** Scale multiplier applied to every widget. */
    public static final Setting<Double> HUD_GLOBAL_SCALE = add(Setting.doubleRange("hud.globalScale", HUD, 1.0, 0.5,
            2.0, 0.05).keywords("size", "widgets", "zoom"));
    /** Opacity multiplier applied to every widget. */
    public static final Setting<Double> HUD_GLOBAL_OPACITY = add(Setting.doubleRange("hud.globalOpacity", HUD, 1.0,
            0.1, 1.0, 0.05).keywords("transparency", "alpha", "widgets"));
    /** Draw HUD text with a shadow. */
    public static final Setting<Boolean> HUD_TEXT_SHADOW = add(Setting.bool("hud.textShadow", HUD, true)
            .keywords("font", "shadow", "readability"));
    /** Button: open the HUD editor. */
    public static final Setting<String> HUD_OPEN_EDITOR = add(Setting.action("hud.openEditor", HUD, "open_hud_editor")
            .keywords("editor", "layout", "move widgets"));

    // ---- performance ------------------------------------------------------------------------------------------------
    /** Quick frame-rate limit choice (writes the vanilla limit and vsync). */
    public static final Setting<FpsLimitPreset> PERFORMANCE_FPS_LIMIT_PRESET = add(Setting.enumOf(
            "performance.fpsLimitPreset", PERFORMANCE, FpsLimitPreset.class, FpsLimitPreset.UNLIMITED)
            .keywords("fps", "frame rate", "limit", "cap", "vsync", "max fps"));
    /** Show the frame time graph in the Performance Center. */
    public static final Setting<Boolean> PERFORMANCE_SHOW_FRAME_TIME_GRAPH = add(Setting.bool(
            "performance.showFrameTimeGraph", PERFORMANCE, true).keywords("fps", "graph", "chart", "frametime"));
    /** Suggest a lower or higher render distance when the frame rate drifts from the target. */
    public static final Setting<Boolean> PERFORMANCE_RENDER_DISTANCE_SUGGESTIONS = add(Setting.bool(
            "performance.dynamicRenderDistanceSuggestions", PERFORMANCE, true)
            .keywords("render distance", "chunks", "advice", "suggestion", "fps"));
    /** Apply render distance suggestions automatically (off by default; suggestions only otherwise). */
    public static final Setting<Boolean> PERFORMANCE_AUTO_APPLY_RENDER_DISTANCE = add(Setting.bool(
            "performance.autoApplyRenderDistance", PERFORMANCE, false)
            .keywords("render distance", "automatic", "dynamic", "chunks"));
    /**
     * Smart Boost: measure gameplay once after installing or updating VANTA and pick the video preset this PC runs
     * smoothly (never the graphics preset, the frame-rate limit or VSync; options the player changed stay theirs).
     */
    public static final Setting<Boolean> PERFORMANCE_SMART_BOOST = add(Setting.bool("performance.smartBoost",
            PERFORMANCE, true).keywords("smart boost", "automatic", "auto", "tune", "fps", "optimize", "booster"));
    /** Smart Boost's continuous mode: adjusts the render distance alone while playing (off by default). */
    public static final Setting<Boolean> PERFORMANCE_SMART_BOOST_ADAPTIVE = add(Setting.bool(
            "performance.smartBoostAdaptive", PERFORMANCE, false)
            .keywords("smart boost", "render distance", "dynamic", "adaptive", "chunks", "fps"));
    /** Last applied performance preset. */
    public static final Setting<PerformancePreset> PERFORMANCE_PRESET = add(Setting.enumOf("performance.perfPreset",
            PERFORMANCE, PerformancePreset.class, PerformancePreset.BALANCED)
            .keywords("boost", "max fps", "low", "balanced", "high", "ultra", "quality", "preset", "fps"));
    /** Button: open the Performance Center. */
    public static final Setting<String> PERFORMANCE_OPEN_CENTER = add(Setting.action("performance.openCenter",
            PERFORMANCE, "open_performance").keywords("fps", "monitor", "memory"));
    /**
     * Offer the Performance pack once per game start when members are missing (see {@code PackOffer}). "Not now" in
     * that dialog turns this off.
     */
    public static final Setting<Boolean> MODS_PACK_OFFER = add(Setting.bool("performance.offerPack", PERFORMANCE, true)
            .keywords("performance pack", "sodium", "mods", "modrinth", "boost", "offer", "prompt", "start"));

    // ---- accessibility ----------------------------------------------------------------------------------------------
    /** Disable animations and transitions in VANTA screens. */
    public static final Setting<Boolean> ACCESSIBILITY_REDUCED_MOTION = add(Setting.bool(
            "accessibility.reducedMotion", ACCESSIBILITY, false).keywords("animation", "motion", "transitions"));
    /** Stronger borders and text contrast in VANTA screens. */
    public static final Setting<Boolean> ACCESSIBILITY_HIGH_CONTRAST = add(Setting.bool("accessibility.highContrast",
            ACCESSIBILITY, false).keywords("contrast", "visibility", "borders"));
    /** Larger text in VANTA screens. */
    public static final Setting<Boolean> ACCESSIBILITY_LARGE_TEXT = add(Setting.bool("accessibility.largeText",
            ACCESSIBILITY, false).keywords("font size", "big text", "readability"));
    /** Opaque panels instead of translucent ones. */
    public static final Setting<Boolean> ACCESSIBILITY_REDUCED_TRANSPARENCY = add(Setting.bool(
            "accessibility.reducedTransparency", ACCESSIBILITY, false).keywords("opaque", "blur", "glass", "alpha"));
    /** Colour-blind friendly palette for status colours. */
    public static final Setting<ColorBlindPalette> ACCESSIBILITY_COLOR_BLIND_PALETTE = add(Setting.enumOf(
            "accessibility.colorBlindPalette", ACCESSIBILITY, ColorBlindPalette.class, ColorBlindPalette.NONE)
            .keywords("color blind", "deuteranopia", "protanopia", "tritanopia", "colours"));
    /** Button: open the vanilla accessibility screen. */
    public static final Setting<String> ACCESSIBILITY_OPEN_VANILLA = add(Setting.action("accessibility.openVanilla",
            ACCESSIBILITY, "open_vanilla_accessibility").keywords("narrator", "subtitles", "vanilla"));

    // ---- privacy ------------------------------------------------------------------------------------------------------
    /** Record local statistics at all. */
    public static final Setting<Boolean> PRIVACY_STATS_ENABLED = add(Setting.bool("privacy.statsEnabled", PRIVACY, true)
            .keywords("statistics", "tracking", "playtime", "data"));
    /** Remember the hostnames of servers you join (local only). */
    public static final Setting<Boolean> PRIVACY_STATS_TRACK_SERVERS = add(Setting.bool("privacy.statsTrackServers",
            PRIVACY, true).keywords("servers", "history", "hostnames"));
    /** Remember the names of worlds you play (local only). */
    public static final Setting<Boolean> PRIVACY_STATS_TRACK_WORLDS = add(Setting.bool("privacy.statsTrackWorlds",
            PRIVACY, true).keywords("worlds", "history", "saves"));
    /** Button: delete all statistics. */
    public static final Setting<String> PRIVACY_CLEAR_STATS = add(Setting.action("privacy.clearStats", PRIVACY,
            "clear_statistics").keywords("delete", "erase", "statistics"));

    // ---- nexus (Vanta Nexus assistant and the Local AI) ------------------------------------------------------------------
    /** Master switch of the Nexus assistant (the Local AI answers only when this is on). */
    public static final Setting<Boolean> NEXUS_ENABLED = add(Setting.bool("nexus.enabled", NEXUS, true)
            .keywords("nexus", "assistant", "ai", "local ai", "chat", "llama"));
    /** Offer the Local AI install card when Nexus opens without an install (the download still needs a click). */
    public static final Setting<Boolean> NEXUS_AUTO_INSTALL = add(Setting.bool("nexus.autoInstall", NEXUS, true)
            .keywords("nexus", "local ai", "install", "download", "offer", "first start"));
    /** Minutes without a question after which the llama-server process stops (restarts on demand). */
    public static final Setting<Integer> NEXUS_IDLE_TIMEOUT_MINUTES = add(Setting.intRange("nexus.idleTimeoutMinutes",
            NEXUS, 10, 1, 120, 1).keywords("nexus", "local ai", "idle", "timeout", "stop", "ram", "memory"));
    /** CPU threads for llama-server; 0 = automatic (max(2, min(8, cores - 2))). */
    public static final Setting<Integer> NEXUS_THREADS = add(Setting.intRange("nexus.threads", NEXUS, 0, 0, 32, 1)
            .keywords("nexus", "local ai", "threads", "cpu", "cores", "auto"));
    /** Never stop llama-server for idleness while the game runs. */
    public static final Setting<Boolean> NEXUS_KEEP_RUNNING = add(Setting.bool("nexus.keepRunning", NEXUS, false)
            .keywords("nexus", "local ai", "keep running", "always on", "ram"));
    /** Show the model's reasoning text in the transcript when the server returns any. */
    public static final Setting<Boolean> NEXUS_SHOW_THINKING = add(Setting.bool("nexus.showThinking", NEXUS, false)
            .keywords("nexus", "thinking", "reasoning", "debug", "model"));

    // ---- cosmetics ----------------------------------------------------------------------------------------------------
    /** Visual-only profile badge. */
    public static final Setting<Badge> COSMETICS_BADGE = add(Setting.enumOf("cosmetics.badge", COSMETICS, Badge.class,
            Badge.NONE).keywords("badge", "founder", "contributor", "supporter", "icon"));
    /** HUD widget style. */
    public static final Setting<HudTheme> COSMETICS_HUD_THEME = add(Setting.enumOf("cosmetics.hudTheme", COSMETICS,
            HudTheme.class, HudTheme.CLEAN).keywords("hud", "glass", "outline", "minimal", "style"));
    /** Id of the active crosshair preset (see {@code CrosshairPresets}). */
    public static final Setting<String> COSMETICS_CROSSHAIR_PRESET = add(Setting.string("cosmetics.crosshairPreset",
            COSMETICS, "default").keywords("crosshair", "reticle", "dot", "circle"));
    /** Button: open the crosshair customizer. */
    public static final Setting<String> COSMETICS_OPEN_CROSSHAIR = add(Setting.action("cosmetics.openCrosshair",
            COSMETICS, "open_crosshair").keywords("crosshair", "reticle", "customize"));

    // ---- controls: zoom ---------------------------------------------------------------------------------------------
    /** Enable the zoom key (temporarily narrows the field of view, like a spyglass without the item). */
    public static final Setting<Boolean> ZOOM_ENABLED = add(Setting.bool("zoom.enabled", CONTROLS, true)
            .keywords("zoom", "fov", "spyglass", "magnify"));
    /** Zoom factor. */
    public static final Setting<Double> ZOOM_FACTOR = add(Setting.doubleRange("zoom.factor", CONTROLS, 3.0, 1.5, 8.0,
            0.5).keywords("zoom", "magnification", "strength"));
    /** Zoom key (mirrors the {@code key.vanta.zoom} key mapping). */
    public static final Setting<String> ZOOM_KEY = add(Setting.key("zoom.key", CONTROLS, "key.keyboard.c")
            .keywords("zoom", "key", "bind"));
    /** Animate the zoom transition. */
    public static final Setting<Boolean> ZOOM_SMOOTH = add(Setting.bool("zoom.smooth", CONTROLS, true)
            .keywords("zoom", "animation", "smooth", "camera"));
    /** Adjust the zoom level with the mouse wheel while zooming. */
    public static final Setting<Boolean> ZOOM_SCROLL_ADJUST = add(Setting.bool("zoom.scrollAdjust", CONTROLS, true)
            .keywords("zoom", "scroll", "wheel"));
    /** Button: open the keybind manager. */
    public static final Setting<String> CONTROLS_OPEN_KEYBINDS = add(Setting.action("controls.openKeybinds", CONTROLS,
            "open_keybinds").keywords("keys", "bindings", "controls", "hotkeys"));

    // ---- vanilla-bound: video ---------------------------------------------------------------------------------------
    public static final Setting<?> VIDEO_GRAPHICS_MODE = vanilla("video.graphicsMode", VIDEO, VanillaOption.GRAPHICS_MODE,
            "fast", "fancy", "fabulous", "quality");
    public static final Setting<?> VIDEO_RENDER_DISTANCE = vanilla("video.renderDistance", VIDEO,
            VanillaOption.RENDER_DISTANCE, "chunks", "view distance", "fps");
    public static final Setting<?> VIDEO_SIMULATION_DISTANCE = vanilla("video.simulationDistance", VIDEO,
            VanillaOption.SIMULATION_DISTANCE, "chunks", "ticking", "mobs", "redstone");
    public static final Setting<?> VIDEO_FRAMERATE_LIMIT = vanilla("video.framerateLimit", VIDEO,
            VanillaOption.FRAMERATE_LIMIT, "fps", "max fps", "frame rate", "cap");
    public static final Setting<?> VIDEO_VSYNC = vanilla("video.vsync", VIDEO, VanillaOption.VSYNC, "tearing",
            "refresh rate", "fps");
    public static final Setting<?> VIDEO_FOV = vanilla("video.fov", VIDEO, VanillaOption.FOV, "field of view", "camera",
            "quake pro");
    public static final Setting<?> VIDEO_GUI_SCALE = vanilla("video.guiScale", VIDEO, VanillaOption.GUI_SCALE,
            "interface size", "gui", "scale");
    public static final Setting<?> VIDEO_PARTICLES = vanilla("video.particles", VIDEO, VanillaOption.PARTICLES,
            "effects", "minimal", "decreased");
    public static final Setting<?> VIDEO_CLOUDS = vanilla("video.clouds", VIDEO, VanillaOption.CLOUDS, "sky", "fast",
            "fancy");
    public static final Setting<?> VIDEO_SMOOTH_LIGHTING = vanilla("video.smoothLighting", VIDEO,
            VanillaOption.SMOOTH_LIGHTING, "ambient occlusion", "shading", "light");
    public static final Setting<?> VIDEO_ENTITY_SHADOWS = vanilla("video.entityShadows", VIDEO,
            VanillaOption.ENTITY_SHADOWS, "shadows", "mobs", "players");
    public static final Setting<?> VIDEO_ENTITY_DISTANCE = vanilla("video.entityDistance", VIDEO,
            VanillaOption.ENTITY_DISTANCE_SCALING, "entities", "mobs", "render", "distance");
    public static final Setting<?> VIDEO_BIOME_BLEND = vanilla("video.biomeBlend", VIDEO, VanillaOption.BIOME_BLEND,
            "grass color", "biome", "blend", "transition");
    public static final Setting<?> VIDEO_MIPMAP_LEVELS = vanilla("video.mipmapLevels", VIDEO,
            VanillaOption.MIPMAP_LEVELS, "textures", "flicker", "distance", "quality");
    public static final Setting<?> VIDEO_VIEW_BOBBING = vanilla("video.viewBobbing", VIDEO, VanillaOption.VIEW_BOBBING,
            "camera", "walk", "motion sickness");
    public static final Setting<?> VIDEO_MENU_BLUR = vanilla("video.menuBlur", VIDEO, VanillaOption.MENU_BLUR,
            "blur", "menu background", "pause");
    public static final Setting<?> VIDEO_INACTIVITY_FPS_LIMIT = vanilla("video.inactivityFpsLimit", VIDEO,
            VanillaOption.INACTIVITY_FPS_LIMIT, "afk", "minimized", "background", "fps");
    public static final Setting<?> VIDEO_SCREEN_EFFECT_SCALE = vanilla("video.screenEffectScale", VIDEO,
            VanillaOption.SCREEN_EFFECT_SCALE, "distortion", "nausea", "nether portal", "effects");
    public static final Setting<?> VIDEO_FOV_EFFECT_SCALE = vanilla("video.fovEffectScale", VIDEO,
            VanillaOption.FOV_EFFECT_SCALE, "speed", "fov change", "sprint");
    public static final Setting<?> VIDEO_GLINT_SPEED = vanilla("video.glintSpeed", VIDEO, VanillaOption.GLINT_SPEED,
            "enchantment", "shine", "glint");
    public static final Setting<?> VIDEO_GLINT_STRENGTH = vanilla("video.glintStrength", VIDEO,
            VanillaOption.GLINT_STRENGTH, "enchantment", "shine", "glint");
    public static final Setting<?> VIDEO_AUTOSAVE_INDICATOR = vanilla("video.autosaveIndicator", VIDEO,
            VanillaOption.AUTOSAVE_INDICATOR, "saving", "indicator", "icon");
    public static final Setting<?> VIDEO_PANORAMA_SPEED = vanilla("video.panoramaSpeed", VIDEO,
            VanillaOption.PANORAMA_SPEED, "title screen", "rotation", "panorama");
    public static final Setting<?> VIDEO_DARK_LOADING_SCREEN = vanilla("video.darkLoadingScreen", VIDEO,
            VanillaOption.DARK_LOADING_SCREEN, "mojang", "loading", "dark mode");
    /** Button: open the vanilla video settings screen. */
    public static final Setting<String> VIDEO_OPEN_VANILLA = add(Setting.action("video.openVanilla", VIDEO,
            "open_vanilla_video").keywords("vanilla", "video settings", "fullscreen", "resolution"));

    // ---- vanilla-bound: audio ---------------------------------------------------------------------------------------
    public static final Setting<?> AUDIO_MASTER = vanilla("audio.master", AUDIO, VanillaOption.MASTER_VOLUME, "volume",
            "sound", "mute");
    public static final Setting<?> AUDIO_MUSIC = vanilla("audio.music", AUDIO, VanillaOption.MUSIC_VOLUME, "volume",
            "music", "soundtrack");
    public static final Setting<?> AUDIO_RECORDS = vanilla("audio.records", AUDIO, VanillaOption.RECORDS_VOLUME,
            "jukebox", "note blocks", "discs");
    public static final Setting<?> AUDIO_WEATHER = vanilla("audio.weather", AUDIO, VanillaOption.WEATHER_VOLUME, "rain",
            "thunder");
    public static final Setting<?> AUDIO_BLOCKS = vanilla("audio.blocks", AUDIO, VanillaOption.BLOCKS_VOLUME, "blocks",
            "footsteps", "mining");
    public static final Setting<?> AUDIO_HOSTILE = vanilla("audio.hostile", AUDIO, VanillaOption.HOSTILE_VOLUME, "mobs",
            "monsters", "creatures");
    public static final Setting<?> AUDIO_NEUTRAL = vanilla("audio.neutral", AUDIO, VanillaOption.NEUTRAL_VOLUME,
            "animals", "friendly", "creatures");
    public static final Setting<?> AUDIO_PLAYERS = vanilla("audio.players", AUDIO, VanillaOption.PLAYERS_VOLUME,
            "players", "footsteps", "hurt");
    public static final Setting<?> AUDIO_AMBIENT = vanilla("audio.ambient", AUDIO, VanillaOption.AMBIENT_VOLUME,
            "ambient", "environment", "cave sounds");
    public static final Setting<?> AUDIO_VOICE = vanilla("audio.voice", AUDIO, VanillaOption.VOICE_VOLUME, "voice",
            "speech", "narration");
    public static final Setting<?> AUDIO_SUBTITLES = vanilla("audio.subtitles", AUDIO, VanillaOption.SHOW_SUBTITLES,
            "captions", "deaf", "accessibility");
    /** Button: open the vanilla audio settings screen. */
    public static final Setting<String> AUDIO_OPEN_VANILLA = add(Setting.action("audio.openVanilla", AUDIO,
            "open_vanilla_audio").keywords("vanilla", "sound settings", "device"));

    // ---- vanilla-bound: controls ------------------------------------------------------------------------------------
    public static final Setting<?> CONTROLS_MOUSE_SENSITIVITY = vanilla("controls.mouseSensitivity", CONTROLS,
            VanillaOption.MOUSE_SENSITIVITY, "mouse", "sensitivity", "aim", "speed");
    public static final Setting<?> CONTROLS_INVERT_MOUSE = vanilla("controls.invertMouse", CONTROLS,
            VanillaOption.INVERT_MOUSE, "mouse", "invert", "y axis");
    public static final Setting<?> CONTROLS_MOUSE_WHEEL_SENSITIVITY = vanilla("controls.mouseWheelSensitivity",
            CONTROLS, VanillaOption.MOUSE_WHEEL_SENSITIVITY, "scroll", "hotbar", "wheel");
    public static final Setting<?> CONTROLS_DISCRETE_SCROLL = vanilla("controls.discreteScroll", CONTROLS,
            VanillaOption.DISCRETE_SCROLL, "scroll", "hotbar", "wheel", "touchpad");
    public static final Setting<?> CONTROLS_RAW_MOUSE_INPUT = vanilla("controls.rawMouseInput", CONTROLS,
            VanillaOption.RAW_MOUSE_INPUT, "mouse", "acceleration", "raw input");
    public static final Setting<?> CONTROLS_AUTO_JUMP = vanilla("controls.autoJump", CONTROLS, VanillaOption.AUTO_JUMP,
            "jump", "automatic", "movement");
    public static final Setting<?> CONTROLS_TOGGLE_SNEAK = vanilla("controls.toggleSneak", CONTROLS,
            VanillaOption.TOGGLE_SNEAK, "sneak", "crouch", "toggle", "hold");
    public static final Setting<?> CONTROLS_TOGGLE_SPRINT = vanilla("controls.toggleSprint", CONTROLS,
            VanillaOption.TOGGLE_SPRINT, "sprint", "run", "toggle", "hold");

    // ---- vanilla-bound: accessibility -------------------------------------------------------------------------------
    public static final Setting<?> ACCESSIBILITY_VANILLA_HIGH_CONTRAST = vanilla("accessibility.vanillaHighContrast",
            ACCESSIBILITY, VanillaOption.HIGH_CONTRAST, "contrast", "vanilla", "resource pack");
    public static final Setting<?> ACCESSIBILITY_TEXT_BACKGROUND_OPACITY = vanilla(
            "accessibility.textBackgroundOpacity", ACCESSIBILITY, VanillaOption.TEXT_BACKGROUND_OPACITY, "chat",
            "text background", "readability");
    public static final Setting<?> ACCESSIBILITY_CHAT_OPACITY = vanilla("accessibility.chatOpacity", ACCESSIBILITY,
            VanillaOption.CHAT_OPACITY, "chat", "opacity", "transparency");

    // ---- language -----------------------------------------------------------------------------------------------------
    /** Button: open the vanilla language screen (VANTA strings follow the game language). */
    public static final Setting<String> LANGUAGE_OPEN_VANILLA = add(Setting.action("language.openVanilla", LANGUAGE,
            "open_vanilla_language").keywords("language", "translation", "locale", "english"));
    /** Show VANTA translation keys instead of text (for translators). */
    public static final Setting<Boolean> LANGUAGE_SHOW_KEYS = add(Setting.bool("language.showKeys", LANGUAGE, false)
            .keywords("translation", "keys", "debug", "translators"));

    private VantaSettings() {
    }

    private static <T> Setting<T> add(Setting<T> setting) {
        ALL.add(setting);
        return setting;
    }

    private static Setting<?> vanilla(String id, SettingCategory category, VanillaOption option, String... keywords) {
        return add(Setting.vanilla(id, category, option).keywords(keywords));
    }

    /** Every setting in definition order. */
    public static List<Setting<?>> all() {
        return Collections.unmodifiableList(ALL);
    }

    /** A fresh registry containing every setting. */
    public static SettingsRegistry registry() {
        SettingsRegistry registry = new SettingsRegistry();
        registry.registerAll(ALL);
        return registry;
    }

    /** Number of settings. */
    public static int count() {
        return ALL.size();
    }
}
