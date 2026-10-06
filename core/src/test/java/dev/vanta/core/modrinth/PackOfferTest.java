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
import dev.vanta.core.notifications.NotificationKind;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.common.ScreenTestSupport;
import dev.vanta.core.settings.VantaSettings;
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
