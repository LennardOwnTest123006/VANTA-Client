package dev.vanta.launcher.core.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsTest {

    @TempDir
    Path tmp;

    @Test
    void defaultMemoryIsHalfOfRamClamped() {
        assertEquals(4096, LauncherSettings.defaultMemoryMb(0));
        assertEquals(4096, LauncherSettings.defaultMemoryMb(8192));
        assertEquals(8192, LauncherSettings.defaultMemoryMb(32768));
        assertEquals(8192, LauncherSettings.defaultMemoryMb(65536));
        assertEquals(2048, LauncherSettings.defaultMemoryMb(4096));
        assertEquals(2048, LauncherSettings.defaultMemoryMb(2048));
        assertEquals(6000, LauncherSettings.defaultMemoryMb(12000));
    }

    @Test
    void defaultsAreSane() {
        final LauncherSettings s = LauncherSettings.defaults(16384);
        assertEquals(8192, s.memoryMb());
        assertTrue(s.javaPathOverride().isEmpty());
        assertTrue(s.resolutionOverride().isEmpty());
        assertFalse(s.hasReleasesBaseUrl());
        assertTrue(s.autoUpdateCheck());
        assertFalse(s.developerMode());
        assertTrue(s.shareOfficialMinecraftFiles());
        assertEquals(LauncherSettings.DEFAULT_THEME, s.theme());
        assertEquals(1, s.schemaVersion());
    }

    @Test
    void withersProduceCopies() {
        final LauncherSettings s = LauncherSettings.defaults(0)
            .withMemoryMb(6144).withJavaPath("/opt/jdk").withJvmArgs(List.of("-XX:+UseZGC")).withResolution(new Resolution(1920, 1080))
            .withKeepLauncherOpen(true).withMsClientId("abc").withReleasesBaseUrl("https://r/").withAutoUpdateCheck(false)
            .withDeveloperMode(true).withShareOfficialMinecraftFiles(false).withTheme("x");
        assertEquals(6144, s.memoryMb());
        assertEquals("/opt/jdk", s.javaPath());
        assertEquals(List.of("-XX:+UseZGC"), s.jvmArgs());
        assertEquals("1920x1080", s.resolution().toString());
        assertTrue(s.keepLauncherOpen());
        assertEquals("abc", s.msClientId());
        assertEquals("https://r/", s.releasesBaseUrl());
        assertFalse(s.autoUpdateCheck());
        assertTrue(s.developerMode());
        assertFalse(s.shareOfficialMinecraftFiles());
        assertEquals("x", s.theme());
    }

    @Test
    void storeRoundTripAndTolerantLoad() throws IOException {
        final SettingsStore store = new SettingsStore(tmp.resolve("settings.json"), 16384);
        assertEquals(8192, store.load().memoryMb(), "missing file yields defaults");
        final LauncherSettings saved = store.defaults().withMemoryMb(3072).withMsClientId("client-id").withResolution(new Resolution(1280, 720));
        store.save(saved);
        assertEquals(saved, store.load());

        Files.writeString(store.file(), "{ this is not json");
        assertEquals(8192, store.load().memoryMb(), "corrupt file yields defaults");
        assertTrue(Files.exists(tmp.resolve("settings.json.corrupt")), "corrupt file is preserved");

        Files.writeString(store.file(), "{\"schemaVersion\":1,\"memoryMb\":12}");
        assertEquals(LauncherSettings.DEFAULT_MEMORY_MB, store.load().memoryMb(), "too small memory is replaced by the default");
    }

    @Test
    void resolutionParsing() {
        assertEquals(new Resolution(1920, 1080), Resolution.parse("1920x1080"));
        assertEquals(new Resolution(1280, 720), Resolution.parse(" 1280 X 720 "));
        assertThrows(IllegalArgumentException.class, () -> Resolution.parse("1920"));
        assertThrows(IllegalArgumentException.class, () -> Resolution.parse("ax b"));
        assertThrows(IllegalArgumentException.class, () -> new Resolution(10, 10));
    }
}
