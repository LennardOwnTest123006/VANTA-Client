package dev.vanta.launcher.core.modrinth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vanta.launcher.LauncherVersion;
import dev.vanta.launcher.core.net.CancellationToken;
import dev.vanta.launcher.core.net.Checksums;
import dev.vanta.launcher.core.net.DownloadProgressListener;
import dev.vanta.launcher.core.net.HashAlgorithm;
import dev.vanta.launcher.core.paths.LauncherPaths;
import dev.vanta.launcher.testutil.FakeWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Resolution, verified install, modrinth.json bookkeeping and content management against recorded Modrinth responses
 * served by {@link FakeWorld}.
 */
class ModrinthServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-05T10:00:00Z"), ZoneOffset.UTC);
    private static final List<String> PACK_FILES = List.of("sodium-fabric-0.8.14+mc1.21.11.jar", "lithium-fabric-0.21.4+mc1.21.11.jar",
        "ferritecore-8.2.0-fabric.jar", "ImmediatelyFast-Fabric-1.14.3+1.21.11.jar", "entityculling-fabric-1.11.2-mc1.21.11.jar",
        "iris-fabric-1.10.8+mc1.21.11.jar");

    @TempDir
    Path tmp;
    private FakeWorld world;
    private LauncherPaths paths;
    private ModrinthService service;

    @BeforeEach
    void start() throws IOException {
        world = new FakeWorld(false);
        paths = new LauncherPaths(tmp.resolve("data"));
        service = world.modrinth(paths, CLOCK);
    }

    @AfterEach
    void stop() {
        world.close();
    }

    private List<ModrinthService.Root> packRoots() {
        return PerformancePack.SLUGS.stream().map(s -> new ModrinthService.Root(s, ContentType.MOD)).toList();
    }

    private Map<String, ModrinthService.ResolvedItem> bySlug(final ModrinthService.Resolution r) {
        return r.items().stream().collect(Collectors.toMap(ModrinthService.ResolvedItem::slug, i -> i));
    }

    @Test
    void resolvesThePackPreferringReleasesAndHonouringPinnedDependencies() throws Exception {
        final ModrinthService.Resolution r = service.resolve(packRoots(), true, CancellationToken.NONE);
        assertTrue(r.warnings().isEmpty(), r.warnings().toString());
        assertEquals(PerformancePack.SLUGS, r.items().stream().map(ModrinthService.ResolvedItem::slug).toList());
        final Map<String, ModrinthService.ResolvedItem> items = bySlug(r);
        // Sodium's newest 1.21.11 build is a beta; the newest stable release is chosen, which is also the one Iris pins.
        assertEquals("rkdTcxoT", items.get("sodium").version().id());
        assertEquals("mc1.21.11-0.8.14-fabric", items.get("sodium").version().versionNumber());
        assertEquals(List.of("YL57xq9U"), items.get("sodium").requiredBy(), "Iris requires Sodium");
        assertEquals("Iris Shaders", items.get("iris").title(), "titles come from /projects");
        assertEquals("Entity Culling", items.get("entityculling").title());
        assertTrue(items.get("entityculling").requiredBy().isEmpty(), "its Fabric API dependency is skipped: VANTA installs Fabric API itself");
        assertTrue(r.items().stream().noneMatch(i -> i.projectId().equals(ModrinthApi.FABRIC_API_PROJECT_ID)));
        assertEquals("mods/iris-fabric-1.10.8+mc1.21.11.jar", items.get("iris").relativeFile());
        // Versions are resolved through the API at install time.
        assertTrue(world.server().requests().contains("GET /modrinth/v2/project/sodium/version"));
        assertTrue(world.server().requests().contains("GET /modrinth/v2/version/rkdTcxoT"), "Iris' pinned Sodium version is looked up");
    }

    @Test
    void installsVerifiedFilesAndRecordsThemInModrinthJson() throws Exception {
        final ModrinthService.Resolution r = service.resolve(packRoots(), true, CancellationToken.NONE);
        final List<String> log = new ArrayList<>();
        final ModrinthService.ApplyResult result = service.apply(r, DownloadProgressListener.NONE, log::add, false, CancellationToken.NONE);
        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
        assertEquals(6, result.downloadedCount());
        for (String file : PACK_FILES) {
            final Path jar = paths.modsDir().resolve(file);
            assertTrue(Files.isRegularFile(jar), file);
            assertEquals(Checksums.hex(world.blob("modrinth/cdn/" + file), HashAlgorithm.SHA512), Checksums.hex(jar, HashAlgorithm.SHA512));
        }
        final JsonObject index = JsonParser.parseString(Files.readString(paths.modrinthIndexFile())).getAsJsonObject();
        assertEquals(1, index.get("schemaVersion").getAsInt());
        assertEquals(6, index.getAsJsonArray("installed").size());
        final JsonObject sodium = index.getAsJsonArray("installed").get(0).getAsJsonObject();
        assertEquals("AANobbMI", sodium.get("projectId").getAsString());
        assertEquals("sodium", sodium.get("slug").getAsString());
        assertEquals("Sodium", sodium.get("title").getAsString());
        assertEquals("rkdTcxoT", sodium.get("versionId").getAsString());
        assertEquals("mc1.21.11-0.8.14-fabric", sodium.get("versionNumber").getAsString());
        assertEquals("mod", sodium.get("type").getAsString());
        assertEquals("mods/sodium-fabric-0.8.14+mc1.21.11.jar", sodium.get("file").getAsString());
        assertEquals(128, sodium.get("sha512").getAsString().length());
        assertTrue(sodium.get("enabled").getAsBoolean());
        assertEquals("YL57xq9U", sodium.getAsJsonArray("requiredBy").get(0).getAsString());
        assertEquals("2026-10-05T10:00:00Z", sodium.get("installedAt").getAsString());
        assertTrue(log.stream().anyMatch(l -> l.startsWith("Downloaded Sodium mc1.21.11-0.8.14-fabric")), log.toString());

        // A second run verifies and downloads nothing.
        final int before = world.server().hits("/modrinth/cdn/" + PACK_FILES.get(0));
        final ModrinthService.ApplyResult again = service.apply(service.resolve(packRoots(), true, CancellationToken.NONE),
            DownloadProgressListener.NONE, s -> { }, false, CancellationToken.NONE);
        assertEquals(0, again.downloadedCount());
        assertEquals(before, world.server().hits("/modrinth/cdn/" + PACK_FILES.get(0)));
    }

    @Test
    void anUpdateReplacesTheOlderFileKeepsUnknownKeysAndKeepsADisabledProjectDisabled() throws Exception {
        Files.createDirectories(paths.modsDir());
        Files.createDirectories(paths.vantaConfigDir());
        Files.write(paths.modsDir().resolve("lithium-fabric-0.21.3+mc1.21.11.jar"), "old lithium".getBytes(StandardCharsets.UTF_8));
        Files.write(paths.modsDir().resolve("ferritecore-8.0.3-fabric.jar.disabled"), "old ferrite".getBytes(StandardCharsets.UTF_8));
        Files.writeString(paths.modrinthIndexFile(), """
            {"schemaVersion":1,"futureRootKey":{"keep":true},"installed":[
              {"projectId":"gvQqBUqZ","slug":"lithium","title":"Lithium","versionId":"qvNsoO3l","versionNumber":"mc1.21.11-0.21.3-fabric",
               "type":"mod","file":"mods/lithium-fabric-0.21.3+mc1.21.11.jar","sha512":"","enabled":true,"requiredBy":[],
               "installedAt":"2026-02-08T00:00:00Z","inGameNote":"added by the in-game browser"},
              {"projectId":"uXXizFIs","slug":"ferrite-core","title":"FerriteCore","versionId":"eRLwt73x","versionNumber":"8.0.3-fabric",
               "type":"mod","file":"mods/ferritecore-8.0.3-fabric.jar","sha512":"","enabled":false,"requiredBy":[],"installedAt":"2026-01-01T00:00:00Z"},
              {"projectId":"HVnmMxH1","slug":"complementary-reimagined","title":"Complementary Shaders - Reimagined","versionId":"Bqen1mJX",
               "versionNumber":"r5.9.3","type":"shader","file":"shaderpacks/ComplementaryReimagined_r5.9.3.zip","sha512":"","enabled":true,
               "requiredBy":[],"installedAt":"2026-09-16T00:00:00Z"}
            ]}
            """);
        final List<String> log = new ArrayList<>();
        service.apply(service.resolve(List.of(new ModrinthService.Root("lithium", ContentType.MOD), new ModrinthService.Root("ferrite-core",
            ContentType.MOD)), false, CancellationToken.NONE), DownloadProgressListener.NONE, log::add, false, CancellationToken.NONE);

        assertFalse(Files.exists(paths.modsDir().resolve("lithium-fabric-0.21.3+mc1.21.11.jar")), "the older version is removed");
        assertTrue(Files.isRegularFile(paths.modsDir().resolve("lithium-fabric-0.21.4+mc1.21.11.jar")));
        assertFalse(Files.exists(paths.modsDir().resolve("ferritecore-8.0.3-fabric.jar.disabled")));
        assertTrue(Files.isRegularFile(paths.modsDir().resolve("ferritecore-8.2.0-fabric.jar.disabled")), "a disabled project stays disabled");
        assertFalse(Files.exists(paths.modsDir().resolve("ferritecore-8.2.0-fabric.jar")));
        assertTrue(log.contains("Removed the older version lithium-fabric-0.21.3+mc1.21.11.jar"), log.toString());

        final JsonObject index = JsonParser.parseString(Files.readString(paths.modrinthIndexFile())).getAsJsonObject();
        assertTrue(index.getAsJsonObject("futureRootKey").get("keep").getAsBoolean(), "unknown root keys survive");
        assertEquals(3, index.getAsJsonArray("installed").size());
        final JsonObject lithium = index.getAsJsonArray("installed").get(0).getAsJsonObject();
        assertEquals("Ow7wA0kG", lithium.get("versionId").getAsString());
        assertEquals("mods/lithium-fabric-0.21.4+mc1.21.11.jar", lithium.get("file").getAsString());
        assertEquals("added by the in-game browser", lithium.get("inGameNote").getAsString(), "unknown entry keys survive");
        assertEquals("2026-10-05T10:00:00Z", lithium.get("installedAt").getAsString());
        final JsonObject ferrite = index.getAsJsonArray("installed").get(1).getAsJsonObject();
        assertFalse(ferrite.get("enabled").getAsBoolean());
        assertEquals("mods/ferritecore-8.2.0-fabric.jar", ferrite.get("file").getAsString());
        assertEquals("shader", index.getAsJsonArray("installed").get(2).getAsJsonObject().get("type").getAsString(), "other entries are kept");
    }

    @Test
    void anUntrackedJarWithTheSameModIdIsNotDuplicated() throws Exception {
        Files.createDirectories(paths.modsDir());
        Files.write(paths.modsDir().resolve("my-own-sodium.jar"), FakeWorld.modJar("sodium", "0.8.10"));
        final ModrinthService.ApplyResult result = service.apply(service.resolve(List.of(new ModrinthService.Root("sodium", ContentType.MOD)),
            false, CancellationToken.NONE), DownloadProgressListener.NONE, s -> { }, true, CancellationToken.NONE);
        assertTrue(result.applied().isEmpty());
        assertEquals(1, result.warnings().size(), result.warnings().toString());
        assertFalse(Files.exists(paths.modsDir().resolve("sodium-fabric-0.8.14+mc1.21.11.jar")), "Fabric would refuse two copies of one mod");
        assertTrue(Files.isRegularFile(paths.modsDir().resolve("my-own-sodium.jar")), "the player's own file is never touched");
    }

    @Test
    void aFileThatFailsItsSha512IsDeletedAndReportedButDoesNotStopTheOthers() throws Exception {
        world.server().add("modrinth/cdn/lithium-fabric-0.21.4+mc1.21.11.jar", "tampered".getBytes(StandardCharsets.UTF_8));
        final ModrinthService.ApplyResult result = service.apply(service.resolve(packRoots(), true, CancellationToken.NONE),
            DownloadProgressListener.NONE, s -> { }, true, CancellationToken.NONE);
        assertEquals(5, result.applied().size());
        assertTrue(result.warnings().stream().anyMatch(w -> w.startsWith("Lithium mc1.21.11-0.21.4-fabric was not installed")), result.warnings().toString());
        assertFalse(Files.exists(paths.modsDir().resolve("lithium-fabric-0.21.4+mc1.21.11.jar")));
        assertFalse(ModrinthIndex.load(paths.modrinthIndexFile()).byProjectId("gvQqBUqZ").isPresent());
        // Strict mode (Mods page): the failure is the result.
        assertThrows(IOException.class, () -> service.apply(service.resolve(List.of(new ModrinthService.Root("lithium", ContentType.MOD)), false,
            CancellationToken.NONE), DownloadProgressListener.NONE, s -> { }, false, CancellationToken.NONE));
    }

    @Test
    void unknownProjectsAndProjectsWithoutAVersionBecomeWarnings() throws Exception {
        world.server().addJson("modrinth/v2/project/no-1.21.11/version", "[]");
        final ModrinthService.Resolution r = service.resolve(List.of(new ModrinthService.Root("does-not-exist", ContentType.MOD),
            new ModrinthService.Root("no-1.21.11", ContentType.MOD), new ModrinthService.Root("lithium", ContentType.MOD)), true, CancellationToken.NONE);
        assertEquals(List.of("lithium"), r.items().stream().map(ModrinthService.ResolvedItem::slug).toList());
        assertEquals(2, r.warnings().size(), r.warnings().toString());
        assertTrue(r.warnings().get(0).contains("was not found on Modrinth"));
        assertTrue(r.warnings().get(1).contains("has no version for Minecraft 1.21.11"));
        final IOException strict = assertThrows(IOException.class, () -> service.resolve(List.of(new ModrinthService.Root("does-not-exist",
            ContentType.MOD)), false, CancellationToken.NONE));
        assertTrue(strict.getMessage().contains("does-not-exist was not found on Modrinth"));
    }

    @Test
    void fabricApiIsNeverInstalledFromModrinth() {
        final int before = world.server().totalHits();
        for (String idOrSlug : List.of(ModrinthApi.FABRIC_API_PROJECT_ID, "fabric-api")) {
            final IOException e = assertThrows(IOException.class, () -> service.resolve(List.of(new ModrinthService.Root(idOrSlug, ContentType.MOD)),
                false, CancellationToken.NONE));
            assertEquals(ModrinthService.FABRIC_API_MANAGED, e.getMessage());
        }
        assertEquals(before, world.server().totalHits(), "Modrinth is not even asked");
    }

    @Test
    void aProjectWhoseRequiredDependencyHasNoVersionIsLeftOut() throws Exception {
        // Iris pins a Sodium build that Modrinth no longer lists and Sodium has no 1.21.11 version at all.
        world.server().addJson("modrinth/v2/project/AANobbMI/version", "[]");
        world.server().remove("modrinth/v2/version/rkdTcxoT");
        final ModrinthService.Resolution r = service.resolve(List.of(new ModrinthService.Root("iris", ContentType.MOD)), true, CancellationToken.NONE);
        assertTrue(r.items().isEmpty(), r.items().toString());
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("requires sodium")), r.warnings().toString());
    }

    @Test
    void installedContentListsTrackedUntrackedAndManagedFiles() throws Exception {
        service.apply(service.resolve(List.of(new ModrinthService.Root("iris", ContentType.MOD)), false, CancellationToken.NONE),
            DownloadProgressListener.NONE, s -> { }, false, CancellationToken.NONE);
        Files.write(paths.modsDir().resolve("handmade.jar.disabled"), new byte[] {1});
        Files.write(paths.modsDir().resolve("fabric-api-" + LauncherVersion.FABRIC_API + ".jar"), new byte[] {1});
        Files.write(paths.modsDir().resolve("vanta-client-1.0.1.jar"), new byte[] {1});
        Files.write(paths.modsDir().resolve("notes.txt"), new byte[] {1});
        Files.createDirectories(paths.shaderpacksDir());
        Files.write(paths.shaderpacksDir().resolve("MyShader.zip"), new byte[] {1});

        final List<ModrinthService.InstalledContent> all = service.installed();
        final Map<String, ModrinthService.InstalledContent> byName = all.stream()
            .collect(Collectors.toMap(ModrinthService.InstalledContent::fileName, c -> c));
        assertEquals(Set.of("iris-fabric-1.10.8+mc1.21.11.jar", "sodium-fabric-0.8.14+mc1.21.11.jar", "handmade.jar",
            "fabric-api-" + LauncherVersion.FABRIC_API + ".jar", "vanta-client-1.0.1.jar", "MyShader.zip"), byName.keySet());
        final ModrinthService.InstalledContent sodium = byName.get("sodium-fabric-0.8.14+mc1.21.11.jar");
        assertTrue(sodium.tracked());
        assertEquals("Sodium", sodium.title());
        assertEquals(List.of("Iris Shaders"), sodium.requiredBy());
        assertFalse(byName.get("handmade.jar").enabled());
        assertFalse(byName.get("handmade.jar").tracked());
        assertTrue(byName.get("vanta-client-1.0.1.jar").managed());
        assertEquals(ContentType.SHADER, byName.get("MyShader.zip").type());
    }

    @Test
    void enableDisableRemoveRespectDependencies() throws Exception {
        service.apply(service.resolve(List.of(new ModrinthService.Root("iris", ContentType.MOD)), false, CancellationToken.NONE),
            DownloadProgressListener.NONE, s -> { }, false, CancellationToken.NONE);
        ModrinthService.InstalledContent sodium = find("Sodium");
        final ContentChangeRefusedException refused = assertThrows(ContentChangeRefusedException.class, () -> service.setEnabled(find("Sodium"), false));
        assertEquals(ContentChangeRefusedException.Reason.REQUIRED_BY, refused.reason());
        assertEquals(List.of("Iris Shaders"), refused.names());
        assertThrows(ContentChangeRefusedException.class, () -> service.remove(find("Sodium")));

        service.setEnabled(find("Iris Shaders"), false);
        assertTrue(Files.isRegularFile(paths.modsDir().resolve("iris-fabric-1.10.8+mc1.21.11.jar.disabled")));
        assertFalse(find("Iris Shaders").enabled());
        service.setEnabled(find("Sodium"), false);
        assertTrue(Files.isRegularFile(paths.modsDir().resolve("sodium-fabric-0.8.14+mc1.21.11.jar.disabled")));
        // Enabling Iris enables what it needs again.
        service.setEnabled(find("Iris Shaders"), true);
        assertTrue(Files.isRegularFile(paths.modsDir().resolve("iris-fabric-1.10.8+mc1.21.11.jar")));
        assertTrue(Files.isRegularFile(paths.modsDir().resolve("sodium-fabric-0.8.14+mc1.21.11.jar")));
        assertTrue(ModrinthIndex.load(paths.modrinthIndexFile()).byProjectId("AANobbMI").orElseThrow().enabled());

        service.remove(find("Iris Shaders"));
        assertFalse(Files.exists(paths.modsDir().resolve("iris-fabric-1.10.8+mc1.21.11.jar")));
        sodium = find("Sodium");
        assertTrue(sodium.requiredBy().isEmpty(), "nothing needs Sodium any more");
        service.remove(sodium);
        assertTrue(ModrinthIndex.load(paths.modrinthIndexFile()).entries().isEmpty());

        Files.write(paths.modsDir().resolve("fabric-api-" + LauncherVersion.FABRIC_API + ".jar"), new byte[] {1});
        final ModrinthService.InstalledContent api = service.installed().stream().filter(ModrinthService.InstalledContent::managed).findFirst().orElseThrow();
        assertEquals(ContentChangeRefusedException.Reason.MANAGED, assertThrows(ContentChangeRefusedException.class,
            () -> service.remove(api)).reason());
    }

    private ModrinthService.InstalledContent find(final String title) throws IOException {
        return service.installed().stream().filter(c -> c.title().equals(title)).findFirst().orElseThrow();
    }

    @Test
    void updateAllBringsTrackedProjectsToTheirNewestVersion() throws Exception {
        final ModrinthService.Resolution old = new ModrinthService.Resolution(List.of(), List.of());
        assertTrue(service.updateAll(DownloadProgressListener.NONE, s -> { }, CancellationToken.NONE).applied().isEmpty(), "nothing tracked yet");
        service.apply(old, DownloadProgressListener.NONE, s -> { }, false, CancellationToken.NONE);
        Files.createDirectories(paths.modsDir());
        Files.write(paths.modsDir().resolve("ImmediatelyFast-Fabric-1.14.2+1.21.11.jar"), new byte[] {1, 2, 3});
        final ModrinthIndex index = ModrinthIndex.load(paths.modrinthIndexFile());
        index.upsert("5ZwdcRci").slug("immediatelyfast").title("ImmediatelyFast").versionId("QwkfUKSj").versionNumber("1.14.2+1.21.11-fabric")
            .type(ContentType.MOD).file("mods/ImmediatelyFast-Fabric-1.14.2+1.21.11.jar").enabled(true).requiredBy(List.of());
        index.save();
        final ModrinthService.ApplyResult result = service.updateAll(DownloadProgressListener.NONE, s -> { }, CancellationToken.NONE);
        assertEquals(1, result.applied().size());
        assertTrue(result.applied().get(0).downloaded());
        assertTrue(Files.isRegularFile(paths.modsDir().resolve("ImmediatelyFast-Fabric-1.14.3+1.21.11.jar")));
        assertFalse(Files.exists(paths.modsDir().resolve("ImmediatelyFast-Fabric-1.14.2+1.21.11.jar")));
        world.close();
        // Modrinth unreachable: the update is an error (nothing changed), not a silent no-op.
        assertThrows(IOException.class, () -> service.updateAll(DownloadProgressListener.NONE, s -> { }, CancellationToken.NONE));
    }

    @Test
    void searchAsksForTheRightFacets() throws Exception {
        final ModrinthModels.SearchPage page = service.search(ContentType.MOD, "sodium", 0);
        assertEquals(2, page.hits().size());
        assertEquals("Sodium", page.hits().get(0).title());
        assertEquals("jellysquid3", page.hits().get(0).author());
        assertEquals(236787190L, page.hits().get(0).downloads());
        assertTrue(page.hasMore());
        assertTrue(world.server().requests().contains("GET /modrinth/v2/search"));
    }

    @Test
    void fabricModIdIsReadFromTheJar() throws IOException {
        final Path jar = tmp.resolve("x.jar");
        Files.write(jar, FakeWorld.modJar("iris", "1"));
        assertEquals("iris", ModrinthService.fabricModId(jar).orElseThrow());
        Files.write(jar, new byte[] {1, 2, 3});
        assertTrue(ModrinthService.fabricModId(jar).isEmpty());
    }
}
