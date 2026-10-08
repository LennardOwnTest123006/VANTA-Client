package dev.vanta.core.ai;

import java.util.List;

/**
 * Read view of the waypoints the assistant may name. The waypoints store implements it; {@link #none()} serves until
 * it is wired.
 */
public interface WaypointLookup {
    /** Names of the waypoints of the current world, in display order. */
    List<String> names();

    /** True when a waypoint of that name exists in the current world (case-insensitive). */
    boolean has(String name);

    /** A lookup without waypoints. */
    static WaypointLookup none() {
        return new WaypointLookup() {
            @Override
            public List<String> names() {
                return List.of();
            }

            @Override
            public boolean has(String name) {
                return false;
            }
        };
    }
}
