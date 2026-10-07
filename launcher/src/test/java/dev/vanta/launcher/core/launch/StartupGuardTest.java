package dev.vanta.launcher.core.launch;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vanta.launcher.LauncherVersion;
import dev.vanta.launcher.core.modrinth.ContentType;
import dev.vanta.launcher.core.modrinth.ModJarCheck;
import dev.vanta.launcher.core.modrinth.ModrinthApi;
import dev.vanta.launcher.core.modrinth.ModrinthIndex;
import dev.vanta.launcher.core.modrinth.ModrinthService;
import dev.vanta.launcher.core.net.Downloader;
import dev.vanta.launcher.core.paths.LauncherPaths;
import dev.vanta.launcher.core.util.Sleeper;
import dev.vanta.launcher.testutil.CrashReports;
import dev.vanta.launcher.testutil.FakeTransport;
import dev.vanta.launcher.testutil.ModJars;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The start check on a temporary VANTA instance: jars built for an older Minecraft and the mod a crash report names are
 * switched off (renamed to {@code .disabled}, never deleted).
 */
class StartupGuardTest {

    private static final String REAL_CRASH = CrashReports.SMART_FPS_BOOSTER;
    private static final String SMART = "smart-fps-booster-1.0.0+mc1.21.4.jar";
    private static final String CRASH = "crash-2026-10-06_18.21.44-client.txt";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-06T18:00:00Z"), ZoneOffset.UTC);
    private static final FileTime JAR_TIME = FileTime.from(Instant.parse("2026-10-06T18:00:00Z"));
    private static final FileTime CRASH_TIME = FileTime.from(Instant.parse("2026-10-06T18:21:44Z"));

    @TempDir
    static Path fixturesDir;
    private static ModJars fixtures;

    @TempDir
    Path tmp;
    private LauncherPaths paths;
    private ModrinthService modrinth;
    private StartupGuard guard;

    @BeforeAll
    static void compile() throws IOException {
        fixtures = ModJars.compile(fixturesDir);
    }

    @BeforeEach
    void instance() throws IOException {
        paths = new LauncherPaths(tmp.resolve("data"));
        Files.createDirectories(paths.modsDir());
        final FakeTransport offline = new FakeTransport();
        modrinth = new ModrinthService(new ModrinthApi(offline, URI.create("https://api.modrinth.invalid/v2/"), Sleeper.NONE, "test"),
            new Downloader(offline, Sleeper.NONE, 1, 1), paths, CLOCK, LauncherVersion.MINECRAFT);
        guard = new StartupGuard(paths, modrinth);
    }

    private Path mod(final String file, final byte[] bytes) throws IOException {
        final Path p = paths.modsDir().resolve(file);
        Files.write(p, bytes);
        Files.setLastModifiedTime(p, JAR_TIME);
        return p;
    }

    private Path smartFpsBooster() throws IOException {
        return mod(SMART, fixtures.modJar("smartfpsbooster", "Smart FPS Booster", "1.0.0+mc1.21.4", ModJars.OLD_KEYBINDS));
    }

    /** Smart FPS Booster as far as the check can tell: nothing old in it (the crash report is the only evidence). */
    private Path smartFpsBoosterThatPasses() throws IOException {
        return mod(SMART, fixtures.modJar("smartfpsbooster", "Smart FPS Booster", "1.0.0+mc1.21.4", ModJars.UNRELATED));
    }

    private Path crashReport(final String name, final String text, final FileTime time) throws IOException {
        Files.createDirectories(paths.crashReportsDir());
        final Path p = paths.crashReportsDir().resolve(name);
        Files.writeString(p, text);
        Files.setLastModifiedTime(p, time);
        return p;
    }

    @Test
    void aJarBuiltForAnOlderMinecraftIsSwitchedOffAndTheLauncherManagedJarsAreLeftAlone() throws IOException {
        final Path smart = smartFpsBooster();
        mod("unrelated-1.0.jar", fixtures.modJar("unrelated", "Unrelated", "1.0", ModJars.UNRELATED, ModJars.UNRELATED_OPTION));
        // Same bytecode, but the launcher manages these two files: never checked, never switched off.
        mod("vanta-client-1.2.0.jar", fixtures.modJar("vanta", "VANTA", "1.2.0", ModJars.OLD_KEYBINDS));
        mod("fabric-api-" + LauncherVersion.FABRIC_API + ".jar", fixtures.modJar("fabric-api", "Fabric API", LauncherVersion.FABRIC_API,
            ModJars.OLD_KEYBINDS));
        mod("already-off.jar.disabled", fixtures.modJar("off", "Off", "1", ModJars.OLD_KEYBINDS));

        final StartupGuard.Report report = guard.run();

        assertEquals(2, report.checked(), "Smart FPS Booster and Unrelated");
        assertEquals(1, report.switchedOff().size(), report.toString());
        final StartupGuard.Action a = report.switchedOff().get(0);
        assertEquals(StartupGuard.Source.CHECK, a.source());
        assertEquals("smartfpsbooster", a.modId());
        assertEquals("Smart FPS Booster 1.0.0+mc1.21.4", a.label());
        assertEquals(ModJarCheck.REASON, a.reason());
        assertFalse(Files.exists(smart));
        assertTrue(Files.isRegularFile(paths.modsDir().resolve(SMART + ".disabled")), "renamed, not deleted");
        assertTrue(Files.isRegularFile(paths.modsDir().resolve("vanta-client-1.2.0.jar")));
        assertTrue(Files.isRegularFile(paths.modsDir().resolve("fabric-api-" + LauncherVersion.FABRIC_API + ".jar")));
        assertTrue(Files.isRegularFile(paths.modsDir().resolve("unrelated-1.0.jar")));
        assertEquals(List.of("Startup check: switched off smart-fps-booster-1.0.0+mc1.21.4.jar (Smart FPS Booster 1.0.0+mc1.21.4): built for an"
            + " older Minecraft: it creates key bindings the way Minecraft did before 1.21.9, so Minecraft 1.21.11 would stop while starting"),
            report.cliLines());

        // The next start: nothing left to do (Unrelated is remembered as checked).
        final StartupGuard.Report again = guard.run();
        assertTrue(again.actions().isEmpty());
        assertEquals(List.of("Startup check: 1 mods checked, nothing to switch off"), again.cliLines());
        assertTrue(Files.readString(paths.startupCheckFile()).contains("unrelated-1.0.jar"));
    }

    @Test
    void aTrackedJarIsSwitchedOffThroughTheModsPageSoModrinthJsonSaysDisabled() throws IOException {
        smartFpsBooster();
        Files.createDirectories(paths.vantaConfigDir());
        final ModrinthIndex index = ModrinthIndex.load(paths.modrinthIndexFile());
        index.upsert("PL3U5kms").slug("smart-fps-booster").title("Smart FPS Booster").versionId("hE70j3c1").versionNumber("1.0.0+mc1.21.4")
            .type(ContentType.MOD).file("mods/" + SMART).enabled(true).requiredBy(List.of());
        index.save();

        assertEquals(1, guard.run().switchedOff().size());

        final JsonObject entry = JsonParser.parseString(Files.readString(paths.modrinthIndexFile())).getAsJsonObject()
            .getAsJsonArray("installed").get(0).getAsJsonObject();
        assertFalse(entry.get("enabled").getAsBoolean());
        assertEquals("mods/" + SMART, entry.get("file").getAsString());
        final ModrinthService.InstalledContent listed = modrinth.installed().stream().filter(ModrinthService.InstalledContent::tracked)
            .findFirst().orElseThrow();
        assertFalse(listed.enabled(), "the Mods page shows it switched off and can switch it on again");
        assertEquals(paths.modsDir().resolve(SMART + ".disabled"), listed.path());
        modrinth.setEnabled(listed, true);
        assertTrue(Files.isRegularFile(paths.modsDir().resolve(SMART)));
    }

    @Test
    void theModTheRealCrashReportNamesIsSwitchedOff() throws IOException {
        smartFpsBoosterThatPasses();
        crashReport(CRASH, REAL_CRASH, CRASH_TIME);
        assertEquals("smartfpsbooster", StartupGuard.providerOf(REAL_CRASH).orElseThrow());

        final StartupGuard.Report report = guard.run();

        assertEquals(1, report.switchedOff().size(), report.toString());
        final StartupGuard.Action a = report.switchedOff().get(0);
        assertEquals(StartupGuard.Source.CRASH_REPORT, a.source());
        assertEquals(CRASH, a.crashReport());
        assertEquals("smartfpsbooster", a.modId());
        assertEquals("Smart FPS Booster", a.modName());
        assertEquals("Minecraft stopped while starting because of it (crash report " + CRASH + ")", a.reason());
        assertTrue(Files.isRegularFile(paths.modsDir().resolve(SMART + ".disabled")));
        assertEquals("Startup check: switched off " + SMART + " (Smart FPS Booster 1.0.0+mc1.21.4): Minecraft stopped while starting"
            + " because of it (crash report " + CRASH + ")", report.cliLines().get(0));
    }

    @Test
    void anIdOfANestedJarSwitchesOffTheJarThatContainsIt() throws IOException {
        final byte[] inner = fixtures.modJar("smartfpsbooster", "Smart FPS Booster", "1.0.0", ModJars.UNRELATED);
        mod("booster-bundle-2.0.jar", fixtures.modJar("booster-bundle", "Booster Bundle", "2.0", Map.of("META-INF/jars/smart.jar", inner)));
        mod("other.jar", fixtures.modJar("other", "Other", "1"));
        crashReport(CRASH, REAL_CRASH, CRASH_TIME);

        final StartupGuard.Report report = guard.recoverFromCrashReport();

        assertEquals(1, report.switchedOff().size(), report.toString());
        assertEquals("booster-bundle-2.0.jar", report.switchedOff().get(0).fileName());
        assertTrue(Files.isRegularFile(paths.modsDir().resolve("booster-bundle-2.0.jar.disabled")));
        assertTrue(Files.isRegularFile(paths.modsDir().resolve("other.jar")));
    }

    @Test
    void vantaFabricApiFabricModulesLoaderMinecraftAndJavaAreOnlyReported() throws IOException {
        final Path vanta = mod("vanta-client-1.2.0.jar", fixtures.modJar("vanta", "VANTA", "1.2.0"));
        final byte[] module = fixtures.modJar("fabric-rendering-v1", "Fabric Rendering (v1)", "6.1.0");
        final Path api = mod("fabric-api-" + LauncherVersion.FABRIC_API + ".jar", fixtures.modJar("fabric-api", "Fabric API",
            LauncherVersion.FABRIC_API, Map.of("META-INF/jars/fabric-rendering-v1.jar", module)));
        int minute = 0;
        for (String id : List.of("vanta", "fabric-rendering-v1", "fabric-api", "fabricloader", "minecraft", "java")) {
            crashReport("crash-2026-10-06_19.0" + minute + ".00-client.txt", REAL_CRASH.replace("'smartfpsbooster'", "'" + id + "'"),
                FileTime.from(Instant.parse("2026-10-06T19:0" + minute + ":00Z")));
            minute++;
            final StartupGuard.Report report = guard.recoverFromCrashReport();
            assertTrue(report.switchedOff().isEmpty(), id);
            assertEquals(1, report.reportedOnly().size(), id);
            assertEquals(id, report.reportedOnly().get(0).modId());
            assertTrue(report.cliLines().get(0).startsWith("Startup check: did not switch off "), report.cliLines().toString());
            assertTrue(report.cliLines().get(1).startsWith("Startup check: 0 mods checked, nothing to switch off"), report.cliLines().toString());
        }
        assertTrue(Files.isRegularFile(vanta));
        assertTrue(Files.isRegularFile(api));
        assertTrue(StartupGuard.isProtected("fabric-key-binding-api-v1"));
        assertFalse(StartupGuard.isProtected("smartfpsbooster"));
    }

    @Test
    void aCrashReportOlderThanTheJarIsIgnored() throws IOException {
        final Path smart = smartFpsBoosterThatPasses();
        crashReport(CRASH, REAL_CRASH, FileTime.from(Instant.parse("2026-10-06T17:00:00Z")));
        final StartupGuard.Report report = guard.run();
        assertTrue(report.actions().isEmpty(), "the jar was installed after that crash: " + report);
        assertTrue(Files.isRegularFile(smart));
    }

    @Test
    void aHandledCrashReportIsNotActedOnTwice() throws IOException {
        smartFpsBoosterThatPasses();
        crashReport(CRASH, REAL_CRASH, CRASH_TIME);
        assertEquals(1, guard.run().switchedOff().size());

        // The player switches it on again on the Mods page; the same report must not switch it off a second time.
        final ModrinthService.InstalledContent off = modrinth.installed().stream().filter(c -> c.fileName().equals(SMART)).findFirst().orElseThrow();
        modrinth.setEnabled(off, true);
        Files.setLastModifiedTime(paths.modsDir().resolve(SMART), JAR_TIME);
        assertTrue(guard.run().actions().isEmpty());
        assertTrue(new StartupGuard(paths, modrinth).recoverFromCrashReport().actions().isEmpty(), "remembered in the data directory");
        assertTrue(Files.isRegularFile(paths.modsDir().resolve(SMART)));
        assertTrue(Files.readString(paths.startupCheckFile()).contains(CRASH));

        // A new crash with it is acted on.
        crashReport("crash-2026-10-06_18.40.00-client.txt", REAL_CRASH, FileTime.from(Instant.parse("2026-10-06T18:40:00Z")));
        assertEquals(1, guard.run().switchedOff().size());
    }

    @Test
    void onlyTheNewestCrashReportCounts() throws IOException {
        final Path smart = smartFpsBoosterThatPasses();
        crashReport(CRASH, REAL_CRASH, CRASH_TIME);
        crashReport("crash-2026-10-06_18.30.00-client.txt", "---- Minecraft Crash Report ----\nDescription: Rendering overlay\n",
            FileTime.from(Instant.parse("2026-10-06T18:30:00Z")));
        assertTrue(guard.run().actions().isEmpty());
        assertTrue(Files.isRegularFile(smart));
    }

    @Test
    void aReportNamingAModThatIsNotInstalledChangesNothing() throws IOException {
        mod("unrelated-1.0.jar", fixtures.modJar("unrelated", "Unrelated", "1.0", ModJars.UNRELATED));
        crashReport(CRASH, REAL_CRASH, CRASH_TIME);
        final StartupGuard.Report report = guard.run();
        assertTrue(report.actions().isEmpty());
        assertEquals(List.of("Startup check: 1 mods checked, nothing to switch off"), report.cliLines());
    }

    @Test
    void anEmptyInstanceHasNothingToDo() throws IOException {
        Files.delete(paths.modsDir());
        final StartupGuard.Report report = guard.run();
        assertEquals(List.of("Startup check: 0 mods checked, nothing to switch off"), report.cliLines());
    }
}
