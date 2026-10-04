package dev.vanta.client;

import dev.vanta.client.bridge.MinecraftResourcePackBridge;
import dev.vanta.client.bridge.MinecraftScreenshotBridge;
import dev.vanta.client.hud.FrameTimer;
import dev.vanta.client.keys.VantaKeyMappings;
import dev.vanta.client.render.GuiGraphicsCanvas;
import dev.vanta.client.screen.ThemeFactory;
import dev.vanta.client.zoom.ZoomController;
import dev.vanta.core.crosshair.render.CrosshairRenderer;
import dev.vanta.core.hud.render.HudRenderer;
import dev.vanta.core.keybinds.VantaKeys;
import dev.vanta.core.notifications.render.NotificationOverlay;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.ui.Clock;
import dev.vanta.core.ui.Theme;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Everything the client keeps alive for the lifetime of the game: the core services, the renderers that draw on top
 * of the game, the zoom state, key mappings and the tick scheduler. Created once by {@link VantaClient}; mixins and HUD
 * elements reach it through {@link #get()} and tolerate {@code null} before initialisation.
 */
public final class VantaRuntime {
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
    private volatile Theme theme;

    VantaRuntime(VantaServices services, MinecraftResourcePackBridge resourcePacks,
                 MinecraftScreenshotBridge screenshots, VantaKeyMappings keys) {
        this.services = Objects.requireNonNull(services, "services");
        this.resourcePacks = Objects.requireNonNull(resourcePacks, "resourcePacks");
        this.screenshots = Objects.requireNonNull(screenshots, "screenshots");
        this.keys = Objects.requireNonNull(keys, "keys");
        this.hud = new HudRenderer(services);
        this.crosshair = new CrosshairRenderer(services);
        this.notifications = new NotificationOverlay(services);
        this.frames = new FrameTimer(services.performance());
        this.zoom = new ZoomController(services.settings(), () -> keys.isDown(VantaKeys.ZOOM));
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

    // ---- lifecycle ---------------------------------------------------------------------------------------------

    /** The game is fully constructed: options and key mappings exist now. */
    public void onClientStarted() {
        services.keybinds().refresh();
        services.search().rebuild();
        keys.syncZoomKey(services.settings());
        refreshTheme();
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

    /** Ends the statistics session and writes every store (game stopping). */
    public void shutdown() {
        services.shutdown();
    }

    // ---- theme -------------------------------------------------------------------------------------------------

    /** The theme VANTA screens render with. */
    public Theme theme() {
        return theme;
    }

    /** Rebuilds the theme from cosmetics and accessibility settings. */
    public void refreshTheme() {
        theme = ThemeFactory.build(services);
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
     * Mouse wheel input while no screen is open; used by the {@code MouseHandlerMixin}.
     *
     * @return true when the zoom consumed the scroll
     */
    public static boolean onMouseScroll(double scrollY) {
        VantaRuntime runtime = instance;
        if (runtime == null) {
            return false;
        }
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
}
