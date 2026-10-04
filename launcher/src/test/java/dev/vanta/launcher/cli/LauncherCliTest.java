package dev.vanta.launcher.cli;

import dev.vanta.launcher.LauncherVersion;
import dev.vanta.launcher.Main;
import dev.vanta.launcher.core.LauncherServices;
import dev.vanta.launcher.core.java.ProcessJavaProbe;
import dev.vanta.launcher.core.net.JdkHttpTransport;
import dev.vanta.launcher.core.paths.LauncherPaths;
import dev.vanta.launcher.core.util.OsInfo;
import dev.vanta.launcher.core.util.Sleeper;
import dev.vanta.launcher.testutil.FakeWorld;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end CLI tests: real {@link LauncherServices} with the real JDK HTTP transport and Java probe, pointed at
 * the {@link FakeWorld}. Exercises install → print-command → launch (fake KnotClient) through the public CLI.
 */
class LauncherCliTest {

    @TempDir
    static Path tmp;
    private static FakeWorld world;
    private static Map<String, String> env;
    private static LauncherCli cli;

    @BeforeAll
    static void start() throws IOException {
        world = new FakeWorld(true);
        env = new HashMap<>(System.getenv());
        env.put("VANTA_DEV_OFFLINE", "1");
        env.put(LauncherPaths.HOME_ENV, tmp.resolve("default-data").toString());
        final Function<LauncherPaths, LauncherServices> factory = paths -> new LauncherServices(paths, OsInfo.detect(), env,
            new JdkHttpTransport("VANTA-Launcher/test"), new ProcessJavaProbe(), Clock.systemUTC(), Sleeper.NONE, world.endpoints());
        cli = new LauncherCli(factory, OsInfo.detect(), env, tmp.resolve("home"));
    }

    @AfterAll
    static void stop() {
        world.close();
    }

    private record Run(ExitCode code, String out, String err) {
    }

    private static Run run(final String... args) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ByteArrayOutputStream err = new ByteArrayOutputStream();
        final ExitCode code;
        try (PrintStream o = new PrintStream(out, true, StandardCharsets.UTF_8); PrintStream e = new PrintStream(err, true, StandardCharsets.UTF_8)) {
            code = cli.execute(args, o, e);
        }
        return new Run(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void versionAndHelp() {
        final Run version = run("--version");
        assertEquals(ExitCode.OK, version.code());
        assertTrue(version.out().startsWith("VANTA Launcher " + LauncherVersion.VERSION));
        assertTrue(version.out().contains("Minecraft 1.21.11 · Fabric 0.19.5 · Java 21"));
        final Run help = run("--help");
        assertEquals(ExitCode.OK, help.code());
        assertTrue(help.out().contains("--install"));
        assertTrue(help.out().contains("Exit codes:"));
        assertEquals(ExitCode.OK, run().code(), "no arguments prints help");
        assertTrue(LauncherCli.wantsCli(new String[] {"--version"}));
        assertFalse(LauncherCli.wantsCli(new String[] {}));
        assertFalse(LauncherCli.wantsCli(new String[] {"--ui"}));
        assertFalse(Main.uiAvailable(), "no UI class on the test class path");
    }

    @Test
    void usageErrors() {
        final Run bad = run("--install", "--bogus");
        assertEquals(ExitCode.USAGE, bad.code());
        assertTrue(bad.err().contains("Unknown option '--bogus'"));
        assertTrue(bad.err().contains("Usage:"));
        assertEquals(ExitCode.USAGE, run("--launch", "--memory", "abc", "--data-dir", tmp.resolve("x").toString()).code());
    }

    @Test
    void checkJavaFindsHostJdk() {
        final Run r = run("--check-java", "--data-dir", tmp.resolve("java-data").toString());
        assertEquals(ExitCode.OK, r.code(), r.out() + r.err());
        assertTrue(r.out().contains("Selected: Java " + Runtime.version().feature()), r.out());
        final Run explicit = run("--check-java", "--java", System.getProperty("java.home"), "--data-dir", tmp.resolve("java-data").toString());
        assertEquals(ExitCode.OK, explicit.code());
        assertTrue(explicit.out().startsWith("Configured: Java"));
        final Run broken = run("--check-java", "--java", tmp.resolve("nope").toString(), "--data-dir", tmp.resolve("java-data").toString());
        assertTrue(broken.out().contains("is not a working Java runtime"));
    }

    @Test
    void checkUpdateRequiresConfiguration() {
        final Run r = run("--check-update", "--data-dir", tmp.resolve("upd-data").toString());
        assertEquals(ExitCode.NOT_CONFIGURED, r.code());
        assertTrue(r.out().contains("No releases URL is configured"));
        world.server().addJson("releases/launcher-latest.json", dev.vanta.launcher.testutil.Fixtures.read("release/launcher-latest.json"));
        final Run configured = run("--check-update", "--releases-url", world.releasesBase(), "--data-dir", tmp.resolve("upd-data").toString());
        assertEquals(ExitCode.OK, configured.code(), configured.err());
        assertTrue(configured.out().contains("Launcher " + LauncherVersion.VERSION + ": update available: 1.1.0"));
        assertTrue(configured.out().contains("Client (not installed): update available: 1.0.0"));
    }

    @Test
    void launchWithoutInstallFails() {
        final Run r = run("--launch", "--dev-offline", "--data-dir", tmp.resolve("empty-data").toString());
        assertEquals(ExitCode.FAILURE, r.code());
        assertTrue(r.err().contains("Run --install first"));
        assertEquals(ExitCode.FAILURE, run("--print-command", "--data-dir", tmp.resolve("empty-data").toString()).code());
    }

    @Test
    void installThenPrintCommandThenLaunch() throws IOException {
        final Path data = tmp.resolve("e2e-data");
        final Run install = run("--install", "--no-assets", "--releases-url", world.releasesBase(), "--data-dir", data.toString());
        assertEquals(ExitCode.OK, install.code(), install.out() + install.err());
        assertTrue(install.out().contains("[1/9] Fetching version manifest"));
        assertTrue(install.out().contains("Installing VANTA Client"));
        assertTrue(install.out().contains("Installed instance 'vanta-1.21.11': Minecraft 1.21.11, Fabric Loader 0.19.5, Fabric API "
            + LauncherVersion.FABRIC_API + ", VANTA Client 1.0.0"));
        assertTrue(Files.isRegularFile(data.resolve("instances/vanta-1.21.11/instance.json")));
        assertTrue(Files.isRegularFile(data.resolve("instances/vanta-1.21.11/mods/vanta-client-1.0.0.jar")));

        // Re-install is a no-op download-wise
        final Run again = run("--install", "--no-assets", "--releases-url", world.releasesBase(), "--data-dir", data.toString());
        assertEquals(ExitCode.OK, again.code());
        assertTrue(again.out().contains("Installation complete: 0 KB downloaded"));

        final Run print = run("--print-command", "--dev-offline", "--username", "CIPlayer", "--data-dir", data.toString(), "--memory", "2048", "--resolution", "1280x720");
        assertEquals(ExitCode.OK, print.code(), print.err());
        assertTrue(print.out().contains("# account: CIPlayer (development)"));
        assertTrue(print.out().contains("-Xmx2048M"));
        assertTrue(print.out().contains("net.fabricmc.loader.impl.launch.knot.KnotClient"));
        assertTrue(print.out().contains("--width\n1280"));
        assertTrue(print.out().contains("--userType\nlegacy"));

        final Run launch = run("--launch", "--dev-offline", "--username", "CIPlayer", "--data-dir", data.toString(), "--memory", "1024");
        assertEquals(ExitCode.OK, launch.code(), launch.out() + launch.err());
        assertTrue(launch.out().contains("Launching as CIPlayer (development)"));
        assertTrue(launch.out().contains("FAKE_GAME_START"));
        assertTrue(launch.out().contains("ARG CIPlayer"));
        assertTrue(launch.out().contains("PROP vanta.launcher=" + LauncherVersion.VERSION));
        assertTrue(launch.out().contains("Game log: "));
        try (var logs = Files.list(data.resolve("logs"))) {
            assertTrue(logs.anyMatch(p -> p.getFileName().toString().startsWith("game-")));
        }

        // --exit-after on an already finished process reports its exit code
        final Run timed = run("--launch", "--dev-offline", "--data-dir", data.toString(), "--exit-after", "60");
        assertEquals(ExitCode.OK, timed.code(), timed.err());
        assertTrue(timed.out().contains("Launching as Dev (development)"));
    }

    @Test
    void installWithLocalClientJar() throws IOException {
        final Path data = tmp.resolve("local-data");
        final Path jar = tmp.resolve("vanta-client-9.9.9.jar");
        Files.write(jar, FakeWorld.synthetic("dev build", 300));
        final Run install = run("--install", "--no-assets", "--client-jar", jar.toString(), "--data-dir", data.toString());
        assertEquals(ExitCode.OK, install.code(), install.err());
        assertTrue(install.out().contains("VANTA Client dev"));
        assertTrue(Files.isRegularFile(data.resolve("instances/vanta-1.21.11/mods/vanta-client-9.9.9.jar")));
    }

    @Test
    void installWithoutReleasesUrlIsNotConfiguredAndUnpublishedIsReported() {
        final Path data = tmp.resolve("nourl-data");
        final Run r = run("--install", "--no-assets", "--data-dir", data.toString());
        assertEquals(ExitCode.NOT_CONFIGURED, r.code(), r.err());
        assertTrue(r.err().contains("No releases URL is configured"));
        final Run unpublished = run("--install", "--no-assets", "--releases-url", world.releasesBase().replace("releases/", "unpub/"), "--data-dir", data.toString());
        assertEquals(ExitCode.NETWORK, unpublished.code(), "unknown manifest location is a network error: " + unpublished.err());
        world.server().addJson("unpub/client-latest.json", dev.vanta.launcher.testutil.Fixtures.read("release/client-unpublished.json"));
        final Run announced = run("--install", "--no-assets", "--releases-url", world.releasesBase().replace("releases/", "unpub/"), "--data-dir", data.toString());
        assertEquals(ExitCode.NOT_PUBLISHED, announced.code(), announced.err());
        assertTrue(announced.err().contains("no public download yet"));
    }

    @Test
    void launchWithoutAccountNeedsSignIn() {
        final Path data = tmp.resolve("e2e-data");
        if (!Files.exists(data.resolve("instances/vanta-1.21.11/instance.json"))) {
            run("--install", "--no-assets", "--releases-url", world.releasesBase(), "--data-dir", data.toString());
        }
        final Run r = run("--launch", "--data-dir", data.toString());
        assertEquals(ExitCode.AUTH, r.code(), r.err());
        assertTrue(r.err().contains("No account is signed in"));
    }
}
