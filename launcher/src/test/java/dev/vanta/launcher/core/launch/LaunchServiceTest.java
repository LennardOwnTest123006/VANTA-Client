package dev.vanta.launcher.core.launch;

import dev.vanta.launcher.LauncherVersion;
import dev.vanta.launcher.core.auth.Account;
import dev.vanta.launcher.core.auth.AccountType;
import dev.vanta.launcher.core.install.InstallListener;
import dev.vanta.launcher.core.install.InstallRequest;
import dev.vanta.launcher.core.log.LauncherLog;
import dev.vanta.launcher.core.model.InstanceInfo;
import dev.vanta.launcher.core.net.CancellationToken;
import dev.vanta.launcher.core.paths.LauncherPaths;
import dev.vanta.launcher.core.settings.LauncherSettings;
import dev.vanta.launcher.core.settings.Resolution;
import dev.vanta.launcher.core.util.OsInfo;
import dev.vanta.launcher.testutil.FakeWorld;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaunchServiceTest {

    private static final String TOKEN = "eyJhbGciOiJIUzI1NiJ9.launch-test-secret-token.signature";
    private static final Account ACCOUNT = new Account("3f2a1b4c-5d6e-7f80-91a2-b3c4d5e6f708", "VantaTester", "2535412345678901",
        TOKEN, Long.MAX_VALUE, "refresh", AccountType.MICROSOFT);

    @TempDir
    static Path tmp;
    private static FakeWorld world;
    private static LauncherPaths paths;
    private static InstanceInfo instance;
    private static final OsInfo HOST = OsInfo.detect();

    @BeforeAll
    static void installOnce() throws Exception {
        world = new FakeWorld(true);
        paths = new LauncherPaths(tmp.resolve("data"));
        instance = world.wire(paths, HOST, Optional.empty(), Clock.systemUTC()).installer()
            .install(new InstallRequest("1.21.11", "0.19.5", LauncherVersion.FABRIC_API, null, true, false), InstallListener.NONE, new CancellationToken());
        LauncherLog.init(paths.logsDir());
    }

    @AfterAll
    static void stop() {
        LauncherLog.close();
        world.close();
    }

    private static LaunchService service() {
        return new LaunchService(paths, HOST, Clock.systemUTC());
    }

    @Test
    void buildsCompleteCommandLine() throws IOException {
        final LauncherSettings settings = LauncherSettings.defaults(0).withMemoryMb(3072).withJvmArgs(List.of("-Dvanta.test=1"))
            .withResolution(new Resolution(1600, 900));
        final LaunchCommand command = service().buildCommand(new LaunchRequest(instance, ACCOUNT, Path.of("/usr/bin/java"), settings, "My World", null));
        final List<String> c = command.command();
        assertEquals(Path.of("/usr/bin/java").toString(), c.get(0));
        assertEquals("-Xmx3072M", c.get(1));
        final int main = c.indexOf("net.fabricmc.loader.impl.launch.knot.KnotClient");
        assertTrue(main > 0, "main class present: " + c);
        final List<String> jvm = c.subList(1, main);
        final List<String> game = c.subList(main + 1, c.size());
        assertTrue(jvm.contains("-Dvanta.launcher=" + LauncherVersion.VERSION));
        assertTrue(jvm.contains("-Dvanta.test=1"));
        assertTrue(jvm.contains("-Dfile.encoding=UTF-8"));
        assertTrue(jvm.contains("-Djava.library.path=" + paths.nativesDir()));
        assertTrue(jvm.contains("-DFabricMcEmu= net.minecraft.client.main.Main "));
        final String cp = jvm.get(jvm.indexOf("-cp") + 1);
        final String[] entries = cp.split(java.util.regex.Pattern.quote(HOST.classpathSeparator()));
        assertTrue(entries[0].endsWith("asm-9.9.jar"), "Fabric libraries first: " + entries[0]);
        assertTrue(entries[entries.length - 1].equals(paths.versionJar("1.21.11").toString()), "client jar last");
        assertTrue(cp.contains("fabric-loader-0.19.5.jar"));
        assertTrue(cp.contains("lwjgl-3.3.3.jar"));
        assertFalse(cp.contains("natives-windows") && !HOST.isWindows());

        assertEquals("VantaTester", game.get(game.indexOf("--username") + 1));
        assertEquals("vanta-1.21.11", game.get(game.indexOf("--version") + 1));
        assertEquals(paths.instanceDir().toString(), game.get(game.indexOf("--gameDir") + 1));
        assertEquals(paths.assetsDir().toString(), game.get(game.indexOf("--assetsDir") + 1));
        assertEquals("26", game.get(game.indexOf("--assetIndex") + 1));
        assertEquals("3f2a1b4c5d6e7f8091a2b3c4d5e6f708", game.get(game.indexOf("--uuid") + 1));
        assertEquals(TOKEN, game.get(game.indexOf("--accessToken") + 1));
        assertEquals(LauncherVersion.USER_AGENT, game.get(game.indexOf("--clientId") + 1));
        assertEquals("2535412345678901", game.get(game.indexOf("--xuid") + 1));
        assertEquals("msa", game.get(game.indexOf("--userType") + 1));
        assertEquals("release", game.get(game.indexOf("--versionType") + 1));
        assertEquals("1600", game.get(game.indexOf("--width") + 1));
        assertEquals("900", game.get(game.indexOf("--height") + 1));
        assertEquals("My World", game.get(game.indexOf("--quickPlaySingleplayer") + 1));
        assertFalse(game.contains("--demo"));
        assertFalse(game.contains("--quickPlayMultiplayer"));
        assertEquals(paths.instanceDir(), command.workingDirectory());

        // Secrets never appear in the display form
        final String display = command.toDisplayString();
        assertFalse(display.contains(TOKEN));
        assertTrue(display.contains("--accessToken [redacted]"));
        assertTrue(display.contains("\"My World\""), "tokens with spaces are quoted");
        assertEquals("[redacted]", command.redacted().get(command.redacted().indexOf("--accessToken") + 1));
    }

    @Test
    void offlineAccountUsesLegacyUserTypeAndNoSecrets() throws IOException {
        final Account offline = new Account(ACCOUNT.uuid(), ACCOUNT.name(), "", Account.OFFLINE_TOKEN, Long.MAX_VALUE, "", AccountType.OFFLINE);
        final LaunchCommand command = service().buildCommand(LaunchRequest.of(instance, offline, Path.of("java"), LauncherSettings.defaults(0)));
        final List<String> c = command.command();
        assertEquals("legacy", c.get(c.indexOf("--userType") + 1));
        assertEquals("0", c.get(c.indexOf("--accessToken") + 1));
        assertEquals("0", c.get(c.indexOf("--xuid") + 1));
        assertTrue(command.secrets().isEmpty());
        assertFalse(c.contains("--width"));
    }

    @Test
    void quickPlayServer() throws IOException {
        final LaunchCommand command = service().buildCommand(new LaunchRequest(instance, ACCOUNT, Path.of("java"), LauncherSettings.defaults(0), null, "play.example.net:25565"));
        final List<String> c = command.command();
        assertEquals("play.example.net:25565", c.get(c.indexOf("--quickPlayMultiplayer") + 1));
        assertFalse(c.contains("--quickPlaySingleplayer"));
    }

    @Test
    void mergedVersionLoads() throws IOException {
        final var merged = service().loadMergedVersion(instance);
        assertEquals("fabric-loader-0.19.5-1.21.11", merged.id());
        assertEquals("net.fabricmc.loader.impl.launch.knot.KnotClient", merged.mainClass());
    }

    @Test
    void startsRealProcessStreamsOutputAndRedactsLog() throws Exception {
        final Path javaExe = Path.of(System.getProperty("java.home"), "bin", HOST.javaExecutableName());
        final LaunchCommand command = service().buildCommand(LaunchRequest.of(instance, ACCOUNT, javaExe, LauncherSettings.defaults(0).withMemoryMb(1024)));
        final List<GameProcess.Line> lines = new CopyOnWriteArrayList<>();
        final GameProcess process = service().start(command, List.of(lines::add));
        final int exit = process.exitCode().get(60, TimeUnit.SECONDS);
        assertEquals(0, exit, "fake KnotClient exits 0; output: " + lines);
        assertFalse(process.isAlive());
        assertTrue(process.pid() > 0);

        final List<String> out = lines.stream().filter(l -> l.stream().equals("stdout")).map(GameProcess.Line::text).toList();
        assertTrue(out.contains("FAKE_GAME_START"), out.toString());
        assertTrue(out.contains("ARG --username"));
        assertTrue(out.contains("ARG VantaTester"));
        assertTrue(out.contains("ARG --accessToken"));
        assertTrue(out.contains("ARG [redacted]"), "token redacted in streamed output");
        assertFalse(out.stream().anyMatch(l -> l.contains(TOKEN)));
        assertTrue(out.contains("PROP vanta.launcher=" + LauncherVersion.VERSION));
        assertTrue(out.stream().anyMatch(l -> l.startsWith("CWD ") && l.endsWith(paths.instanceDir().getFileName().toString())));
        assertTrue(lines.stream().anyMatch(l -> l.stream().equals("stderr") && l.text().equals("FAKE_GAME_STDERR")));

        final String log = Files.readString(process.logFile(), StandardCharsets.UTF_8);
        assertTrue(log.contains("FAKE_GAME_END"));
        assertTrue(log.contains("process exited with code 0"));
        assertFalse(log.contains(TOKEN), "token never written to the game log");
        assertTrue(process.logFile().getFileName().toString().matches("game-\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}\\.log"));

        // launcher.log also redacted
        try (Stream<Path> logs = Files.list(paths.logsDir())) {
            for (Path l : logs.filter(p -> p.getFileName().toString().startsWith("launcher") && p.getFileName().toString().endsWith(".log")).toList()) {
                assertFalse(Files.readString(l).contains(TOKEN), l + " leaks the token");
            }
        }
    }

    @Test
    void unresolvedPlaceholderIsAnError() throws IOException {
        // Inject a game argument with an unknown placeholder into a copy of the vanilla version JSON
        final Path json = paths.versionJson("1.21.11");
        final String original = Files.readString(json);
        try {
            Files.writeString(json, original.replace("\"--versionType\"", "\"--mystery\", \"${mystery_value}\", \"--versionType\""));
            final IOException e = org.junit.jupiter.api.Assertions.assertThrows(IOException.class,
                () -> service().buildCommand(LaunchRequest.of(instance, ACCOUNT, Path.of("java"), LauncherSettings.defaults(0))));
            assertTrue(e.getMessage().contains("mystery_value"));
        } finally {
            Files.writeString(json, original);
        }
    }

    @Test
    void resolutionArgsHelper() {
        assertEquals(List.of("--width", "1280", "--height", "720"), LaunchService.resolutionArgs(new Resolution(1280, 720)));
    }

    /**
     * A restart.request nobody consumed (the launcher was closed while the game ran and "Restart game" was pressed
     * afterwards) must not count for the next start: it is removed before the process starts, so after a normal quit
     * no marker is found.
     */
    @Test
    void aLeftOverRestartMarkerIsRemovedBeforeTheGameStarts() throws Exception {
        final Path javaExe = Path.of(System.getProperty("java.home"), "bin", HOST.javaExecutableName());
        final LaunchCommand command = service().buildCommand(LaunchRequest.of(instance, ACCOUNT, javaExe, LauncherSettings.defaults(0).withMemoryMb(1024)));
        final Path marker = RestartRequest.markerFile(command.workingDirectory());
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, "2026-10-06T12:00:00Z");
        final GameProcess process = service().start(command, List.of());
        assertFalse(Files.exists(marker), "the stale marker is deleted at the start");
        assertEquals(0, process.exitCode().get(60, TimeUnit.SECONDS));
        assertFalse(RestartRequest.consume(command.workingDirectory()), "the fake game did not ask for a restart");
    }

    /**
     * Direct PLAY records the official Minecraft folder for the client's Singleplayer before the start, but keeps a
     * note "Use with the Minecraft Launcher" wrote for an existing (possibly custom) folder.
     */
    @Test
    void directPlayRecordsTheMinecraftFolderUnlessALauncherProfileNoteExists() throws Exception {
        final Path javaExe = Path.of(System.getProperty("java.home"), "bin", HOST.javaExecutableName());
        final Path official = tmp.resolve("home").resolve(".minecraft");
        Files.createDirectories(official.resolve("saves"));
        final LaunchService withFolder = new LaunchService(paths, HOST, Clock.systemUTC(), Optional.of(official));
        final LaunchCommand command = withFolder.buildCommand(LaunchRequest.of(instance, ACCOUNT, javaExe,
            LauncherSettings.defaults(0).withMemoryMb(1024)));
        final Path note = MinecraftFolderHint.file(command.workingDirectory());
        assertEquals(paths.minecraftFolderFile(), note);
        Files.deleteIfExists(note);

        assertEquals(0, withFolder.start(command, List.of()).exitCode().get(60, TimeUnit.SECONDS));
        assertEquals(Optional.of(official.toAbsolutePath().normalize()), MinecraftFolderHint.read(command.workingDirectory()));

        final Path custom = tmp.resolve("D").resolve("minecraft");
        Files.createDirectories(custom);
        MinecraftFolderHint.write(command.workingDirectory(), custom);
        assertEquals(0, withFolder.start(command, List.of()).exitCode().get(60, TimeUnit.SECONDS));
        assertEquals(Optional.of(custom.toAbsolutePath().normalize()), MinecraftFolderHint.read(command.workingDirectory()),
            "the note of the Minecraft Launcher profile (custom folder) wins over the platform default");

        Files.delete(note);
        assertEquals(0, service().start(command, List.of()).exitCode().get(60, TimeUnit.SECONDS));
        assertFalse(Files.exists(note), "without an official folder nothing is written");
    }
}
