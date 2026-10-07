package dev.vanta.core.modrinth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.bridge.FakeClipboardBridge;
import dev.vanta.core.bridge.FakeKeybindBridge;
import dev.vanta.core.bridge.FakeOptionsBridge;
import dev.vanta.core.bridge.FakeResourcePackBridge;
import dev.vanta.core.bridge.FakeScreenshotBridge;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.notifications.NotificationKind;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.common.ScreenTestSupport;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.UiScreen;
import dev.vanta.core.ui.widget.Dialog;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackOfferTest {
    @TempDir
    Path dir;
    private ScreenTestSupport t;
    private FakeModrinthApi api;
    private FakeModPlatform platform;
    private ModrinthService service;
    private PackOffer offer;

    @BeforeEach
    void setUp() {
        t = ScreenTestSupport.create(dir);
        t.game.onTitleScreen();
        api = FakeModrinthApi.standard();
        platform = new FakeModPlatform(dir);
        service = platform.installInto(t.services, api);
        offer = t.services.packOffer();
        offer.setSuppressed(false);
    }

    @AfterEach
    void clearProperty() {
        System.clearProperty(RestartMarker.RESTARTABLE_PROPERTY);
    }

    /** A screen other than the main menu, so its context carries no offer of its own. */
    private UiScreen host() {
        return t.show(ScreenId.ABOUT, 854, 480);
    }

    /** Writes the fake Modrinth file of a pack member into mods/ under a name of the bundle's choosing. */
    private Path bundled(String slug, String fileName) throws IOException {
        Path mods = dir.resolve("mods");
        Files.createDirectories(mods);
        Path jar = mods.resolve(fileName);
        Files.write(jar, ("fake " + slug + " content").getBytes(StandardCharsets.UTF_8));
        return jar;
    }

    @Test
    void offeredWhenMembersAreMissingAndNothingSpeaksAgainstIt() {
        assertTrue(offer.shouldOffer());
        assertEquals(PerformancePack.slugs(), PackOffer.missing(service).stream().map(PerformancePack.Item::slug).toList());
    }

    @Test
    void notOfferedWithoutTheModrinthService() {
        ScreenTestSupport plain = ScreenTestSupport.create(dir.resolve("plain"));
        plain.services.packOffer().setSuppressed(false);
        assertFalse(plain.services.packOffer().shouldOffer());
    }

    @Test
    void launcherStartedGamesAreNeverOffered() {
        System.setProperty(RestartMarker.RESTARTABLE_PROPERTY, "true");
        assertTrue(service.launcherRestartable());
        assertFalse(offer.shouldOffer());
        assertTrue(offer.showIfWanted(host().context()).isEmpty());
    }

    @Test
    void notOfferedWhenTheSettingIsOff() {
        t.services.settings().set(VantaSettings.MODS_PACK_OFFER, false);
        assertFalse(offer.shouldOffer());
    }

    @Test
    void notOfferedWhenEveryMemberIsLoadedOrInstalled() {
        for (PerformancePack.Item item : PerformancePack.ITEMS) {
            platform.loaded.add(item.modId());
        }
        assertFalse(offer.shouldOffer(), "all loaded");
        platform.loaded.removeIf(id -> !id.equals(ModrinthConstants.FABRIC_API_MOD_ID) && !id.equals("vanta"));
        assertTrue(offer.shouldOffer(), "none loaded");
        service.installPerformancePack(PerformancePack.slugs(), null);
        assertFalse(offer.shouldOffer(), "all in modrinth.json");
        platform.loaded.add("sodium");
        assertTrue(PackOffer.missing(service).isEmpty());
    }

    @Test
    void partlyPresentPacksNameOnlyTheMissingMembers() {
        platform.loaded.add("sodium");
        platform.loaded.add("iris");
        service.install(List.of(InstallRequest.of("lithium")), "Installing Lithium", null);
        assertEquals(List.of("ferrite-core", "immediatelyfast", "entityculling"),
                PackOffer.missing(service).stream().map(PerformancePack.Item::slug).toList());
        assertTrue(offer.shouldOffer());
    }

    @Test
    void gameTestsAndSuppressedHostsNeverSeeTheDialog() {
        PackOffer inGameTest = new PackOffer(t.services, () -> true);
        assertFalse(inGameTest.shouldOffer());
        offer.suppress();
        assertTrue(offer.isSuppressed());
        assertFalse(offer.shouldOffer());
        offer.setSuppressed(false);
        assertTrue(offer.shouldOffer());
        assertFalse(PackOffer.inGameTest(), "the unit tests do not run with -Dvanta.gametest");
    }

    @Test
    void shownOnlyOncePerSession() {
        UiScreen screen = host();
        Optional<Dialog> dialog = offer.showIfWanted(screen.context());
        assertTrue(dialog.isPresent());
        assertEquals("Boost your FPS?", dialog.get().title());
        assertTrue(dialog.get().message().contains("Sodium, Lithium, FerriteCore, ImmediatelyFast, EntityCulling, Iris Shaders"),
                dialog.get().message());
        assertTrue(dialog.get().message().contains("Modrinth"), dialog.get().message());
        assertTrue(offer.wasShown());
        assertFalse(offer.shouldOffer());
        ScreenTestSupport.cancelDialog(screen);
        assertTrue(offer.showIfWanted(screen.context()).isEmpty());
    }

    @Test
    void notNowTurnsTheOfferOffAndPersists() {
        UiScreen screen = host();
        offer.showIfWanted(screen.context());
        assertNotNull(ScreenTestSupport.openDialog(screen));
        int before = api.calls().size();
        ScreenTestSupport.cancelDialog(screen);
        assertNull(ScreenTestSupport.openDialog(screen));
        assertFalse(t.services.settings().get(VantaSettings.MODS_PACK_OFFER));
        assertEquals(before, api.calls().size(), "Not now talks to nobody");
        assertTrue(service.installedProjectIds().isEmpty());
        t.services.settings().save();
        VantaServices fresh = VantaServices.create(VantaPaths.inGameDirectory(dir), t.game, new FakeOptionsBridge(),
                new FakeKeybindBridge(), new FakeResourcePackBridge(), new FakeScreenshotBridge(),
                new FakeClipboardBridge(), t.clock);
        fresh.load();
        assertFalse(fresh.settings().get(VantaSettings.MODS_PACK_OFFER), "persisted in settings.json");
        new FakeModPlatform(dir).installInto(fresh, FakeModrinthApi.standard());
        assertFalse(fresh.packOffer().shouldOffer(), "never nags again");
    }

    @Test
    void escapeCountsAsNotNow() {
        UiScreen screen = host();
        assertTrue(offer.showIfWanted(screen.context()).isPresent());
        int before = api.calls().size();
        assertTrue(ScreenTestSupport.key(screen, Keys.ESCAPE));
        assertNull(ScreenTestSupport.openDialog(screen), "Escape closes the dialog");
        assertFalse(screen.isClosing(), "only the dialog");
        assertFalse(t.services.settings().get(VantaSettings.MODS_PACK_OFFER), "Escape is Not now");
        assertEquals(before, api.calls().size());
        assertFalse(new PackOffer(t.services, () -> false).shouldOffer(), "the next start does not ask again");
        assertTrue(Lang.tr("vanta.mods.offer.body", "x").contains("Escape"), "the dialog says so");
    }

    @Test
    void installDoesNotDownloadALoadedMemberModrinthCannotIdentify() throws IOException {
        // Sodium runs from a CurseForge build: Fabric loaded it, Modrinth's hash lookup does not know the jar.
        Path mods = dir.resolve("mods");
        Files.createDirectories(mods);
        Files.write(mods.resolve("sodium-fabric-0.8.14+mc1.21.11.jar"), "unknown bytes".getBytes(StandardCharsets.UTF_8));
        platform.loaded.add("sodium");
        UiScreen screen = host();
        Dialog dialog = offer.showIfWanted(screen.context()).orElseThrow();
        assertFalse(dialog.message().contains("Sodium"), dialog.message());

        ScreenTestSupport.confirmDialog(screen);

        assertFalse(api.calls().contains("download:sodium-1.0.0.jar"), "a loaded member is never downloaded again");
        assertFalse(Files.exists(mods.resolve("sodium-1.0.0.jar")), "no duplicate Sodium jar");
        for (String file : List.of("lithium-1.0.0.jar", "ferrite-core-1.0.0.jar", "immediatelyfast-1.0.0.jar",
                "entityculling-1.0.0.jar", "iris-1.0.0.jar")) {
            assertTrue(api.calls().contains("download:" + file), file);
        }
        assertEquals(5, service.installedProjectIds().size());
        assertTrue(PackOffer.missing(service).isEmpty());
    }

    @Test
    void installWhileAPackInstallIsRunningQueuesNothing() {
        java.util.Deque<Runnable> worker = new java.util.ArrayDeque<>();
        ModrinthService queued = new ModrinthService(api, new ModrinthLibrary(dir, t.services.jsonStore()), platform,
                t.services.notifications(), worker::add, Runnable::run, t.clock);
        t.services.setModrinth(queued);
        t.services.boostFps();
        assertTrue(queued.isBusy());
        int queuedTasks = worker.size();
        offer.install(null);
        assertEquals(queuedTasks, worker.size(), "no second install behind the running one");
        assertTrue(t.services.notifications().history().stream().anyMatch(n -> n.title().equals("Download still running")));
    }

    @Test
    void installSwitchesAHandDisabledMemberBackOnAndSaysSo() throws IOException {
        // The player renamed Modrinth's own Sodium jar to .disabled by hand; the offer rightly calls it missing.
        bundled("sodium", "sodium-1.0.0.jar.disabled");
        assertTrue(PackOffer.missing(service).stream().anyMatch(i -> i.slug().equals("sodium")));
        UiScreen screen = host();
        offer.showIfWanted(screen.context());
        ScreenTestSupport.confirmDialog(screen);

        InstalledEntry sodium = service.installed("AANobbMI").orElseThrow();
        assertTrue(sodium.enabled());
        assertEquals("mods/sodium-1.0.0.jar", sodium.file());
        assertTrue(Files.exists(dir.resolve("mods/sodium-1.0.0.jar")));
        assertFalse(Files.exists(dir.resolve("mods/sodium-1.0.0.jar.disabled")));
        assertFalse(api.calls().contains("download:sodium-1.0.0.jar"), "identical bytes are not downloaded again");
        var toast = t.services.notifications().history().stream().filter(n -> n.title().equals("Installed")).findFirst()
                .orElseThrow();
        assertTrue(toast.body().startsWith("Sodium, "), "the player is told Sodium was switched on: " + toast.body());
    }

    @Test
    void installAdoptsBundledJarsAndInstallsTheRest() throws IOException {
        // The mods bundle put Sodium and Iris into mods/ under Modrinth's file names; they are loaded in this game.
        bundled("sodium", "sodium-bundled.jar");
        bundled("iris", "iris-bundled.jar");
        platform.loaded.add("sodium");
        platform.loaded.add("iris");
        UiScreen screen = host();
        offer.showIfWanted(screen.context());
        List<InstallResult> results = new ArrayList<>();
        // The dialog's confirm action is PackOffer.install(null); call the same entry point with a listener.
        ScreenTestSupport.confirmDialog(screen);
        assertNull(ScreenTestSupport.openDialog(screen));
        assertTrue(t.services.settings().get(VantaSettings.MODS_PACK_OFFER), "Install keeps the setting on");

        InstalledEntry sodium = service.installed("AANobbMI").orElseThrow();
        assertEquals("mods/sodium-bundled.jar", sodium.file());
        assertEquals("VAANobbMI", sodium.versionId());
        assertEquals("Sodium", sodium.title());
        assertEquals(List.of("YL57xq9U"), sodium.requiredBy(), "Iris requires Sodium, like after a VANTA install");
        assertEquals("mods/iris-bundled.jar", service.installed("YL57xq9U").orElseThrow().file());
        assertFalse(api.calls().contains("download:sodium-1.0.0.jar"), "the bundled Sodium is not downloaded again");
        assertFalse(api.calls().contains("download:iris-1.0.0.jar"));
        for (String file : List.of("lithium-1.0.0.jar", "ferrite-core-1.0.0.jar", "immediatelyfast-1.0.0.jar",
                "entityculling-1.0.0.jar")) {
            assertTrue(api.calls().contains("download:" + file), file);
            assertTrue(Files.exists(dir.resolve("mods").resolve(file)), file);
        }
        assertEquals(6, service.installedProjectIds().size());
        assertTrue(service.restartRequired());
        assertEquals(NotificationKind.SUCCESS, t.services.notifications().history().get(0).kind());
        assertTrue(PackOffer.missing(service).isEmpty());
        assertTrue(service.installedItems().stream().allMatch(i -> i.state() == LocalItem.State.INSTALLED),
                "every pack jar is managed now: " + service.installedItems());

        // The listener variant reports the install result as well.
        offer.install(results::add);
        assertEquals(1, results.size());
        assertTrue(results.get(0).installed().isEmpty(), "everything was already there");
    }
}
