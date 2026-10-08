package dev.vanta.core.screen;

import dev.vanta.core.VantaVersion;
import dev.vanta.core.accessibility.AccessibilityService;
import dev.vanta.core.ai.LabToggle;
import dev.vanta.core.ai.LocalAiService;
import dev.vanta.core.ai.NexusActions;
import dev.vanta.core.ai.NexusAssistant;
import dev.vanta.core.ai.NexusTranscript;
import dev.vanta.core.ai.NexusUndo;
import dev.vanta.core.ai.NexusWaypointActions;
import dev.vanta.core.ai.WaypointLookup;
import dev.vanta.core.bridge.ClipboardBridge;
import dev.vanta.core.bridge.GameBridge;
import dev.vanta.core.bridge.KeybindBridge;
import dev.vanta.core.bridge.OptionsBridge;
import dev.vanta.core.bridge.ResourcePackBridge;
import dev.vanta.core.bridge.ScreenshotBridge;
import dev.vanta.core.bridge.VanillaScreen;
import dev.vanta.core.config.CoreLog;
import dev.vanta.core.config.JsonStore;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.cosmetics.CosmeticsRegistry;
import dev.vanta.core.cosmetics.CosmeticsStore;
import dev.vanta.core.cosmetics.MenuBackground;
import dev.vanta.core.cosmetics.MenuParticles;
import dev.vanta.core.crosshair.CrosshairStore;
import dev.vanta.core.hud.HudStore;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.keybinds.KeybindModel;
import dev.vanta.core.modrinth.LocalItem;
import dev.vanta.core.modrinth.ModrinthService;
import dev.vanta.core.modrinth.PackOffer;
import dev.vanta.core.modrinth.PerformancePack;
import dev.vanta.core.notifications.NotificationCenter;
import dev.vanta.core.perf.FpsLimitPreset;
import dev.vanta.core.perf.PerformanceCenter;
import dev.vanta.core.perf.PerformancePreset;
import dev.vanta.core.perf.SmartBoostTuner;
import dev.vanta.core.perf.SystemInfo;
import dev.vanta.core.profiles.Profile;
import dev.vanta.core.profiles.ProfileManager;
import dev.vanta.core.search.ActionEntry;
import dev.vanta.core.search.GlobalSearch;
import dev.vanta.core.settings.SettingsRegistry;
import dev.vanta.core.settings.SettingsStore;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.stats.StatsStore;
import dev.vanta.core.stats.StatsTracker;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;

/**
 * Composition root of the domain layer. The client creates one instance at start-up with its bridge implementations,
 * calls {@link #load()}, then hands the services to the screens; {@link #saveAll()} runs on a timer / when screens
 * close and {@link #shutdown()} when the game stops.
 */
public final class VantaServices {
    private final Clock clock;
    private final VantaPaths paths;
    private final JsonStore jsonStore;
    private final GameBridge game;
    private final OptionsBridge options;
    private final KeybindBridge keybindBridge;
    private final ResourcePackBridge resourcePacks;
    private final Optional<ScreenshotBridge> screenshots;
    private final Optional<ClipboardBridge> clipboard;
    private final SettingsRegistry settingsRegistry;
    private final SettingsStore settings;
    private final HudStore hud;
    private final CrosshairStore crosshair;
    private final CosmeticsRegistry cosmeticsRegistry;
    private final CosmeticsStore cosmetics;
    private final NotificationCenter notifications;
    private final StatsStore statsStore;
    private final StatsTracker stats;
    private final PerformanceCenter performance;
    private final SmartBoostTuner smartBoost;
    private final KeybindModel keybinds;
    private final ProfileManager profiles;
    private final AccessibilityService accessibility;
    private final GlobalSearch search;
    private final TickQueue mainThreadQueue = new TickQueue();
    private volatile Executor mainThread;
    private final LocalAiService localAi;
    private final NexusActions nexusActions;
    private final NexusUndo nexusUndo;
    private final NexusTranscript nexusTranscript;
    private final NexusAssistant nexus;
    private final ScreenRegistry screens = new ScreenRegistry();
    private Optional<String> websiteUrl = Optional.empty();
    private ModrinthService modrinth;
    private PackOffer packOffer;
    private boolean loaded;

    private VantaServices(VantaPaths paths, GameBridge game, OptionsBridge options, KeybindBridge keybindBridge,
                          ResourcePackBridge resourcePacks, Optional<ScreenshotBridge> screenshots,
                          Optional<ClipboardBridge> clipboard, Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.paths = Objects.requireNonNull(paths, "paths");
        this.game = Objects.requireNonNull(game, "game");
        this.options = Objects.requireNonNull(options, "options");
        this.keybindBridge = Objects.requireNonNull(keybindBridge, "keybindBridge");
        this.resourcePacks = Objects.requireNonNull(resourcePacks, "resourcePacks");
        this.screenshots = Objects.requireNonNull(screenshots, "screenshots");
        this.clipboard = Objects.requireNonNull(clipboard, "clipboard");
        this.jsonStore = new JsonStore(clock);
        this.settingsRegistry = VantaSettings.registry();
        this.settings = new SettingsStore(settingsRegistry, jsonStore, paths.settingsFile(), Optional.of(options));
        this.hud = new HudStore(jsonStore, paths);
        this.crosshair = new CrosshairStore(jsonStore, paths);
        this.cosmeticsRegistry = new CosmeticsRegistry();
        this.cosmetics = new CosmeticsStore(settings, cosmeticsRegistry, jsonStore, paths);
        this.notifications = new NotificationCenter(clock,
                () -> settings.get(VantaSettings.GENERAL_NOTIFICATION_DURATION));
        this.statsStore = new StatsStore(jsonStore, paths);
        this.stats = new StatsTracker(statsStore, settings, clock);
        this.performance = new PerformanceCenter(game, options, settings, notifications, clock);
        // Smart Boost's automatic run stays off in the client game test and with -Dvanta.smartBoost.auto=false; the
        // explicit Re-tune always works.
        this.smartBoost = new SmartBoostTuner(performance, game, options, settings, notifications, jsonStore,
                paths.smartBoostFile(), SystemInfo.current(),
                SmartBoostTuner.autoAllowedByProperty() && !PackOffer.inGameTest());
        performance.attachSmartBoost(smartBoost);
        this.keybinds = new KeybindModel(keybindBridge);
        this.profiles = new ProfileManager(jsonStore, paths, clock, settings, hud, crosshair, keybindBridge);
        this.accessibility = new AccessibilityService(settings);
        this.search = new GlobalSearch(settingsRegistry, ActionEntry.builtIns(), keybinds::all);
        // Vanta Nexus and the Local AI: background work on the Local AI worker, every result back through
        // mainThread() (the client installs its scheduler; until then the queue drained in tick()).
        this.mainThread = mainThreadQueue;
        this.localAi = new LocalAiService(paths, settings, notifications, this::runOnMainThread, clock,
                LocalAiService.Dependencies.system(VantaVersion.CLIENT,
                        java.lang.Runtime.getRuntime().availableProcessors()));
        this.nexusActions = new NexusActions(hud, settings, profiles, this::activateProfile, performance,
                smartBoost::retune);
        this.nexusUndo = new NexusUndo(hud, settings, profiles, this::activateProfile);
        this.nexusTranscript = new NexusTranscript(jsonStore, paths.nexusChatFile());
        this.nexus = new NexusAssistant(nexusActions, nexusUndo, nexusTranscript, localAi, hud, profiles, performance,
                clock);
    }

    /** Runs {@code task} on the render thread: through the host's executor, or the queue drained by {@link #tick()}. */
    private void runOnMainThread(Runnable task) {
        mainThread.execute(task);
    }

    /** Executor queue drained once per tick (default main-thread executor for hosts without a scheduler). */
    private static final class TickQueue implements Executor {
        private final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();

        @Override
        public void execute(Runnable task) {
            queue.add(Objects.requireNonNull(task, "task"));
        }

        void drain() {
            Runnable task;
            int budget = 1000;
            while (budget-- > 0 && (task = queue.poll()) != null) {
                try {
                    task.run();
                } catch (RuntimeException e) {
                    CoreLog.warn(e, "A main-thread task failed");
                }
            }
        }
    }

    /** Creates the services with the four mandatory bridges. */
    public static VantaServices create(VantaPaths paths, GameBridge game, OptionsBridge options, KeybindBridge keybinds,
                                       ResourcePackBridge resourcePacks, Clock clock) {
        return new VantaServices(paths, game, options, keybinds, resourcePacks, Optional.empty(), Optional.empty(),
                clock);
    }

    /** Creates the services with every bridge. */
    public static VantaServices create(VantaPaths paths, GameBridge game, OptionsBridge options, KeybindBridge keybinds,
                                       ResourcePackBridge resourcePacks, ScreenshotBridge screenshots,
                                       ClipboardBridge clipboard, Clock clock) {
        return new VantaServices(paths, game, options, keybinds, resourcePacks, Optional.of(screenshots),
                Optional.of(clipboard), clock);
    }

    // ---- lifecycle -----------------------------------------------------------------------------------------------

    /** Creates directories and loads every store. Safe to call once. */
    public void load() {
        try {
            paths.createDirectories();
        } catch (IOException e) {
            CoreLog.warn(e, "Could not create the VANTA config directories under {}", paths.root());
        }
        settings.load();
        accessibility.refresh();
        hud.load();
        crosshair.load();
        cosmetics.load();
        statsStore.load();
        profiles.load();
        smartBoost.load();
        nexusTranscript.load();
        localAi.load();
        keybinds.refresh();
        search.rebuild();
        stats.startSession();
        loaded = true;
        CoreLog.info("VANTA services loaded from {} ({} settings, {} profiles)", paths.root(), settingsRegistry.size(),
                profiles.size());
    }

    /**
     * Writes every dirty store. Runs on the render thread whenever a VANTA screen closes (also on the Escape back into
     * the game), so every store writes only what changed: with nothing changed it touches no file.
     */
    public void saveAll() {
        settings.saveIfDirty();
        hud.saveIfDirty();
        crosshair.saveIfDirty();
        statsStore.saveIfDirty();
        profiles.saveAll();
        cosmetics.saveIfDirty();
        smartBoost.saveIfDirty();
        nexusTranscript.saveIfDirty();
    }

    /** Ends the statistics session, saves everything and stops the Local AI server. */
    public void shutdown() {
        stats.endSession();
        saveAll();
        if (modrinth != null) {
            modrinth.close();
        }
        localAi.close();
        loaded = false;
    }

    /** Once per client tick. */
    public void tick() {
        mainThreadQueue.drain();
        notifications.tick();
        stats.onTick(game);
        performance.tick();
        localAi.tick();
    }

    /** True after {@link #load()}. */
    public boolean isLoaded() {
        return loaded;
    }

    // ---- actions -------------------------------------------------------------------------------------------------

    /**
     * Executes a command action by id (see {@link ActionEntry}). Navigation actions are not handled here: the host
     * opens the screen from {@link ActionEntry#screen()}.
     *
     * @return true when the id was recognised and executed
     */
    public boolean runAction(String actionId) {
        switch (actionId) {
            case ActionEntry.RESET_SETTINGS -> {
                settings.resetAll();
                // The vanilla defaults (120 FPS, VSync on) and the frame-rate choice's default (Unlimited) disagree;
                // the choice is the source of truth, so it is applied last.
                performance.applyFpsLimit(settings.get(VantaSettings.PERFORMANCE_FPS_LIMIT_PRESET));
                settings.save();
                notifications.settingsSaved();
                return true;
            }
            case ActionEntry.OPEN_CONFIG_FOLDER -> {
                game.openUrl(paths.root().toUri().toString());
                return true;
            }
            case ActionEntry.EXPORT_PROFILE -> {
                Optional<Profile> active = profiles.active();
                if (active.isEmpty()) {
                    return false;
                }
                Path target = paths.root().resolve("exports").resolve(ProfileManager.exportFileName(active.get()));
                profiles.exportTo(active.get().id(), target);
                notifications.profileExported(target);
                return true;
            }
            case ActionEntry.SCREENSHOT_HUD_FREE -> {
                if (screenshots.isEmpty()) {
                    return false;
                }
                screenshots.get().capture(true, saved -> {
                    saved.ifPresent(notifications::screenshotSaved);
                    saved.ifPresent(p -> stats.onScreenshot());
                });
                return true;
            }
            case ActionEntry.TOGGLE_HUD -> {
                boolean next = !settings.get(VantaSettings.HUD_ENABLED);
                settings.set(VantaSettings.HUD_ENABLED, next);
                notifications.hudToggled(next);
                return true;
            }
            case ActionEntry.APPLY_BALANCED_PRESET -> {
                performance.applyPreset(PerformancePreset.BALANCED);
                return true;
            }
            case ActionEntry.BOOST_FPS -> {
                boostFps();
                return true;
            }
            case ActionEntry.SMART_BOOST_RETUNE -> {
                smartBoost.retune();
                return true;
            }
            case ActionEntry.SMART_BOOST_UNDO -> {
                smartBoost.undo();
                return true;
            }
            case ActionEntry.CLEAR_STATISTICS -> {
                statsStore.clearAll();
                notifications.statisticsCleared();
                return true;
            }
            case ActionEntry.OPEN_WEBSITE -> {
                game.openUrl(websiteUrl.orElse(VantaLinks.DOCUMENTATION));
                return true;
            }
            case "open_vanilla_video" -> {
                game.openVanillaScreen(VanillaScreen.VIDEO);
                return true;
            }
            case "open_vanilla_audio" -> {
                game.openVanillaScreen(VanillaScreen.AUDIO);
                return true;
            }
            case "open_vanilla_language" -> {
                game.openVanillaScreen(VanillaScreen.LANGUAGE);
                return true;
            }
            case "open_vanilla_accessibility" -> {
                game.openVanillaScreen(VanillaScreen.ACCESSIBILITY);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * The one-click "Boost FPS": applies {@link PerformancePreset#BOOST}, removes the frame-rate cap and turns VSync
     * off ({@link FpsLimitPreset#UNLIMITED}), switches the VANTA menu to a solid background without particles and,
     * when the Modrinth integration is installed, installs the Performance pack members that are neither loaded nor
     * installed (only those: a member Fabric already loaded from a jar Modrinth does not know is never downloaded a
     * second time). Nothing is ever downloaded except through this explicit action; the one confirmation toast says
     * when a restart is needed for the new mods. The settings part always runs; while a Modrinth task is still
     * running (the pack install of a second click, the main-menu offer's Install in the same session, or any other
     * download) no pack install is queued behind it: a toast says a download is still running, and the running
     * task's own toast reports its outcome.
     */
    public void boostFps() {
        performance.applyPreset(PerformancePreset.BOOST, false);
        performance.applyFpsLimit(FpsLimitPreset.UNLIMITED);
        settings.set(VantaSettings.MENU_BACKGROUND, MenuBackground.SOLID);
        settings.set(VantaSettings.MENU_PARTICLES, MenuParticles.NONE);
        List<PerformancePack.Item> missing = modrinth == null ? List.of() : missingPerformancePackMembers(modrinth);
        if (missing.isEmpty()) {
            notifications.boostApplied(modrinth != null && modrinth.restartRequired());
            return;
        }
        if (modrinth.isBusy()) {
            notifications.downloadStillRunning();
            return;
        }
        ModrinthService service = modrinth;
        service.installPerformancePack(missing.stream().map(PerformancePack.Item::slug).toList(), result -> {
            if (result.installed().isEmpty() && result.enabled().isEmpty() && !result.problems().isEmpty()) {
                return; // nothing landed; the install's own error toast already said so
            }
            notifications.boostApplied(result.modsChanged() || service.restartRequired());
        });
    }

    /** Performance pack members that are neither loaded in this game nor in the Modrinth index. */
    public static List<PerformancePack.Item> missingPerformancePackMembers(ModrinthService service) {
        Set<String> installedSlugs = new HashSet<>();
        for (LocalItem item : service.installedItems()) {
            item.entry().ifPresent(e -> installedSlugs.add(e.slug()));
        }
        List<PerformancePack.Item> missing = new ArrayList<>();
        for (PerformancePack.Item item : PerformancePack.ITEMS) {
            if (!service.platform().isModLoaded(item.modId()) && !installedSlugs.contains(item.slug())) {
                missing.add(item);
            }
        }
        return missing;
    }

    /** Activates a profile and shows the confirmation toast. */
    public boolean activateProfile(String id) {
        boolean ok = profiles.activate(id);
        if (ok) {
            // Options the profile changed now belong to the player; Smart Boost lets go of them.
            smartBoost.checkOwnership(clock.millis());
            profiles.find(id).ifPresent(p -> notifications.profileLoaded(p.name()));
            keybinds.refresh();
        }
        return ok;
    }

    /** Configures the public website URL (from the host's configuration; absent means "not configured"). */
    public void setWebsiteUrl(String url) {
        websiteUrl = url == null || url.isBlank() ? Optional.empty() : Optional.of(url.trim());
    }

    /** Public website URL when configured. */
    public Optional<String> websiteUrl() {
        return websiteUrl;
    }

    /**
     * Installs the Modrinth integration (the client creates it with the game directory and its main-thread executor;
     * tests and previews install one backed by an in-memory API). Without it the Mods &amp; Shaders screen explains
     * that downloads are unavailable.
     */
    public void setModrinth(ModrinthService service) {
        this.modrinth = service;
    }

    /** The Modrinth integration, when installed. */
    public Optional<ModrinthService> modrinth() {
        return Optional.ofNullable(modrinth);
    }

    /**
     * Installs the host's main-thread executor (the client passes {@code runtime.scheduler()::nextTick}). Background
     * work of the Local AI delivers its results through it; without one the results wait for the next {@link #tick()}.
     */
    public void setMainThreadExecutor(Executor executor) {
        this.mainThread = Objects.requireNonNull(executor, "executor");
    }

    /** The main-thread executor: callbacks handed to it run on the render thread. */
    public Executor mainThread() {
        return this::runOnMainThread;
    }

    /** Wires the waypoints store into the assistant (until then waypoint actions are refused). */
    public void setWaypoints(WaypointLookup lookup, NexusWaypointActions actions) {
        nexusActions.setWaypoints(lookup, actions);
    }

    /** Wires the Vanta Lab features into the assistant (until then lab actions are refused). */
    public void setLabToggle(LabToggle toggle) {
        nexusActions.setLab(toggle);
    }

    /** The one-time Performance pack offer of this session (created on first use). */
    public PackOffer packOffer() {
        if (packOffer == null) {
            packOffer = new PackOffer(this);
        }
        return packOffer;
    }

    /** Translated label line for the About screen: "VANTA Client 1.0.0 · Minecraft 1.21.11 · Fabric 0.19.5". */
    public String versionLine() {
        return Lang.tr("vanta.about.version_line", game.clientVersion(), game.minecraftVersion(),
                game.fabricLoaderVersion());
    }

    // ---- accessors -----------------------------------------------------------------------------------------------

    public Clock clock() {
        return clock;
    }

    public VantaPaths paths() {
        return paths;
    }

    public JsonStore jsonStore() {
        return jsonStore;
    }

    public GameBridge game() {
        return game;
    }

    public OptionsBridge options() {
        return options;
    }

    public KeybindBridge keybindBridge() {
        return keybindBridge;
    }

    public ResourcePackBridge resourcePacks() {
        return resourcePacks;
    }

    public Optional<ScreenshotBridge> screenshots() {
        return screenshots;
    }

    public Optional<ClipboardBridge> clipboard() {
        return clipboard;
    }

    public SettingsRegistry settingsRegistry() {
        return settingsRegistry;
    }

    public SettingsStore settings() {
        return settings;
    }

    public HudStore hud() {
        return hud;
    }

    public CrosshairStore crosshair() {
        return crosshair;
    }

    public CosmeticsRegistry cosmeticsRegistry() {
        return cosmeticsRegistry;
    }

    public CosmeticsStore cosmetics() {
        return cosmetics;
    }

    public NotificationCenter notifications() {
        return notifications;
    }

    public StatsStore statsStore() {
        return statsStore;
    }

    public StatsTracker stats() {
        return stats;
    }

    public PerformanceCenter performance() {
        return performance;
    }

    /** Smart Boost (automatic local video tuning). */
    public SmartBoostTuner smartBoost() {
        return smartBoost;
    }

    public KeybindModel keybinds() {
        return keybinds;
    }

    public ProfileManager profiles() {
        return profiles;
    }

    public AccessibilityService accessibility() {
        return accessibility;
    }

    public GlobalSearch search() {
        return search;
    }

    /** The Local AI: install state, installer, llama-server runtime. */
    public LocalAiService localAi() {
        return localAi;
    }

    /** The Vanta Nexus assistant. */
    public NexusAssistant nexus() {
        return nexus;
    }

    /** The validated actions the assistant (and the Nexus HUD Designer) can run. */
    public NexusActions nexusActions() {
        return nexusActions;
    }

    /** Undo of assistant turns. */
    public NexusUndo nexusUndo() {
        return nexusUndo;
    }

    /** The Nexus conversation. */
    public NexusTranscript nexusTranscript() {
        return nexusTranscript;
    }

    public ScreenRegistry screens() {
        return screens;
    }
}
