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
        assertEquals(HudPresets.find("pvp").orElseThrow().layout(), pvp.hud());
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
        assertEquals(120, dflt.settings().get("video.framerateLimit").getAsInt(), "vanilla default kept");
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
        assertEquals(6, manager.size());

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
        for (String id : List.of("my-setup-2", "pvp", "building", "performance", "recording")) {
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

    @Test
    void unsafeFilesInProfilesDirAreIgnored() throws IOException {
        Files.writeString(paths.profilesDir().resolve("Bad Name.json"), "{\"name\":\"x\"}");
        Files.writeString(paths.profilesDir().resolve("broken.json"), "{{{");
        ProfileManager fresh = new ProfileManager(json, paths, clock, settings, hud, crosshair, keybinds);
        fresh.load();
        assertEquals(BuiltInProfiles.IDS.size(), fresh.size());
    }
}
