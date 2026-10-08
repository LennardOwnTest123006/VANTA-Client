package dev.vanta.core.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory stand-ins for the waypoints store and the lab features until the real ones are wired.
 */
final class FakeNexusIntegrations {
    private FakeNexusIntegrations() {
    }

    /** Waypoints by name with enabled flags; records every command. */
    static final class Waypoints implements WaypointLookup, NexusWaypointActions {
        final Map<String, Boolean> enabled = new LinkedHashMap<>();
        final List<String> log = new ArrayList<>();

        Waypoints(String... names) {
            for (String name : names) {
                enabled.put(name, true);
            }
        }

        @Override
        public List<String> names() {
            return new ArrayList<>(enabled.keySet());
        }

        @Override
        public boolean has(String name) {
            return enabled.keySet().stream().anyMatch(n -> n.equalsIgnoreCase(name));
        }

        @Override
        public boolean add(String name, double x, double y, double z, Optional<String> category) {
            log.add("add:" + name + ":" + (int) x + "," + (int) y + "," + (int) z + ":" + category.orElse("-"));
            enabled.put(name, true);
            return true;
        }

        @Override
        public boolean remove(String name) {
            log.add("remove:" + name);
            return enabled.remove(name) != null;
        }

        @Override
        public boolean toggle(String name, boolean value) {
            log.add("toggle:" + name + ":" + value);
            if (!enabled.containsKey(name)) {
                return false;
            }
            enabled.put(name, value);
            return true;
        }
    }

    /** Lab features by id. */
    static final class Lab implements LabToggle {
        final Map<String, Boolean> state = new LinkedHashMap<>();

        Lab(String... features) {
            for (String feature : features) {
                state.put(feature, false);
            }
        }

        @Override
        public List<String> features() {
            return new ArrayList<>(state.keySet());
        }

        @Override
        public boolean isEnabled(String feature) {
            return state.getOrDefault(feature, false);
        }

        @Override
        public boolean set(String feature, boolean enabled) {
            if (!state.containsKey(feature)) {
                return false;
            }
            state.put(feature, enabled);
            return true;
        }
    }
}
