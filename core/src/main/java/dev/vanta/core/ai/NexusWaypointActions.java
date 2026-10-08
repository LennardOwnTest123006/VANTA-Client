package dev.vanta.core.ai;

import java.util.Optional;

/**
 * The waypoint commands the assistant may run. The waypoints store implements it; {@link #none()} refuses everything
 * until it is wired, so a reply honestly reports the rejection.
 */
public interface NexusWaypointActions {
    /**
     * Adds a waypoint in the current world.
     *
     * @param category free text, empty for the default category
     * @return true when the waypoint was created
     */
    boolean add(String name, double x, double y, double z, Optional<String> category);

    /** Removes the waypoint of that name in the current world. */
    boolean remove(String name);

    /** Enables or disables the waypoint of that name in the current world. */
    boolean toggle(String name, boolean enabled);

    /** Actions that refuse everything (no waypoints store wired). */
    static NexusWaypointActions none() {
        return new NexusWaypointActions() {
            @Override
            public boolean add(String name, double x, double y, double z, Optional<String> category) {
                return false;
            }

            @Override
            public boolean remove(String name) {
                return false;
            }

            @Override
            public boolean toggle(String name, boolean enabled) {
                return false;
            }
        };
    }
}
