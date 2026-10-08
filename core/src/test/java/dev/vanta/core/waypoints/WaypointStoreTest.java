package dev.vanta.core.waypoints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vanta.core.bridge.Vec3d;
import dev.vanta.core.config.JsonStore;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WaypointStoreTest {
    private static final String WORLD = "sp:New World";
    private static final String SERVER = "mp:play.example.net";
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    @TempDir
    Path dir;

    private MutableClock clock;
    private JsonStore jsonStore;
    private VantaPaths paths;
    private WaypointStore store;

    @BeforeEach
    void setUp() {
        clock = MutableClock.standard();
        jsonStore = new JsonStore(clock);
        paths = new VantaPaths(dir);
        store = new WaypointStore(jsonStore, paths, clock);
        store.load();
    }

    @Test
    void addQueriesByWorldAndName() {
        Waypoint home = store.add(WORLD, OVERWORLD, "Home", new Vec3d(0, 64, 0)).orElseThrow();
        clock.advance(10);
        Waypoint farm = store.add(WORLD, NETHER, "Farm", new Vec3d(100, 70, 0), "Farm", 0xFF00FF00).orElseThrow();
        clock.advance(10);
        Waypoint spawn = store.add(SERVER, OVERWORLD, "Spawn", new Vec3d(0, 0, 0)).orElseThrow();

        assertEquals(3, store.size());
        assertEquals(List.of(home, farm), store.forWorld(WORLD));
        assertEquals(List.of(home), store.forWorld(WORLD, OVERWORLD));
        assertEquals(List.of(farm), store.forWorld(WORLD, NETHER));
        assertEquals(List.of(spawn), store.forWorld(SERVER));
        assertEquals(List.of(), store.forWorld("sp:Other"));
        assertEquals(List.of("Home", "Farm"), store.names(WORLD));
        assertEquals(List.of(WORLD, SERVER), store.worldKeys());
        assertTrue(store.has(WORLD, "home"), "names compare case-insensitively");
        assertTrue(store.has(WORLD, "  Farm "));
        assertFalse(store.has(SERVER, "Home"), "names are per world");
        assertEquals(Optional.of(farm), store.find(WORLD, "FARM"));
        assertEquals(Optional.of(home), store.find(home.id()));
        assertEquals(Optional.empty(), store.find("nope"));
        assertEquals(Optional.empty(), store.find(null));
        assertEquals(Optional.empty(), store.find(WORLD, null));

        assertEquals(WaypointCategories.DEFAULT, home.category());
        assertEquals(WaypointColors.forIndex(0), home.color());
        assertEquals(WaypointColors.forIndex(2), spawn.color(), "palette cycles with the count");
        assertEquals("Farm", farm.category());
        assertEquals(0xFF00FF00, farm.color());
        assertEquals(clock.millis() - 20, home.createdAt());
        assertTrue(home.enabled());
        assertTrue(store.isDirty());
    }

    @Test
    void idsAreUniqueEvenWithinOneMillisecond() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            ids.add(store.add(WORLD, OVERWORLD, "wp " + i, Vec3d.ZERO).orElseThrow().id());
        }
        assertEquals(50, ids.size());
        for (String id : ids) {
            assertTrue(id.startsWith("wp"), id);
        }
    }

    @Test
    void duplicateNamesAreRejectedPerWorld() {
        assertTrue(store.add(WORLD, OVERWORLD, "Home", Vec3d.ZERO).isPresent());
        assertEquals(Optional.empty(), store.add(WORLD, NETHER, " home ", Vec3d.ZERO),
                "same name in the same world, even in another dimension and letter case");
        assertEquals(Optional.empty(), store.add(" " + WORLD + " ", OVERWORLD, "Home", Vec3d.ZERO),
                "a padded world key is the same world");
        assertTrue(store.add(SERVER, OVERWORLD, "Home", Vec3d.ZERO).isPresent(), "other world is fine");
        assertEquals(2, store.size());
    }

    @Test
    void nothingIsStoredWithoutAWorld() {
        assertEquals(Optional.empty(), store.add(WorldKeys.NONE, OVERWORLD, "Home", Vec3d.ZERO));
        assertEquals(Optional.empty(), store.add("", OVERWORLD, "Home", Vec3d.ZERO));
        assertEquals(Optional.empty(), store.add(null, OVERWORLD, "Home", Vec3d.ZERO));
        assertEquals(0, store.size());
        assertFalse(store.isDirty());
    }

    @Test
    void namesCategoriesAndDimensionsAreSanitised() {
        Waypoint w = store.add(WORLD, "  ", "  my\u0000 \t base  ", Vec3d.ZERO, "  home ", 1).orElseThrow();
        assertEquals("my base", w.name());
        assertEquals("Home", w.category());
        assertEquals(Waypoint.UNKNOWN_DIMENSION, w.dimension());
        Waypoint blank = store.add(WORLD, OVERWORLD, "   ", Vec3d.ZERO).orElseThrow();
        assertEquals(Waypoint.DEFAULT_NAME, blank.name());
        assertEquals(Optional.empty(), store.add(WORLD, OVERWORLD, "", Vec3d.ZERO), "second blank name collides");
        Waypoint longName = store.add(WORLD, OVERWORLD, "n".repeat(200), Vec3d.ZERO).orElseThrow();
        assertEquals(Waypoint.MAX_NAME_LENGTH, longName.name().length());
    }

    @Test
    void limitOfFiveHundredWaypoints() {
        for (int i = 0; i < WaypointStore.MAX_WAYPOINTS; i++) {
            assertFalse(store.isFull());
            assertTrue(store.add(i % 2 == 0 ? WORLD : SERVER, OVERWORLD, "wp " + i, new Vec3d(i, 0, 0)).isPresent(),
                    "add " + i);
        }
        assertTrue(store.isFull());
        assertEquals(WaypointStore.MAX_WAYPOINTS, store.size());
        assertEquals(Optional.empty(), store.add(WORLD, OVERWORLD, "one too many", Vec3d.ZERO));
        assertTrue(store.remove(WORLD, "wp 0"));
        assertFalse(store.isFull());
        assertTrue(store.add(WORLD, OVERWORLD, "fits again", Vec3d.ZERO).isPresent());
    }

    @Test
    void removeToggleAndListeners() {
        AtomicInteger events = new AtomicInteger();
        Runnable unsubscribe = store.onChange(events::incrementAndGet);
        Waypoint home = store.add(WORLD, OVERWORLD, "Home", Vec3d.ZERO).orElseThrow();
        Waypoint farm = store.add(WORLD, OVERWORLD, "Farm", Vec3d.ZERO).orElseThrow();
        assertEquals(2, events.get());

        assertTrue(store.setEnabled(home.id(), false));
        assertFalse(store.find(home.id()).orElseThrow().enabled());
        assertEquals(3, events.get());
        assertTrue(store.setEnabled(home.id(), false), "found, nothing to change");
        assertEquals(3, events.get(), "no event without a change");
        assertFalse(store.setEnabled("missing", true));

        assertEquals(Optional.of(true), store.toggle(home.id()));
        assertEquals(Optional.of(false), store.toggle(home.id()));
        assertEquals(Optional.empty(), store.toggle("missing"));
        assertTrue(store.toggle(WORLD, "farm", false));
        assertFalse(store.find(farm.id()).orElseThrow().enabled());
        assertFalse(store.toggle(WORLD, "nope", true));
        assertEquals(List.of(), store.enabledIn(WORLD, OVERWORLD));
        assertTrue(store.toggle(WORLD, "Farm", true));
        assertEquals(List.of(store.find(farm.id()).orElseThrow()), store.enabledIn(WORLD, OVERWORLD));

        int before = events.get();
        assertTrue(store.remove(WORLD, "HOME"));
        assertFalse(store.remove(WORLD, "HOME"));
        assertFalse(store.remove("missing"));
        assertEquals(before + 1, events.get());
        assertEquals(List.of("Farm"), store.names(WORLD));

        unsubscribe.run();
        store.remove(farm.id());
        assertEquals(before + 1, events.get(), "unsubscribed");
        assertEquals(0, store.size());
    }

    @Test
    void listenerFailuresDoNotStopOtherListeners() {
        AtomicInteger good = new AtomicInteger();
        store.onChange(() -> {
            throw new IllegalStateException("boom");
        });
        store.onChange(good::incrementAndGet);
        assertTrue(store.add(WORLD, OVERWORLD, "Home", Vec3d.ZERO).isPresent());
        assertEquals(1, good.get());
    }

    @Test
    void updateKeepsIdentityAndRejectsClashes() {
        Waypoint home = store.add(WORLD, OVERWORLD, "Home", Vec3d.ZERO).orElseThrow();
        Waypoint farm = store.add(WORLD, OVERWORLD, "Farm", Vec3d.ZERO).orElseThrow();
        clock.advance(5000);

        Waypoint edited = home.withName("  Main  Base ").withCoordinates(10, 70, -5).withCategory("base")
                .withColor(0xFF112233).withEnabled(false);
        Waypoint stored = store.update(edited).orElseThrow();
        assertEquals(home.id(), stored.id());
        assertEquals("Main Base", stored.name());
        assertEquals(new Vec3d(10, 70, -5), stored.position());
        assertEquals("Base", stored.category());
        assertEquals(0xFF112233, stored.color());
        assertFalse(stored.enabled());
        assertEquals(home.createdAt(), stored.createdAt(), "creation time never changes");
        assertEquals(WORLD, stored.worldKey());
        assertEquals(Optional.of(stored), store.find(home.id()));
        assertFalse(store.has(WORLD, "Home"));

        assertEquals(Optional.empty(), store.update(stored.withName("farm")), "name taken by another waypoint");
        assertEquals("Main Base", store.find(home.id()).orElseThrow().name());
        assertEquals(Optional.of(stored), store.update(stored.withName("MAIN BASE").withName("Main Base")),
                "unchanged content is accepted");
        assertEquals(Optional.empty(), store.update(new Waypoint("ghost", "x", WORLD, OVERWORLD, 0, 0, 0, 0, "c",
                true, 0)));

        // the world key of an update is ignored: the waypoint stays in its world
        Waypoint moved = new Waypoint(farm.id(), "Farm", SERVER, NETHER, 1, 2, 3, farm.color(), "Farm", true, 99);
        Waypoint result = store.update(moved).orElseThrow();
        assertEquals(WORLD, result.worldKey());
        assertEquals(NETHER, result.dimension());
        assertEquals(farm.createdAt(), result.createdAt());
    }

    @Test
    void searchMatchesNameOrCategoryWithinAWorld() {
        store.add(WORLD, OVERWORLD, "Iron farm", Vec3d.ZERO, "Farm", 1);
        store.add(WORLD, OVERWORLD, "Nether portal", Vec3d.ZERO, "Portal", 1);
        store.add(WORLD, OVERWORLD, "Village", Vec3d.ZERO, "Other", 1);
        store.add(SERVER, OVERWORLD, "Farm hub", Vec3d.ZERO, "Farm", 1);

        assertEquals(List.of("Iron farm"), names(store.search(WORLD, "FARM")));
        assertEquals(List.of("Nether portal"), names(store.search(WORLD, "port")));
        assertEquals(List.of("Village"), names(store.search(WORLD, "other")), "category matches");
        assertEquals(3, store.search(WORLD, "  ").size(), "blank query lists everything");
        assertEquals(3, store.search(WORLD, null).size());
        assertEquals(List.of(), store.search(WORLD, "diamond"));
        assertEquals(List.of("Iron farm", "Farm hub"), names(store.searchAll("farm")));
        assertEquals(4, store.searchAll("").size());
    }

    @Test
    void sortingByNameDistanceAndCreated() {
        Waypoint far = store.add(WORLD, OVERWORLD, "banana", new Vec3d(100, 64, 0)).orElseThrow();
        clock.advance(1);
        Waypoint near = store.add(WORLD, OVERWORLD, "Cherry", new Vec3d(3, 64, 4)).orElseThrow();
        clock.advance(1);
        Waypoint mid = store.add(WORLD, OVERWORLD, "apple", new Vec3d(-30, 64, 0)).orElseThrow();
        List<Waypoint> all = store.forWorld(WORLD);

        assertEquals(List.of(mid, far, near), WaypointStore.sort(all, WaypointSort.NAME, Optional.empty()),
                "case-insensitive by name");
        Vec3d player = new Vec3d(0, 64, 0);
        assertEquals(List.of(near, mid, far), WaypointStore.sort(all, WaypointSort.DISTANCE, Optional.of(player)));
        assertEquals(List.of(far, near, mid), WaypointStore.sort(all, WaypointSort.DISTANCE,
                Optional.of(new Vec3d(100, 64, 0))), "0, 97 and 130 blocks away");
        assertEquals(List.of(mid, far, near), WaypointStore.sort(all, WaypointSort.DISTANCE, Optional.empty()),
                "no origin: falls back to name order");
        assertEquals(List.of(mid, near, far), WaypointStore.sort(all, WaypointSort.CREATED, Optional.empty()),
                "newest first");
        assertEquals(all, store.forWorld(WORLD), "sorting never reorders the store");
        assertEquals(Optional.of(WaypointSort.DISTANCE), WaypointSort.fromId(" Distance "));
        assertEquals(Optional.empty(), WaypointSort.fromId("nearest"));
        assertEquals("vanta.waypoints.sort.created", WaypointSort.CREATED.langKey());
    }

    @Test
    void sortTiesAreStable() {
        Waypoint a = store.add(WORLD, OVERWORLD, "Same", new Vec3d(1, 0, 0)).orElseThrow();
        Waypoint b = store.add(SERVER, OVERWORLD, "same", new Vec3d(1, 0, 0)).orElseThrow();
        List<Waypoint> sorted = WaypointStore.sort(List.of(b, a), WaypointSort.DISTANCE, Optional.of(Vec3d.ZERO));
        assertEquals(List.of(a, b), sorted, "equal distance and folded name: upper case first, then id");
        assertEquals(sorted, WaypointStore.sort(List.of(a, b), WaypointSort.CREATED, Optional.empty()));
    }

    @Test
    void categorySuggestionsIncludeThoseInUse() {
        store.add(WORLD, OVERWORLD, "a", Vec3d.ZERO, "Mine shaft", 1);
        store.add(SERVER, OVERWORLD, "b", Vec3d.ZERO, "farm", 1);
        List<String> categories = store.categories();
        assertEquals(WaypointCategories.SUGGESTIONS, categories.subList(0, WaypointCategories.SUGGESTIONS.size()));
        assertEquals(List.of("Mine shaft"), categories.subList(WaypointCategories.SUGGESTIONS.size(),
                categories.size()));
    }

    @Test
    void persistenceRoundTripAndDirtyTracking() throws IOException {
        store.saveIfDirty();
        assertFalse(Files.exists(paths.waypointsFile()), "an unchanged store touches no file");

        Waypoint home = store.add(WORLD, OVERWORLD, "Home", new Vec3d(1.5, 64, -2.25), "Home", 0xFF123456)
                .orElseThrow();
        store.setEnabled(home.id(), false);
        Waypoint spawn = store.add(SERVER, NETHER, "Spawn", Vec3d.ZERO).orElseThrow();
        assertTrue(store.isDirty());
        store.saveIfDirty();
        assertFalse(store.isDirty());
        assertTrue(Files.exists(paths.waypointsFile()));
        assertFalse(Files.exists(dir.resolve("waypoints.json.tmp")));

        JsonObject json = JsonParser.parseReader(Files.newBufferedReader(paths.waypointsFile())).getAsJsonObject();
        assertEquals(WaypointStore.SCHEMA_VERSION, json.get(JsonStore.SCHEMA_VERSION).getAsInt());
        assertEquals(2, json.getAsJsonArray("waypoints").size());
        assertEquals("#FF123456", json.getAsJsonArray("waypoints").get(0).getAsJsonObject().get("color")
                .getAsString());

        WaypointStore reloaded = new WaypointStore(jsonStore, paths, clock);
        reloaded.load();
        assertFalse(reloaded.isDirty());
        assertEquals(List.of(home.withEnabled(false), spawn), reloaded.all());
        assertEquals(store.toJson(), reloaded.toJson());

        // a fresh id after reload does not collide with the stored ones
        Waypoint third = reloaded.add(WORLD, OVERWORLD, "Third", Vec3d.ZERO).orElseThrow();
        assertNotEquals(home.id(), third.id());
        assertNotEquals(spawn.id(), third.id());
        assertEquals(3, reloaded.size());
    }

    @Test
    void loadReplacesInMemoryStateAndFiresListeners() {
        store.add(WORLD, OVERWORLD, "Home", Vec3d.ZERO);
        store.save();
        store.add(WORLD, OVERWORLD, "Unsaved", Vec3d.ZERO);
        AtomicInteger events = new AtomicInteger();
        store.onChange(events::incrementAndGet);
        store.load();
        assertEquals(List.of("Home"), store.names(WORLD));
        assertFalse(store.isDirty());
        assertEquals(1, events.get());
    }

    @Test
    void newerSchemaIsReadTolerantly() throws IOException {
        Files.writeString(paths.waypointsFile(), """
                {"schemaVersion": 7, "futureField": {"x": 1},
                 "waypoints": [{"id":"w1","name":"Home","worldKey":"sp:New World","dimension":"minecraft:overworld",
                                "x":1,"y":2,"z":3,"color":"#FF00FF00","category":"Home","enabled":true,
                                "createdAt":5,"futureFlag":true}]}
                """, StandardCharsets.UTF_8);
        store.load();
        assertEquals(1, store.size());
        assertEquals("Home", store.names(WORLD).get(0));
        assertEquals(0, jsonStore.backups(paths.waypointsFile()).size(), "a newer file is never moved aside");
        store.save();
        JsonObject json = JsonParser.parseReader(Files.newBufferedReader(paths.waypointsFile())).getAsJsonObject();
        assertEquals(WaypointStore.SCHEMA_VERSION, json.get(JsonStore.SCHEMA_VERSION).getAsInt(),
                "written back in the current schema");
    }

    @Test
    void missingSchemaVersionIsTreatedAsVersionOne() throws IOException {
        Files.writeString(paths.waypointsFile(), """
                {"waypoints": [{"id":"w1","name":"Home","worldKey":"sp:New World","x":1,"y":2,"z":3}]}
                """, StandardCharsets.UTF_8);
        store.load();
        assertEquals(1, store.size());
        assertEquals(Waypoint.UNKNOWN_DIMENSION, store.all().get(0).dimension());
        assertFalse(store.isDirty());
    }

    @Test
    void invalidEntriesAndDuplicateIdsAreSkipped() throws IOException {
        Files.writeString(paths.waypointsFile(), """
                {"schemaVersion": 1, "waypoints": [
                  {"id":"w1","name":"Home","worldKey":"sp:a","x":1,"y":2,"z":3},
                  {"id":"w1","name":"Duplicate id","worldKey":"sp:a","x":1,"y":2,"z":3},
                  {"name":"No id","worldKey":"sp:a","x":1,"y":2,"z":3},
                  {"id":"w3","worldKey":"sp:a","x":1,"y":2,"z":3},
                  {"id":"w4","name":"Bad x","worldKey":"sp:a","x":"east","y":2,"z":3},
                  "not an object",
                  42,
                  {"id":"w5","name":"Ok","worldKey":"mp:srv","x":0,"y":0,"z":0}
                ]}
                """, StandardCharsets.UTF_8);
        store.load();
        assertEquals(List.of("w1", "w5"), store.all().stream().map(Waypoint::id).toList());
    }

    @Test
    void waypointsArrayMissingOrWrongTypeYieldsEmptyStore() throws IOException {
        Files.writeString(paths.waypointsFile(), "{\"schemaVersion\": 1, \"waypoints\": {\"oops\": 1}}",
                StandardCharsets.UTF_8);
        store.load();
        assertEquals(0, store.size());
        Files.writeString(paths.waypointsFile(), "{\"schemaVersion\": 1}", StandardCharsets.UTF_8);
        store.load();
        assertEquals(0, store.size());
    }

    @Test
    void corruptFileIsMovedAsideAndTheStoreKeepsWorking() throws IOException {
        Files.writeString(paths.waypointsFile(), "{ not json", StandardCharsets.UTF_8);
        store.load();
        assertEquals(0, store.size());
        assertFalse(store.isDirty());
        assertFalse(Files.exists(paths.waypointsFile()), "corrupt file moved aside");
        List<Path> backups = jsonStore.backups(paths.waypointsFile());
        assertEquals(1, backups.size());
        assertEquals("waypoints.broken-" + clock.millis() + ".json", backups.get(0).getFileName().toString());
        assertEquals("{ not json", Files.readString(backups.get(0)));

        assertTrue(store.add(WORLD, OVERWORLD, "Home", Vec3d.ZERO).isPresent());
        store.saveIfDirty();
        WaypointStore reloaded = new WaypointStore(jsonStore, paths, clock);
        reloaded.load();
        assertEquals(List.of("Home"), reloaded.names(WORLD));
    }

    @Test
    void overLongFileIsCappedAtTheLimit() throws IOException {
        StringBuilder sb = new StringBuilder("{\"schemaVersion\":1,\"waypoints\":[");
        for (int i = 0; i < WaypointStore.MAX_WAYPOINTS + 20; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"id\":\"w").append(i).append("\",\"name\":\"n").append(i)
                    .append("\",\"worldKey\":\"sp:a\",\"x\":0,\"y\":0,\"z\":0}");
        }
        sb.append("]}");
        Files.writeString(paths.waypointsFile(), sb.toString(), StandardCharsets.UTF_8);
        store.load();
        assertEquals(WaypointStore.MAX_WAYPOINTS, store.size());
        assertTrue(store.isFull());
        assertEquals("w0", store.all().get(0).id());
    }

    private static List<String> names(List<Waypoint> waypoints) {
        return waypoints.stream().map(Waypoint::name).toList();
    }
}
