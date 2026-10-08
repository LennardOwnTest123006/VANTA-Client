package dev.vanta.core.waypoints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.bridge.Cardinal;
import dev.vanta.core.bridge.Vec3d;
import dev.vanta.core.config.JsonStore;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WaypointMarkersTest {
    private static final String WORLD = "sp:New World";
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final double EPS = 1e-9;

    @TempDir
    Path dir;

    @Test
    void bearingFollowsMinecraftYawConvention() {
        Vec3d origin = new Vec3d(0, 64, 0);
        assertEquals(0.0, WaypointMarkers.bearing(origin, new Vec3d(0, 64, 10)), EPS, "south is 0");
        assertEquals(90.0, WaypointMarkers.bearing(origin, new Vec3d(-10, 64, 0)), EPS, "west is 90");
        assertEquals(180.0, WaypointMarkers.bearing(origin, new Vec3d(0, 64, -10)), EPS, "north is 180");
        assertEquals(270.0, WaypointMarkers.bearing(origin, new Vec3d(10, 64, 0)), EPS, "east is 270");
        assertEquals(315.0, WaypointMarkers.bearing(origin, new Vec3d(10, 64, 10)), EPS, "south-east");
        assertEquals(0.0, WaypointMarkers.bearing(origin, new Vec3d(0, 200, 0)), EPS, "straight up");
        // the result is independent of the absolute position
        assertEquals(180.0, WaypointMarkers.bearing(new Vec3d(500, 10, 500), new Vec3d(500, 10, 400)), EPS);
        // and agrees with the direction the player would have to face
        assertEquals(Cardinal.NORTH, Cardinal.fromYaw(WaypointMarkers.bearing(origin, new Vec3d(0, 64, -10))));
        assertEquals(Cardinal.EAST, Cardinal.fromYaw(WaypointMarkers.bearing(origin, new Vec3d(10, 64, 0))));
    }

    @Test
    void relativeYawIsSignedAndNormalised() {
        assertEquals(0.0, WaypointMarkers.relativeYaw(180, 180), EPS);
        assertEquals(90.0, WaypointMarkers.relativeYaw(270, 180), EPS, "facing north, east is to the right");
        assertEquals(-90.0, WaypointMarkers.relativeYaw(90, 180), EPS, "facing north, west is to the left");
        assertEquals(-180.0, WaypointMarkers.relativeYaw(0, 180), EPS, "straight behind");
        assertEquals(10.0, WaypointMarkers.relativeYaw(5, -5), EPS);
        assertEquals(10.0, WaypointMarkers.relativeYaw(365, 715), EPS, "any yaw range");
        assertEquals(-170.0, WaypointMarkers.relativeYaw(10, 180), EPS);
    }

    @Test
    void computesDistanceRangeAndCompassPosition() {
        Vec3d player = new Vec3d(0, 64, 0);
        Waypoint north = waypoint("w1", "North", 0, 64, -100, true);
        Waypoint east = waypoint("w2", "East", 30, 64, 0, true);
        Waypoint behind = waypoint("w3", "Behind", 0, 64, 600, true);
        Waypoint disabled = waypoint("w4", "Hidden", 1, 64, 1, false);
        Waypoint upLeft = waypoint("w5", "Up left", -20, 94, -20, true);

        // facing north (yaw 180), compass strip 400 px wide spanning 90 degrees, markers up to 512 blocks
        List<WaypointMarkers.Marker> markers = WaypointMarkers.compute(
                List.of(behind, north, disabled, east, upLeft), player, 180, 400, 512, 90);

        assertEquals(List.of("East", "Up left", "North", "Behind"),
                markers.stream().map(m -> m.waypoint().name()).toList(), "nearest first, disabled skipped");

        WaypointMarkers.Marker n = markers.get(2);
        assertEquals(100.0, n.distance(), EPS);
        assertEquals(180.0, n.bearing(), EPS);
        assertEquals(0.0, n.relativeYaw(), EPS);
        assertEquals(Cardinal.NORTH, n.direction());
        assertTrue(n.inRange());
        assertTrue(n.inView());
        assertEquals(200.0, n.screenX(), EPS, "dead ahead is the centre of the strip");

        WaypointMarkers.Marker e = markers.get(0);
        assertEquals(30.0, e.distance(), EPS);
        assertEquals(270.0, e.bearing(), EPS);
        assertEquals(90.0, e.relativeYaw(), EPS);
        assertEquals(Cardinal.EAST, e.direction());
        assertFalse(e.inView(), "90 degrees to the right is outside a 90 degree span");
        assertEquals(400.0, e.screenX(), EPS, "clamped to the right edge");

        WaypointMarkers.Marker b = markers.get(3);
        assertEquals(600.0, b.distance(), EPS);
        assertFalse(b.inRange(), "further than 512 blocks");
        assertEquals(-180.0, b.relativeYaw(), EPS);
        assertFalse(b.inView());
        assertEquals(0.0, b.screenX(), EPS, "clamped to the left edge");

        WaypointMarkers.Marker u = markers.get(1);
        assertEquals(Math.sqrt(20 * 20 + 30 * 30 + 20 * 20), u.distance(), EPS, "distance includes height");
        assertEquals(135.0, u.bearing(), EPS, "north-west");
        assertEquals(-45.0, u.relativeYaw(), EPS);
        assertTrue(u.inView(), "exactly at the edge of the span counts as in view");
        assertEquals(0.0, u.screenX(), EPS);
    }

    @Test
    void screenPositionScalesWithTheSpan() {
        Waypoint right = waypoint("w1", "R", 10, 64, -10, true); // bearing 225: 45 degrees right of north
        WaypointMarkers.Marker wide = WaypointMarkers.compute(List.of(right), Vec3d.ZERO, 180, 800, 100, 180).get(0);
        assertEquals(45.0, wide.relativeYaw(), EPS);
        assertEquals(600.0, wide.screenX(), EPS, "45 of 90 degrees to the right: three quarters across");
        WaypointMarkers.Marker narrow = WaypointMarkers.compute(List.of(right), Vec3d.ZERO, 180, 800, 100, 60)
                .get(0);
        assertFalse(narrow.inView());
        assertEquals(800.0, narrow.screenX(), EPS);
        assertThrows(IllegalArgumentException.class,
                () -> WaypointMarkers.compute(List.of(right), Vec3d.ZERO, 0, 800, 100, 0));
        assertThrows(IllegalArgumentException.class,
                () -> WaypointMarkers.compute(List.of(right), Vec3d.ZERO, 0, 800, 100, Double.NaN));
    }

    @Test
    void storeOverloadFiltersWorldDimensionAndEnabledState() {
        MutableClock clock = MutableClock.standard();
        WaypointStore store = new WaypointStore(new JsonStore(clock), new VantaPaths(dir), clock);
        store.load();
        store.add(WORLD, OVERWORLD, "Home", new Vec3d(0, 64, 50));
        Waypoint off = store.add(WORLD, OVERWORLD, "Off", new Vec3d(0, 64, 20)).orElseThrow();
        store.setEnabled(off.id(), false);
        store.add(WORLD, NETHER, "Fortress", new Vec3d(0, 64, 5));
        store.add("mp:other", OVERWORLD, "Elsewhere", new Vec3d(0, 64, 1));

        List<WaypointMarkers.Marker> markers = WaypointMarkers.compute(store, WORLD, OVERWORLD, Vec3d.ZERO, 0, 400,
                512, 70);
        assertEquals(List.of("Home"), markers.stream().map(m -> m.waypoint().name()).toList());
        assertEquals(List.of("Fortress"), WaypointMarkers.compute(store, WORLD, NETHER, Vec3d.ZERO, 0, 400, 512, 70)
                .stream().map(m -> m.waypoint().name()).toList());
        assertEquals(List.of(), WaypointMarkers.compute(store, WorldKeys.NONE, OVERWORLD, Vec3d.ZERO, 0, 400, 512,
                70), "no world, no markers");
        assertEquals(List.of(), WaypointMarkers.compute(store, "sp:unknown", OVERWORLD, Vec3d.ZERO, 0, 400, 512, 70));
    }

    @Test
    void distanceTextAndLabels() {
        assertEquals("0 m", WaypointMarkers.formatDistance(0));
        assertEquals("0 m", WaypointMarkers.formatDistance(-5));
        assertEquals("12 m", WaypointMarkers.formatDistance(12.4));
        assertEquals("13 m", WaypointMarkers.formatDistance(12.5));
        assertEquals("999 m", WaypointMarkers.formatDistance(999.4));
        assertEquals("1.0 km", WaypointMarkers.formatDistance(1000));
        assertEquals("1.5 km", WaypointMarkers.formatDistance(1500));
        assertEquals("12.3 km", WaypointMarkers.formatDistance(12_345));

        WaypointMarkers.Marker marker = WaypointMarkers.compute(List.of(waypoint("w", "Home", 0, 64, 120, true)),
                new Vec3d(0, 64, 0), 0, 400, 512, 70).get(0);
        assertEquals("Home · 120 m", WaypointMarkers.label(marker, true));
        assertEquals("Home", WaypointMarkers.label(marker, false));
    }

    private static Waypoint waypoint(String id, String name, double x, double y, double z, boolean enabled) {
        return new Waypoint(id, name, WORLD, OVERWORLD, x, y, z, WaypointColors.DEFAULT, WaypointCategories.DEFAULT,
                enabled, 0L);
    }
}
