package dev.vanta.core.waypoints;

import dev.vanta.core.bridge.Cardinal;
import dev.vanta.core.bridge.Vec3d;
import dev.vanta.core.i18n.Lang;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The per-frame numbers behind waypoint markers. For every enabled waypoint of the current world and dimension it
 * computes the distance, the compass bearing in Minecraft's yaw convention (0 = south, 90 = west, 180 = north,
 * 270 = east), the angle relative to the player's view and a horizontal screen position for a compass strip. The
 * 3D projection of a marker onto the screen needs the camera matrices and is done by the client.
 */
public final class WaypointMarkers {
    private WaypointMarkers() {
    }

    /**
     * One marker.
     *
     * @param waypoint    the waypoint
     * @param distance    blocks from the player to the waypoint
     * @param bearing     compass bearing to the waypoint in degrees, Minecraft yaw convention, {@code [0, 360)}
     * @param relativeYaw bearing minus the player's yaw, normalised to {@code [-180, 180)}; positive is to the
     *                    player's right
     * @param direction   nearest cardinal direction of the bearing
     * @param inRange     distance at most the configured marker distance
     * @param screenX     x position on a compass strip of the given width whose span is the horizontal field of
     *                    view, clamped to {@code [0, width]}
     * @param inView      whether the waypoint lies within the horizontal field of view (before clamping)
     */
    public record Marker(Waypoint waypoint, double distance, double bearing, double relativeYaw,
                         Cardinal direction, boolean inRange, double screenX, boolean inView) {
        public Marker {
            Objects.requireNonNull(waypoint, "waypoint");
            Objects.requireNonNull(direction, "direction");
        }
    }

    /**
     * Markers for the enabled waypoints of {@code worldKey} in {@code dimension}, nearest first.
     *
     * @param store                the store to read from
     * @param worldKey             current world key ({@link WorldKeys}); {@link WorldKeys#NONE} yields no markers
     * @param dimension            current dimension id
     * @param player               player position
     * @param yaw                  player yaw in degrees (any range)
     * @param screenWidth          width of the compass strip in pixels
     * @param maxDistance          marker distance in blocks ({@code waypoints.maxMarkerDistance})
     * @param horizontalFovDegrees horizontal span of the compass strip in degrees ({@code > 0})
     */
    public static List<Marker> compute(WaypointStore store, String worldKey, String dimension, Vec3d player,
                                       double yaw, int screenWidth, double maxDistance, double horizontalFovDegrees) {
        if (WorldKeys.isNone(worldKey)) {
            return List.of();
        }
        return compute(store.enabledIn(worldKey, dimension), player, yaw, screenWidth, maxDistance,
                horizontalFovDegrees);
    }

    /**
     * Markers for {@code waypoints} (disabled ones are skipped; the caller has already chosen world and dimension),
     * nearest first.
     */
    public static List<Marker> compute(Collection<Waypoint> waypoints, Vec3d player, double yaw, int screenWidth,
                                       double maxDistance, double horizontalFovDegrees) {
        Objects.requireNonNull(waypoints, "waypoints");
        Objects.requireNonNull(player, "player");
        if (!(horizontalFovDegrees > 0) || !Double.isFinite(horizontalFovDegrees)) {
            throw new IllegalArgumentException("horizontalFovDegrees must be positive: " + horizontalFovDegrees);
        }
        double halfFov = Math.min(horizontalFovDegrees, 360.0) / 2.0;
        List<Marker> out = new ArrayList<>();
        for (Waypoint waypoint : waypoints) {
            if (!waypoint.enabled()) {
                continue;
            }
            double distance = waypoint.distanceTo(player);
            double bearing = bearing(player, waypoint.position());
            double relative = relativeYaw(bearing, yaw);
            boolean inView = Math.abs(relative) <= halfFov;
            double fraction = relative / halfFov;
            double screenX = screenWidth / 2.0 + Math.max(-1.0, Math.min(1.0, fraction)) * (screenWidth / 2.0);
            out.add(new Marker(waypoint, distance, bearing, relative, Cardinal.fromYaw(bearing),
                    distance <= maxDistance, screenX, inView));
        }
        out.sort(Comparator.comparingDouble(Marker::distance)
                .thenComparing(m -> m.waypoint().name().toLowerCase(Locale.ROOT)));
        return List.copyOf(out);
    }

    /**
     * Compass bearing from {@code from} to {@code to} in Minecraft yaw degrees, {@code [0, 360)}: 0 when the target
     * is due south (+Z), 90 west (-X), 180 north (-Z), 270 east (+X). A target straight above or below yields 0.
     */
    public static double bearing(Vec3d from, Vec3d to) {
        double dx = to.x() - from.x();
        double dz = to.z() - from.z();
        if (dx == 0 && dz == 0) {
            return 0;
        }
        double degrees = Math.toDegrees(Math.atan2(-dx, dz));
        return ((degrees % 360.0) + 360.0) % 360.0;
    }

    /** {@code bearing - yaw} normalised to {@code [-180, 180)}; positive means the target is to the right. */
    public static double relativeYaw(double bearing, double yaw) {
        return Cardinal.normalizeYaw(bearing - yaw);
    }

    /**
     * Distance text for a marker: whole blocks below 1000 ("842 m"), otherwise kilometres with one decimal
     * ("1.2 km"). One block is one metre.
     */
    public static String formatDistance(double blocks) {
        double d = Math.max(0, blocks);
        if (d < 1000) {
            return Lang.tr("vanta.waypoints.distance.blocks", Long.toString(Math.round(d)));
        }
        return Lang.tr("vanta.waypoints.distance.kilometers", String.format(Locale.ROOT, "%.1f", d / 1000.0));
    }

    /** Marker caption: the name, followed by the distance when {@code showDistance}. */
    public static String label(Marker marker, boolean showDistance) {
        if (!showDistance) {
            return marker.waypoint().name();
        }
        return Lang.tr("vanta.waypoints.marker.label", marker.waypoint().name(), formatDistance(marker.distance()));
    }
}
