package dev.vanta.core.waypoints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.vanta.core.bridge.Vec3d;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WaypointTest {
    private static final Waypoint HOME = new Waypoint("wp1", "Home", "sp:New World", "minecraft:overworld", 10.5, 64,
            -20.25, 0xFF7C5CFF, "Home", true, 1_000L);

    @Test
    void recordValidatesIdentityAndCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> HOME.withName(" "));
        assertThrows(IllegalArgumentException.class, () -> new Waypoint("", "x", "sp:a", "d", 0, 0, 0, 0, "c", true, 0));
        assertThrows(IllegalArgumentException.class, () -> new Waypoint("i", "x", " ", "d", 0, 0, 0, 0, "c", true, 0));
        assertThrows(IllegalArgumentException.class, () -> HOME.withCoordinates(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> HOME.withCoordinates(0, Double.POSITIVE_INFINITY, 0));
        assertThrows(NullPointerException.class, () -> HOME.withCategory(null));
    }

    @Test
    void distanceAndMembership() {
        Waypoint w = HOME.withCoordinates(3, 4, 0);
        assertEquals(5.0, w.distanceTo(Vec3d.ZERO), 1e-9);
        assertEquals(5.0, w.distanceTo(0, 0, 0), 1e-9);
        assertEquals(new Vec3d(3, 4, 0), w.position());
        assertTrue(w.isIn("sp:New World"));
        assertTrue(w.isIn("sp:New World", "minecraft:overworld"));
        assertFalse(w.isIn("sp:New World", "minecraft:the_nether"));
        assertFalse(w.isIn("mp:play.example.net"));
        assertTrue(w.hasName(" home "));
        assertTrue(w.hasName("HOME"));
        assertFalse(w.hasName("Homes"));
        assertFalse(w.hasName(null));
    }

    @Test
    void withersCopyEverythingElse() {
        Waypoint moved = HOME.withPosition(new Vec3d(1, 2, 3)).withColor(0xFF00FF00).withCategory("Farm")
                .withEnabled(false).withDimension("minecraft:the_end").withName("Farm 1");
        assertEquals("wp1", moved.id());
        assertEquals("Farm 1", moved.name());
        assertEquals("sp:New World", moved.worldKey());
        assertEquals("minecraft:the_end", moved.dimension());
        assertEquals(new Vec3d(1, 2, 3), moved.position());
        assertEquals(0xFF00FF00, moved.color());
        assertEquals("Farm", moved.category());
        assertFalse(moved.enabled());
        assertEquals(1_000L, moved.createdAt());
    }

    @Test
    void jsonRoundTrip() {
        JsonObject json = HOME.toJson();
        assertEquals("#FF7C5CFF", json.get("color").getAsString());
        assertEquals(HOME, Waypoint.fromJson(json).orElseThrow());
        // via text, as the file would hold it
        JsonObject reparsed = JsonParser.parseString(json.toString()).getAsJsonObject();
        assertEquals(HOME, Waypoint.fromJson(reparsed).orElseThrow());
    }

    @Test
    void fromJsonIsTolerant() {
        JsonObject json = JsonParser.parseString("""
                {"id":"a","name":"  Spawn\\tPoint ","worldKey":"mp:srv","x":1,"y":2,"z":3,"color":-16777216,
                 "extra":"ignored"}
                """).getAsJsonObject();
        Waypoint w = Waypoint.fromJson(json).orElseThrow();
        assertEquals("Spawn Point", w.name(), "name sanitised");
        assertEquals(Waypoint.UNKNOWN_DIMENSION, w.dimension());
        assertEquals(0xFF000000, w.color(), "numeric colour accepted");
        assertEquals(WaypointCategories.DEFAULT, w.category());
        assertTrue(w.enabled());
        assertEquals(0L, w.createdAt());

        assertEquals(Optional.empty(), Waypoint.fromJson(without(HOME.toJson(), "id")));
        assertEquals(Optional.empty(), Waypoint.fromJson(without(HOME.toJson(), "name")));
        assertEquals(Optional.empty(), Waypoint.fromJson(without(HOME.toJson(), "worldKey")));
        assertEquals(Optional.empty(), Waypoint.fromJson(without(HOME.toJson(), "x")));
        JsonObject stringX = HOME.toJson();
        stringX.addProperty("x", "12");
        assertEquals(Optional.empty(), Waypoint.fromJson(stringX));
        JsonObject nanY = HOME.toJson();
        nanY.add("y", new JsonPrimitive(Double.NaN));
        assertEquals(Optional.empty(), Waypoint.fromJson(nanY));
        JsonObject blankName = HOME.toJson();
        blankName.addProperty("name", "   ");
        assertEquals(Waypoint.DEFAULT_NAME, Waypoint.fromJson(blankName).orElseThrow().name());
        JsonObject badColor = HOME.toJson();
        badColor.addProperty("color", "not a colour");
        assertEquals(WaypointColors.DEFAULT, Waypoint.fromJson(badColor).orElseThrow().color());
    }

    @Test
    void nameSanitising() {
        assertEquals("My Base", Waypoint.sanitizeName("  My\tBase\n  "));
        assertEquals("Waypoint", Waypoint.sanitizeName(null));
        assertEquals("Waypoint", Waypoint.sanitizeName("   "));
        assertEquals("Waypoint", Waypoint.sanitizeName("\u0000\u0007"));
        assertEquals("A B", Waypoint.sanitizeName("A\u0000 \u0001 B"));
        String longName = Waypoint.sanitizeName("x".repeat(100));
        assertEquals(Waypoint.MAX_NAME_LENGTH, longName.length());
        String spaced = Waypoint.sanitizeName("word ".repeat(40));
        assertTrue(spaced.length() <= Waypoint.MAX_NAME_LENGTH);
        assertFalse(spaced.endsWith(" "), "no trailing space after truncation");
        assertEquals("Ünïcödé ✓", Waypoint.sanitizeName("Ünïcödé ✓"));
    }

    @Test
    void categoriesAreSanitisedAndSuggested() {
        assertEquals("Home", WaypointCategories.sanitize("home"));
        assertEquals("Portal", WaypointCategories.sanitize("  PORTAL "));
        assertEquals("my spot", WaypointCategories.sanitize("  my   spot  "));
        assertEquals(WaypointCategories.DEFAULT, WaypointCategories.sanitize(null));
        assertEquals(WaypointCategories.DEFAULT, WaypointCategories.sanitize(" \n"));
        assertEquals(WaypointCategories.MAX_LENGTH, WaypointCategories.sanitize("c".repeat(60)).length());
        assertTrue(WaypointCategories.isBuiltIn("resource"));
        assertFalse(WaypointCategories.isBuiltIn("Mine"));
        assertFalse(WaypointCategories.isBuiltIn(null));

        List<Waypoint> existing = List.of(HOME.withCategory("Mine"), HOME.withCategory("Ancient City"),
                HOME.withCategory("mine"), HOME.withCategory("Farm"));
        List<String> suggestions = WaypointCategories.suggestions(existing);
        assertEquals(List.of("Home", "Base", "Farm", "Portal", "Resource", "Other", "Ancient City", "Mine"),
                suggestions);
    }

    @Test
    void colours() {
        assertEquals("#FF7C5CFF", WaypointColors.toHex(0xFF7C5CFF));
        assertEquals(Optional.of(0xFF7C5CFF), WaypointColors.parse("#FF7C5CFF"));
        assertEquals(Optional.of(0xFF7C5CFF), WaypointColors.parse("7c5cff"), "6 digits become opaque");
        assertEquals(Optional.empty(), WaypointColors.parse("#12345"));
        assertEquals(Optional.empty(), WaypointColors.parse("#GGGGGG"));
        assertEquals(Optional.empty(), WaypointColors.parse((String) null));
        assertEquals(Optional.of(42), WaypointColors.parse(new JsonPrimitive(42)));
        assertEquals(Optional.empty(), WaypointColors.parse(new JsonPrimitive(true)));
        assertEquals(WaypointColors.PALETTE.get(0), WaypointColors.forIndex(0));
        assertEquals(WaypointColors.PALETTE.get(1), WaypointColors.forIndex(WaypointColors.PALETTE.size() + 1));
        assertEquals(WaypointColors.PALETTE.get(WaypointColors.PALETTE.size() - 1), WaypointColors.forIndex(-1));
    }

    private static JsonObject without(JsonObject json, String key) {
        json.remove(key);
        return json;
    }
}
