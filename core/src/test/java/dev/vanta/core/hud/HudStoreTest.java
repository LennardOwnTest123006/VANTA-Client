package dev.vanta.core.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.config.JsonStore;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HudStoreTest {
    @TempDir
    Path dir;

    @Test
    void loadsDefaultSavesAndReloads() throws IOException {
        VantaPaths paths = new VantaPaths(dir);
        paths.createDirectories();
        JsonStore json = new JsonStore(MutableClock.standard());
        HudStore store = new HudStore(json, paths);
        List<HudLayout> seen = new ArrayList<>();
        store.onLayoutChanged(seen::add);
        store.load();
        assertEquals(HudPresets.defaultPreset().layout(), store.layout());
        assertEquals("default", store.activePreset().orElseThrow().id());
        assertFalse(store.isDirty());

        store.setLayout(store.layout().without("fps"));
        assertTrue(store.isDirty());
        assertTrue(store.activePreset().isEmpty());
        store.saveIfDirty();
        assertTrue(Files.exists(paths.hudLayoutFile()));

        HudPreset preset = store.saveUserPreset("My Layout!");
        assertEquals("my-layout", preset.id());
        assertEquals("My Layout!", preset.name());
        assertTrue(Files.exists(paths.hudPresetsDir().resolve("my-layout.json")));
        HudPreset second = store.saveUserPreset("My Layout!");
        assertEquals("my-layout-2", second.id());
        assertEquals(HudPresets.IDS.size() + 2, store.allPresets().size());

        HudStore reloaded = new HudStore(json, paths);
        reloaded.load();
        assertEquals(store.layout(), reloaded.layout());
        assertEquals(2, reloaded.userPresets().size());
        assertEquals("My Layout!", reloaded.findPreset("my-layout").orElseThrow().name());
        assertTrue(reloaded.activePreset().orElseThrow().id().startsWith("my-layout"),
                "both user presets hold the live layout; either may be reported");

        reloaded.applyPreset(HudPresets.find("pvp").orElseThrow());
        assertEquals("pvp", reloaded.activePreset().orElseThrow().id());
        assertTrue(reloaded.deleteUserPreset("my-layout"));
        assertFalse(Files.exists(paths.hudPresetsDir().resolve("my-layout.json")));
        assertFalse(reloaded.deleteUserPreset("default"), "built-ins cannot be deleted");
        assertTrue(reloaded.updateUserPreset("my-layout-2"));
        assertEquals(reloaded.layout(), reloaded.findPreset("my-layout-2").orElseThrow().layout());
        assertEquals(2, seen.size());
    }

    @Test
    void corruptLayoutFallsBackToDefault() throws IOException {
        VantaPaths paths = new VantaPaths(dir);
        paths.createDirectories();
        Files.writeString(paths.hudLayoutFile(), "{broken");
        Files.writeString(paths.hudPresetsDir().resolve("Bad Name.json"), "{}");
        HudStore store = new HudStore(new JsonStore(MutableClock.standard()), paths);
        store.load();
        assertEquals(HudPresets.defaultPreset().layout(), store.layout());
        assertTrue(store.userPresets().isEmpty(), "unsafe preset file names are ignored");
    }
}
