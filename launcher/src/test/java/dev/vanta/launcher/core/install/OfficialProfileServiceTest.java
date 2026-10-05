package dev.vanta.launcher.core.install;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vanta.launcher.LauncherVersion;
import dev.vanta.launcher.core.model.ReleaseManifest;
import dev.vanta.launcher.core.net.CancellationToken;
import dev.vanta.launcher.core.net.Checksums;
import dev.vanta.launcher.core.net.HashAlgorithm;
import dev.vanta.launcher.core.paths.LauncherPaths;
import dev.vanta.launcher.core.util.Json;
import dev.vanta.launcher.core.util.OsInfo;
import dev.vanta.launcher.testutil.FakeWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "Use with the Minecraft Launcher" against the fake world: what is written, what is preserved, and that nothing in
 * the Minecraft directory changes when a step fails.
 */
class OfficialProfileServiceTest {

    private static final OsInfo LINUX = new OsInfo("linux", "x64", "6.8");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-05T08:15:30.123Z"), ZoneOffset.UTC);
    private static final byte[] ICON = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
    private static final String VERSION_ID = "fabric-loader-0.19.5-1.21.11";
    private static final String PROFILES = "{\"profiles\":{\"other\":{\"name\":\"Other\",\"type\":\"custom\",\"lastVersionId\":\"1.20.1\"}},"
        + "\"settings\":{\"locale\":\"en-us\"},\"version\":3,\"unknownFutureKey\":[true,null,1e3]}";

    @TempDir
    Path tmp;
    private FakeWorld world;
    private LauncherPaths paths;
    private Path mc;
    private OfficialProfileService service;

    @BeforeEach
    void start() throws IOException {
        world = new FakeWorld(false);
        paths = new LauncherPaths(tmp.resolve("data"));
        mc = tmp.resolve(".minecraft");
        final FakeWorld.Wired wired = world.wire(paths, LINUX, Optional.empty(), CLOCK);
        service = new OfficialProfileService(paths, wired.downloader(), wired.fabric(), wired.fabricApi(), wired.vanta(), CLOCK, () -> ICON.clone());
    }

    @AfterEach
    void stop() {
        world.close();
    }

    private OfficialProfileService.Request request() {
        return OfficialProfileService.Request.standard(mc, null, 4096);
    }

    private void officialLauncherStartedOnce() throws IOException {
        Files.createDirectories(mc);
        Files.writeString(mc.resolve(OfficialProfileService.PROFILES_FILE), PROFILES);
    }

    @Test
    void namesFollowTheContract() {
        assertEquals("vanta-1.21.11", OfficialProfileService.profileKey(LauncherVersion.MINECRAFT));
        assertEquals(LauncherVersion.INSTANCE_ID, OfficialProfileService.profileKey(LauncherVersion.MINECRAFT));
        assertEquals("VANTA 1.21.11", OfficialProfileService.profileName("1.21.11"));
        assertEquals("Open the Minecraft Launcher, choose the profile 'VANTA 1.21.11' and press Play.", OfficialProfileService.nextStep("VANTA 1.21.11"));
        assertEquals(VERSION_ID, request().versionId());
        assertTrue(OfficialProfileService.bundledIcon().length > 1000, "the bundled 128 px icon is on the class path");
        assertThrows(IllegalArgumentException.class, () -> OfficialProfileService.Request.standard(mc, null, 0));
    }

    private static List<OfficialProfileService.Kind> kinds(final OfficialProfileService.Plan plan) {
        return plan.files().stream().map(OfficialProfileService.PlannedFile::kind).toList();
    }

    private static java.util.Set<String> locations(final List<OfficialProfileService.PlannedFile> files) {
        return files.stream().map(OfficialProfileService.PlannedFile::location).collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void planListsExactlyWhatIsWrittenAndChangesNothing() throws Exception {
        officialLauncherStartedOnce();
        final OfficialProfileService.Plan plan = service.plan(request());
        assertEquals(List.of(OfficialProfileService.Kind.FABRIC_API, OfficialProfileService.Kind.VANTA_CLIENT,
            OfficialProfileService.Kind.CLIENT_ROLLBACK_COPY, OfficialProfileService.Kind.CLIENT_ROLLBACK_COPY, OfficialProfileService.Kind.VERSION_JSON,
            OfficialProfileService.Kind.VERSION_JAR, OfficialProfileService.Kind.PROFILES_BACKUP, OfficialProfileService.Kind.PROFILES), kinds(plan));
        assertEquals(paths.modsDir().resolve("fabric-api-" + LauncherVersion.FABRIC_API + ".jar").toString(), plan.files().get(0).location());
        assertEquals(paths.modsDir().resolve("vanta-client-1.0.0.jar").toString(), plan.files().get(1).location(),
            "the release is resolved, so the jar is named exactly");
        assertEquals(paths.clientVersionsDir().resolve("1.0.0").resolve("vanta-client-1.0.0.jar").toString(), plan.files().get(2).location());
        assertEquals(paths.clientVersionsDir().resolve("1.0.0").resolve("manifest.json").toString(), plan.files().get(3).location());
        assertEquals(mc.resolve("versions").resolve(VERSION_ID).resolve(VERSION_ID + ".json").toAbsolutePath().toString(), plan.files().get(4).location());
        assertEquals(mc.resolve("launcher_profiles.json.vanta-backup").toAbsolutePath().toString(), plan.files().get(6).location());
        assertFalse(plan.files().stream().anyMatch(f -> f.location().contains("<")), "no placeholders: " + plan.files());
        assertTrue(plan.removed().isEmpty());
        assertEquals("1.0.0", plan.vantaClientVersion());
        assertEquals(paths.instanceDir(), plan.gameDir());
        assertFalse(plan.profileExists());
        assertEquals(PROFILES, Files.readString(mc.resolve(OfficialProfileService.PROFILES_FILE)));
        assertEquals(List.of("GET /releases/client-latest.json"), world.server().requests(), "planning only reads the client release manifest");
        assertFalse(Files.exists(paths.modsDir()), "planning writes nothing");
        assertFalse(Files.exists(mc.resolve(OfficialProfileService.PROFILES_FILE + OfficialProfileService.BACKUP_SUFFIX)));

        Files.writeString(mc.resolve(OfficialProfileService.STORE_PROFILES_FILE), "{}");
        final Path local = tmp.resolve("my-build.jar");
        Files.writeString(local, "jar");
        final OfficialProfileService.Plan withStore = service.plan(OfficialProfileService.Request.standard(mc, local, 2048));
        assertEquals(List.of(OfficialProfileService.Kind.FABRIC_API, OfficialProfileService.Kind.VANTA_CLIENT_LOCAL, OfficialProfileService.Kind.VERSION_JSON,
            OfficialProfileService.Kind.VERSION_JAR, OfficialProfileService.Kind.PROFILES_BACKUP, OfficialProfileService.Kind.PROFILES,
            OfficialProfileService.Kind.STORE_PROFILES_BACKUP, OfficialProfileService.Kind.STORE_PROFILES), kinds(withStore));
        assertEquals(paths.modsDir().resolve("vanta-client-dev.jar").toString(), withStore.files().get(1).location());
        assertEquals("dev", withStore.vantaClientVersion());
        assertEquals(1, world.server().totalHits(), "a local jar needs no release manifest");

        final InstallException missing = assertThrows(InstallException.class,
            () -> service.plan(OfficialProfileService.Request.standard(mc, tmp.resolve("missing.jar"), 2048)));
        assertEquals(InstallStep.VANTA_CLIENT, missing.step());
    }

    @Test
    void planMatchesWhatInstallWritesAndRemoves() throws Exception {
        officialLauncherStartedOnce();
        Files.writeString(mc.resolve(OfficialProfileService.STORE_PROFILES_FILE), "{\"profiles\":{}}");
        // Leftovers of earlier installs: an old Fabric API, an old client jar, three old rollback copies and instance.json.
        Files.createDirectories(paths.modsDir());
        final Path oldApi = paths.modsDir().resolve("fabric-api-0.100.0+1.21.11.jar");
        final Path oldClient = paths.modsDir().resolve("vanta-client-0.9.0.jar");
        final Path otherMod = paths.modsDir().resolve("sodium-fabric-0.6.jar");
        Files.writeString(oldApi, "old api");
        Files.writeString(oldClient, "old client");
        Files.writeString(otherMod, "someone else's mod");
        for (String v : List.of("0.1.0", "0.2.0", "0.3.0")) {
            final Path dir = paths.clientVersionsDir().resolve(v);
            Files.createDirectories(dir);
            Json.write(dir.resolve(VantaClientService.KEPT_MANIFEST), new ReleaseManifest(1, "client", v, "1.21.11", "0.19.5", LauncherVersion.FABRIC_API,
                21, "2026-01-01", "stable", List.of(new ReleaseManifest.ReleaseFile("vanta-client-" + v + ".jar", "https://example.invalid/x.jar", 1,
                "ab".repeat(32))), ""));
            Files.writeString(dir.resolve("vanta-client-" + v + ".jar"), "kept " + v);
        }
        Json.write(paths.instanceFile(), dev.vanta.launcher.ui.testutil.FakeBackend.installedInstance("0.9.0"));

        final OfficialProfileService.Plan plan = service.plan(request());
        // The active 0.9.0 has no rollback copy: it is kept (0.9.0 is among the three highest versions after the install),
        // so the two lowest copies are pruned.
        assertEquals(java.util.Set.of(oldApi.toString(), oldClient.toString(), paths.clientVersionsDir().resolve("0.1.0").toString(),
            paths.clientVersionsDir().resolve("0.2.0").toString()), locations(plan.removed()),
            "only VANTA's own older files are removed: " + plan.removed());
        assertTrue(locations(plan.written()).contains(paths.clientVersionsDir().resolve("0.9.0").resolve("vanta-client-0.9.0.jar").toString()),
            String.valueOf(plan.written()));
        assertTrue(kinds(plan).contains(OfficialProfileService.Kind.INSTANCE_RECORD));
        assertTrue(kinds(plan).contains(OfficialProfileService.Kind.STORE_PROFILES_BACKUP));

        final OfficialProfileService.Result result = service.install(request(), InstallListener.NONE, new CancellationToken());
        assertEquals(locations(plan.written()), result.written().stream().map(Path::toString).collect(java.util.stream.Collectors.toSet()),
            "the confirmation lists exactly the files that are written");
        for (OfficialProfileService.PlannedFile f : plan.written()) {
            assertTrue(Files.exists(Path.of(f.location())), f.toString());
        }
        for (OfficialProfileService.PlannedFile f : plan.removed()) {
            assertFalse(Files.exists(Path.of(f.location())), f.toString());
        }
        assertTrue(Files.exists(otherMod), "other mods are left alone");
        assertTrue(Files.isDirectory(paths.clientVersionsDir().resolve("0.3.0")));
        assertEquals("old client", Files.readString(paths.clientVersionsDir().resolve("0.9.0").resolve("vanta-client-0.9.0.jar")));
        assertEquals("1.0.0", Json.read(paths.instanceFile(), dev.vanta.launcher.core.model.InstanceInfo.class).vantaClientVersion());

        // Second run: the backups exist already and are no longer listed.
        final OfficialProfileService.Plan again = service.plan(request());
        assertFalse(kinds(again).contains(OfficialProfileService.Kind.PROFILES_BACKUP));
        assertFalse(kinds(again).contains(OfficialProfileService.Kind.STORE_PROFILES_BACKUP));
        assertTrue(again.removed().isEmpty(), String.valueOf(again.removed()));
        assertTrue(again.profileExists());
    }

    @Test
    void planReportsAnUnpublishedOrMissingReleaseBeforeAnythingIsConfirmed() throws IOException {
        officialLauncherStartedOnce();
        world.server().addJson("releases/client-latest.json", dev.vanta.launcher.testutil.Fixtures.read("release/client-unpublished.json"));
        final InstallException unpublished = assertThrows(InstallException.class, () -> service.plan(request()));
        assertEquals(InstallStep.VANTA_CLIENT, unpublished.step());
        assertTrue(unpublished.getCause() instanceof NotPublishedException, String.valueOf(unpublished.getCause()));

        world.server().remove("releases/client-latest.json");
        final InstallException missing = assertThrows(InstallException.class, () -> service.plan(request()));
        assertTrue(missing.getCause() instanceof NotPublishedException, String.valueOf(missing.getCause()));
        assertTrue(missing.getCause().getMessage().contains("HTTP 404"), missing.getCause().getMessage());
        assertEquals(PROFILES, Files.readString(mc.resolve(OfficialProfileService.PROFILES_FILE)));
        assertFalse(Files.exists(paths.modsDir()));
    }

    @Test
    void nullValuedMembersArePreserved() throws Exception {
        Files.createDirectories(mc);
        final String withNulls = "{\"profiles\":{\"other\":{\"name\":\"Other\",\"javaDir\":null,\"resolution\":{\"width\":null}}},"
            + "\"clientToken\":null,\"version\":3,\"list\":[null,{\"k\":null}]}";
        Files.writeString(mc.resolve(OfficialProfileService.PROFILES_FILE), withNulls);
        Files.writeString(mc.resolve(OfficialProfileService.STORE_PROFILES_FILE), "{\"profiles\":{},\"selectedUser\":null}");
        service.install(request(), InstallListener.NONE, new CancellationToken());

        final JsonObject expected = JsonParser.parseString(withNulls).getAsJsonObject();
        final String text = Files.readString(mc.resolve(OfficialProfileService.PROFILES_FILE));
        final JsonObject root = JsonParser.parseString(text).getAsJsonObject();
        assertTrue(root.has("clientToken") && root.get("clientToken").isJsonNull(), "top-level null kept: " + text);
        assertEquals(expected.getAsJsonObject("profiles").get("other"), root.getAsJsonObject("profiles").get("other"), "null inside another profile kept");
        assertEquals(expected.get("list"), root.get("list"));
        root.getAsJsonObject("profiles").remove("vanta-1.21.11");
        assertEquals(expected, root, "nothing but the VANTA profile changed");

        final JsonObject store = JsonParser.parseString(Files.readString(mc.resolve(OfficialProfileService.STORE_PROFILES_FILE))).getAsJsonObject();
        assertTrue(store.has("selectedUser") && store.get("selectedUser").isJsonNull());
    }

    @Test
    void installWritesProfileVersionAndMods() throws Exception {
        officialLauncherStartedOnce();
        final List<String> steps = new ArrayList<>();
        final OfficialProfileService.Result result = service.install(request(), new InstallListener() {
            @Override
            public void onProgress(final InstallProgress progress) {
                if (steps.isEmpty() || !steps.get(steps.size() - 1).equals(progress.step().name())) {
                    steps.add(progress.step().name());
                }
            }
        }, new CancellationToken());
        assertEquals(List.of("FABRIC_PROFILE", "FABRIC_API", "VANTA_CLIENT", "FINALIZE"), steps);
        assertTrue(result.created());
        assertEquals("1.0.0", result.vantaClientVersion());
        assertEquals(paths.modsDir().resolve("vanta-client-1.0.0.jar"), result.vantaClientJar());
        assertEquals(List.of(mc.resolve(OfficialProfileService.PROFILES_FILE + OfficialProfileService.BACKUP_SUFFIX).toAbsolutePath()), result.backups());
        assertEquals("Open the Minecraft Launcher, choose the profile 'VANTA 1.21.11' and press Play.", result.nextStep());

        final JsonObject root = JsonParser.parseString(Files.readString(mc.resolve(OfficialProfileService.PROFILES_FILE))).getAsJsonObject();
        final JsonObject original = JsonParser.parseString(PROFILES).getAsJsonObject();
        assertEquals(original.getAsJsonObject("profiles").get("other"), root.getAsJsonObject("profiles").get("other"));
        assertEquals(original.get("settings"), root.get("settings"));
        assertEquals(original.get("unknownFutureKey"), root.get("unknownFutureKey"));
        assertEquals(original.get("version"), root.get("version"));
        final JsonObject vanta = root.getAsJsonObject("profiles").getAsJsonObject("vanta-1.21.11");
        assertEquals("VANTA 1.21.11", vanta.get("name").getAsString());
        assertEquals("custom", vanta.get("type").getAsString());
        assertEquals("2026-10-05T08:15:30.123Z", vanta.get("created").getAsString());
        assertEquals("2026-10-05T08:15:30.123Z", vanta.get("lastUsed").getAsString());
        assertEquals("data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(ICON), vanta.get("icon").getAsString());
        assertEquals(VERSION_ID, vanta.get("lastVersionId").getAsString());
        assertEquals(paths.instanceDir().toAbsolutePath().toString(), vanta.get("gameDir").getAsString());
        assertEquals("-Xmx4096M", vanta.get("javaArgs").getAsString());
        assertEquals(8, vanta.size(), "exactly the contract keys");

        final Path versionJson = mc.resolve("versions").resolve(VERSION_ID).resolve(VERSION_ID + ".json");
        final JsonObject version = JsonParser.parseString(Files.readString(versionJson)).getAsJsonObject();
        final JsonObject served = world.fabricProfile().deepCopy();
        assertEquals(served, version, "the version JSON is the Fabric meta document, unchanged");
        assertEquals(0L, Files.size(mc.resolve("versions").resolve(VERSION_ID).resolve(VERSION_ID + ".jar")));
        assertTrue(Files.isRegularFile(paths.modsDir().resolve("fabric-api-" + LauncherVersion.FABRIC_API + ".jar")));
        assertEquals(PROFILES, Files.readString(mc.resolve(OfficialProfileService.PROFILES_FILE + OfficialProfileService.BACKUP_SUFFIX)));
        assertEquals(0, world.server().hits("mojang/version_manifest_v2.json"), "Minecraft is left to the official launcher");
        assertEquals(0, world.server().hits("fabric/maven/" + FakeWorld.FABRIC_LOADER_PATH), "libraries are left to the official launcher");

        // Re-running is an update: no second profile, no new backup, 'created' kept.
        final Clock later = Clock.fixed(Instant.parse("2026-10-06T00:00:00Z"), ZoneOffset.UTC);
        final FakeWorld.Wired wired = world.wire(paths, LINUX, Optional.empty(), later);
        final OfficialProfileService again = new OfficialProfileService(paths, wired.downloader(), wired.fabric(), wired.fabricApi(), wired.vanta(), later,
            () -> ICON.clone());
        final OfficialProfileService.Result second = again.install(request(), InstallListener.NONE, new CancellationToken());
        assertFalse(second.created());
        assertTrue(second.backups().isEmpty());
        final JsonObject updated = JsonParser.parseString(Files.readString(mc.resolve(OfficialProfileService.PROFILES_FILE))).getAsJsonObject()
            .getAsJsonObject("profiles").getAsJsonObject("vanta-1.21.11");
        assertEquals("2026-10-05T08:15:30.123Z", updated.get("created").getAsString());
        assertEquals("2026-10-06T00:00:00.000Z", updated.get("lastUsed").getAsString());
        assertTrue(service.plan(request()).profileExists());
    }

    private OfficialProfileService withPack() {
        final FakeWorld.Wired wired = world.wire(paths, LINUX, Optional.empty(), CLOCK);
        return new OfficialProfileService(paths, wired.downloader(), wired.fabric(), wired.fabricApi(), wired.vanta(), CLOCK, () -> ICON.clone(),
            wired.pack());
    }

    @Test
    void thePerformancePackIsPlannedAfterTheClientJarAndInstalledBeforeTheProfile() throws Exception {
        officialLauncherStartedOnce();
        final OfficialProfileService packed = withPack();
        final OfficialProfileService.Request request = OfficialProfileService.Request.standard(mc, null, 4096, true);
        final OfficialProfileService.Plan plan = packed.plan(request);
        final List<OfficialProfileService.Kind> kinds = kinds(plan);
        assertEquals(OfficialProfileService.Kind.VANTA_CLIENT, kinds.get(1));
        assertEquals(List.of(OfficialProfileService.Kind.PERFORMANCE_MOD, OfficialProfileService.Kind.PERFORMANCE_MOD,
            OfficialProfileService.Kind.PERFORMANCE_MOD, OfficialProfileService.Kind.PERFORMANCE_MOD, OfficialProfileService.Kind.PERFORMANCE_MOD,
            OfficialProfileService.Kind.PERFORMANCE_MOD), kinds.subList(2, 8));
        assertEquals(paths.modsDir().resolve("sodium-fabric-0.8.14+mc1.21.11.jar").toString(), plan.files().get(2).location());
        assertEquals("Sodium mc1.21.11-0.8.14-fabric", plan.files().get(2).detail());
        assertTrue(plan.notes().isEmpty(), plan.notes().toString());
        assertFalse(Files.exists(paths.modsDir()), "planning writes nothing");

        final List<String> steps = new ArrayList<>();
        final List<String> messages = new ArrayList<>();
        final OfficialProfileService.Result result = packed.install(request, new InstallListener() {
            @Override
            public void onProgress(final InstallProgress progress) {
                if (steps.isEmpty() || !steps.get(steps.size() - 1).equals(progress.step().name())) {
                    steps.add(progress.step().name());
                }
                messages.add(progress.message());
            }
        }, new CancellationToken());
        assertEquals(List.of("FABRIC_PROFILE", "FABRIC_API", "VANTA_CLIENT", "PERFORMANCE_PACK", "FINALIZE"), steps);
        assertTrue(messages.contains("Downloaded fabric-api-" + LauncherVersion.FABRIC_API + ".jar"), "each step names the file: " + messages);
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("Downloaded Sodium mc1.21.11-0.8.14-fabric")), messages.toString());
        assertTrue(result.written().contains(paths.modsDir().resolve("iris-fabric-1.10.8+mc1.21.11.jar")));
        final java.util.Set<String> planned = locations(plan.written());
        for (Path written : result.written()) {
            assertTrue(planned.contains(written.toAbsolutePath().toString()) || written.toString().endsWith(".vanta-backup"),
                written + " was listed in the plan");
        }
        assertTrue(Files.isRegularFile(paths.modsDir().resolve("lithium-fabric-0.21.4+mc1.21.11.jar")));

        // Updating again downloads nothing new: every pack file is verified with its SHA-512.
        final int before = world.server().totalHits();
        final List<String> second = new ArrayList<>();
        packed.install(request, new InstallListener() {
            @Override
            public void onProgress(final InstallProgress progress) {
                second.add(progress.message());
            }
        }, new CancellationToken());
        assertTrue(second.stream().anyMatch(m -> m.startsWith("Verified Sodium")), second.toString());
        assertTrue(world.server().requests().subList(before, world.server().totalHits()).stream().noneMatch(r -> r.contains("/modrinth/cdn/")),
            "no pack file is downloaded twice");
    }

    @Test
    void modrinthBeingDownOnlyAddsANoteAndTheProfileIsStillWritten() throws Exception {
        officialLauncherStartedOnce();
        for (String slug : dev.vanta.launcher.core.modrinth.PerformancePack.SLUGS) {
            world.server().addStatus("modrinth/v2/project/" + slug + "/version", 503, "{}");
        }
        final OfficialProfileService packed = withPack();
        final OfficialProfileService.Request request = OfficialProfileService.Request.standard(mc, null, 4096, true);
        final OfficialProfileService.Plan plan = packed.plan(request);
        assertFalse(plan.notes().isEmpty());
        assertFalse(kinds(plan).contains(OfficialProfileService.Kind.PERFORMANCE_MOD));
        final OfficialProfileService.Result result = packed.install(request, InstallListener.NONE, new CancellationToken());
        assertFalse(result.notes().isEmpty());
        assertTrue(JsonParser.parseString(Files.readString(mc.resolve(OfficialProfileService.PROFILES_FILE))).getAsJsonObject()
            .getAsJsonObject("profiles").has("vanta-1.21.11"), "the profile does not depend on the pack");
    }

    @Test
    void restartHintExplainsTheTray() {
        assertTrue(OfficialProfileService.restartHint().contains("system tray"));
    }

    @Test
    void clientJarIsPickedExactlyEvenWhenFabricApiIsListedFirst() throws Exception {
        officialLauncherStartedOnce();
        final byte[] jar = FakeWorld.synthetic("vanta 1.0.0 published", 4_000);
        world.server().add("releases/vanta-client-1.0.0.jar", jar);
        final ReleaseManifest manifest = new ReleaseManifest(1, "client", "1.0.0", "1.21.11", "0.19.5", LauncherVersion.FABRIC_API, 21, "2026-10-04",
            "stable", List.of(
                new ReleaseManifest.ReleaseFile("fabric-api-0.141.6+1.21.11.jar", world.server().url("releases/fabric-api.jar").toString(), 10, "ab".repeat(32)),
                new ReleaseManifest.ReleaseFile("vanta-client-1.0.0-mods.zip", world.server().url("releases/mods.zip").toString(), 10, "cd".repeat(32)),
                new ReleaseManifest.ReleaseFile("vanta-client-1.0.0.jar", world.server().url("releases/vanta-client-1.0.0.jar").toString(), jar.length,
                    Checksums.hex(jar, HashAlgorithm.SHA256))), "website/content/changelog/client-1.0.0.md");
        world.server().addJson("releases/client-latest.json", Json.toJson(manifest));
        final OfficialProfileService.Result result = service.install(request(), InstallListener.NONE, new CancellationToken());
        assertEquals(paths.modsDir().resolve("vanta-client-1.0.0.jar"), result.vantaClientJar());
        assertEquals(Checksums.hex(jar, HashAlgorithm.SHA256), Checksums.sha256Hex(result.vantaClientJar()));
        assertEquals(0, world.server().hits("releases/fabric-api.jar"));
        assertEquals(0, world.server().hits("releases/mods.zip"));
        try (var mods = Files.list(paths.modsDir())) {
            assertEquals(List.of("fabric-api-" + LauncherVersion.FABRIC_API + ".jar", "vanta-client-1.0.0.jar"),
                mods.map(p -> p.getFileName().toString()).sorted().toList());
        }
    }

    @Test
    void missingProfilesFileFailsBeforeAnyDownload() throws IOException {
        Files.createDirectories(mc);
        final OfficialLauncherNotFoundException e = assertThrows(OfficialLauncherNotFoundException.class,
            () -> service.install(request(), InstallListener.NONE, new CancellationToken()));
        assertTrue(e.getMessage().contains("Start the Minecraft Launcher once, then try again"));
        assertEquals(mc.toAbsolutePath(), e.minecraftDir());
        assertThrows(OfficialLauncherNotFoundException.class, () -> service.plan(request()));
        assertEquals(0, world.server().totalHits());
        try (var files = Files.list(mc)) {
            assertEquals(0, files.count());
        }
        assertThrows(OfficialLauncherNotFoundException.class,
            () -> service.plan(OfficialProfileService.Request.standard(tmp.resolve("does-not-exist"), null, 2048)));
    }

    @Test
    void storeOnlyLauncherGetsTheProfileAndNoJavaEditionFileIsCreated() throws Exception {
        // The launcher from the Microsoft Store / Xbox app keeps only launcher_profiles_microsoft_store.json.
        Files.createDirectories(mc);
        final Path store = mc.resolve(OfficialProfileService.STORE_PROFILES_FILE);
        Files.writeString(store, "{\"profiles\":{\"other\":{\"name\":\"Other\"}},\"settings\":{\"locale\":\"de-de\"}}");
        assertEquals(List.of(store), OfficialProfileService.existingProfileFiles(mc));

        final OfficialProfileService.Plan plan = service.plan(request());
        assertEquals(List.of(OfficialProfileService.Kind.STORE_PROFILES_BACKUP, OfficialProfileService.Kind.STORE_PROFILES),
            kinds(plan).stream().filter(k -> k.name().contains("PROFILES")).toList());

        final OfficialProfileService.Result result = service.install(request(), InstallListener.NONE, new CancellationToken());
        assertTrue(result.created());
        assertFalse(Files.exists(mc.resolve(OfficialProfileService.PROFILES_FILE)), "launcher_profiles.json must not be created");
        assertEquals(List.of(OfficialProfileService.backupOf(store).toAbsolutePath()),
            result.backups().stream().map(Path::toAbsolutePath).toList());
        final JsonObject root = JsonParser.parseString(Files.readString(store)).getAsJsonObject();
        assertEquals("de-de", root.getAsJsonObject("settings").get("locale").getAsString());
        assertEquals("Other", root.getAsJsonObject("profiles").getAsJsonObject("other").get("name").getAsString());
        assertEquals("fabric-loader-" + LauncherVersion.FABRIC_LOADER + "-" + LauncherVersion.MINECRAFT,
            root.getAsJsonObject("profiles").getAsJsonObject("vanta-1.21.11").get("lastVersionId").getAsString());
    }

    @Test
    void malformedProfilesAreNeverOverwritten() throws IOException {
        Files.createDirectories(mc);
        for (String bad : List.of("{ broken", "[1,2]", "{\"profiles\": \"text\"}")) {
            Files.writeString(mc.resolve(OfficialProfileService.PROFILES_FILE), bad);
            assertThrows(IOException.class, () -> service.install(request(), InstallListener.NONE, new CancellationToken()), bad);
            assertEquals(bad, Files.readString(mc.resolve(OfficialProfileService.PROFILES_FILE)));
        }
        assertFalse(Files.exists(mc.resolve("versions")));
        assertEquals(0, world.server().totalHits());
    }

    @Test
    void profilesWithoutProfilesObjectGetOne() throws Exception {
        Files.createDirectories(mc);
        Files.writeString(mc.resolve(OfficialProfileService.PROFILES_FILE), "{\"settings\":{}}");
        service.install(request(), InstallListener.NONE, new CancellationToken());
        final JsonObject root = JsonParser.parseString(Files.readString(mc.resolve(OfficialProfileService.PROFILES_FILE))).getAsJsonObject();
        assertTrue(root.getAsJsonObject("profiles").has("vanta-1.21.11"));
        assertTrue(root.has("settings"));
    }

    @Test
    void failedDownloadLeavesTheMinecraftDirectoryUntouched() throws IOException {
        officialLauncherStartedOnce();
        final String apiPath = FabricApiService.coordinate(LauncherVersion.FABRIC_API).path();
        world.server().addText("fabric/maven/" + apiPath + ".sha256", "0".repeat(64));
        final InstallException e = assertThrows(InstallException.class, () -> service.install(request(), InstallListener.NONE, new CancellationToken()));
        assertEquals(InstallStep.FABRIC_API, e.step());
        assertTrue(e.getCause() instanceof dev.vanta.launcher.core.net.IntegrityException, String.valueOf(e.getCause()));
        assertTrue(e.getMessage().contains(world.fabricMavenBase().resolve(apiPath).toString()), e.getMessage());
        assertEquals(PROFILES, Files.readString(mc.resolve(OfficialProfileService.PROFILES_FILE)));
        assertFalse(Files.exists(mc.resolve("versions")));
        assertFalse(Files.exists(mc.resolve(OfficialProfileService.PROFILES_FILE + OfficialProfileService.BACKUP_SUFFIX)));
    }

    @Test
    void unpublishedClientIsReportedAndChangesNothing() throws IOException {
        officialLauncherStartedOnce();
        world.server().addJson("releases/client-latest.json", dev.vanta.launcher.testutil.Fixtures.read("release/client-unpublished.json"));
        final InstallException e = assertThrows(InstallException.class, () -> service.install(request(), InstallListener.NONE, new CancellationToken()));
        assertEquals(InstallStep.VANTA_CLIENT, e.step());
        assertTrue(e.getCause() instanceof NotPublishedException);
        assertEquals(PROFILES, Files.readString(mc.resolve(OfficialProfileService.PROFILES_FILE)));
    }

    @Test
    void cancellationStopsBeforeWriting() throws IOException {
        officialLauncherStartedOnce();
        final CancellationToken token = new CancellationToken();
        token.cancel();
        assertThrows(CancellationException.class, () -> service.install(request(), InstallListener.NONE, token));
        assertEquals(PROFILES, Files.readString(mc.resolve(OfficialProfileService.PROFILES_FILE)));
    }

    @Test
    void upsertKeepsUnmanagedKeysOfAnExistingEntry() throws IOException {
        final JsonObject root = JsonParser.parseString("{\"profiles\":{\"vanta-1.21.11\":{\"name\":\"old\",\"created\":\"2020-01-01T00:00:00.000Z\","
            + "\"javaDir\":\"C:\\\\Java\\\\bin\\\\javaw.exe\",\"resolution\":{\"width\":1280,\"height\":720}}}}").getAsJsonObject();
        final JsonObject profile = service.profile("VANTA 1.21.11", VERSION_ID, 3000);
        assertFalse(OfficialProfileService.upsertProfile(root, "vanta-1.21.11", profile));
        final JsonObject merged = root.getAsJsonObject("profiles").getAsJsonObject("vanta-1.21.11");
        assertEquals("VANTA 1.21.11", merged.get("name").getAsString());
        assertEquals("2020-01-01T00:00:00.000Z", merged.get("created").getAsString());
        assertEquals("C:\\Java\\bin\\javaw.exe", merged.get("javaDir").getAsString());
        assertEquals(1280, merged.getAsJsonObject("resolution").get("width").getAsInt());
        assertEquals("-Xmx3000M", merged.get("javaArgs").getAsString());
        assertTrue(OfficialProfileService.upsertProfile(root, "vanta-other", profile));
    }
}
