package dev.vanta.launcher.core.paths;

import dev.vanta.launcher.core.util.OsInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LauncherPathsTest {

    @TempDir
    Path tmp;

    @Test
    void platformDefaults() {
        final Path home = Path.of("/home/alex");
        assertEquals(Path.of("C:\\Users\\alex\\AppData\\Roaming", "VANTA Launcher"),
            LauncherPaths.defaultDataDir(new OsInfo("windows", "x64", ""), Map.of("APPDATA", "C:\\Users\\alex\\AppData\\Roaming"), home));
        assertEquals(home.resolve("Library/Application Support/VANTA Launcher"),
            LauncherPaths.defaultDataDir(new OsInfo("osx", "arm64", ""), Map.of(), home));
        assertEquals(home.resolve(".local/share/vanta-launcher"),
            LauncherPaths.defaultDataDir(new OsInfo("linux", "x64", ""), Map.of(), home));
        assertEquals(Path.of("/xdg/data/vanta-launcher"),
            LauncherPaths.defaultDataDir(new OsInfo("linux", "x64", ""), Map.of("XDG_DATA_HOME", "/xdg/data"), home));
        assertEquals(Path.of("/custom").toAbsolutePath(),
            LauncherPaths.defaultDataDir(new OsInfo("linux", "x64", ""), Map.of(LauncherPaths.HOME_ENV, "/custom"), home));
    }

    @Test
    void layout() throws IOException {
        final LauncherPaths p = new LauncherPaths(tmp.resolve("data"));
        assertEquals("vanta-1.21.11", p.instanceId());
        assertEquals(p.dataDir().resolve("instances/vanta-1.21.11"), p.instanceDir());
        assertEquals(p.instanceDir().resolve("mods"), p.modsDir());
        assertEquals(p.instanceDir().resolve("config"), p.configDir());
        assertEquals(p.instanceDir().resolve("saves"), p.savesDir());
        assertEquals(p.instanceDir().resolve("instance.json"), p.instanceFile());
        assertEquals(p.dataDir().resolve("libraries"), p.librariesDir());
        assertEquals(p.dataDir().resolve("assets/indexes"), p.assetIndexesDir());
        assertEquals(p.dataDir().resolve("assets/objects"), p.assetObjectsDir());
        assertEquals(p.dataDir().resolve("versions/1.21.11/1.21.11.json"), p.versionJson("1.21.11"));
        assertEquals(p.dataDir().resolve("versions/1.21.11/1.21.11.jar"), p.versionJar("1.21.11"));
        assertEquals(p.dataDir().resolve("runtimes"), p.runtimesDir());
        assertEquals(p.dataDir().resolve("logs"), p.logsDir());
        assertEquals(p.dataDir().resolve("cache/updates"), p.updatesCacheDir());
        assertEquals(p.dataDir().resolve("settings.json"), p.settingsFile());
        assertEquals(p.dataDir().resolve("accounts.dat"), p.accountsFile());
        assertEquals(p.dataDir().resolve("key.bin"), p.keyFile());
        p.createDirectories();
        for (Path dir : p.allDirectories()) {
            assertTrue(Files.isDirectory(dir), dir.toString());
        }
    }

    @Test
    void officialMinecraftDirOnlyWhenPresent() throws IOException {
        final Path home = tmp.resolve("home");
        Files.createDirectories(home);
        assertFalse(LauncherPaths.officialMinecraftDir(new OsInfo("linux", "x64", ""), Map.of(), home).isPresent());
        Files.createDirectories(home.resolve(".minecraft"));
        assertEquals(home.resolve(".minecraft"), LauncherPaths.officialMinecraftDir(new OsInfo("linux", "x64", ""), Map.of(), home).orElseThrow());
        final Path appData = tmp.resolve("AppData/Roaming");
        Files.createDirectories(appData.resolve(".minecraft"));
        assertEquals(appData.resolve(".minecraft"),
            LauncherPaths.officialMinecraftDir(new OsInfo("windows", "x64", ""), Map.of("APPDATA", appData.toString()), home).orElseThrow());
    }

    @Test
    void officialMinecraftDirCandidateExistsOrNot() {
        final Path home = tmp.resolve("candidate-home");
        assertEquals(home.resolve(".minecraft"), LauncherPaths.officialMinecraftDirCandidate(new OsInfo("linux", "x64", ""), Map.of(), home));
        assertEquals(home.resolve("Library").resolve("Application Support").resolve("minecraft"),
            LauncherPaths.officialMinecraftDirCandidate(new OsInfo("osx", "arm64", ""), Map.of(), home));
        assertEquals(Path.of("X:", "Users", "nova", "AppData", "Roaming").resolve(".minecraft"),
            LauncherPaths.officialMinecraftDirCandidate(new OsInfo("windows", "x64", ""),
                Map.of("APPDATA", Path.of("X:", "Users", "nova", "AppData", "Roaming").toString()), home));
        assertEquals(home.resolve("AppData").resolve("Roaming").resolve(".minecraft"),
            LauncherPaths.officialMinecraftDirCandidate(new OsInfo("windows", "x64", ""), Map.of(), home));
    }
}
