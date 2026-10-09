package dev.vanta.core.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vanta.core.bridge.VanillaOption;
import dev.vanta.core.hud.HudAnchor;
import dev.vanta.core.hud.HudLayout;
import dev.vanta.core.hud.HudPresets;
import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.perf.FpsLimitPreset;
import dev.vanta.core.perf.PerformancePreset;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.cosmetics.ServicesFixture;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.waypoints.NexusWaypointBridge;
import dev.vanta.core.waypoints.WorldKeys;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Validation and application of every action against the real stores of a loaded {@link VantaServices} on fakes.
 */
class NexusActionsTest {
    @TempDir
    Path dir;
    ServicesFixture fixture;
    VantaServices services;
    NexusActions actions;
    NexusUndo undo;
    final FakeNexusIntegrations.Waypoints waypoints = new FakeNexusIntegrations.Waypoints("Home", "Mine");
    final FakeNexusIntegrations.Lab lab = new FakeNexusIntegrations.Lab("dynamic_hud", "frametime_graph");

    @BeforeEach
    void setUp() {
        fixture = new ServicesFixture(dir);
        services = fixture.services;
        actions = services.nexusActions();
        undo = services.nexusUndo();
        services.setWaypoints(waypoints, waypoints);
        services.setLabToggle(lab);
    }

    private NexusActions.Outcome run(String... actionJsons) {
        JsonArray array = new JsonArray();
        for (String json : actionJsons) {
            array.add(JsonParser.parseString(json));
        }
        NexusUndo.Turn turn = undo.begin();
        NexusActions.Outcome outcome = actions.execute(array, turn);
        undo.commit(turn, "test");
        return outcome;
    }

    private static RejectedAction.Reason onlyRejection(NexusActions.Outcome outcome) {
        assertEquals(1, outcome.rejected().size(), outcome.toString());
        assertTrue(outcome.applied().isEmpty(), outcome.toString());
        return outcome.rejected().get(0).reason();
    }

    @Test
    void hudSetValidatesElementsAndClampsValues() {
        assertEquals(RejectedAction.Reason.UNKNOWN_ELEMENT,
                onlyRejection(run("{\"type\":\"hud.set\",\"element\":\"radar\",\"visible\":false}")));
        assertEquals(RejectedAction.Reason.MISSING_FIELD, onlyRejection(run("{\"type\":\"hud.set\"}")));
        assertEquals(RejectedAction.Reason.MISSING_FIELD,
                onlyRejection(run("{\"type\":\"hud.set\",\"element\":\"fps\"}")), "nothing to change");
        assertEquals(RejectedAction.Reason.INVALID_VALUE,
                onlyRejection(run("{\"type\":\"hud.set\",\"element\":\"fps\",\"scale\":\"big\"}")));
        assertEquals(RejectedAction.Reason.INVALID_VALUE,
                onlyRejection(run("{\"type\":\"hud.set\",\"element\":\"fps\",\"anchor\":\"nowhere\"}")));

        NexusActions.Outcome outcome = run(
                "{\"type\":\"hud.set\",\"element\":\"FPS\",\"visible\":false,\"scale\":5,\"opacity\":0.01}");
        assertTrue(outcome.rejected().isEmpty(), outcome.rejected().toString());
        HudWidgetState fps = services.hud().layout().find("fps").orElseThrow();
        assertFalse(fps.enabled());
        assertEquals(HudWidgetState.MAX_SCALE, fps.scale(), 1e-9, "scale clamped to 2.0");
        assertEquals(NexusActions.MIN_OPACITY, fps.opacity(), 1e-9, "opacity clamped to 0.1");
        AppliedAction applied = outcome.applied().get(0);
        assertEquals("hud.set", applied.type());
        assertEquals("fps", applied.target());
        assertTrue(applied.detail().contains("scale=2"), applied.detail());
        assertTrue(applied.summary().startsWith("HUD fps:"), applied.summary());
        assertTrue(services.hud().isDirty());
    }

    @Test
    void hudSetMovesByFractionAndAnchorAndAddsMissingTypes() {
        run("{\"type\":\"hud.set\",\"element\":\"fps\",\"x\":1,\"y\":1}");
        HudWidgetState fps = services.hud().layout().find("fps").orElseThrow();
        assertEquals(HudAnchor.BOTTOM_RIGHT, fps.anchor());
        assertEquals(NexusActions.MARGIN, fps.offsetX());
        assertEquals(NexusActions.MARGIN, fps.offsetY());

        run("{\"type\":\"hud.set\",\"element\":\"fps\",\"x\":0.5,\"y\":0}");
        fps = services.hud().layout().find("fps").orElseThrow();
        assertEquals(HudAnchor.TOP_CENTER, fps.anchor());

        run("{\"type\":\"hud.set\",\"element\":\"coordinates\",\"anchor\":\"middle_left\"}");
        HudWidgetState coords = services.hud().layout().find("coordinates").orElseThrow();
        assertEquals(HudAnchor.MIDDLE_LEFT, coords.anchor());
        assertEquals(NexusActions.MARGIN, coords.offsetX());
        assertEquals(0, coords.offsetY());

        assertTrue(services.hud().layout().byType(HudWidgetType.MEMORY).isEmpty());
        assertTrue(actions.hudElementIds().contains("memory"), "types not on the HUD are offered for adding");
        NexusActions.Outcome added = run("{\"type\":\"hud.set\",\"element\":\"memory\",\"visible\":true}");
        assertTrue(added.rejected().isEmpty());
        assertTrue(added.applied().get(0).detail().startsWith("added"));
        HudWidgetState memory = services.hud().layout().find("memory").orElseThrow();
        assertTrue(memory.enabled());
        HudLayout layout = services.hud().layout();
        var memRect = layout.resolveRect(memory, NexusActions.REFERENCE_WIDTH, NexusActions.REFERENCE_HEIGHT);
        for (HudWidgetState other : layout.enabled()) {
            if (!other.id().equals("memory") && !other.type().alwaysCentered()) {
                assertFalse(layout.resolveRect(other, NexusActions.REFERENCE_WIDTH, NexusActions.REFERENCE_HEIGHT)
                        .intersects(memRect), "new widget does not cover " + other.id());
            }
        }
        // The crosshair never moves.
        run("{\"type\":\"hud.set\",\"element\":\"crosshair\",\"x\":0,\"y\":0,\"visible\":true}");
        assertEquals(HudAnchor.CENTER, services.hud().layout().find("crosshair").orElseThrow().anchor());
    }

    @Test
    void hudOnlyShowsExactlyTheNamedWidgetsPlusTheCrosshair() {
        NexusActions.Outcome outcome = run("{\"type\":\"hud.only\",\"elements\":[\"fps\",\"coordinates\"]}");
        assertTrue(outcome.rejected().isEmpty());
        for (HudWidgetState widget : services.hud().layout().widgets()) {
            boolean expected = widget.id().equals("fps") || widget.id().equals("coordinates")
                    || widget.type() == HudWidgetType.CROSSHAIR;
            assertEquals(expected, widget.enabled(), widget.id());
        }
        assertEquals(RejectedAction.Reason.UNKNOWN_ELEMENT,
                onlyRejection(run("{\"type\":\"hud.only\",\"elements\":[\"fps\",\"radar\"]}")));
        assertEquals(RejectedAction.Reason.MISSING_FIELD, onlyRejection(run("{\"type\":\"hud.only\"}")));
        assertTrue(services.hud().layout().find("fps").orElseThrow().enabled(), "a rejected action changes nothing");
        // Adding a missing type through hud.only works too.
        run("{\"type\":\"hud.only\",\"elements\":[\"cpu\"]}");
        assertTrue(services.hud().layout().byType(HudWidgetType.CPU).get(0).enabled());
        assertFalse(services.hud().layout().find("fps").orElseThrow().enabled());
    }

    @Test
    void hudPresetsAndLayouts() {
        NexusActions.Outcome outcome = run("{\"type\":\"hud.preset\",\"preset\":\"minimal\"}");
        assertTrue(outcome.rejected().isEmpty());
        assertEquals(NexusHudPreset.MINIMAL.apply(HudPresets.defaultPreset().layout()), services.hud().layout());
        assertEquals(RejectedAction.Reason.UNKNOWN_PRESET,
                onlyRejection(run("{\"type\":\"hud.preset\",\"preset\":\"streamer\"}")));

        run("{\"type\":\"hud.layout.save\",\"name\":\"My minimal\"}");
        assertEquals(1, services.hud().userPresets().size());
        assertEquals("My minimal", services.hud().userPresets().get(0).name());
        run("{\"type\":\"hud.layout.load\",\"name\":\"PvP\"}");
        assertEquals(HudPresets.find("pvp").orElseThrow().layout(), services.hud().layout());
        run("{\"type\":\"hud.layout.save\",\"name\":\"my minimal\"}");
        assertEquals(1, services.hud().userPresets().size(), "same name updates the layout");
        assertEquals(HudPresets.find("pvp").orElseThrow().layout(), services.hud().userPresets().get(0).layout());
        run("{\"type\":\"hud.layout.load\",\"name\":\"default\"}");
        assertEquals(HudPresets.defaultPreset().layout(), services.hud().layout());
        run("{\"type\":\"hud.layout.load\",\"name\":\"My minimal\"}");
        assertEquals(HudPresets.find("pvp").orElseThrow().layout(), services.hud().layout());
        assertEquals(RejectedAction.Reason.UNKNOWN_LAYOUT,
                onlyRejection(run("{\"type\":\"hud.layout.load\",\"name\":\"Nope\"}")));
        assertTrue(actions.layoutNames().contains("My minimal"));
    }

    @Test
    void profilesSwitchAndCreate() {
        NexusActions.Outcome outcome = run("{\"type\":\"profile.switch\",\"name\":\"PvP\"}");
        assertTrue(outcome.rejected().isEmpty());
        assertEquals("pvp", services.profiles().activeId().orElseThrow());
        assertEquals(16, fixture.options.getInt(VanillaOption.RENDER_DISTANCE, 0));
        assertFalse(services.notifications().visible().isEmpty(), "the usual profile toast appears");
        assertEquals(RejectedAction.Reason.UNKNOWN_PROFILE,
                onlyRejection(run("{\"type\":\"profile.switch\",\"name\":\"Speedrun\"}")));

        int before = services.profiles().size();
        NexusActions.Outcome created = run("{\"type\":\"profile.create\",\"name\":\"Recording 2\",\"fromCurrent\":true}");
        assertTrue(created.rejected().isEmpty());
        assertEquals(before + 1, services.profiles().size());
        String id = services.profiles().activeId().orElseThrow();
        assertEquals("Recording 2", services.profiles().find(id).orElseThrow().name());
        assertEquals(RejectedAction.Reason.INVALID_VALUE,
                onlyRejection(run("{\"type\":\"profile.create\",\"name\":\"Recording 2\"}")), "no duplicates");
        assertTrue(actions.profileNames().contains("Recording 2"));
    }

    @Test
    void performancePresetsAndSmartBoost() {
        NexusActions.Outcome outcome = run("{\"type\":\"perf.preset\",\"preset\":\"HIGH\"}");
        assertTrue(outcome.rejected().isEmpty());
        assertEquals(16, fixture.options.getInt(VanillaOption.RENDER_DISTANCE, 0));
        assertEquals(PerformancePreset.HIGH, services.settings().get(VantaSettings.PERFORMANCE_PRESET));
        assertEquals(RejectedAction.Reason.UNKNOWN_PRESET,
                onlyRejection(run("{\"type\":\"perf.preset\",\"preset\":\"turbo\"}")));
        assertEquals(RejectedAction.Reason.MISSING_FIELD,
                onlyRejection(run("{\"type\":\"perf.smartBoost\",\"run\":false}")));
        NexusActions.Outcome boost = run("{\"type\":\"perf.smartBoost\",\"run\":true}");
        assertTrue(boost.rejected().isEmpty());
        assertTrue(services.smartBoost().isPending() || services.smartBoost().isRunning()
                || services.smartBoost().isMeasuring() || services.smartBoost().isGated(),
                "Smart Boost was asked to measure");
    }

    @Test
    void settingsAreLimitedToTheAllowedCategories() {
        NexusActions.Outcome outcome = run("{\"type\":\"setting.set\",\"id\":\"hud.globalScale\",\"value\":1.5}");
        assertTrue(outcome.rejected().isEmpty());
        assertEquals(1.5, services.settings().get(VantaSettings.HUD_GLOBAL_SCALE), 1e-9);
        assertEquals("1.5", outcome.applied().get(0).detail());

        run("{\"type\":\"setting.set\",\"id\":\"video.renderDistance\",\"value\":\"8\"}");
        assertEquals(8, fixture.options.getInt(VanillaOption.RENDER_DISTANCE, 0), "vanilla-bound video settings");
        run("{\"type\":\"setting.set\",\"id\":\"performance.fpsLimitPreset\",\"value\":\"fps_60\"}");
        assertEquals(FpsLimitPreset.FPS_60, services.settings().get(VantaSettings.PERFORMANCE_FPS_LIMIT_PRESET));
        assertEquals(60, fixture.options.getInt(VanillaOption.FRAMERATE_LIMIT, 0));
        run("{\"type\":\"setting.set\",\"id\":\"accessibility.largeText\",\"value\":true}");
        assertTrue(services.settings().get(VantaSettings.ACCESSIBILITY_LARGE_TEXT));

        assertEquals(RejectedAction.Reason.FORBIDDEN_SETTING,
                onlyRejection(run("{\"type\":\"setting.set\",\"id\":\"controls.autoJump\",\"value\":true}")));
        assertEquals(RejectedAction.Reason.FORBIDDEN_SETTING,
                onlyRejection(run("{\"type\":\"setting.set\",\"id\":\"privacy.statsEnabled\",\"value\":false}")));
        assertEquals(RejectedAction.Reason.FORBIDDEN_SETTING,
                onlyRejection(run("{\"type\":\"setting.set\",\"id\":\"zoom.key\",\"value\":\"key.keyboard.x\"}")));
        assertEquals(RejectedAction.Reason.FORBIDDEN_SETTING,
                onlyRejection(run("{\"type\":\"setting.set\",\"id\":\"hud.openEditor\",\"value\":\"x\"}")),
                "buttons are not values");
        assertEquals(RejectedAction.Reason.FORBIDDEN_SETTING,
                onlyRejection(run("{\"type\":\"setting.set\",\"id\":\"nexus.enabled\",\"value\":false}")),
                "the assistant cannot switch itself off");
        assertEquals(RejectedAction.Reason.UNKNOWN_SETTING,
                onlyRejection(run("{\"type\":\"setting.set\",\"id\":\"video.rayTracing\",\"value\":true}")));
        assertEquals(RejectedAction.Reason.INVALID_VALUE,
                onlyRejection(run("{\"type\":\"setting.set\",\"id\":\"video.renderDistance\",\"value\":\"far\"}")));
        assertEquals(RejectedAction.Reason.MISSING_FIELD,
                onlyRejection(run("{\"type\":\"setting.set\",\"id\":\"video.renderDistance\"}")));
        assertTrue(services.settings().get(VantaSettings.PRIVACY_STATS_ENABLED));
        assertFalse(fixture.options.getBoolean(VanillaOption.AUTO_JUMP, false));

        List<String> ids = actions.allowedSettingIds();
        assertTrue(ids.contains("video.fov"));
        assertTrue(ids.contains("hud.globalOpacity"));
        assertTrue(ids.contains("performance.perfPreset"));
        assertTrue(ids.contains("accessibility.reducedMotion"));
        assertFalse(ids.contains("controls.mouseSensitivity"));
        assertFalse(ids.contains("hud.openEditor"));
        assertFalse(ids.contains("privacy.statsEnabled"));
        assertFalse(ids.contains("nexus.threads"));
        assertEquals("integer 2..32", NexusActions.allowedValues(services.settingsRegistry().require(
                "video.renderDistance")));
        assertTrue(NexusActions.allowedValues(VantaSettings.PERFORMANCE_PRESET).startsWith("one of boost, low"));
        assertEquals("true or false", NexusActions.allowedValues(VantaSettings.HUD_ENABLED));
    }

    @Test
    void waypointsAndLabGoThroughTheWiredInterfaces() {
        NexusActions.Outcome outcome = run(
                "{\"type\":\"waypoint.add\",\"name\":\"Portal\",\"x\":10.7,\"y\":64,\"z\":-20,\"category\":\"Portal\"}",
                "{\"type\":\"waypoint.toggle\",\"name\":\"Home\",\"enabled\":false}",
                "{\"type\":\"waypoint.remove\",\"name\":\"Mine\"}",
                "{\"type\":\"waypoint.remove\",\"name\":\"Nowhere\"}",
                "{\"type\":\"lab.set\",\"feature\":\"dynamic_hud\",\"enabled\":true}",
                "{\"type\":\"lab.set\",\"feature\":\"teleport\",\"enabled\":true}",
                "{\"type\":\"lab.set\",\"feature\":\"dynamic_hud\"}");
        assertEquals(4, outcome.applied().size(), outcome.toString());
        assertEquals(3, outcome.rejected().size());
        assertEquals(List.of("add:Portal:10,64,-20:Portal", "toggle:Home:false", "remove:Mine"), waypoints.log);
        assertEquals(RejectedAction.Reason.UNKNOWN_WAYPOINT, outcome.rejected().get(0).reason());
        assertEquals(RejectedAction.Reason.UNKNOWN_FEATURE, outcome.rejected().get(1).reason());
        assertEquals(RejectedAction.Reason.MISSING_FIELD, outcome.rejected().get(2).reason());
        assertTrue(lab.isEnabled("dynamic_hud"));
        assertEquals("Waypoint “Portal” added at 10, 64, -20", outcome.applied().get(0).summary());

        // Without a wired store everything is honestly refused.
        services.setWaypoints(WaypointLookup.none(), NexusWaypointActions.none());
        services.setLabToggle(LabToggle.none());
        assertEquals(RejectedAction.Reason.NOT_AVAILABLE,
                onlyRejection(run("{\"type\":\"waypoint.add\",\"name\":\"X\",\"x\":1,\"y\":2,\"z\":3}")));
        assertEquals(RejectedAction.Reason.UNKNOWN_FEATURE,
                onlyRejection(run("{\"type\":\"lab.set\",\"feature\":\"dynamic_hud\",\"enabled\":true}")));
    }

    @Test
    void waypointNamesThatGetSanitisedCanStillBeUndoneAndRemoved() {
        // The real store behind the real bridge: a stored name is sanitised (whitespace collapsed, 48 characters).
        NexusWaypointBridge bridge = services.waypointBridge();
        services.setWaypoints(bridge, bridge);
        String world = bridge.worldKey();
        assertFalse(WorldKeys.isNone(world));

        NexusActions.Outcome outcome = run(
                "{\"type\":\"waypoint.add\",\"name\":\"My  Base\",\"x\":1,\"y\":2,\"z\":3}");
        assertEquals(1, outcome.applied().size(), outcome.toString());
        assertTrue(services.waypoints().has(world, "My Base"), "stored with a single space");
        assertTrue(undo.undoLast());
        assertFalse(services.waypoints().has(world, "My Base"), "undo removes the waypoint under its stored name");

        String longName = "x".repeat(60);
        outcome = run("{\"type\":\"waypoint.add\",\"name\":\"" + longName + "\",\"x\":1,\"y\":2,\"z\":3}",
                "{\"type\":\"waypoint.toggle\",\"name\":\"" + longName + "\",\"enabled\":false}",
                "{\"type\":\"waypoint.remove\",\"name\":\"" + longName + "\"}");
        assertEquals(List.of(), outcome.rejected());
        assertEquals(3, outcome.applied().size(), outcome.toString());
        assertTrue(services.waypoints().forWorld(world).isEmpty(), "the over-long name removed the truncated waypoint");
    }

    @Test
    void unknownTypesAndNonObjectsAreRejectedWithoutStoppingTheRest() {
        JsonArray array = new JsonArray();
        array.add(JsonParser.parseString("{\"type\":\"game.fly\",\"on\":true}"));
        array.add(JsonParser.parseString("\"just a string\""));
        array.add(JsonParser.parseString("{\"type\":\"hud.preset\",\"preset\":\"pvp\"}"));
        NexusActions.Outcome outcome = actions.execute(array, undo.begin());
        assertEquals(1, outcome.applied().size());
        assertEquals(2, outcome.rejected().size());
        assertEquals(RejectedAction.Reason.UNKNOWN_TYPE, outcome.rejected().get(0).reason());
        assertEquals("Unknown action “game.fly”", outcome.rejected().get(0).summary());
        assertTrue(actions.execute(null, undo.begin()).applied().isEmpty());
    }

    @Test
    void schemaEnumeratesTheLiveRegistries() {
        JsonObject schema = actions.schema();
        assertEquals("object", schema.get("type").getAsString());
        assertFalse(schema.get("additionalProperties").getAsBoolean());
        JsonArray variants = schema.getAsJsonObject("properties").getAsJsonObject("actions").getAsJsonObject("items")
                .getAsJsonArray("anyOf");
        JsonObject hudSet = variant(variants, "hud.set");
        JsonArray elements = hudSet.getAsJsonObject("properties").getAsJsonObject("element").getAsJsonArray("enum");
        assertTrue(elements.contains(new com.google.gson.JsonPrimitive("fps")));
        assertTrue(elements.contains(new com.google.gson.JsonPrimitive("memory")), "addable widget types");
        assertEquals(2, hudSet.getAsJsonArray("required").size());
        JsonObject settingSet = variant(variants, "setting.set");
        JsonArray ids = settingSet.getAsJsonObject("properties").getAsJsonObject("id").getAsJsonArray("enum");
        assertTrue(ids.contains(new com.google.gson.JsonPrimitive("video.renderDistance")));
        assertFalse(ids.contains(new com.google.gson.JsonPrimitive("controls.autoJump")));
        assertEquals(3, settingSet.getAsJsonObject("properties").getAsJsonObject("value").getAsJsonArray("anyOf")
                .size());
        JsonObject profileSwitch = variant(variants, "profile.switch");
        assertTrue(profileSwitch.getAsJsonObject("properties").getAsJsonObject("name").getAsJsonArray("enum")
                .contains(new com.google.gson.JsonPrimitive("PvP")));
        JsonObject waypointRemove = variant(variants, "waypoint.remove");
        assertTrue(waypointRemove.getAsJsonObject("properties").getAsJsonObject("name").getAsJsonArray("enum")
                .contains(new com.google.gson.JsonPrimitive("Home")));
        assertEquals(2, variant(variants, "lab.set").getAsJsonObject("properties").getAsJsonObject("feature")
                .getAsJsonArray("enum").size());
        assertEquals(6, variant(variants, "hud.preset").getAsJsonObject("properties").getAsJsonObject("preset")
                .getAsJsonArray("enum").size());

        services.setWaypoints(WaypointLookup.none(), NexusWaypointActions.none());
        services.setLabToggle(LabToggle.none());
        JsonArray reduced = actions.schema().getAsJsonObject("properties").getAsJsonObject("actions")
                .getAsJsonObject("items").getAsJsonArray("anyOf");
        assertFalse(hasVariant(reduced, "waypoint.remove"), "no waypoints: the model cannot remove any");
        assertFalse(hasVariant(reduced, "lab.set"));
        assertTrue(hasVariant(reduced, "waypoint.add"));
    }

    private static JsonObject variant(JsonArray variants, String type) {
        for (var v : variants) {
            if (type.equals(v.getAsJsonObject().getAsJsonObject("properties").getAsJsonObject("type").get("const")
                    .getAsString())) {
                return v.getAsJsonObject();
            }
        }
        throw new AssertionError("no variant " + type);
    }

    private static boolean hasVariant(JsonArray variants, String type) {
        try {
            variant(variants, type);
            return true;
        } catch (AssertionError e) {
            return false;
        }
    }
}
