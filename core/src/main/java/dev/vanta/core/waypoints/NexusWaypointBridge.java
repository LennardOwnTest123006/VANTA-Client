package dev.vanta.core.waypoints;

import dev.vanta.core.ai.NexusWaypointActions;
import dev.vanta.core.ai.WaypointLookup;
import dev.vanta.core.bridge.GameBridge;
import dev.vanta.core.bridge.Vec3d;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The {@link WaypointStore} as the Nexus assistant sees it: the waypoints of the world the player is in right now
 * (the {@link GameBridge#worldKey()} and {@link GameBridge#dimensionId()} of the game), by name. Outside a world
 * ({@link WorldKeys#NONE}) nothing is listed and nothing can be added, so the assistant's {@code waypoint.*}
 * actions are refused honestly instead of writing into a world that does not exist.
 */
public final class NexusWaypointBridge implements WaypointLookup, NexusWaypointActions {
    private final WaypointStore store;
    private final GameBridge game;

    public NexusWaypointBridge(WaypointStore store, GameBridge game) {
        this.store = Objects.requireNonNull(store, "store");
        this.game = Objects.requireNonNull(game, "game");
    }

    /** The world key of the game right now ({@link WorldKeys#NONE} outside a world). */
    public String worldKey() {
        return game.worldKey();
    }

    /** The dimension id of the game, or {@link Waypoint#UNKNOWN_DIMENSION}. */
    public String dimension() {
        return game.dimensionId().orElse(Waypoint.UNKNOWN_DIMENSION);
    }

    @Override
    public List<String> names() {
        String world = worldKey();
        return WorldKeys.isNone(world) ? List.of() : store.names(world);
    }

    @Override
    public boolean has(String name) {
        String world = worldKey();
        return !WorldKeys.isNone(world) && name != null && store.has(world, name);
    }

    @Override
    public boolean add(String name, double x, double y, double z, Optional<String> category) {
        String world = worldKey();
        if (WorldKeys.isNone(world) || name == null) {
            return false;
        }
        String cat = WaypointCategories.sanitize(category.orElse(WaypointCategories.DEFAULT));
        int color = WaypointColors.forIndex(store.forWorld(world).size());
        return store.add(world, dimension(), name, new Vec3d(x, y, z), cat, color).isPresent();
    }

    @Override
    public boolean remove(String name) {
        String world = worldKey();
        return !WorldKeys.isNone(world) && name != null && store.remove(world, name);
    }

    @Override
    public boolean toggle(String name, boolean enabled) {
        String world = worldKey();
        return !WorldKeys.isNone(world) && name != null && store.toggle(world, name, enabled);
    }
}
