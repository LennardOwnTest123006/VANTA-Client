package dev.vanta.core.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import dev.vanta.core.bridge.VanillaOption;
import dev.vanta.core.hud.HudLayout;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.cosmetics.ServicesFixture;
import dev.vanta.core.settings.VantaSettings;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NexusUndoTest {
    @TempDir
    Path dir;
    ServicesFixture fixture;
    VantaServices services;
    final FakeNexusIntegrations.Waypoints waypoints = new FakeNexusIntegrations.Waypoints("Home");
    final FakeNexusIntegrations.Lab lab = new FakeNexusIntegrations.Lab("dynamic_hud");

    @BeforeEach
    void setUp() {
        fixture = new ServicesFixture(dir);
        services = fixture.services;
        services.setWaypoints(waypoints, waypoints);
        services.setLabToggle(lab);
    }

    private boolean turn(String... actionJsons) {
        JsonArray array = new JsonArray();
        for (String json : actionJsons) {
            array.add(JsonParser.parseString(json));
        }
        NexusUndo.Turn t = services.nexusUndo().begin();
        services.nexusActions().execute(array, t);
        return services.nexusUndo().commit(t, "turn");
    }

    @Test
    void undoRestoresHudSettingsProfileWaypointsAndLab() {
        NexusUndo undo = services.nexusUndo();
        HudLayout layoutBefore = services.hud().layout();
        int renderBefore = fixture.options.getInt(VanillaOption.RENDER_DISTANCE, 0);
        assertFalse(undo.canUndo());

        assertTrue(turn("{\"type\":\"hud.set\",\"element\":\"fps\",\"visible\":false}",
                "{\"type\":\"setting.set\",\"id\":\"hud.globalScale\",\"value\":1.5}",
                "{\"type\":\"setting.set\",\"id\":\"video.renderDistance\",\"value\":6}",
                "{\"type\":\"waypoint.add\",\"name\":\"Farm\",\"x\":1,\"y\":2,\"z\":3}",
                "{\"type\":\"waypoint.toggle\",\"name\":\"Home\",\"enabled\":false}",
                "{\"type\":\"lab.set\",\"feature\":\"dynamic_hud\",\"enabled\":true}"));
        assertTrue(undo.canUndo());
        assertEquals(1, undo.depth());
        assertEquals("turn", undo.lastLabel().orElseThrow());
        assertFalse(services.hud().layout().find("fps").orElseThrow().enabled());
        assertEquals(6, fixture.options.getInt(VanillaOption.RENDER_DISTANCE, 0));

        assertTrue(undo.undoLast());
        assertEquals(layoutBefore, services.hud().layout());
        assertEquals(1.0, services.settings().get(VantaSettings.HUD_GLOBAL_SCALE), 1e-9);
        assertEquals(renderBefore, fixture.options.getInt(VanillaOption.RENDER_DISTANCE, 0));
        assertFalse(waypoints.enabled.containsKey("Farm"), "the added waypoint is removed again");
        assertTrue(waypoints.enabled.get("Home"), "the toggle is reverted");
        assertFalse(lab.isEnabled("dynamic_hud"));
        assertFalse(undo.canUndo());
        assertFalse(undo.undoLast());
    }

    @Test
    void undoReactivatesThePreviousProfileAndDeletesACreatedOne() {
        NexusUndo undo = services.nexusUndo();
        assertEquals("default", services.profiles().activeId().orElseThrow());
        assertTrue(turn("{\"type\":\"profile.switch\",\"name\":\"PvP\"}"));
        assertEquals("pvp", services.profiles().activeId().orElseThrow());
        assertEquals(16, fixture.options.getInt(VanillaOption.RENDER_DISTANCE, 0));
        assertTrue(undo.undoLast());
        assertEquals("default", services.profiles().activeId().orElseThrow());
        assertEquals(10, fixture.options.getInt(VanillaOption.RENDER_DISTANCE, 0));

        int count = services.profiles().size();
        assertTrue(turn("{\"type\":\"profile.create\",\"name\":\"Temp\",\"fromCurrent\":true}"));
        assertEquals(count + 1, services.profiles().size());
        assertTrue(undo.undoLast());
        assertEquals(count, services.profiles().size(), "the created profile is deleted");
        assertEquals("default", services.profiles().activeId().orElseThrow());
    }

    @Test
    void turnsWithoutChangesAreNotRecordedAndTheStackIsBounded() {
        NexusUndo undo = services.nexusUndo();
        assertFalse(turn("{\"type\":\"hud.set\",\"element\":\"radar\",\"visible\":false}"));
        assertFalse(turn());
        assertFalse(undo.canUndo());
        for (int i = 0; i < NexusUndo.MAX_TURNS + 5; i++) {
            boolean visible = i % 2 == 0;
            assertTrue(turn("{\"type\":\"hud.set\",\"element\":\"fps\",\"visible\":" + !visible + "}"));
        }
        assertEquals(NexusUndo.MAX_TURNS, undo.depth());
        undo.clear();
        assertFalse(undo.canUndo());
    }
}
