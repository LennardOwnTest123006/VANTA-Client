package dev.vanta.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VantaPathsTest {
    @TempDir
    Path dir;

    @Test
    void resolvesStandardLayout() throws IOException {
        VantaPaths paths = VantaPaths.inGameDirectory(dir);
        assertEquals(dir.resolve("config").resolve("vanta").toAbsolutePath(), paths.root());
        assertEquals("settings.json", paths.settingsFile().getFileName().toString());
        assertEquals(paths.root().resolve("profiles"), paths.profilesDir());
        assertEquals(paths.hudDir().resolve("layout.json"), paths.hudLayoutFile());
        assertEquals(paths.hudDir().resolve("presets"), paths.hudPresetsDir());
        assertEquals("stats.json", paths.statsFile().getFileName().toString());
        assertEquals("crosshair.json", paths.crosshairFile().getFileName().toString());
        assertEquals("cosmetics.json", paths.cosmeticsFile().getFileName().toString());
        assertEquals(paths.root().resolve("local-ai"), paths.localAiDir());
        assertEquals("local-ai.json", paths.localAiNoteFile().getFileName().toString());
        assertEquals("nexus-chat.json", paths.nexusChatFile().getFileName().toString());
        assertTrue(paths.directories().contains(paths.localAiDir()));
        assertEquals(paths.root().resolve("waypoints.json"), paths.waypointsFile());
        paths.createDirectories();
        for (Path p : paths.directories()) {
            assertTrue(Files.isDirectory(p), p.toString());
        }
        assertTrue(paths.directories().contains(paths.waypointsFile().getParent()),
                "the waypoints file lives in a directory createDirectories() creates");
        assertTrue(paths.contains(paths.settingsFile()));
        assertTrue(paths.contains(paths.waypointsFile()));
        assertFalse(paths.contains(dir.resolve("other")));
    }

    @Test
    void fileNamesAreSanitised() {
        assertEquals("my-pvp-profile", FileNames.sanitize("My PvP Profile!", "x"));
        assertEquals("etc-passwd", FileNames.sanitize("../../etc/passwd", "x"));
        assertEquals("fallback", FileNames.sanitize("   ", "fallback"));
        assertEquals("a_b", FileNames.sanitize("a_b", "x"));
        assertTrue(FileNames.sanitize("x".repeat(100), "f").length() <= FileNames.MAX_LENGTH);
        assertTrue(FileNames.isSafe("pvp-2"));
        assertFalse(FileNames.isSafe("../x"));
        assertFalse(FileNames.isSafe("Trailing-"));
    }

    @Test
    void coreLogFormatsPlaceholders() {
        assertEquals("a=1 b=two extra", CoreLog.format("a={} b={}", 1, "two", "extra"));
        assertEquals("plain", CoreLog.format("plain"));
    }
}
