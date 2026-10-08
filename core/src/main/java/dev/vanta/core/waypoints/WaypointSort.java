package dev.vanta.core.waypoints;

import dev.vanta.core.bridge.Vec3d;
import dev.vanta.core.i18n.LangKeyed;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Sort orders of a waypoint list. Ties are broken by name and then by id so the order is stable.
 */
public enum WaypointSort implements LangKeyed {
    /** Alphabetical by name (case-insensitive). */
    NAME,
    /** Nearest first; falls back to {@link #NAME} when no origin is known. */
    DISTANCE,
    /** Newest first. */
    CREATED;

    private static final Comparator<Waypoint> BY_NAME = Comparator
            .comparing((Waypoint w) -> w.name().toLowerCase(Locale.ROOT))
            .thenComparing(Waypoint::name)
            .thenComparing(Waypoint::id);

    /** Lower-case id used in JSON and lang keys. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String langKey() {
        return "vanta.waypoints.sort." + id();
    }

    /**
     * Comparator for this order.
     *
     * @param origin the player's position for {@link #DISTANCE}; ignored by the other orders
     */
    public Comparator<Waypoint> comparator(Optional<Vec3d> origin) {
        return switch (this) {
            case NAME -> BY_NAME;
            case DISTANCE -> origin.<Comparator<Waypoint>>map(o -> Comparator
                    .comparingDouble((Waypoint w) -> w.distanceTo(o)).thenComparing(BY_NAME)).orElse(BY_NAME);
            case CREATED -> Comparator.comparingLong(Waypoint::createdAt).reversed().thenComparing(BY_NAME);
        };
    }

    /** A sorted copy of {@code waypoints}. */
    public List<Waypoint> sort(Collection<Waypoint> waypoints, Optional<Vec3d> origin) {
        List<Waypoint> out = new ArrayList<>(waypoints);
        out.sort(comparator(origin));
        return List.copyOf(out);
    }

    /** Finds an order by id (case-insensitive). */
    public static Optional<WaypointSort> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (WaypointSort sort : values()) {
            if (sort.id().equalsIgnoreCase(id.strip())) {
                return Optional.of(sort);
            }
        }
        return Optional.empty();
    }
}
