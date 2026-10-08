package dev.vanta.client.hud;

import dev.vanta.client.VantaRuntime;
import dev.vanta.client.render.WorldCamera;
import dev.vanta.client.screen.VantaScreen;
import dev.vanta.core.bridge.GameBridge;
import dev.vanta.core.bridge.Vec3d;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.settings.SettingsStore;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.waypoints.Waypoint;
import dev.vanta.core.waypoints.WaypointMarkers;
import dev.vanta.core.waypoints.WorldKeys;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Fabric HUD element drawing the screen-projected waypoint markers: for every enabled waypoint of the current world
 * and dimension within {@code waypoints.maxMarkerDistance}, a small diamond in the waypoint's colour at the projected
 * position (one block above the waypoint) and, below it, the label from {@link WaypointMarkers#label} (name, and the
 * distance when {@code waypoints.showDistance} is on), both scaled by {@code waypoints.markerScale}. The projection
 * uses the camera the world pass captured ({@link WorldCamera}: view matrix and position from the world render
 * extraction, field of view from the FOV hook); positions behind the camera are not drawn.
 * <p>
 * Nothing is drawn when {@code waypoints.enabled} is off, outside a world, while the GUI is hidden (F1), while the F3
 * debug overlay is visible, during a HUD-free screenshot, while a VANTA screen is open, or when no world frame was
 * rendered recently. Attached before the VANTA HUD element so the widgets draw over the markers.
 * <p>
 * {@link #drawnLastFrame()} reports how many markers the last call drew (client game test).
 */
public final class WaypointMarkerElement implements HudElement {
    /** Half size of the diamond at scale 1, in GUI pixels. */
    public static final int DIAMOND_RADIUS = 4;
    /** Height above the stored position the marker points at (the stored position is the player's feet). */
    public static final double MARKER_HEIGHT = 1.0;
    /** Compass span passed to the marker computation; only distance and label are used here. */
    private static final double COMPASS_SPAN_DEGREES = 90.0;
    private static final int LABEL_BACKGROUND = 0x99000000;
    private static final int LABEL_TEXT = 0xFFF5F5F7;
    private static final int OFFSCREEN_MARGIN = 64;

    private static volatile int drawnLastFrame;
    private static volatile long drawnTotal;

    /** Markers drawn by the most recent call (0 when the element was skipped). */
    public static int drawnLastFrame() {
        return drawnLastFrame;
    }

    /** Markers drawn since start. */
    public static long drawnTotal() {
        return drawnTotal;
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        int drawn = 0;
        try {
            drawn = renderMarkers(graphics);
        } finally {
            drawnLastFrame = drawn;
            drawnTotal += drawn;
        }
    }

    private static int renderMarkers(GuiGraphics graphics) {
        VantaRuntime runtime = VantaRuntime.get();
        Minecraft minecraft = Minecraft.getInstance();
        if (runtime == null || minecraft == null || minecraft.player == null || minecraft.level == null) {
            return 0;
        }
        if (minecraft.options.hideGui || minecraft.debugEntries.isOverlayVisible()
                || runtime.screenshots().isHudSuppressed() || minecraft.screen instanceof VantaScreen) {
            return 0;
        }
        VantaServices services = runtime.services();
        SettingsStore settings = services.settings();
        if (!settings.get(VantaSettings.WAYPOINTS_ENABLED) || !WorldCamera.isFresh()) {
            return 0;
        }
        GameBridge game = services.game();
        String worldKey = game.worldKey();
        if (WorldKeys.isNone(worldKey)) {
            return 0;
        }
        Optional<Vec3d> player = game.playerPosition();
        if (player.isEmpty()) {
            return 0;
        }
        String dimension = game.dimensionId().orElse(Waypoint.UNKNOWN_DIMENSION);
        double maxDistance = settings.get(VantaSettings.WAYPOINTS_MAX_MARKER_DISTANCE);
        boolean showDistance = settings.get(VantaSettings.WAYPOINTS_SHOW_DISTANCE);
        double scale = settings.get(VantaSettings.WAYPOINTS_MARKER_SCALE);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        // The game's projection uses the framebuffer aspect ratio.
        float aspect = (float) minecraft.getWindow().getWidth() / Math.max(1, minecraft.getWindow().getHeight());
        List<WaypointMarkers.Marker> markers = WaypointMarkers.compute(services.waypoints(), worldKey, dimension,
                player.get(), game.yaw(), width, maxDistance, COMPASS_SPAN_DEGREES);
        int drawn = 0;
        // Nearest first in the list; drawn farthest first so the nearest marker ends on top.
        for (int i = markers.size() - 1; i >= 0; i--) {
            WaypointMarkers.Marker marker = markers.get(i);
            if (!marker.inRange()) {
                continue;
            }
            Waypoint waypoint = marker.waypoint();
            Optional<WorldCamera.ScreenPoint> point = WorldCamera.project(waypoint.x(), waypoint.y() + MARKER_HEIGHT,
                    waypoint.z(), aspect, width, height);
            if (point.isEmpty()) {
                continue;
            }
            double sx = point.get().x();
            double sy = point.get().y();
            if (sx < -OFFSCREEN_MARGIN || sx > width + OFFSCREEN_MARGIN || sy < -OFFSCREEN_MARGIN
                    || sy > height + OFFSCREEN_MARGIN) {
                continue;
            }
            drawMarker(graphics, minecraft, marker, sx, sy, (float) scale, showDistance);
            drawn++;
        }
        return drawn;
    }

    /** One marker: the diamond centred on the projected point, the label centred below it. */
    private static void drawMarker(GuiGraphics graphics, Minecraft minecraft, WaypointMarkers.Marker marker, double sx,
                                   double sy, float scale, boolean showDistance) {
        int color = 0xFF000000 | (marker.waypoint().color() & 0xFFFFFF);
        String label = WaypointMarkers.label(marker, showDistance);
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) sx, (float) sy);
        graphics.pose().scale(scale, scale);
        for (int row = -DIAMOND_RADIUS; row <= DIAMOND_RADIUS; row++) {
            int half = DIAMOND_RADIUS - Math.abs(row);
            graphics.fill(-half, row, half + 1, row + 1, color);
        }
        int textWidth = minecraft.font.width(label);
        int textX = -textWidth / 2;
        int textY = DIAMOND_RADIUS + 3;
        graphics.fill(textX - 2, textY - 1, textX + textWidth + 2, textY + minecraft.font.lineHeight,
                LABEL_BACKGROUND);
        graphics.drawString(minecraft.font, label, textX, textY, LABEL_TEXT, true);
        graphics.pose().popMatrix();
    }
}
