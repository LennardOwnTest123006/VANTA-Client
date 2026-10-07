package dev.vanta.core.modrinth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.bridge.FakeClipboardBridge;
import dev.vanta.core.bridge.FakeGameBridge;
import dev.vanta.core.bridge.FakeKeybindBridge;
import dev.vanta.core.bridge.FakeOptionsBridge;
import dev.vanta.core.bridge.FakeResourcePackBridge;
import dev.vanta.core.bridge.FakeScreenshotBridge;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.notifications.Notification;
import dev.vanta.core.notifications.NotificationKind;
import dev.vanta.core.screen.VantaServices;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModrinthServiceTest {
    @TempDir
    Path gameDir;

    private final FakeGameBridge game = new FakeGameBridge();
    private final MutableClock clock = MutableClock.standard();
    private VantaServices services;
    private FakeModrinthApi api;
    private FakeModPlatform platform;
    private ModrinthService service;

    @BeforeEach
    void setUp() {
        services = VantaServices.create(VantaPaths.inGameDirectory(gameDir), game, new FakeOptionsBridge(),
                new FakeKeybindBridge(), new FakeResourcePackBridge(), new FakeScreenshotBridge(),
                new FakeClipboardBridge(), clock);
        services.load();
        api = FakeModrinthApi.standard();
        platform = new FakeModPlatform(gameDir);
        service = platform.installInto(services, api);
    }

    @AfterEach
    void clearProperty() {
        System.clearProperty(RestartMarker.RESTARTABLE_PROPERTY);
    }

    private List<Notification> toasts() {
        return services.notifications().history();
    }

    @Test
    void installShowsProgressThenSuccessAndAsksForARestart() {
        List<InstallResult> results = new ArrayList<>();
        long changes = service.changeCount();
        service.install(List.of(InstallRequest.of("iris")), "Installing Iris Shaders", results::add);
        InstallResult result = results.get(0);
        assertEquals(List.of("AANobbMI", "YL57xq9U"), result.installed().stream().map(PlannedInstall::projectId).toList());
        assertTrue(service.restartRequired());
        assertTrue(service.changeCount() > changes);
        assertFalse(service.isBusy());
        List<Notification> toasts = toasts();
        Notification last = toasts.get(0);
        assertEquals(NotificationKind.SUCCESS, last.kind());
        assertTrue(last.body().contains("Sodium") && last.body().contains("Iris Shaders"), last.body());
        assertTrue(last.body().contains("Restart"), last.body());
        assertTrue(toasts.stream().anyMatch(t -> t.title().equals("Installing Iris Shaders") && t.progress().isPresent()));
        assertTrue(Files.exists(gameDir.resolve("mods/iris-1.0.0.jar")));
        assertTrue(service.installed("AANobbMI").isPresent());
    }

    @Test
    void performancePackInstallsEverySelectedMember() {
        List<InstallResult> results = new ArrayList<>();
        service.installPerformancePack(PerformancePack.slugs(), results::add);
        assertEquals(6, results.get(0).installed().size());
        assertTrue(results.get(0).isClean());
        for (String slug : PerformancePack.slugs()) {
            assertTrue(service.installedItems().stream().anyMatch(i -> i.entry().map(e -> e.slug().equals(slug))
                    .orElse(false)), slug);
        }
    }

    @Test
    void adoptKnownFilesRecordsBundledPackJarsAndLeavesOtherJarsAlone() throws Exception {
        Path mods = gameDir.resolve("mods");
        Files.createDirectories(mods);
        // Bundle files carry Modrinth's own names, which may differ from the fake API's; identification is by hash.
        Files.writeString(mods.resolve("sodium-fabric-bundled.jar"), "fake sodium content");
        Files.writeString(mods.resolve("iris-fabric-bundled.jar.disabled"), "fake iris content");
        Files.writeString(mods.resolve("modmenu-bundled.jar"), "fake modmenu content");
        Files.writeString(mods.resolve("unknown-mod.jar"), "no Modrinth file has this content");
        List<List<InstalledEntry>> results = new ArrayList<>();
        long changes = service.changeCount();
        service.adoptKnownFiles(results::add);
        List<InstalledEntry> adopted = results.get(0);
        assertEquals(List.of("AANobbMI"), adopted.stream().map(InstalledEntry::projectId).sorted().toList(),
                "the enabled pack jar; a hand-disabled one stays the player's choice");
        assertTrue(service.changeCount() > changes);
        InstalledEntry sodium = service.installed("AANobbMI").orElseThrow();
        assertEquals("mods/sodium-fabric-bundled.jar", sodium.file());
        assertEquals("sodium", sodium.slug());
        assertEquals("VAANobbMI", sodium.versionId());
        assertEquals("1.0.0", sodium.versionNumber());
        assertEquals("mod", sodium.type());
        assertTrue(sodium.enabled());
        assertEquals(Sha512.hex("fake sodium content".getBytes(java.nio.charset.StandardCharsets.UTF_8)), sodium.sha512());
        assertTrue(sodium.requiredBy().isEmpty(), "no link to an Iris that is not managed");
        assertEquals(Instant.parse(clock.instant().toString()).truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString(),
                sodium.installedAt());
        assertTrue(service.installed("YL57xq9U").isEmpty(), "a .jar.disabled file is never adopted");
        assertTrue(service.installed("MODMENU1").isEmpty(), "Mod Menu is on Modrinth but not a pack member");
        List<LocalItem> items = service.installedItems();
        assertEquals(1, items.stream().filter(i -> i.state() == LocalItem.State.INSTALLED).count());
        assertEquals(3, items.stream().filter(i -> i.state() == LocalItem.State.MANUAL).count(), items.toString());
        assertFalse(service.restartRequired(), "adoption changes no files");
        assertTrue(toasts().isEmpty(), "adoption is silent");

        // Running it again changes nothing; installing the pack afterwards downloads only what is really missing.
        // The hand-disabled Iris under its own name is not "installed": the player gets a loadable copy next to it.
        service.adoptKnownFiles(results::add);
        assertTrue(results.get(1).isEmpty());
        service.installPerformancePack(PerformancePack.slugs(), null);
        assertFalse(api.calls().contains("download:sodium-1.0.0.jar"));
        assertTrue(api.calls().contains("download:iris-1.0.0.jar"));
        assertTrue(api.calls().contains("download:lithium-1.0.0.jar"));
        assertEquals(6, service.installedProjectIds().size());
        assertTrue(service.installed("YL57xq9U").orElseThrow().enabled());
        assertEquals(List.of("YL57xq9U"), service.installed("AANobbMI").orElseThrow().requiredBy(), "Iris requires Sodium");
        assertTrue(Files.exists(mods.resolve("iris-fabric-bundled.jar.disabled")), "the switched-off jar is left alone");
    }

    @Test
    void adoptionSurvivesAJarWhoseProjectIsGoneAndWritesOnlyExactPackMatches() throws Exception {
        Path mods = gameDir.resolve("mods");
        Files.createDirectories(mods);
        Files.writeString(mods.resolve("sodium-fabric-bundled.jar"), "fake sodium content");
        // Same mod, but not the bytes Modrinth published (a rebuilt or patched jar): no hash hit, so no entry.
        Files.writeString(mods.resolve("lithium-patched.jar"), "fake lithium content with a patch");
        // A Modrinth file whose project page no longer exists: the hash resolves, the project lookup fails.
        Files.writeString(mods.resolve("modmenu-old.jar"), "fake modmenu content");
        api.removeProject("MODMENU1");
        List<List<InstalledEntry>> results = new ArrayList<>();
        service.adoptKnownFiles(results::add);
        assertEquals(List.of("AANobbMI"), results.get(0).stream().map(InstalledEntry::projectId).toList());
        assertTrue(service.installed("LITHIUM1").isEmpty(), "only the exact SHA-512 of a published file is adopted");
        assertTrue(service.installed("MODMENU1").isEmpty());
        assertTrue(service.installedItems().stream().filter(i -> i.state() == LocalItem.State.MANUAL)
                .map(LocalItem::name).toList().containsAll(List.of("lithium-patched.jar", "modmenu-old.jar")));
        assertTrue(toasts().isEmpty());
        // The index on disk is the shared format: schemaVersion and the adopted entry, nothing else.
        String index = Files.readString(gameDir.resolve("config/vanta/modrinth.json"));
        assertTrue(index.contains("\"schemaVersion\""), index);
        assertTrue(index.contains("\"AANobbMI\"") && !index.contains("LITHIUM1") && !index.contains("MODMENU1"), index);
    }

    @Test
    void installingWhatIsThereAlreadySaysSo() {
        service.install(List.of(InstallRequest.of("sodium")), "Installing Sodium", null);
        clock.advance(5_000);
        service.install(List.of(InstallRequest.of("sodium")), "Installing Sodium again", null);
        assertEquals("Already installed", toasts().get(0).title());
    }

    @Test
    void shaderPacksDoNotNeedARestart() {
        service.install(List.of(InstallRequest.of("complementary-reimagined")), "Installing a shader", null);
        assertFalse(service.restartRequired());
        assertTrue(Files.exists(gameDir.resolve("shaderpacks/complementary-reimagined-1.0.0.zip")));
        assertTrue(toasts().get(0).body().contains("shader settings"), toasts().get(0).body());
    }

    @Test
    void searchResultsAreCachedForFiveMinutes() {
        List<ModrinthSearchResult> results = new ArrayList<>();
        SearchRequest request = SearchRequest.of("", ModrinthProjectType.SHADER);
        service.search(request, results::add, e -> { });
        service.search(request, results::add, e -> { });
        assertEquals(2, results.size());
        assertEquals(2, results.get(0).hits().size());
        assertEquals(1, api.calls().stream().filter(c -> c.startsWith("search:")).count());
        clock.advance(5 * 60_000L + 1);
        service.search(request, results::add, e -> { });
        assertEquals(2, api.calls().stream().filter(c -> c.startsWith("search:")).count());
    }

    @Test
    void searchErrorsReachTheCaller() {
        api.failSearch(new ModrinthException(ModrinthException.Kind.NETWORK, "offline"));
        List<ModrinthException> errors = new ArrayList<>();
        service.search(SearchRequest.of("x", ModrinthProjectType.MOD), r -> { }, errors::add);
        assertEquals(ModrinthException.Kind.NETWORK, errors.get(0).kind());
    }

    @Test
    void removeAndToggleChangeFilesAndRequireARestart() {
        service.install(List.of(InstallRequest.of("lithium")), "Installing Lithium", null);
        ModrinthService fresh = platform.installInto(services, api);
        assertFalse(fresh.restartRequired());
        fresh.setEnabled("LITHIUM1", false, null);
        assertTrue(fresh.restartRequired());
        assertTrue(Files.exists(gameDir.resolve("mods/lithium-1.0.0.jar.disabled")));
        List<InstalledEntry> removed = new ArrayList<>();
        fresh.remove("LITHIUM1", removed::add);
        assertEquals("Lithium", removed.get(0).title());
        assertFalse(Files.exists(gameDir.resolve("mods/lithium-1.0.0.jar.disabled")));
        assertEquals("Removed", toasts().get(0).title());
        services.notifications().dismissAll();
        fresh.remove("LITHIUM1", removed::add);
        assertEquals(NotificationKind.ERROR, toasts().get(0).kind(), "removing twice reports an error");
        assertEquals(1, removed.size());
    }

    @Test
    void restartWritesTheMarkerOnlyWhenTheLauncherCanRelaunch() throws Exception {
        assertFalse(service.restartOrQuit(game));
        assertEquals("quit", game.actions.get(game.actions.size() - 1));
        Path marker = gameDir.resolve("config/vanta/restart.request");
        assertFalse(Files.exists(marker));

        System.setProperty(RestartMarker.RESTARTABLE_PROPERTY, "true");
        assertTrue(service.launcherRestartable());
        assertTrue(service.restartOrQuit(game));
        String stamp = Files.readString(marker);
        assertEquals(Instant.ofEpochMilli(clock.millis()).toString(), stamp, "UTF-8 ISO-8601 timestamp only");
        assertEquals(2, game.actions.stream().filter("quit"::equals).count());
    }

    @Test
    void restartWaitsUntilRunningWorkIsDone() throws Exception {
        List<Runnable> mainQueue = new ArrayList<>();
        ModrinthLibrary library = new ModrinthLibrary(gameDir, new dev.vanta.core.config.JsonStore(clock));
        ModrinthService queued = new ModrinthService(api, library, platform, services.notifications(), Runnable::run,
                mainQueue::add, clock);
        queued.install(List.of(InstallRequest.of("lithium")), "Installing Lithium", null);
        assertTrue(queued.isBusy(), "the download is still running");
        Path marker = gameDir.resolve("config/vanta/restart.request");
        assertFalse(queued.restartOrQuit(game));
        System.setProperty(RestartMarker.RESTARTABLE_PROPERTY, "true");
        assertFalse(queued.restartOrQuit(game), "no marker while busy either");
        assertFalse(game.actions.contains("quit"), "quitting now would abandon the download");
        assertFalse(Files.exists(marker));

        mainQueue.forEach(Runnable::run);
        assertFalse(queued.isBusy());
        assertTrue(queued.restartOrQuit(game));
        assertTrue(Files.exists(marker));
        assertEquals("quit", game.actions.get(game.actions.size() - 1));
    }

    @Test
    void adoptionSkipsHandDisabledAndWrongVersionJarsAndInstallGetsALoadableCopy() throws Exception {
        api.addRelease("AANobbMI", "VOLDSOD", "0.9.0", "1.21.1", "sodium-0.9.0.jar");
        Path mods = gameDir.resolve("mods");
        Files.createDirectories(mods);
        Files.write(mods.resolve("lithium-1.0.0.jar.disabled"), api.fileContent("VLITHIUM1"));
        Files.write(mods.resolve("sodium-0.9.0.jar"), api.fileContent("VOLDSOD"));
        Files.write(mods.resolve("iris-1.0.0.jar"), api.fileContent("VYL57xq9U"));

        List<InstalledEntry> adopted = service.adoptKnownFilesNow();
        assertEquals(List.of("YL57xq9U"), adopted.stream().map(InstalledEntry::projectId).toList(),
                "only the enabled jar of a version for this game is adopted");
        assertTrue(service.installed("LITHIUM1").isEmpty(), "a hand-disabled jar stays the player's choice");
        assertTrue(service.installed("AANobbMI").isEmpty(), "a 1.21.1 jar is not Sodium for 1.21.11");
        assertTrue(Files.exists(mods.resolve("lithium-1.0.0.jar.disabled")), "adoption renames nothing");

        List<InstallResult> results = new ArrayList<>();
        service.installPerformancePack(PerformancePack.slugs(), results::add);
        assertTrue(api.calls().contains("download:sodium-1.0.0.jar"), "the old jar does not count as installed");
        assertTrue(Files.exists(mods.resolve("sodium-0.9.0.jar")), "the old jar is left alone");
        assertTrue(Files.exists(mods.resolve("sodium-1.0.0.jar")));
        assertFalse(api.calls().contains("download:lithium-1.0.0.jar"), "identical bytes are not downloaded again");
        assertTrue(Files.exists(mods.resolve("lithium-1.0.0.jar")), "Install switches the identical copy back on");
        assertFalse(Files.exists(mods.resolve("lithium-1.0.0.jar.disabled")));
        assertTrue(service.installed("LITHIUM1").orElseThrow().enabled());
        assertTrue(results.get(0).installed().stream().anyMatch(i -> i.projectId().equals("LITHIUM1")),
                "the toast names what was switched on");
        assertTrue(results.get(0).modsChanged());
    }

    @Test
    void callbacksRunOnTheMainThreadExecutor() {
        List<Runnable> mainQueue = new ArrayList<>();
        Executor main = mainQueue::add;
        ModrinthLibrary library = new ModrinthLibrary(gameDir, new dev.vanta.core.config.JsonStore(clock));
        ModrinthService queued = new ModrinthService(api, library, platform, services.notifications(), Runnable::run,
                main, clock);
        List<ModrinthSearchResult> results = new ArrayList<>();
        queued.search(SearchRequest.of("", ModrinthProjectType.MOD), results::add, e -> { });
        assertTrue(results.isEmpty(), "not delivered before the main thread runs");
        assertFalse(queued.isBusy(), "searches never block installs");
        mainQueue.forEach(Runnable::run);
        assertEquals(1, results.size());
        mainQueue.clear();
        queued.install(List.of(InstallRequest.of("lithium")), "Installing Lithium", null);
        assertTrue(queued.isBusy(), "install pending until its result reaches the main thread");
        mainQueue.forEach(Runnable::run);
        assertFalse(queued.isBusy());
        assertTrue(queued.installed("LITHIUM1").isPresent());
    }
}
