package dev.vanta.core.profiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.bridge.FakeKeybindBridge;
import dev.vanta.core.bridge.FakeOptionsBridge;
import dev.vanta.core.config.JsonStore;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.crosshair.CrosshairStore;
import dev.vanta.core.hud.HudStore;
import dev.vanta.core.settings.SettingsStore;
import dev.vanta.core.settings.VantaSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link ProfileManager#saveAll()} no longer rewrites every profile, so every command has to persist its own change.
 * Each test runs one command and then loads a second manager from disk <em>without</em> calling {@code saveAll()}: it
 * must see the change.
 */
class ProfilePersistenceTest {
    @TempDir
    Path dir;

    private MutableClock clock;
    private VantaPaths paths;
    private JsonStore json;
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
        settings = new SettingsStore(VantaSettings.registry(), json, paths.settingsFile(),
                Optional.of(new FakeOptionsBridge()));
        settings.load();
        hud = new HudStore(json, paths);
        hud.load();
        crosshair = new CrosshairStore(json, paths);
        crosshair.load();
        keybinds = new FakeKeybindBridge();
        manager = new ProfileManager(json, paths, clock, settings, hud, crosshair, keybinds);
        manager.load();
        manager.saveAll();
        assertFalse(manager.hasUnsavedChanges());
    }

    /** A second manager reading the files as they are now (no saveAll in between). */
    private ProfileManager reloaded() {
        ProfileManager fresh = new ProfileManager(json, paths, clock, settings, hud, crosshair, keybinds);
        fresh.load();
        return fresh;
    }

    @Test
    void firstLoadRecordsTheActiveProfileOnce() {
        assertTrue(Files.exists(paths.profilesStateFile()));
        ProfileManager again = reloaded();
        assertFalse(again.hasUnsavedChanges(), "a valid state file needs no write");
        assertEquals(manager.activeId(), again.activeId());
    }

    @Test
    void createIsPersisted() {
        clock.advance(10);
        Profile created = manager.createFromCurrent("Mine", "star");
        assertFalse(manager.hasUnsavedChanges());
        assertEquals(Optional.of(created), reloaded().find(created.id()));
    }

    @Test
    void duplicateIsPersisted() {
        clock.advance(10);
        Profile copy = manager.duplicate("pvp", "PvP copy").orElseThrow();
        assertEquals(Optional.of(copy), reloaded().find(copy.id()));
    }

    @Test
    void renameIsPersisted() {
        clock.advance(10);
        assertTrue(manager.rename("pvp", "Arena"));
        assertEquals("Arena", reloaded().find("pvp").orElseThrow().name());
    }

    @Test
    void iconChangeIsPersisted() {
        clock.advance(10);
        assertTrue(manager.setIcon("pvp", "sword"));
        assertEquals("sword", reloaded().find("pvp").orElseThrow().icon());
    }

    @Test
    void updateFromCurrentIsPersisted() {
        clock.advance(10);
        settings.set(VantaSettings.HUD_TEXT_SHADOW, false);
        Profile updated = manager.updateActiveFromCurrent().orElseThrow();
        assertEquals(Optional.of(updated), reloaded().find(updated.id()));
    }

    @Test
    void activationIsPersisted() {
        assertTrue(manager.activate("pvp"));
        assertFalse(manager.hasUnsavedChanges());
        assertEquals(Optional.of("pvp"), reloaded().activeId());
    }

    @Test
    void deleteIsPersistedAndMovesTheActiveProfile() {
        assertTrue(manager.activate("pvp"));
        assertTrue(manager.delete("pvp"));
        ProfileManager again = reloaded();
        assertTrue(again.find("pvp").isEmpty());
        assertEquals(manager.activeId(), again.activeId());
        assertFalse(manager.hasUnsavedChanges());
    }

    @Test
    void importIsPersisted() throws Exception {
        clock.advance(10);
        Path exported = dir.resolve("export.json");
        assertTrue(manager.exportTo("pvp", exported));
        Profile imported = manager.importFrom(exported);
        assertEquals(Optional.of(imported), reloaded().find(imported.id()));
    }
}
