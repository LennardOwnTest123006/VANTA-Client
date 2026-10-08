package dev.vanta.core.profiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.vanta.core.bridge.FakeKeybindBridge;
import dev.vanta.core.bridge.FakeOptionsBridge;
import dev.vanta.core.bridge.KeyRef;
import dev.vanta.core.bridge.VanillaOption;
import dev.vanta.core.config.JsonStore;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.cosmetics.Badge;
import dev.vanta.core.crosshair.CrosshairPresets;
import dev.vanta.core.crosshair.CrosshairShape;
import dev.vanta.core.crosshair.CrosshairStore;
import dev.vanta.core.hud.HudPresets;
import dev.vanta.core.hud.HudStore;
import dev.vanta.core.keybinds.VantaKeys;
import dev.vanta.core.perf.FpsLimitPreset;
import dev.vanta.core.perf.PerformancePreset;
import dev.vanta.core.settings.Setting;
import dev.vanta.core.settings.SettingsStore;
import dev.vanta.core.settings.VantaSettings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileManagerTest {
    @TempDir
    Path dir;

    private MutableClock clock;
    private VantaPaths paths;
    private JsonStore json;
    private FakeOptionsBridge options;
    private SettingsStore settings;
    private HudStore hud;
    private CrosshairStore crosshair;
    private FakeKeybindBridge keybinds;
    private ProfileManager manager;

    @BeforeEach
    void setUp() throws IOException {
        clock = MutableClock.standard();
        paths = new VantaPaths(dir.resolve("config").resolve("vanta"));
        paths.createDirectories();
        json = new JsonStore(clock);
        options = new FakeOptionsBridge();
        settings = new SettingsStore(VantaSettings.registry(), json, paths.settingsFile(), Optional.of(options));
        settings.load();
        hud = new HudStore(json, paths);
        hud.load();
        crosshair = new CrosshairStore(json, paths);
        crosshair.load();
        keybinds = new FakeKeybindBridge();
        manager = new ProfileManager(json, paths, clock, settings, hud, crosshair, keybinds);
        manager.load();
    }

    @Test
    void builtInsAreCreatedOnFirstRunWithSensibleDifferences() {
        assertEquals(BuiltInProfiles.IDS, manager.list().stream().map(Profile::id).toList());
        assertEquals(Optional.of("default"), manager.activeId());
        for (String id : BuiltInProfiles.IDS) {
            assertTrue(Files.exists(paths.profilesDir().resolve(id + ".json")), id);
        }
        Profile pvp = manager.find("pvp").orElseThrow();
        assertEquals("PvP", pvp.name());
        assertEquals(BuiltInProfiles.nexusHud(dev.vanta.core.ai.NexusHudPreset.PVP), pvp.hud(),
                "the PvP profile carries the Nexus PvP preset applied to the Default layout");
        assertEquals(CrosshairPresets.find("bold").orElseThrow().style(), pvp.crosshair());
        assertEquals("high", pvp.settings().get("performance.perfPreset").getAsString());
        assertEquals(16, pvp.settings().get("video.renderDistance").getAsInt());
        Profile building = manager.find("building").orElseThrow();
        assertEquals(24, building.settings().get("video.renderDistance").getAsInt());
        assertEquals(85, building.settings().get("video.fov").getAsInt());
        Profile perf = manager.find("performance").orElseThrow();
        assertEquals("solid", perf.settings().get("menu.background").getAsString());
        assertEquals("boost", perf.settings().get("performance.perfPreset").getAsString());
        assertEquals("unlimited", perf.settings().get("performance.fpsLimitPreset").getAsString());
        assertEquals(260, perf.settings().get("video.framerateLimit").getAsInt(), "no cap in the Performance profile");
        assertFalse(perf.settings().get("video.vsync").getAsBoolean());
        assertEquals(260, pvp.settings().get("video.framerateLimit").getAsInt());
        Profile dflt = manager.find("default").orElseThrow();
        assertEquals("balanced", dflt.settings().get("performance.perfPreset").getAsString());
        for (Profile profile : manager.list()) {
            // Activation replays every setting: the vanilla limit and VSync a profile carries must be the ones its
            // own frame-rate choice stands for, never the registry defaults (120 FPS with VSync on).
            FpsLimitPreset limit = FpsLimitPreset.valueOf(
                    profile.settings().get("performance.fpsLimitPreset").getAsString().toUpperCase(Locale.ROOT));
            assertEquals(limit.framerateLimit(), profile.settings().get("video.framerateLimit").getAsInt(),
                    profile.id());
            assertEquals(limit.vsync(), profile.settings().get("video.vsync").getAsBoolean(), profile.id());
            assertEquals(260, profile.settings().get("video.framerateLimit").getAsInt(),
                    profile.id() + " never caps the frame rate");
        }
        Profile recording = manager.find("recording").orElseThrow();
        assertEquals("dot", recording.cosmetics().crosshairPreset());
        assertFalse(recording.settings().get("privacy.statsTrackServers").getAsBoolean());
    }

    @Test
    void activateAppliesEverything() {
        List<Profile> activated = new ArrayList<>();
        manager.onActivated(activated::add);
        assertTrue(manager.activate("performance"));
        assertEquals(PerformancePreset.BOOST, settings.get(VantaSettings.PERFORMANCE_PRESET));
        assertEquals(5, options.getInt(VanillaOption.RENDER_DISTANCE, 0), "vanilla options applied");
        assertEquals("FAST", options.getEnum(VanillaOption.GRAPHICS_MODE, ""));
        assertEquals(260, options.getInt(VanillaOption.FRAMERATE_LIMIT, 0), "the profile removes the cap");
        assertFalse(options.getBoolean(VanillaOption.VSYNC, true));
        assertEquals(HudPresets.find("performance").orElseThrow().layout(), hud.layout());
        assertEquals(CrosshairShape.CROSS, crosshair.style().shape());
        assertEquals(1, activated.size());
        assertEquals(Optional.of("performance"), manager.activeId());

        assertTrue(manager.activate("pvp"));
        assertEquals(16, options.getInt(VanillaOption.RENDER_DISTANCE, 0));
        assertTrue(crosshair.style().dynamic());
        assertFalse(manager.activate("nope"));

        ProfileManager reloaded = new ProfileManager(json, paths, clock, settings, hud, crosshair, keybinds);
        reloaded.load();
        assertEquals(Optional.of("pvp"), reloaded.activeId(), "active id persisted in state.json");
    }

    @Test
    void createDuplicateRenameDeleteAndUpdate() {
        settings.set(VantaSettings.HUD_GLOBAL_OPACITY, 0.6);
        settings.set(VantaSettings.COSMETICS_BADGE, Badge.CONTRIBUTOR);
        keybinds.setKey(VantaKeys.ZOOM, KeyRef.keyboard(88, "key.keyboard.x"));
        Profile created = manager.createFromCurrent("My Setup", "star");
        assertEquals("my-setup", created.id());
        assertEquals(0.6, created.settings().get("hud.globalOpacity").getAsDouble());
        assertEquals(Badge.CONTRIBUTOR, created.cosmetics().badge());
        assertEquals(KeyRef.keyboard(88, "key.keyboard.x"), created.keybinds().get(VantaKeys.ZOOM));
        assertEquals(BuiltInProfiles.IDS.size() + 1, manager.size());

        Profile copy = manager.duplicate("my-setup", "My Setup").orElseThrow();
        assertEquals("my-setup-2", copy.id());
        assertTrue(manager.rename("my-setup-2", "Renamed"));
        assertEquals("Renamed", manager.find("my-setup-2").orElseThrow().name());
        assertEquals("my-setup-2", manager.find("my-setup-2").orElseThrow().id(), "id is stable");
        assertTrue(manager.setIcon("my-setup-2", "grid"));
        assertThrows(IllegalArgumentException.class, () -> manager.rename("my-setup-2", "   "));

        settings.resetAll();
        keybinds.resetAll();
        manager.activate("my-setup");
        assertEquals(0.6, settings.get(VantaSettings.HUD_GLOBAL_OPACITY));
        assertEquals(KeyRef.keyboard(88, "key.keyboard.x"), keybinds.find(VantaKeys.ZOOM).orElseThrow().boundKey());

        settings.set(VantaSettings.HUD_GLOBAL_OPACITY, 0.3);
        clock.advance(1000);
        Profile updated = manager.updateActiveFromCurrent().orElseThrow();
        assertEquals(0.3, updated.settings().get("hud.globalOpacity").getAsDouble());
        assertTrue(updated.updatedAt() > updated.createdAt());

        assertTrue(manager.delete("my-setup"));
        assertEquals(Optional.of("default"), manager.activeId(), "deleting the active profile activates another");
        assertFalse(Files.exists(paths.profilesDir().resolve("my-setup.json")));
        for (String id : List.of("my-setup-2", "pvp", "building", "performance", "recording", "survival", "minimal")) {
            assertTrue(manager.delete(id));
        }
        assertFalse(manager.delete("default"), "the last profile cannot be deleted");
        assertEquals(1, manager.size());
    }

    @Test
    void exportAndImportRoundTrip() throws Exception {
        settings.set(VantaSettings.HUD_SNAP, false);
        Profile created = manager.createFromCurrent("Shared Setup", "profile");
        Path export = dir.resolve(ProfileManager.exportFileName(created));
        assertEquals("vanta-profile-shared-setup.json", export.getFileName().toString());
        assertTrue(manager.exportTo(created.id(), export));
        String text = Files.readString(export);
        assertTrue(text.contains("\"hud.snap\": false"));
        assertFalse(text.contains(dir.toString()), "no path information inside the file");

        Profile imported = manager.importFrom(export);
        assertEquals("shared-setup-2", imported.id());
        assertEquals("Shared Setup (2)", imported.name(), "name made unique");
        assertEquals(created.settings(), imported.settings());
        assertEquals(created.hud(), imported.hud());
        assertEquals(created.crosshair(), imported.crosshair());
        assertEquals(created.cosmetics(), imported.cosmetics());
        assertTrue(Files.exists(paths.profilesDir().resolve("shared-setup-2.json")));
    }

    @Test
    void importRejectsOversizeWrongSchemaAndTraversal() throws Exception {
        Path big = dir.resolve("big.json");
        Files.writeString(big, "{\"name\":\"x\",\"pad\":\"" + "a".repeat((int) ProfileManager.MAX_IMPORT_BYTES) + "\"}");
        assertEquals(ProfileImportException.Reason.TOO_LARGE,
                assertThrows(ProfileImportException.class, () -> manager.importFrom(big)).reason());

        Path newer = dir.resolve("newer.json");
        Files.writeString(newer, "{\"schemaVersion\": 99, \"name\": \"Future\"}");
        assertEquals(ProfileImportException.Reason.UNSUPPORTED_SCHEMA,
                assertThrows(ProfileImportException.class, () -> manager.importFrom(newer)).reason());

        Path notJson = dir.resolve("text.json");
        Files.writeString(notJson, "hello");
        assertEquals(ProfileImportException.Reason.NOT_JSON,
                assertThrows(ProfileImportException.class, () -> manager.importFrom(notJson)).reason());

        Path array = dir.resolve("array.json");
        Files.writeString(array, "[]");
        assertEquals(ProfileImportException.Reason.NOT_JSON,
                assertThrows(ProfileImportException.class, () -> manager.importFrom(array)).reason());

        Path noName = dir.resolve("noname.json");
        Files.writeString(noName, "{\"schemaVersion\": 1}");
        assertEquals(ProfileImportException.Reason.INVALID_FIELD,
                assertThrows(ProfileImportException.class, () -> manager.importFrom(noName)).reason());

        assertEquals(ProfileImportException.Reason.IO_ERROR,
                assertThrows(ProfileImportException.class, () -> manager.importFrom(dir.resolve("missing.json")))
                        .reason());

        Path traversal = dir.resolve("traversal.json");
        Files.writeString(traversal, """
                {"schemaVersion": 1, "id": "../../etc/passwd", "name": "../../../evil", "icon": "../x",
                 "settings": {"hud.enabled": false, "unknown.key": "ignored", "huge": "%s",
                              "general.themeId": "x"},
                 "keybinds": {"key.forward": "keysym:65:key.keyboard.a", "key.vanta.zoom": "keysym:90:key.keyboard.z"},
                 "hud": {"widgets": [{"type": "fps", "id": "fps"}]},
                 "extra": {"nested": [1,2,3]}}
                """.formatted("x".repeat(5000)), StandardCharsets.UTF_8);
        Profile imported = manager.importFrom(traversal);
        assertEquals("evil", imported.id(), "id derived from the sanitised name, never from the file");
        assertFalse(imported.id().contains("."));
        assertFalse(imported.id().contains("/"));
        assertEquals(Profile.DEFAULT_ICON, imported.icon());
        assertFalse(imported.settings().containsKey("huge"), "over-long strings dropped");
        assertTrue(imported.settings().containsKey("unknown.key"), "unknown ids kept but ignored on apply");
        assertEquals(1, imported.keybinds().size(), "only VANTA keys accepted");
        assertEquals(KeyRef.keyboard(90, "key.keyboard.z"), imported.keybinds().get(VantaKeys.ZOOM));
        assertEquals(1, imported.hud().size());
        assertTrue(Files.exists(paths.profilesDir().resolve(imported.id() + ".json")));
        assertTrue(manager.activate(imported.id()));
        assertFalse(settings.get(VantaSettings.HUD_ENABLED));
    }

    @Test
    void codecValidatesNamesAndIds() throws ProfileImportException {
        JsonObject json = new JsonObject();
        json.addProperty("name", "n".repeat(500));
        Profile profile = ProfileCodec.fromJson(json, "ok");
        assertEquals(Profile.MAX_NAME, profile.name().length());
        assertThrows(ProfileImportException.class, () -> ProfileCodec.fromJson(json, "Not Safe"));
        assertThrows(IllegalArgumentException.class, () -> profile.withName("", 0));
        @SuppressWarnings("unchecked")
        Setting<Integer> fov = (Setting<Integer>) VantaSettings.VIDEO_FOV;
        assertEquals(70, fov.defaultValue());
    }

    /**
     * Rewrites a built-in profile file the way client 1.0.0–1.1.0 wrote it: schema 1 and the old preset table's
     * frame-rate cap in the vanilla-bound keys. {@code touched} moves updatedAt past createdAt, as every edit does.
     */
    private void writeLegacy(String id, int framerateLimit, String fpsPreset, String perfPreset, boolean touched) {
        Path file = paths.profilesDir().resolve(id + ".json");
        JsonObject o = json.readObject(file).orElseThrow();
        o.addProperty("schemaVersion", 1);
        if (touched) {
            o.addProperty("updatedAt", o.get("createdAt").getAsLong() + 1000);
        }
        JsonObject values = o.getAsJsonObject("settings");
        values.addProperty("video.framerateLimit", framerateLimit);
        values.addProperty("video.vsync", false);
        values.addProperty("performance.fpsLimitPreset", fpsPreset);
        values.addProperty("performance.perfPreset", perfPreset);
        json.writeObject(file, o);
    }

    private ProfileManager reload() {
        ProfileManager fresh = new ProfileManager(json, paths, clock, settings, hud, crosshair, keybinds);
        fresh.load();
        return fresh;
    }

    @Test
    void upgradeReseedsUntouchedBuiltInsOnceAndLeavesEditedOnesAlone() {
        writeLegacy("performance", 60, "fps_60", "low", false);
        writeLegacy("default", 120, "unlimited", "balanced", false);
        writeLegacy("pvp", 60, "fps_60", "high", true);
        Path pvpFile = paths.profilesDir().resolve("pvp.json");
        JsonObject pvpJson = json.readObject(pvpFile).orElseThrow();
        pvpJson.getAsJsonObject("settings").addProperty("video.renderDistance", 7);
        json.writeObject(pvpFile, pvpJson);
        clock.advance(5_000);

        ProfileManager upgraded = reload();
        assertEquals(BuiltInProfiles.IDS.size(), upgraded.size(), "old files are loaded, not replaced wholesale");
        Profile performance = upgraded.find("performance").orElseThrow();
        assertEquals(260, performance.settings().get("video.framerateLimit").getAsInt(), "the 60 FPS cap is gone");
        assertFalse(performance.settings().get("video.vsync").getAsBoolean());
        assertEquals("unlimited", performance.settings().get("performance.fpsLimitPreset").getAsString());
        assertEquals("boost", performance.settings().get("performance.perfPreset").getAsString());
        assertEquals(Profile.SCHEMA_VERSION, performance.schemaVersion());
        assertEquals(performance.createdAt(), performance.updatedAt(), "still counts as untouched");
        Profile defaults = upgraded.find("default").orElseThrow();
        assertEquals(260, defaults.settings().get("video.framerateLimit").getAsInt(), "the 120 FPS cap is gone");
        assertEquals("unlimited", defaults.settings().get("performance.fpsLimitPreset").getAsString());
        Profile pvp = upgraded.find("pvp").orElseThrow();
        assertEquals(7, pvp.settings().get("video.renderDistance").getAsInt(), "the player's edit is kept");
        assertEquals(60, pvp.settings().get("video.framerateLimit").getAsInt(), "edited content is not re-seeded");
        assertEquals(Profile.SCHEMA_VERSION, pvp.schemaVersion());
        for (String id : BuiltInProfiles.IDS) {
            JsonObject onDisk = json.readObject(paths.profilesDir().resolve(id + ".json")).orElseThrow();
            assertEquals(Profile.SCHEMA_VERSION, onDisk.get("schemaVersion").getAsInt(), id + " re-written");
        }

        // Schema 2 files are never re-seeded again, so a cap the player puts into an untouched built-in stays.
        Path perfFile = paths.profilesDir().resolve("performance.json");
        JsonObject perfJson = json.readObject(perfFile).orElseThrow();
        perfJson.getAsJsonObject("settings").addProperty("video.framerateLimit", 60);
        json.writeObject(perfFile, perfJson);
        assertEquals(60, reload().find("performance").orElseThrow().settings().get("video.framerateLimit").getAsInt());
    }

    @Test
    void activationDerivesTheFrameRateFromTheProfilesPreset() {
        // A profile saved by 1.1.0 (or edited by hand) says "unlimited" but still carries vanilla's 120 / VSync on.
        Path file = paths.profilesDir().resolve("default.json");
        JsonObject o = json.readObject(file).orElseThrow();
        JsonObject values = o.getAsJsonObject("settings");
        values.addProperty("performance.fpsLimitPreset", "unlimited");
        values.addProperty("video.framerateLimit", 120);
        values.addProperty("video.vsync", true);
        json.writeObject(file, o);
        ProfileManager fresh = reload();
        assertEquals(120, fresh.find("default").orElseThrow().settings().get("video.framerateLimit").getAsInt(),
                "the file itself is left as it is");

        options.set(VanillaOption.FRAMERATE_LIMIT, 260);
        options.set(VanillaOption.VSYNC, false);
        assertTrue(fresh.activate("default"));
        assertEquals(260, options.getInt(VanillaOption.FRAMERATE_LIMIT, 0), "the preset wins over the stale cap");
        assertFalse(options.getBoolean(VanillaOption.VSYNC, true));
        assertEquals(FpsLimitPreset.UNLIMITED, settings.get(VantaSettings.PERFORMANCE_FPS_LIMIT_PRESET));

        // The other direction holds as well: a 60 FPS profile applies its cap even if the file says 260.
        values.addProperty("performance.fpsLimitPreset", "fps_60");
        values.addProperty("video.framerateLimit", 260);
        json.writeObject(file, o);
        assertTrue(reload().activate("default"));
        assertEquals(60, options.getInt(VanillaOption.FRAMERATE_LIMIT, 0));
    }

    @Test
    void unsafeFilesInProfilesDirAreIgnored() throws IOException {
        Files.writeString(paths.profilesDir().resolve("Bad Name.json"), "{\"name\":\"x\"}");
        Files.writeString(paths.profilesDir().resolve("broken.json"), "{{{");
        ProfileManager fresh = new ProfileManager(json, paths, clock, settings, hud, crosshair, keybinds);
        fresh.load();
        assertEquals(BuiltInProfiles.IDS.size(), fresh.size());
    }

    // ---- vanilla options the player set elsewhere ------------------------------------------------------------------

    @Test
    void aProfileSavedWhileTheGameIsCustomKeepsCustomAndNeverAppliesAFakeFancy() {
        options.graphicsPresetBundle = true;
        options.setFromGame(VanillaOption.GRAPHICS_MODE, "FAST");
        options.setFromGame(VanillaOption.RENDER_DISTANCE, 7);
        Profile mine = manager.createFromCurrent("Mine", "profile");
        assertEquals("CUSTOM", mine.settings().get("video.graphicsMode").getAsString());

        options.setOrder.clear();
        assertTrue(manager.activate(mine.id()));
        assertFalse(options.setOrder.contains(VanillaOption.GRAPHICS_MODE), "Custom is not written: "
                + options.setOrder);
        assertEquals(7, options.getInt(VanillaOption.RENDER_DISTANCE, 0));
        assertEquals("CUSTOM", options.values().get(VanillaOption.GRAPHICS_MODE));
    }

    @Test
    void aProfileStoresTheFrameRateTheGameReallyHas() {
        // Stale choice in settings.json (Unlimited), while the player set 60 FPS in vanilla Video Settings.
        assertEquals(FpsLimitPreset.UNLIMITED, settings.get(VantaSettings.PERFORMANCE_FPS_LIMIT_PRESET));
        options.setFromGame(VanillaOption.FRAMERATE_LIMIT, 60);
        options.setFromGame(VanillaOption.VSYNC, false);
        Profile mine = manager.createFromCurrent("Sixty", "profile");
        assertEquals("fps_60", mine.settings().get("performance.fpsLimitPreset").getAsString());
        assertEquals(60, mine.settings().get("video.framerateLimit").getAsInt());

        assertTrue(manager.activate("performance"));
        assertEquals(260, options.getInt(VanillaOption.FRAMERATE_LIMIT, 0));
        assertTrue(manager.activate(mine.id()));
        assertEquals(60, options.getInt(VanillaOption.FRAMERATE_LIMIT, 0), "the profile restores the real 60");

        options.setFromGame(VanillaOption.FRAMERATE_LIMIT, 140);
        Profile updated = manager.updateActiveFromCurrent().orElseThrow();
        assertEquals("fps_144", updated.settings().get("performance.fpsLimitPreset").getAsString());
    }

    @Test
    void builtInProfilesOnlyCarryTheVanillaOptionsTheirPresetDefines() {
        for (Profile profile : manager.list()) {
            assertFalse(profile.settings().containsKey("audio.master"), profile.id() + " carries a volume");
            assertFalse(profile.settings().containsKey("video.guiScale"), profile.id() + " carries the GUI scale");
            assertTrue(profile.settings().containsKey("video.graphicsMode"), profile.id());
            assertTrue(profile.settings().containsKey("video.renderDistance"), profile.id());
            assertTrue(profile.settings().containsKey("video.framerateLimit"), profile.id());
        }
        assertFalse(manager.find("default").orElseThrow().settings().containsKey("video.fov"));
        assertEquals(85, manager.find("building").orElseThrow().settings().get("video.fov").getAsInt(),
                "an explicit override stays");

        options.setFromGame(VanillaOption.MASTER_VOLUME, 0.3);
        options.setFromGame(VanillaOption.FOV, 95);
        options.setFromGame(VanillaOption.GUI_SCALE, 3);
        assertTrue(manager.activate("default"));
        assertEquals(0.3, (Double) options.values().get(VanillaOption.MASTER_VOLUME), 1e-9, "volume kept");
        assertEquals(95, options.getInt(VanillaOption.FOV, 0), "FOV kept");
        assertEquals(3, options.getInt(VanillaOption.GUI_SCALE, 0), "GUI scale kept");
    }

    @Test
    void untouchedBuiltInsOfAnEarlierReleaseAreReseededOnceAndEditedOnesAreKept() {
        // A 1.2.x install: schema-2 files carrying every vanilla default, and a state.json without the seed marker.
        for (String id : List.of("default", "pvp")) {
            Path file = paths.profilesDir().resolve(id + ".json");
            JsonObject o = json.readObject(file).orElseThrow();
            o.getAsJsonObject("settings").addProperty("audio.master", 1.0);
            if (id.equals("pvp")) {
                o.addProperty("updatedAt", o.get("createdAt").getAsLong() + 1000);
            }
            json.writeObject(file, o);
        }
        JsonObject state = new JsonObject();
        state.addProperty("schemaVersion", 1);
        state.addProperty("activeId", "pvp");
        json.writeObject(paths.profilesStateFile(), state);
        clock.advance(5_000);

        ProfileManager upgraded = reload();
        Profile dflt = upgraded.find("default").orElseThrow();
        assertFalse(dflt.settings().containsKey("audio.master"), "untouched built-in re-seeded");
        assertEquals(dflt.createdAt(), dflt.updatedAt(), "still counts as untouched");
        assertTrue(upgraded.find("pvp").orElseThrow().settings().containsKey("audio.master"),
                "the player's edited built-in is left alone");
        assertEquals(Optional.of("pvp"), upgraded.activeId(), "the active profile is kept");
        assertFalse(json.readObject(paths.profilesDir().resolve("default.json")).orElseThrow()
                .getAsJsonObject("settings").has("audio.master"), "re-seed written to disk");

        // Once per upgrade: a later change to the untouched file is not undone on the next start.
        Path file = paths.profilesDir().resolve("default.json");
        JsonObject o = json.readObject(file).orElseThrow();
        o.getAsJsonObject("settings").addProperty("audio.master", 0.5);
        json.writeObject(file, o);
        assertTrue(reload().find("default").orElseThrow().settings().containsKey("audio.master"));
    }
}
