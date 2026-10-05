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
