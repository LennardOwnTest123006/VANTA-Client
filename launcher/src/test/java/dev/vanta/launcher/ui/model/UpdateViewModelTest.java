package dev.vanta.launcher.ui.model;

import dev.vanta.launcher.core.install.VantaClientService;
import dev.vanta.launcher.core.model.ReleaseManifest;
import dev.vanta.launcher.core.util.LauncherPackaging;
import dev.vanta.launcher.ui.LauncherLinks;
import dev.vanta.launcher.ui.testutil.FakeBackend;
import dev.vanta.launcher.ui.testutil.TestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateViewModelTest {

    @TempDir
    Path tmp;
    private TestContext ctx;
    private UpdateViewModel vm;
    private LauncherLinks links;

    @BeforeEach
    void setUp() {
        ctx = new TestContext(tmp);
        final Properties props = new Properties();
        props.setProperty("source.url", "https://github.com/example/vanta");
        props.setProperty("changelog.raw.base", "https://raw.example/main/");
        props.setProperty("changelog.browse.base", "https://github.com/example/vanta/blob/main/");
        links = new LauncherLinks(props, Map.of());
        vm = new UpdateViewModel(ctx.session, ctx.backend, ctx.executors, ctx.messages, ctx.formats, ctx.toasts, links);
    }

    @Test
    void notConfiguredSkipsTheNetwork() {
        ctx.backend.updatesConfigured = false;
        vm.check(false);
        assertEquals(UpdateViewModel.ClientAvailability.NOT_CONFIGURED, vm.clientAvailabilityProperty().get());
        assertFalse(vm.bannerVisibleProperty().get());
        assertTrue(ctx.backend.calls.isEmpty());
        assertTrue(vm.checkedOnce());
    }

    @Test
    void bannerShowsBothUpdatesAndDismisses() {
        ctx.backend.instance = FakeBackend.installedInstance("1.0.0");
        ctx.session.refreshInstance();
        ctx.backend.launcherUpdate = Optional.of(FakeBackend.update(ReleaseManifest.PRODUCT_LAUNCHER, "1.0.0", "1.1.0", true));
        ctx.backend.clientUpdate = Optional.of(FakeBackend.update(ReleaseManifest.PRODUCT_CLIENT, "1.0.0", "1.1.0", true));
        vm.check(false);
        assertTrue(vm.bannerVisibleProperty().get());
        assertEquals(ctx.messages.format("update.banner.both", "1.1.0", "1.1.0"), vm.bannerTextProperty().get());
        assertEquals(UpdateViewModel.ClientAvailability.UPDATE_AVAILABLE, vm.clientAvailabilityProperty().get());
        assertEquals("1.1.0", vm.latestClientVersionProperty().get());
        vm.dismissBanner();
        assertFalse(vm.bannerVisibleProperty().get());
        vm.check(false);
        assertTrue(vm.bannerVisibleProperty().get(), "a new check with updates shows the banner again");
    }

    @Test
    void upToDateIsReportedInteractively() {
        ctx.backend.instance = FakeBackend.installedInstance("1.0.0");
        ctx.session.refreshInstance();
        vm.check(true);
        assertEquals(UpdateViewModel.ClientAvailability.UP_TO_DATE, vm.clientAvailabilityProperty().get());
        assertEquals("1.0.0", vm.latestClientVersionProperty().get());
        assertEquals(ctx.messages.get("update.upToDate.title"), ctx.toasts.toasts().get(0).title());
        assertFalse(vm.bannerVisibleProperty().get());
    }

    @Test
    void announcedButNotDownloadableIsHonest() {
        ctx.backend.instance = FakeBackend.installedInstance("0.9.0");
        ctx.session.refreshInstance();
        ctx.backend.clientUpdate = Optional.of(FakeBackend.update(ReleaseManifest.PRODUCT_CLIENT, "0.9.0", "1.0.0", false));
        vm.check(false);
        assertEquals(UpdateViewModel.ClientAvailability.ANNOUNCED, vm.clientAvailabilityProperty().get());
        assertNotNull(vm.clientUpdateProperty().get());
        vm.installClientUpdate(() -> { });
        assertFalse(ctx.backend.calls.contains("installClientUpdate"), "no download without a URL");
    }

    @Test
    void notPublishedManifestIsHonest() {
        ctx.backend.clientCheckFailure = new dev.vanta.launcher.core.install.NotPublishedException("client", "1.0.0", "no public download yet");
        vm.check(false);
        assertEquals(UpdateViewModel.ClientAvailability.NOT_PUBLISHED, vm.clientAvailabilityProperty().get());
        assertNull(vm.clientUpdateProperty().get());
    }

    @Test
    void missingManifestsAreNeverReportedAsUpToDate() {
        ctx.backend.instance = FakeBackend.installedInstance("1.0.0");
        ctx.session.refreshInstance();
        ctx.backend.launcherCheckFailure = new dev.vanta.launcher.core.install.NotPublishedException("launcher", "",
            "No release manifest at https://releases.example/launcher-latest.json (HTTP 404)");
        ctx.backend.clientCheckFailure = new dev.vanta.launcher.core.install.NotPublishedException("client", "",
            "No release manifest at https://releases.example/client-latest.json (HTTP 404)");
        vm.check(true);
        assertEquals(1, ctx.toasts.toasts().size(), String.valueOf(ctx.toasts.toasts()));
        final ToastModel.Toast toast = ctx.toasts.toasts().get(0);
        assertEquals(ToastModel.Kind.INFO, toast.kind(), "no success toast without a manifest: " + toast);
        assertEquals(ctx.messages.get("update.notPublished.title"), toast.title());
        assertTrue(toast.message().contains("https://releases.example/launcher-latest.json"), toast.message());
        assertTrue(toast.message().contains("https://releases.example/client-latest.json"), toast.message());
        assertFalse(toast.message().contains(ctx.messages.format("update.upToDate.message", dev.vanta.launcher.LauncherVersion.VERSION)));
        assertEquals(UpdateViewModel.ClientAvailability.NOT_PUBLISHED, vm.clientAvailabilityProperty().get());
        assertTrue(vm.checkErrorProperty().get().isEmpty(), "not published is not an error");
        assertFalse(vm.bannerVisibleProperty().get());
    }

    @Test
    void oneMissingManifestIsEnoughToNotClaimUpToDate() {
        ctx.backend.instance = FakeBackend.installedInstance("1.0.0");
        ctx.session.refreshInstance();
        // Launcher manifest found and current, client manifest missing.
        ctx.backend.clientCheckFailure = new dev.vanta.launcher.core.install.NotPublishedException("client", "",
            "No release manifest at https://releases.example/client-latest.json (HTTP 404)");
        vm.check(true);
        assertEquals(ToastModel.Kind.INFO, ctx.toasts.toasts().get(0).kind());
        assertEquals(ctx.messages.get("update.notPublished.title"), ctx.toasts.toasts().get(0).title());

        // Launcher manifest missing, client manifest found and current.
        ctx.toasts.clear();
        ctx.backend.clientCheckFailure = null;
        ctx.backend.launcherCheckFailure = new dev.vanta.launcher.core.install.NotPublishedException("launcher", "",
            "No release manifest at https://releases.example/launcher-latest.json (HTTP 404)");
        vm.check(true);
        assertEquals(ToastModel.Kind.INFO, ctx.toasts.toasts().get(0).kind());
        assertTrue(ctx.toasts.toasts().get(0).message().contains("launcher-latest.json"), ctx.toasts.toasts().get(0).message());
        assertEquals(UpdateViewModel.ClientAvailability.UP_TO_DATE, vm.clientAvailabilityProperty().get());

        // A missing launcher manifest does not hide a client update: the banner shows it and no toast is needed.
        ctx.toasts.clear();
        ctx.backend.clientUpdate = Optional.of(FakeBackend.update(ReleaseManifest.PRODUCT_CLIENT, "1.0.0", "1.1.0", true));
        vm.check(true);
        assertTrue(ctx.toasts.toasts().isEmpty(), String.valueOf(ctx.toasts.toasts()));
        assertTrue(vm.bannerVisibleProperty().get());

        // Not interactive (startup check): no toast at all.
        ctx.backend.clientUpdate = Optional.empty();
        ctx.backend.clientCheckFailure = new dev.vanta.launcher.core.install.NotPublishedException("client", "", "missing");
        vm.check(false);
        assertTrue(ctx.toasts.toasts().isEmpty(), String.valueOf(ctx.toasts.toasts()));
    }

    @Test
    void freshLauncherNeverOffersAClientUpdate() {
        // Nothing installed: no instance.json, no jar in mods/. The release 1.0.1 exists.
        ctx.backend.clientUpdate = Optional.of(FakeBackend.update(ReleaseManifest.PRODUCT_CLIENT, "1.0.0", "1.0.1", true));
        vm.check(false);
        assertEquals(UpdateViewModel.ClientAvailability.NOT_INSTALLED, vm.clientAvailabilityProperty().get());
        assertEquals("1.0.1", vm.latestClientVersionProperty().get(), "the latest release is still shown");
        assertTrue(vm.latestClientDownloadableProperty().get());
        assertNull(vm.clientUpdateProperty().get(), "no update without an installed client");
        assertFalse(vm.bannerVisibleProperty().get(), "no banner for a client that is not installed");
        assertEquals("", vm.bannerTextProperty().get());
        assertTrue(ctx.session.installedClient().isEmpty(), "the check published the installed state it used");

        vm.installClientUpdate(() -> { });
        assertFalse(ctx.backend.calls.contains("installClientUpdate"), "nothing is downloaded into mods/ of a missing instance");

        // Interactive: honest about the client instead of claiming "the installed client matches".
        vm.check(true);
        assertEquals(1, ctx.toasts.toasts().size(), String.valueOf(ctx.toasts.toasts()));
        assertEquals(ctx.messages.format("update.upToDate.notInstalled.message", dev.vanta.launcher.LauncherVersion.VERSION, "1.0.1"),
            ctx.toasts.toasts().get(0).message());
    }

    @Test
    void announcedReleaseWithoutClientIsNotOfferedEither() {
        ctx.backend.clientUpdate = Optional.of(FakeBackend.update(ReleaseManifest.PRODUCT_CLIENT, "1.0.0", "1.0.1", false));
        vm.check(false);
        assertEquals(UpdateViewModel.ClientAvailability.NOT_INSTALLED, vm.clientAvailabilityProperty().get());
        assertFalse(vm.latestClientDownloadableProperty().get(), "the card says 'announced' instead of offering it");
        assertNull(vm.clientUpdateProperty().get());
    }

    @Test
    void launcherUpdateStillShowsWhenNoClientIsInstalled() {
        ctx.backend.launcherUpdate = Optional.of(FakeBackend.update(ReleaseManifest.PRODUCT_LAUNCHER, "1.0.0", "1.0.1", true));
        ctx.backend.clientUpdate = Optional.of(FakeBackend.update(ReleaseManifest.PRODUCT_CLIENT, "1.0.0", "1.0.1", true));
        vm.check(false);
        assertTrue(vm.bannerVisibleProperty().get());
        assertEquals(ctx.messages.format("update.banner.launcher", "1.0.1"), vm.bannerTextProperty().get(),
            "only the launcher is announced, never 'VANTA Client 1.0.1 is available' for a client that is not installed");
        assertNull(vm.clientUpdateProperty().get());
    }

    @Test
    void clientJarInModsWithoutInstanceJsonCountsAsInstalled() {
        // "Use with Minecraft Launcher" on a fresh launcher: the jar is in mods/, no instance.json.
        ctx.backend.activeJar = ctx.backend.paths().modsDir().resolve("vanta-client-1.0.0.jar");
        ctx.backend.clientUpdate = Optional.of(FakeBackend.update(ReleaseManifest.PRODUCT_CLIENT, "1.0.0", "1.0.1", true));
        vm.check(false);
        assertEquals("1.0.0", ctx.session.installedClient().orElseThrow().version());
        assertEquals(UpdateViewModel.ClientAvailability.UPDATE_AVAILABLE, vm.clientAvailabilityProperty().get());
        assertTrue(vm.bannerVisibleProperty().get());
        vm.installClientUpdate(() -> { });
        assertTrue(ctx.backend.calls.contains("installClientUpdate"));
        assertEquals("1.0.1", ctx.session.installedClient().orElseThrow().version(), "the card reads the jar that is now in mods/");
        assertEquals(UpdateViewModel.ClientAvailability.UP_TO_DATE, vm.clientAvailabilityProperty().get());
        assertNull(vm.clientUpdateProperty().get(), "after the update nothing is offered again");
    }

    @Test
    void installingTheClientRechecksSoTheCardIsNeverStale() {
        ctx.backend.latestClientVersion = "1.0.0";
        vm.check(false);
        assertEquals(UpdateViewModel.ClientAvailability.NOT_INSTALLED, vm.clientAvailabilityProperty().get());
        // PLAY installed the instance with the client.
        ctx.backend.instance = FakeBackend.installedInstance("1.0.0");
        ctx.session.setInstance(ctx.backend.instance);
        assertEquals("1.0.0", ctx.session.installedClient().orElseThrow().version());
        assertEquals(UpdateViewModel.ClientAvailability.UP_TO_DATE, vm.clientAvailabilityProperty().get());
        assertEquals(2, ctx.backend.calls.stream().filter("checkClient"::equals).count());
    }

    @Test
    void installedStateChangingDuringACheckIsNeverOverwritten() {
        final boolean[] first = {true};
        ctx.backend.clientCheckHook = () -> {
            if (first[0]) {
                first[0] = false;
                // PLAY finishes its install while the check (which read "not installed") is still running.
                ctx.backend.instance = FakeBackend.installedInstance("1.0.0");
                ctx.session.setInstance(ctx.backend.instance);
            }
        };
        vm.check(false);
        assertEquals("1.0.0", ctx.session.installedClient().orElseThrow().version(), "the check's older 'not installed' never wins");
        assertEquals(UpdateViewModel.ClientAvailability.UP_TO_DATE, vm.clientAvailabilityProperty().get());
        assertEquals(2, ctx.backend.calls.stream().filter("checkClient"::equals).count(), "compared again with the new state");
    }

    @Test
    void checkFailureIsReported() {
        ctx.backend.launcherCheckFailure = new java.net.UnknownHostException("releases.example");
        vm.check(true);
        assertEquals(UpdateViewModel.ClientAvailability.FAILED, vm.clientAvailabilityProperty().get());
        assertEquals(ctx.messages.get("error.network.offline"), vm.checkErrorProperty().get());
        assertEquals(ToastModel.Kind.ERROR, ctx.toasts.toasts().get(0).kind());
    }

    @Test
    void clientUpdateInstallsAndRefreshesInstance() {
        ctx.backend.instance = FakeBackend.installedInstance("1.0.0");
        ctx.backend.activeJar = ctx.backend.paths().modsDir().resolve("vanta-client-1.0.0.jar");
        ctx.session.refreshInstance();
        ctx.backend.clientUpdate = Optional.of(FakeBackend.update(ReleaseManifest.PRODUCT_CLIENT, "1.0.0", "1.1.0", true));
        vm.check(false);
        final boolean[] done = {false};
        vm.installClientUpdate(() -> done[0] = true);
        assertTrue(done[0]);
        assertTrue(ctx.backend.calls.contains("installClientUpdate"));
        assertEquals("1.1.0", ctx.session.instance().orElseThrow().vantaClientVersion());
        assertNull(vm.clientUpdateProperty().get());
        assertEquals(UpdateViewModel.ClientAvailability.UP_TO_DATE, vm.clientAvailabilityProperty().get());
        assertFalse(vm.busyProperty().get());
        assertEquals(ctx.messages.format("update.toast.client.installed.title", "1.1.0"), ctx.toasts.toasts().get(0).title());
        assertEquals(ctx.messages.format("update.toast.client.installed.message", "1.0.0"), ctx.toasts.toasts().get(0).message(),
            "1.0.0 is kept, so the toast names it for roll back");
    }

    @Test
    void rollBackIsOnlyPromisedWhenAKeptCopyOfThePreviousVersionExists() {
        // The core did not keep the replaced jar (pruned right away, or no release version): no roll back sentence.
        ctx.backend.instance = FakeBackend.installedInstance("1.0.0");
        ctx.backend.activeJar = ctx.backend.paths().modsDir().resolve("vanta-client-1.0.0.jar");
        ctx.backend.keepReplacedClient = false;
        ctx.session.refreshInstance();
        ctx.backend.clientUpdate = Optional.of(FakeBackend.update(ReleaseManifest.PRODUCT_CLIENT, "1.0.0", "1.1.0", true));
        vm.check(false);
        vm.installClientUpdate(() -> { });
        final ToastModel.Toast toast = ctx.toasts.toasts().get(0);
        assertEquals(ctx.messages.get("update.toast.client.installed.noRollback"), toast.message());
        assertFalse(toast.message().contains("kept on the Versions page"), toast.message());
        assertFalse(toast.message().contains("previous version is kept for roll back"), "the 1.0.1 claim: " + toast.message());

        assertEquals(ctx.messages.get("update.toast.client.installed.noRollback"), vm.clientInstalledMessage("", false));
        assertEquals(ctx.messages.get("update.toast.client.installed.notKept"), vm.clientInstalledMessage("", true));
        assertEquals(ctx.messages.format("update.toast.client.installed.message", "1.0.0"), vm.clientInstalledMessage("1.0.0", true));
        assertTrue(vm.clientInstalledMessage("1.0.0", false).contains("1.0.0"));
    }

    @Test
    void developmentBuildIsNeverClaimedAsKept() {
        ctx.backend.activeJar = ctx.backend.paths().modsDir().resolve("vanta-client-dev.jar");
        ctx.backend.clientUpdate = Optional.of(FakeBackend.update(ReleaseManifest.PRODUCT_CLIENT, "1.0.0", "1.1.0", true));
        vm.check(false);
        vm.installClientUpdate(() -> { });
        assertEquals(ctx.messages.get("update.toast.client.installed.noRollback"), ctx.toasts.toasts().get(0).message(),
            "nothing else is kept either");
    }

    @Test
    void otherKeptVersionsAreNotDeniedWhenTheReplacedOneIsNotKept() {
        // 1.0.0 installed normally (kept), then a dev jar activated with --install --client-jar, then an update to 1.1.0:
        // the replaced "dev" jar has no copy, but 1.0.0 is still offered for roll back on the Versions page.
        final var manifest = FakeBackend.update(ReleaseManifest.PRODUCT_CLIENT, "0.9.0", "1.0.0", true).manifest();
        ctx.backend.kept.add(new VantaClientService.KeptVersion("1.0.0", manifest,
            ctx.backend.paths().clientVersionsDir().resolve("1.0.0").resolve("vanta-client-1.0.0.jar"), Instant.parse("2026-10-01T10:00:00Z")));
        // A kept entry whose jar is missing cannot be rolled back to, so it does not count.
        ctx.backend.kept.add(new VantaClientService.KeptVersion("0.9.0", manifest, null, Instant.parse("2026-09-01T10:00:00Z")));
        ctx.backend.activeJar = ctx.backend.paths().modsDir().resolve("vanta-client-dev.jar");
        ctx.session.refreshInstance();
        ctx.backend.clientUpdate = Optional.of(FakeBackend.update(ReleaseManifest.PRODUCT_CLIENT, "1.0.0", "1.1.0", true));
        vm.check(false);
        vm.installClientUpdate(() -> { });
        final String message = ctx.toasts.toasts().get(0).message();
        assertEquals(ctx.messages.get("update.toast.client.installed.notKept"), message);
        assertFalse(message.contains("nothing to roll back"), message);

        // Without the kept 1.0.0 there is really nothing left to roll back to.
        ctx.backend.kept.removeIf(k -> k.version().equals("1.0.0"));
        ctx.backend.kept.removeIf(k -> k.version().equals("1.1.0"));
        ctx.backend.activeJar = ctx.backend.paths().modsDir().resolve("vanta-client-dev.jar");
        ctx.session.refreshInstance();
        ctx.backend.clientUpdate = Optional.of(FakeBackend.update(ReleaseManifest.PRODUCT_CLIENT, "1.0.0", "1.1.0", true));
        vm.check(false);
        vm.installClientUpdate(() -> { });
        assertEquals(ctx.messages.get("update.toast.client.installed.noRollback"), ctx.toasts.toasts().get(1).message());
    }

    @Test
    void verifiedDownloadsAreDescribedWithTheirRealFileName() {
        final Path dir = ctx.backend.paths().updatesCacheDir().resolve("1.0.2");
        final Path jar = dir.resolve("vanta-launcher-1.0.2-linux-all.jar");
        assertEquals(UpdateViewModel.DownloadedFile.JAR, UpdateViewModel.kindOf(jar));
        final String jarText = vm.downloadedInstructions(jar);
        assertTrue(jarText.contains("java -jar \"" + jar.toAbsolutePath() + "\""), jarText);
        assertFalse(jarText.contains("1.0.2-vanta-launcher"), "no <version>- prefix (the 1.0.1 bug): " + jarText);

        final Path msi = dir.resolve("VANTA-Launcher-1.0.2.msi");
        assertEquals(UpdateViewModel.DownloadedFile.INSTALLER, UpdateViewModel.kindOf(msi));
        assertTrue(vm.downloadedInstructions(msi).startsWith("The installer VANTA-Launcher-1.0.2.msi was downloaded"), vm.downloadedInstructions(msi));
        assertEquals(UpdateViewModel.DownloadedFile.INSTALLER, UpdateViewModel.kindOf(dir.resolve("VANTA-Launcher-1.0.2.exe")));
        final Path archive = dir.resolve("VANTA-Launcher-1.0.2-linux-x64.tar.gz");
        assertEquals(UpdateViewModel.DownloadedFile.ARCHIVE, UpdateViewModel.kindOf(archive));
        assertTrue(vm.downloadedInstructions(archive).startsWith("VANTA-Launcher-1.0.2-linux-x64.tar.gz was downloaded"));
        final Path zip = dir.resolve("VANTA-Launcher-1.0.2-windows-portable.zip");
        assertEquals(UpdateViewModel.DownloadedFile.PORTABLE, UpdateViewModel.kindOf(zip));
        assertEquals(vm.portableUpdateInstructions(zip.getFileName().toString()), vm.downloadedInstructions(zip));

        // The download lands in cache/updates/<version>/<asset name>; the toast names that folder.
        ctx.backend.launcherUpdate = Optional.of(FakeBackend.update(ReleaseManifest.PRODUCT_LAUNCHER, "1.0.1", "1.0.2", true));
        vm.check(false);
        final AtomicReference<Path> downloaded = new AtomicReference<>();
        vm.downloadLauncherUpdate(downloaded::set);
        assertEquals(dir.resolve("VANTA-Launcher-1.0.2.msi"), downloaded.get());
        assertEquals(ctx.messages.format("update.toast.downloaded.message", "VANTA-Launcher-1.0.2.msi", dir.toString()),
            ctx.toasts.toasts().get(0).message());
    }

    @Test
    void launcherUpdateDownloadsVerifiesAndHandsOverTheInstaller() {
        ctx.backend.launcherUpdate = Optional.of(FakeBackend.update(ReleaseManifest.PRODUCT_LAUNCHER, "1.0.0", "1.1.0", true));
        vm.check(false);
        final AtomicReference<Path> installer = new AtomicReference<>();
        vm.downloadLauncherUpdate(installer::set);
        assertNotNull(installer.get());
        assertTrue(installer.get().getFileName().toString().endsWith("VANTA-Launcher-1.1.0.msi"));
        assertEquals(java.util.List.of("checkLauncherUpdate", "checkClient", "downloadUpdate", "prepareInstaller"), ctx.backend.calls);
    }

    @Test
    void changelogIsFetchedFromTheRawUrlOrFallsBack() {
        final var update = FakeBackend.update(ReleaseManifest.PRODUCT_CLIENT, "1.0.0", "1.1.0", true);
        ctx.backend.documents.put(URI.create("https://raw.example/main/website/content/changelog/client-1.1.0.md"), "# Notes\n- fixed");
        final AtomicReference<String> text = new AtomicReference<>();
        vm.loadChangelog(update, text::set, error -> text.set("ERR"));
        assertEquals("# Notes\n- fixed", text.get());
        assertEquals(Optional.of(URI.create("https://github.com/example/vanta/blob/main/website/content/changelog/client-1.1.0.md")),
            vm.changelogPage(update));
        assertEquals("vanta-client-1.1.0.jar · 4.2 MB", vm.describeFile(update), "4,194,304 bytes in decimal units");

        ctx.backend.documents.clear();
        vm.loadChangelog(update, text::set, error -> text.set("ERR"));
        assertEquals("ERR", text.get());
    }

    @Test
    void releasePageForPlatformsWithoutAnAsset() {
        final UpdateViewModel withReleases;
        final Properties props = new Properties();
        props.setProperty("releases.url", "https://github.com/example/vanta/releases");
        withReleases = new UpdateViewModel(ctx.session, ctx.backend, ctx.executors, ctx.messages, ctx.formats, ctx.toasts, new LauncherLinks(props, Map.of()));
        final dev.vanta.launcher.core.update.UpdateInfo base = FakeBackend.update(ReleaseManifest.PRODUCT_LAUNCHER, "1.0.0", "1.1.0", true);
        final dev.vanta.launcher.core.update.UpdateInfo noAsset = new dev.vanta.launcher.core.update.UpdateInfo(base.product(), base.currentVersion(),
            base.latestVersion(), base.changelog(), new ReleaseManifest.ReleaseFile("", "", 0, ""), "", base.manifest(),
            "https://github.com/example/vanta/releases/tag/launcher-v1.1.0");
        assertFalse(noAsset.hasPlatformAsset());
        assertEquals(Optional.of(URI.create("https://github.com/example/vanta/releases/tag/launcher-v1.1.0")), withReleases.releasePage(noAsset));
        assertEquals(Optional.of(URI.create("https://github.com/example/vanta/releases")), withReleases.releasePage(base),
            "without a release page in the manifest the project's releases page is used");
        assertTrue(vm.releasePage(base).isEmpty(), "nothing configured, nothing shown");
    }

    @Test
    void portableZipIsExtractedIntoTheFolderThatContainsThePortableFolder() {
        // The zip's top level is "VANTA Launcher/": extracting it into the portable folder itself would nest a second
        // "VANTA Launcher" folder inside it and leave the old launcher in place.
        final String zip = "VANTA-Launcher-1.1.0-windows-portable.zip";
        final Path portable = Path.of("D:/Games/VANTA Launcher");
        ctx.backend.packaging = LauncherPackaging.portable(portable);
        final String text = vm.portableUpdateInstructions(zip);
        assertEquals(ctx.messages.format("update.confirm.portable.text", zip, portable.toString(), portable.getParent().toString()), text);
        assertTrue(text.contains("extract the zip into " + portable.getParent() + ", the folder that contains your portable folder"), text);
        assertTrue(text.contains("the \"VANTA Launcher\" folder in the zip replaces " + portable + "."), text);
        assertTrue(text.contains("Do not extract it into " + portable + " itself"), text);
        assertTrue(text.startsWith(zip + " was downloaded and its SHA-256 matches"), text);

        // Windows paths are case-insensitive: a "vanta launcher" folder is still replaced by the zip's folder.
        ctx.backend.packaging = LauncherPackaging.portable(Path.of("E:/vanta launcher"));
        assertTrue(vm.portableUpdateInstructions(zip).contains("extract the zip into E:"), vm.portableUpdateInstructions(zip));
    }

    @Test
    void renamedOrUnknownPortableFolderGetsItsFilesCopiedIn() {
        final String zip = "VANTA-Launcher-1.1.0-windows-portable.zip";
        final Path renamed = Path.of("D:/Games/VANTA");
        ctx.backend.packaging = LauncherPackaging.portable(renamed);
        final String text = vm.portableUpdateInstructions(zip);
        assertEquals(ctx.messages.format("update.confirm.portable.copy.text", zip, renamed.toString()), text);
        assertTrue(text.contains("copy everything inside its \"VANTA Launcher\" folder into your portable folder " + renamed), text);
        assertFalse(text.contains("{"), "every placeholder is filled: " + text);

        ctx.backend.packaging = new LauncherPackaging(LauncherPackaging.Kind.PORTABLE, Optional.empty());
        final String unknown = vm.portableUpdateInstructions(zip);
        assertEquals(ctx.messages.format("update.confirm.portable.copy.text", zip, ctx.messages.get("update.confirm.portable.folderUnknown")), unknown);
        assertTrue(unknown.contains("into your portable folder (the folder that contains \"VANTA Launcher.exe\")"), unknown);
    }
}
