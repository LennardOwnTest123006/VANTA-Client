package dev.vanta.launcher.ui.prefs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiPreferencesStoreTest {

    @TempDir
    Path tmp;

    @Test
    void roundTripAndTolerantRead() throws IOException {
        final UiPreferencesStore store = UiPreferencesStore.in(tmp);
        assertEquals(UiPreferences.defaults(), store.load());
        final UiPreferences prefs = UiPreferences.defaults().withReducedMotion(true).withWindow(1280, 800, false).withLastPage("LOGS");
        assertTrue(store.save(prefs));
        assertEquals(prefs, store.load());
        assertTrue(prefs.hasWindowSize());
        assertTrue(Files.readString(store.file()).contains("\"schemaVersion\": 1"));
        Files.writeString(store.file(), "{not json");
        assertEquals(UiPreferences.defaults(), store.load(), "corrupt files fall back to defaults");
        final UiPreferences negative = new UiPreferences(0, false, -5, Double.NaN, true, null);
        assertEquals(UiPreferences.SCHEMA_VERSION, negative.schemaVersion());
        assertEquals(0, negative.windowWidth());
        assertEquals(0, negative.windowHeight());
        assertEquals("", negative.lastPage());
        assertFalse(negative.hasWindowSize());
    }
}
