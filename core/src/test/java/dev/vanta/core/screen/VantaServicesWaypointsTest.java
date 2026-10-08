package dev.vanta.core.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.bridge.FakeGameBridge;
import dev.vanta.core.bridge.FakeKeybindBridge;
import dev.vanta.core.bridge.FakeOptionsBridge;
import dev.vanta.core.bridge.FakeResourcePackBridge;
import dev.vanta.core.bridge.Vec3d;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.lab.LabFeature;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.waypoints.Waypoint;
import dev.vanta.core.waypoints.WaypointMarkers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The composition root wires the waypoint store and the Lab toggles into load, saveAll and the accessors. */
class VantaServicesWaypointsTest {
    @TempDir
    Path dir;

    @Test
    void waypointsAndLabAreLoadedSavedAndReachable() {
        MutableClock clock = MutableClock.standard();
        VantaPaths paths = VantaPaths.inGameDirectory(dir);
        FakeGameBridge game = new FakeGameBridge();
        VantaServices services = VantaServices.create(paths, game, new FakeOptionsBridge(), new FakeKeybindBridge(),
                new FakeResourcePackBridge(), clock);
        services.load();

        assertEquals(0, services.waypoints().size());
        assertFalse(services.waypoints().isDirty());
        assertEquals("sp:New World", game.worldKey());

        Waypoint home = services.waypoints().add(game.worldKey(), game.dimensionId().orElseThrow(), "Home",
                game.playerPosition().orElseThrow()).orElseThrow();
        services.saveAll();
        assertTrue(Files.exists(paths.waypointsFile()));
        assertTrue(paths.contains(paths.waypointsFile()));
        assertFalse(services.waypoints().isDirty());

        // the marker numbers for the current frame come straight from the store and the settings
        game.position = new Vec3d(home.x(), home.y(), home.z() - 40);
        game.yaw = 0f; // facing south, towards the waypoint
        List<WaypointMarkers.Marker> markers = WaypointMarkers.compute(services.waypoints(), game.worldKey(),
                game.dimensionId().orElseThrow(), game.playerPosition().orElseThrow(), game.yaw(), 400,
                services.settings().get(VantaSettings.WAYPOINTS_MAX_MARKER_DISTANCE), 70);
        assertEquals(1, markers.size());
        assertEquals(40.0, markers.get(0).distance(), 1e-9);
        assertEquals(0.0, markers.get(0).relativeYaw(), 1e-9);
        assertTrue(markers.get(0).inRange());
        assertEquals(512, services.settings().get(VantaSettings.WAYPOINTS_MAX_MARKER_DISTANCE));
        assertTrue(services.settings().get(VantaSettings.WAYPOINTS_ENABLED));
        assertTrue(services.settings().get(VantaSettings.WAYPOINTS_SHOW_DISTANCE));
        assertEquals(1.0, services.settings().get(VantaSettings.WAYPOINTS_MARKER_SCALE));

        assertFalse(services.lab().isEnabled(LabFeature.WAYPOINT_BEAMS));
        assertTrue(services.lab().set(LabFeature.WAYPOINT_BEAMS, true));
        assertEquals(Boolean.TRUE, services.settings().get(VantaSettings.LAB_WAYPOINT_BEAMS));
        services.shutdown();

        VantaServices again = VantaServices.create(paths, new FakeGameBridge(), new FakeOptionsBridge(),
                new FakeKeybindBridge(), new FakeResourcePackBridge(), clock);
        again.load();
        assertEquals(List.of(home), again.waypoints().all());
        assertTrue(again.lab().isEnabled(LabFeature.WAYPOINT_BEAMS), "lab toggles ride along in settings.json");
    }
}
