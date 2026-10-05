package dev.vanta.launcher.cli;

import dev.vanta.launcher.LauncherVersion;
import dev.vanta.launcher.Main;
import dev.vanta.launcher.core.LauncherServices;
import dev.vanta.launcher.core.java.ProcessJavaProbe;
import dev.vanta.launcher.core.net.JdkHttpTransport;
import dev.vanta.launcher.core.paths.LauncherPaths;
import dev.vanta.launcher.core.settings.LauncherSettings;
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
        env.remove(LauncherSettings.RELEASES_BASE_URL_ENV);
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
        return run(cli, args);
    }

    private static Run run(final LauncherCli target, final String... args) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ByteArrayOutputStream err = new ByteArrayOutputStream();
        final ExitCode code;
        try (PrintStream o = new PrintStream(out, true, StandardCharsets.UTF_8); PrintStream e = new PrintStream(err, true, StandardCharsets.UTF_8)) {
            code = target.execute(args, o, e);
        }
        return new Run(code, out.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"), err.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"));
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
        assertTrue(Main.uiAvailable(), "the JavaFX UI class and runtime are on the test class path");
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
    void checkUpdateUsesTheBuiltInReleasesUrlAndRejectsAnInvalidOne() {
        final Run invalid = run("--check-update", "--releases-url", "ftp://releases.example/vanta", "--data-dir", tmp.resolve("upd-data").toString());
        assertEquals(ExitCode.NOT_CONFIGURED, invalid.code());
        assertTrue(invalid.out().contains("is not a valid http(s) URL"), invalid.out());
        world.server().addJson("releases/launcher-latest.json", dev.vanta.launcher.testutil.Fixtures.read("release/launcher-latest.json"));
        // No --releases-url and nothing in settings.json: the built-in default (this test's fake releases/) is used.
        final Run configured = run("--check-update", "--data-dir", tmp.resolve("upd-data").toString());
        assertEquals(ExitCode.OK, configured.code(), configured.err());
        assertTrue(configured.out().contains("Release manifests: " + world.releasesBase() + " (built-in default)"), configured.out());
        assertTrue(configured.out().contains("Launcher " + LauncherVersion.VERSION + ": update available: 1.1.0"));
        assertTrue(configured.out().contains("Client (not installed): update available: 1.0.0"));
    }

    @Test
    void releasesUrlPrecedenceIsFlagThenSettingsThenEnvironmentThenDefault() throws IOException {
        final Map<String, String> withEnv = new HashMap<>(env);
        withEnv.put(LauncherSettings.RELEASES_BASE_URL_ENV, "not-a-url");
        final LauncherCli envCli = new LauncherCli(paths -> new LauncherServices(paths, OsInfo.detect(), withEnv, new JdkHttpTransport("VANTA-Launcher/test"),
            new ProcessJavaProbe(), Clock.systemUTC(), Sleeper.NONE, world.endpoints()), OsInfo.detect(), withEnv, tmp.resolve("home"));
        final Path data = tmp.resolve("precedence-data");
        final Run fromEnv = run(envCli, "--check-update", "--data-dir", data.toString());
        assertEquals(ExitCode.NOT_CONFIGURED, fromEnv.code(), "the environment variable wins over the default");
        assertTrue(fromEnv.out().contains("'not-a-url' (from " + LauncherSettings.RELEASES_BASE_URL_ENV + ")"), fromEnv.out());

        world.server().addJson("releases/launcher-latest.json", dev.vanta.launcher.testutil.Fixtures.read("release/launcher-latest.json"));
        final Run flag = run(envCli, "--check-update", "--releases-url", world.releasesBase(), "--data-dir", data.toString());
        assertEquals(ExitCode.OK, flag.code(), "--releases-url wins over the environment: " + flag.err());
        assertTrue(flag.out().contains("(from settings or --releases-url)"), flag.out());

        Files.createDirectories(data);
        Files.writeString(data.resolve("settings.json"), "{\"schemaVersion\":1,\"releasesBaseUrl\":\"" + world.releasesBase() + "\"}");
        final Run settings = run(envCli, "--check-update", "--data-dir", data.toString());
        assertEquals(ExitCode.OK, settings.code(), "a non-empty setting wins over the environment: " + settings.err());

        Files.writeString(data.resolve("settings.json"), "{\"schemaVersion\":1,\"releasesBaseUrl\":\"\"}");
        assertEquals(ExitCode.NOT_CONFIGURED, run(envCli, "--check-update", "--data-dir", data.toString()).code(),
            "an empty setting (existing settings files) falls through to the environment");
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
    void installUsesTheBuiltInReleasesUrlByDefault() {
        final Path data = tmp.resolve("default-url-data");
        final Run install = run("--install", "--no-assets", "--data-dir", data.toString());
        assertEquals(ExitCode.OK, install.code(), install.out() + install.err());
        assertTrue(install.out().contains("VANTA Client release manifests: " + world.releasesBase() + " (built-in default)"), install.out());
        assertTrue(install.out().contains("VANTA Client 1.0.0"), install.out());
        assertTrue(Files.isRegularFile(data.resolve("instances/vanta-1.21.11/mods/vanta-client-1.0.0.jar")));
    }

    @Test
    void invalidReleasesUrlIsNotConfiguredAndUnpublishedIsReported() {
        final Path data = tmp.resolve("nourl-data");
        final Run r = run("--install", "--no-assets", "--releases-url", "not a url", "--data-dir", data.toString());
        assertEquals(ExitCode.NOT_CONFIGURED, r.code(), r.err());
        assertTrue(r.err().contains("is not a valid http(s) URL"), r.err());
        final Run missing = run("--install", "--no-assets", "--releases-url", world.releasesBase().replace("releases/", "unpub/"), "--data-dir", data.toString());
        assertEquals(ExitCode.NOT_PUBLISHED, missing.code(), "no manifest at the location (HTTP 404) means nothing is published: " + missing.err());
        assertTrue(missing.err().contains("HTTP 404"), missing.err());
        world.server().addJson("unpub/client-latest.json", dev.vanta.launcher.testutil.Fixtures.read("release/client-unpublished.json"));
        final Run announced = run("--install", "--no-assets", "--releases-url", world.releasesBase().replace("releases/", "unpub/"), "--data-dir", data.toString());
        assertEquals(ExitCode.NOT_PUBLISHED, announced.code(), announced.err());
        assertTrue(announced.err().contains("no public download yet"));
    }

    // ---------------------------------------------------------------- --install-official-profile

    private static final String PROFILES = """
        {
          "profiles": {
            "4f1c0de0a5b2": {
              "created": "2024-03-01T10:00:00.000Z",
              "icon": "Grass",
              "javaArgs": "-Xmx2G -XX:+UseG1GC",
              "lastUsed": "2025-01-02T03:04:05.006Z",
              "lastVersionId": "1.21.4",
              "name": "My vanilla",
              "type": "custom"
            }
          },
          "settings": {"crashAssistance": true, "enableHistorical": false, "keepLauncherOpen": false},
          "version": 3,
          "extraTopLevelKey": {"nested": [1, 2.50, "x"]}
        }
        """;

    private static Path officialDir(final String name) throws IOException {
        final Path mc = tmp.resolve(name);
        Files.createDirectories(mc);
        Files.writeString(mc.resolve("launcher_profiles.json"), PROFILES);
        return mc;
    }

    private static com.google.gson.JsonObject readJson(final Path file) throws IOException {
        return com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    @Test
    void installOfficialProfileAddsTheProfileAndKeepsEverythingElse() throws IOException {
        final Path mc = officialDir("dotminecraft");
        final Path data = tmp.resolve("official-data");
        final Run r = run("--install-official-profile", "--minecraft-dir", mc.toString(), "--data-dir", data.toString(), "--memory", "3072");
        assertEquals(ExitCode.OK, r.code(), r.out() + r.err());
        assertTrue(r.out().contains("Open the Minecraft Launcher, choose the profile 'VANTA 1.21.11' and press Play."), r.out());
        assertTrue(r.out().contains("Added the profile 'VANTA 1.21.11'"), r.out());
        assertTrue(r.out().contains("VANTA Client release manifests: " + world.releasesBase() + " (built-in default)"), r.out());
        assertTrue(r.out().contains("VANTA Client 1.0.0 (published release)"), r.out());
        assertTrue(r.out().contains("Files that will be written"), r.out());
        assertFalse(r.out().contains("Files written:"), "nothing is claimed as written before it is: " + r.out());
        assertTrue(r.out().contains("  - " + data.resolve("instances/vanta-1.21.11/mods/vanta-client-1.0.0.jar").toAbsolutePath()), r.out());
        assertTrue(r.out().contains("  - " + mc.resolve("launcher_profiles.json.vanta-backup").toAbsolutePath()), "the one-time backup is listed: " + r.out());
        assertFalse(r.out().contains("<version>"), "no placeholders: " + r.out());

        final com.google.gson.JsonObject before = com.google.gson.JsonParser.parseString(PROFILES).getAsJsonObject();
        final com.google.gson.JsonObject after = readJson(mc.resolve("launcher_profiles.json"));
        assertEquals(before.getAsJsonObject("profiles").get("4f1c0de0a5b2"), after.getAsJsonObject("profiles").get("4f1c0de0a5b2"));
        assertEquals(before.get("settings"), after.get("settings"));
        assertEquals(before.get("extraTopLevelKey"), after.get("extraTopLevelKey"));
        assertEquals("3", after.get("version").toString(), "numbers keep their text");
        assertTrue(Files.readString(mc.resolve("launcher_profiles.json")).contains("2.50"), "number formatting preserved");
        assertEquals(2, after.getAsJsonObject("profiles").size());

        final com.google.gson.JsonObject vanta = after.getAsJsonObject("profiles").getAsJsonObject("vanta-1.21.11");
        assertEquals("VANTA 1.21.11", vanta.get("name").getAsString());
        assertEquals("custom", vanta.get("type").getAsString());
        assertEquals("fabric-loader-0.19.5-1.21.11", vanta.get("lastVersionId").getAsString());
        final Path gameDir = data.resolve("instances/vanta-1.21.11").toAbsolutePath();
        assertEquals(gameDir.toString(), vanta.get("gameDir").getAsString());
        assertEquals("-Xmx3072M", vanta.get("javaArgs").getAsString());
        assertTrue(vanta.get("created").getAsString().matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z"), vanta.toString());
        assertEquals(vanta.get("created"), vanta.get("lastUsed"));
        final String icon = vanta.get("icon").getAsString();
        assertTrue(icon.startsWith("data:image/png;base64,"));
        final byte[] png = java.util.Base64.getDecoder().decode(icon.substring("data:image/png;base64,".length()));
        assertEquals(dev.vanta.launcher.core.install.OfficialProfileService.bundledIcon().length, png.length);
        assertEquals((byte) 0x89, png[0]);
        assertEquals('P', png[1]);

        assertTrue(Files.isRegularFile(gameDir.resolve("mods/vanta-client-1.0.0.jar")));
        assertTrue(Files.isRegularFile(gameDir.resolve("mods/fabric-api-" + LauncherVersion.FABRIC_API + ".jar")));
        final Path versionDir = mc.resolve("versions/fabric-loader-0.19.5-1.21.11");
        final com.google.gson.JsonObject version = readJson(versionDir.resolve("fabric-loader-0.19.5-1.21.11.json"));
        assertEquals("fabric-loader-0.19.5-1.21.11", version.get("id").getAsString());
        assertEquals("1.21.11", version.get("inheritsFrom").getAsString());
        assertEquals("net.fabricmc.loader.impl.launch.knot.KnotClient", version.get("mainClass").getAsString());
        assertEquals(world.fabricProfile().getAsJsonArray("libraries"), version.getAsJsonArray("libraries"), "written exactly as Fabric meta serves it");
        assertEquals(0L, Files.size(versionDir.resolve("fabric-loader-0.19.5-1.21.11.jar")));
        assertEquals(PROFILES, Files.readString(mc.resolve("launcher_profiles.json.vanta-backup")), "backup holds the original bytes");
        assertFalse(Files.exists(mc.resolve("versions/1.21.11")), "Minecraft itself is left to the official launcher");
        assertFalse(Files.exists(data.resolve("versions/1.21.11/1.21.11.jar")), "no Minecraft download on this path");
        assertFalse(Files.exists(data.resolve("assets/indexes/26.json")), "no asset download on this path");

        // A second run updates the same entry: user tweaks and 'created' survive, the backup is not replaced.
        final com.google.gson.JsonObject tweaked = readJson(mc.resolve("launcher_profiles.json"));
        tweaked.getAsJsonObject("profiles").getAsJsonObject("vanta-1.21.11").addProperty("javaDir", "/opt/java21/bin/java");
        Files.writeString(mc.resolve("launcher_profiles.json"), tweaked.toString());
        final Run again = run("--install-official-profile", "--minecraft-dir", mc.toString(), "--data-dir", data.toString(), "--memory", "4096");
        assertEquals(ExitCode.OK, again.code(), again.err());
        assertTrue(again.out().contains("Updated the profile 'VANTA 1.21.11'"), again.out());
        assertFalse(again.out().contains(".vanta-backup"), "the backup exists already, so it is neither listed nor made again: " + again.out());
        final com.google.gson.JsonObject updated = readJson(mc.resolve("launcher_profiles.json")).getAsJsonObject("profiles")
            .getAsJsonObject("vanta-1.21.11");
        assertEquals("/opt/java21/bin/java", updated.get("javaDir").getAsString());
        assertEquals("-Xmx4096M", updated.get("javaArgs").getAsString());
        assertEquals(vanta.get("created"), updated.get("created"));
        assertEquals(PROFILES, Files.readString(mc.resolve("launcher_profiles.json.vanta-backup")));
        assertEquals(2, readJson(mc.resolve("launcher_profiles.json")).getAsJsonObject("profiles").size());
    }

    @Test
    void officialProfileWithLocalJarAlsoUpdatesTheStoreEdition() throws IOException {
        final Path mc = officialDir("dotminecraft-store");
        Files.writeString(mc.resolve("launcher_profiles_microsoft_store.json"), "{\"profiles\":{},\"version\":3}");
        final Path data = tmp.resolve("official-local-data");
        final Path jar = tmp.resolve("vanta-client-9.9.9.jar");
        Files.write(jar, FakeWorld.synthetic("dev build", 300));
        final Run r = run("--install-official-profile", "--client-jar", jar.toString(), "--minecraft-dir", mc.toString(), "--data-dir", data.toString());
        assertEquals(ExitCode.OK, r.code(), r.err());
        assertTrue(Files.isRegularFile(data.resolve("instances/vanta-1.21.11/mods/vanta-client-9.9.9.jar")));
        assertFalse(r.out().contains("release manifests"), "a local jar needs no release manifest");
        final com.google.gson.JsonObject store = readJson(mc.resolve("launcher_profiles_microsoft_store.json"));
        assertEquals("fabric-loader-0.19.5-1.21.11", store.getAsJsonObject("profiles").getAsJsonObject("vanta-1.21.11").get("lastVersionId").getAsString());
        assertEquals(3, store.get("version").getAsInt());
        assertTrue(Files.isRegularFile(mc.resolve("launcher_profiles_microsoft_store.json.vanta-backup")));
    }

    @Test
    void officialProfileNeedsTheMinecraftLauncherToHaveRunOnce() throws IOException {
        final Path mc = tmp.resolve("never-started");
        Files.createDirectories(mc);
        final Path data = tmp.resolve("official-missing-data");
        final Run r = run("--install-official-profile", "--minecraft-dir", mc.toString(), "--data-dir", data.toString());
        assertEquals(ExitCode.NOT_CONFIGURED, r.code(), r.out() + r.err());
        assertTrue(r.err().contains("Start the Minecraft Launcher once, then try again"), r.err());
        try (var files = Files.list(mc)) {
            assertEquals(0, files.count(), "nothing is created in the Minecraft directory");
        }
        assertFalse(Files.exists(data.resolve("instances/vanta-1.21.11/mods/vanta-client-1.0.0.jar")), "nothing downloaded");

        Files.writeString(mc.resolve("launcher_profiles.json"), "{ not json");
        final Run malformed = run("--install-official-profile", "--minecraft-dir", mc.toString(), "--data-dir", data.toString());
        assertEquals(ExitCode.FAILURE, malformed.code());
        assertTrue(malformed.err().contains("is not valid JSON, so VANTA did not change it"), malformed.err());
        assertEquals("{ not json", Files.readString(mc.resolve("launcher_profiles.json")));
    }

    @Test
    void officialProfileWithoutPublishedReleaseFailsBeforeListingOrWritingAnything() throws IOException {
        final Path mc = officialDir("dotminecraft-unpublished");
        final Path data = tmp.resolve("official-unpublished-data");
        final Run r = run("--install-official-profile", "--releases-url", world.releasesBase().replace("releases/", "nothing-here/"),
            "--minecraft-dir", mc.toString(), "--data-dir", data.toString());
        assertEquals(ExitCode.NOT_PUBLISHED, r.code(), r.out() + r.err());
        assertTrue(r.err().contains("HTTP 404"), r.err());
        assertFalse(r.out().contains("Files that will be written"), "no file list for a setup that cannot run: " + r.out());
        assertEquals(PROFILES, Files.readString(mc.resolve("launcher_profiles.json")));
        assertFalse(Files.exists(mc.resolve("launcher_profiles.json.vanta-backup")));
        assertFalse(Files.exists(mc.resolve("versions")));
        final Path mods = data.resolve("instances/vanta-1.21.11/mods");
        if (Files.isDirectory(mods)) {
            try (var files = Files.list(mods)) {
                assertEquals(java.util.List.of(), files.toList(), "nothing downloaded");
            }
        }
    }

    @Test
    void officialProfileNetworkFailureIsReportedAndChangesNothing() throws IOException {
        final int closedPort;
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }
        final java.net.URI dead = java.net.URI.create("http://127.0.0.1:" + closedPort + "/v2/");
        final dev.vanta.launcher.core.ServiceEndpoints w = world.endpoints();
        final dev.vanta.launcher.core.ServiceEndpoints offline = new dev.vanta.launcher.core.ServiceEndpoints(w.mojangManifest(), w.mojangResources(),
            dead, w.fabricMaven(), w.adoptiumApi(), w.auth(), w.defaultReleasesBaseUrl());
        final LauncherCli offlineCli = new LauncherCli(paths -> new LauncherServices(paths, OsInfo.detect(), env, new JdkHttpTransport("VANTA-Launcher/test"),
            new ProcessJavaProbe(), Clock.systemUTC(), Sleeper.NONE, offline), OsInfo.detect(), env, tmp.resolve("home"));
        final Path mc = officialDir("dotminecraft-offline");
        final Run r = run(offlineCli, "--install-official-profile", "--minecraft-dir", mc.toString(), "--data-dir", tmp.resolve("official-offline").toString());
        assertEquals(ExitCode.NETWORK, r.code(), r.out() + r.err());
        assertTrue(r.err().contains("Fetching Fabric Loader profile failed (" + dead + "versions/loader/1.21.11/0.19.5/profile/json)"), r.err());
        assertTrue(r.err().contains("network error: the connection failed"), r.err());
        assertEquals(PROFILES, Files.readString(mc.resolve("launcher_profiles.json")), "the profiles file is untouched");
        assertFalse(Files.exists(mc.resolve("versions")));
        assertFalse(Files.exists(mc.resolve("launcher_profiles.json.vanta-backup")));
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
