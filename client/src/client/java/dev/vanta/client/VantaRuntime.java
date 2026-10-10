package dev.vanta.client;

import dev.vanta.client.bridge.MinecraftGameBridge;
import dev.vanta.client.bridge.MinecraftResourcePackBridge;
import dev.vanta.client.bridge.MinecraftScreenshotBridge;
import dev.vanta.client.hud.FrameTimer;
import dev.vanta.client.keys.VantaKeyMappings;
import dev.vanta.client.lab.LabInputs;
import dev.vanta.client.render.GuiGraphicsCanvas;
import dev.vanta.client.screen.ThemeFactory;
import dev.vanta.client.zoom.ZoomController;
import dev.vanta.core.ai.LocalAiPaths;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.crosshair.render.CrosshairRenderer;
import dev.vanta.core.hud.render.HudRenderer;
import dev.vanta.core.keybinds.VantaKeys;
import dev.vanta.core.notifications.render.NotificationOverlay;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.ui.Clock;
import dev.vanta.core.ui.Theme;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Everything the client keeps alive for the lifetime of the game: the core services, the renderers that draw on top
 * of the game, the zoom state, key mappings, the Vanta Lab input relay and the tick scheduler. Created once by
 * {@link VantaClient}; mixins and HUD elements reach it through {@link #get()} and tolerate {@code null} before
 * initialisation.
 * <p>
 * The scheduler is also the services' main-thread executor: every Local AI result, Nexus reply and Modrinth answer
 * comes back through {@link ClientScheduler#nextTick} and runs on the render thread at the end of the next tick.
 */
public final class VantaRuntime {
    /**
     * Environment variable of the client game test: the absolute path of a prepared Local AI install (runtime, model
     * and {@code installed.json} as the launcher writes them). When it names an existing directory the client writes
     * the launcher note {@code config/vanta/local-ai.json} before the services load, so the game uses that install
     * read-only exactly as it would a launcher-managed one.
     */
    public static final String LOCAL_AI_DIR_ENV = "VANTA_LOCAL_AI_DIR";

    private static volatile VantaRuntime instance;

    private final VantaServices services;
    private final MinecraftResourcePackBridge resourcePacks;
    private final MinecraftScreenshotBridge screenshots;
    private final VantaKeyMappings keys;
    private final ClientScheduler scheduler = new ClientScheduler();
    private final Clock uiClock = Clock.system();
    private final HudRenderer hud;
    private final CrosshairRenderer crosshair;
    private final NotificationOverlay notifications;
    private final FrameTimer frames;
    private final ZoomController zoom;
    private final LabInputs labInputs;
    private volatile Theme theme;

    VantaRuntime(VantaServices services, MinecraftResourcePackBridge resourcePacks,
                 MinecraftScreenshotBridge screenshots, VantaKeyMappings keys) {
        this.services = Objects.requireNonNull(services, "services");
        this.resourcePacks = Objects.requireNonNull(resourcePacks, "resourcePacks");
        this.screenshots = Objects.requireNonNull(screenshots, "screenshots");
        this.keys = Objects.requireNonNull(keys, "keys");
        // Background work of the Local AI (downloads, llama-server, chat replies) delivers its results through this
        // executor; nothing asynchronous has started yet at this point, so no result can be queued elsewhere.
        services.setMainThreadExecutor(scheduler::nextTick);
        this.hud = new HudRenderer(services);
        this.crosshair = new CrosshairRenderer(services);
        this.notifications = new NotificationOverlay(services);
        this.frames = new FrameTimer(services.performance());
        this.zoom = new ZoomController(services.settings(), () -> keys.isDown(VantaKeys.ZOOM));
        this.labInputs = new LabInputs(services);
        this.theme = ThemeFactory.build(services);
        services.accessibility().listen(state -> refreshTheme());
        services.cosmetics().onSelectionChanged(selection -> refreshTheme());
    }

    /** Publishes the runtime for mixins and HUD elements. */
    static void install(VantaRuntime runtime) {
        instance = runtime;
    }

    /** The runtime, or {@code null} before the client entrypoint finished. */
    public static VantaRuntime get() {
        return instance;
    }

    /**
     * Game test hook, called before {@code VantaServices.load()}: when {@value #LOCAL_AI_DIR_ENV} names an existing
     * directory, writes the launcher note so the Local AI service resolves that directory (read-only). Without the
     * variable nothing happens; a value that is not a directory is logged and ignored.
     *
     * @return the directory the note now points at, when one was written
     */
    static Optional<Path> prepareLocalAiNote(VantaPaths paths) {
        String value = System.getenv(LOCAL_AI_DIR_ENV);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        Path dir = Path.of(value.trim()).toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) {
            VantaClient.LOGGER.warn("{}={} is not a directory; the Local AI note is not written", LOCAL_AI_DIR_ENV,
                    value);
            return Optional.empty();
        }
        try {
            LocalAiPaths.writeNote(paths.localAiNoteFile(), dir);
            VantaClient.LOGGER.info("Local AI note {} written from {}: using the prepared install in {}",
                    paths.localAiNoteFile(), LOCAL_AI_DIR_ENV, dir);
            return Optional.of(dir);
        } catch (IOException e) {
            VantaClient.LOGGER.warn("Could not write the Local AI note {} for {}", paths.localAiNoteFile(), dir, e);
            return Optional.empty();
        }
    }

    // ---- lifecycle ---------------------------------------------------------------------------------------------

    /** The game is fully constructed: options and key mappings exist now. */
    public void onClientStarted() {
        // Smart Boost's starting guess uses the GPU name; it is read once here, on the render thread.
        if (services.game() instanceof MinecraftGameBridge bridge) {
            bridge.captureGpuRenderer();
            bridge.gpuRenderer().ifPresent(gpu -> VantaClient.LOGGER.info("GPU renderer: {}", gpu));
        }
        services.keybinds().refresh();
        services.search().rebuild();
        keys.syncZoomKey(services.settings());
        refreshTheme();
        // The one-time frame-rate uncap of client 1.5.0 (VSync off, Max Framerate Unlimited when the game folder still
        // has Minecraft's defaults); the options exist from here on. Its notification follows on the main menu.
        VantaClient.LOGGER.info("Frame-rate check at start-up: {}", services.onGameStarted());
    }

    /** Re-reads every configuration store from disk ({@code /vanta reload}). */
    public void reloadConfiguration() {
        services.settings().load();
        services.accessibility().refresh();
        services.hud().load();
        services.crosshair().load();
        services.cosmetics().load();
        services.profiles().load();
        services.keybinds().refresh();
        services.search().rebuild();
        refreshTheme();
    }

    /**
     * Ends the statistics session and writes every store (game stopping). {@link VantaServices#shutdown()} also closes
     * the Local AI service, which stops a running {@code llama-server} process and its worker thread.
     */
    public void shutdown() {
        services.shutdown();
    }

    // ---- theme -------------------------------------------------------------------------------------------------

    /** The theme VANTA screens render with. The instance is replaced only when the theme actually changes. */
    public Theme theme() {
        return theme;
    }

    /** Rebuilds the theme from cosmetics and accessibility settings; keeps the current instance when it is equal. */
    public void refreshTheme() {
        Theme next = ThemeFactory.build(services);
        if (!next.equals(theme)) {
            theme = next;
        }
    }

    // ---- rendering helpers -------------------------------------------------------------------------------------

    /** Draws the notification overlay on a vanilla screen. */
    public void renderNotifications(GuiGraphics graphics, int width, int height, float deltaTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        GuiGraphicsCanvas canvas = new GuiGraphicsCanvas(graphics, minecraft.font, width, height, false);
        try {
            notifications.render(canvas, width, height, deltaTicks);
        } finally {
            canvas.finish();
        }
    }

    /** FOV divisor for this frame (1.0 when not zooming); used by the {@code GameRendererMixin}. */
    public static double zoomFovDivisor() {
        VantaRuntime runtime = instance;
        return runtime == null ? 1.0 : runtime.zoom.update(System.nanoTime());
    }

    /**
     * Mouse wheel input while no screen is open; used by the {@code MouseHandlerMixin}. Every scroll counts as input
     * for the Vanta Lab (Dynamic HUD) before the zoom decides whether it consumes the wheel.
     *
     * @return true when the zoom consumed the scroll
     */
    public static boolean onMouseScroll(double scrollY) {
        VantaRuntime runtime = instance;
        if (runtime == null) {
            return false;
        }
        runtime.labInputs.onInput();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.screen != null || minecraft.player == null) {
            return false;
        }
        return runtime.zoom.onScroll(scrollY);
    }

    // ---- accessors ---------------------------------------------------------------------------------------------

    public VantaServices services() {
        return services;
    }

    public MinecraftResourcePackBridge resourcePacks() {
        return resourcePacks;
    }

    public MinecraftScreenshotBridge screenshots() {
        return screenshots;
    }

    public VantaKeyMappings keys() {
        return keys;
    }

    public ClientScheduler scheduler() {
        return scheduler;
    }

    /** Monotonic clock shared by all screens so animations continue across navigation. */
    public Clock uiClock() {
        return uiClock;
    }

    public HudRenderer hud() {
        return hud;
    }

    public CrosshairRenderer crosshair() {
        return crosshair;
    }

    public NotificationOverlay notifications() {
        return notifications;
    }

    public FrameTimer frames() {
        return frames;
    }

    public ZoomController zoom() {
        return zoom;
    }

    /** The Vanta Lab input relay (keys, mouse, movement, attacks to {@code LabEffects}). */
    public LabInputs labInputs() {
        return labInputs;
    }
}
