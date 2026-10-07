package dev.vanta.launcher.ui.model;

import dev.vanta.launcher.LauncherVersion;
import dev.vanta.launcher.core.auth.Account;
import dev.vanta.launcher.core.install.InstallException;
import dev.vanta.launcher.core.install.InstallStep;
import dev.vanta.launcher.core.install.NotPublishedException;
import dev.vanta.launcher.core.launch.StartupGuard;
import dev.vanta.launcher.ui.testutil.FakeBackend;
import dev.vanta.launcher.ui.testutil.TestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeViewModelTest {

    @TempDir
    Path tmp;
    private TestContext ctx;
    private FakeBackend backend;

    @BeforeEach
    void setUp() {
        ctx = new TestContext(tmp);
        backend = ctx.backend;
    }

    @Test
    void notReadyWithoutAccountThenReadyAfterSignIn() {
        backend.javaInstalls.add(backend.temurin21());
        ctx.session.refreshAll();
        final HomeViewModel vm = ctx.home();
        assertEquals(HomeViewModel.State.NOT_READY, vm.state());
        assertFalse(vm.playEnabledProperty().get());
        assertEquals(ctx.messages.get("home.block.noAccount"), vm.blockReasonProperty().get());
        assertEquals("Minecraft 1.21.11 · Fabric 0.19.5 · Java 21 (21.0.4)", vm.factsTextProperty().get());

        ctx.session.setAccount(FakeBackend.microsoftAccount("Nova"));
        assertEquals(HomeViewModel.State.READY, vm.state());
        assertTrue(vm.playEnabledProperty().get());
        assertEquals("", vm.blockReasonProperty().get());
        assertEquals(ctx.messages.get("home.status.ready"), vm.statusTextProperty().get());
    }

    @Test
    void notReadyWithoutJavaExplainsAndFactsSayNotFound() {
        backend.accounts.add(FakeBackend.microsoftAccount("Nova"));
        ctx.session.refreshAll();
        final HomeViewModel vm = ctx.home();
        assertEquals(HomeViewModel.State.NOT_READY, vm.state());
        assertEquals(ctx.messages.format("home.block.noJava", Integer.toString(LauncherVersion.JAVA_MAJOR)), vm.blockReasonProperty().get());
        assertTrue(vm.factsTextProperty().get().endsWith("(not found)"));

        vm.installJava();
        // nothing configured on the fake → failure toast, still not ready
        assertEquals(HomeViewModel.State.NOT_READY, vm.state());
        assertEquals(ToastModel.Kind.ERROR, ctx.toasts.toasts().get(0).kind());

        backend.temurin = backend.temurin21();
        vm.installJava();
        assertEquals(HomeViewModel.State.READY, vm.state());
        assertTrue(backend.calls.contains("saveSettings"), "selecting the installed runtime persists the override");
        assertEquals(backend.temurin21().home().toString(), backend.settings.javaPath());
    }

    @Test
    void playInstallsRefreshesLaunchesAndReturnsToReadyOnExit() {
        signedInWithJava();
        final HomeViewModel vm = ctx.home();
        final List<HomeViewModel.State> seen = new ArrayList<>();
        backend.installHook = () -> seen.add(vm.state());
        final List<String> steps = new ArrayList<>();
        vm.stepTextProperty().addListener((obs, old, now) -> steps.add(now));

        vm.play();

        assertEquals(List.of(HomeViewModel.State.INSTALLING), seen, "install runs in the INSTALLING state when nothing is installed");
        assertEquals(HomeViewModel.State.RUNNING, vm.state());
        assertFalse(vm.playEnabledProperty().get());
        assertEquals(ctx.messages.get("home.block.running"), vm.blockReasonProperty().get());
        assertNotNull(vm.gameProperty().get());
        assertTrue(steps.stream().anyMatch(s -> s.contains("Step 4 of 11 \u00B7 Downloading libraries")), steps.toString());
        assertTrue(steps.contains(ctx.messages.get("home.status.refreshingAccount")));
        assertEquals(List.of("install", "refreshIfExpired", "launch"), backend.calls.stream()
            .filter(c -> c.equals("install") || c.equals("refreshIfExpired") || c.equals("launch")).toList());
        assertEquals(5, ctx.gameLog.size(), "game output is streamed into the game log");
        assertEquals(LogLevel.WARN, ctx.gameLog.snapshot().get(3).level());
        assertTrue(ctx.session.instance().isPresent());

        backend.game.exit(0);
        assertEquals(HomeViewModel.State.READY, vm.state());
        assertTrue(vm.playEnabledProperty().get());
        assertTrue(ctx.toasts.toasts().stream().anyMatch(t -> t.title().equals(ctx.messages.get("home.toast.gameExited.title"))));
    }

    @Test
    void nonZeroExitShowsWarningToastAndStaysReady() {
        signedInWithJava();
        backend.instance = FakeBackend.installedInstance("1.0.0");
        ctx.session.refreshInstance();
        final HomeViewModel vm = ctx.home();
        final List<HomeViewModel.State> seen = new ArrayList<>();
        backend.installHook = () -> seen.add(vm.state());
        vm.play();
        assertEquals(List.of(HomeViewModel.State.VERIFYING), seen, "an existing instance is verified, not installed");
        backend.game.exit(1);
        assertEquals(HomeViewModel.State.READY, vm.state());
        final ToastModel.Toast last = ctx.toasts.toasts().get(ctx.toasts.toasts().size() - 1);
        assertEquals(ToastModel.Kind.WARNING, last.kind());
        assertEquals(ctx.messages.format("home.toast.gameCrashed.title", "1"), last.title());
    }

    @Test
    void installFailureGoesToErrorAndPlayRetries() {
        signedInWithJava();
        backend.installFailure = new InstallException(InstallStep.VANTA_CLIENT, "Installing VANTA Client failed",
            new NotPublishedException("client", "1.0.0", "VANTA Client 1.0.0 has no public download yet"));
        final HomeViewModel vm = ctx.home();
        vm.play();
        assertEquals(HomeViewModel.State.ERROR, vm.state());
        assertTrue(vm.playEnabledProperty().get(), "PLAY retries after an error");
        assertTrue(vm.errorTextProperty().get().contains("Installing VANTA Client"));
        assertTrue(vm.errorTextProperty().get().contains("No public release is available yet"));
        assertEquals(ctx.messages.get("home.title.error"), vm.titleTextProperty().get());
        assertFalse(vm.progressVisibleProperty().get());

        backend.installFailure = null;
        vm.play();
        assertEquals(HomeViewModel.State.RUNNING, vm.state());
        assertEquals("", vm.errorTextProperty().get());
    }

    @Test
    void launchFailureIsReportedAsError() {
        signedInWithJava();
        backend.launchFailure = new java.io.IOException("Unresolved launch placeholders: [auth_xuid]");
        final HomeViewModel vm = ctx.home();
        vm.play();
        assertEquals(HomeViewModel.State.ERROR, vm.state());
        assertTrue(vm.errorTextProperty().get().contains("Unresolved launch placeholders"));
    }

    @Test
    void verifyWithoutLaunchingShowsToastAndStaysReady() {
        signedInWithJava();
        backend.instance = FakeBackend.installedInstance("1.0.0");
        ctx.session.refreshInstance();
        final HomeViewModel vm = ctx.home();
        vm.verify();
        assertEquals(HomeViewModel.State.READY, vm.state());
        assertFalse(backend.calls.contains("launch"));
        assertEquals(ctx.messages.get("home.toast.verified.title"), ctx.toasts.toasts().get(0).title());
    }

    @Test
    void verifySaysWhenItAlsoReplacedTheClientWithTheLatestRelease() {
        // The regular install behind "Verify files" always installs the latest VANTA Client release.
        backend.javaInstalls.add(backend.temurin21());
        backend.instance = FakeBackend.installedInstance("1.0.0");
        backend.installClientVersion = "1.0.1";
        ctx.session.refreshAll();
        final HomeViewModel vm = ctx.home();
        vm.verify();
        final ToastModel.Toast toast = ctx.toasts.toasts().get(0);
        assertEquals(ctx.messages.get("home.toast.verified.title"), toast.title());
        assertEquals(ctx.messages.format("home.toast.verified.clientUpdated", "1.0.1"), toast.message());
        assertFalse(toast.message().contains("Nothing was missing"), toast.message());

        vm.verify();
        assertEquals(ctx.messages.get("home.toast.verified.message"), ctx.toasts.toasts().get(1).message(), "already the latest release");
        assertTrue(ctx.messages.get("home.verify.tooltip").contains("latest release"), "the tooltip says so too");
    }

    @Test
    void verifyWorksWithoutAccount() {
        backend.javaInstalls.add(backend.temurin21());
        backend.instance = FakeBackend.installedInstance("1.0.0");
        ctx.session.refreshAll();
        final HomeViewModel vm = ctx.home();
        assertEquals(HomeViewModel.State.NOT_READY, vm.state());
        assertTrue(vm.verifyAvailableProperty().get());
        final List<HomeViewModel.State> seen = new ArrayList<>();
        backend.installHook = () -> seen.add(vm.state());
        vm.verify();
        assertEquals(List.of(HomeViewModel.State.VERIFYING), seen);
        assertEquals(HomeViewModel.State.NOT_READY, vm.state(), "verifying does not need an account and returns to the idle state");
        assertEquals(ctx.messages.get("home.toast.verified.title"), ctx.toasts.toasts().get(0).title());
    }

    @Test
    void verifyNeverStartsAFirstInstall() {
        // 1.0.1: "Verify files" on a fresh launcher downloaded the whole game (INSTALLING).
        backend.javaInstalls.add(backend.temurin21());
        ctx.session.refreshAll();
        final HomeViewModel vm = ctx.home();
        assertFalse(vm.verifyAvailableProperty().get(), "hidden while nothing is installed");
        final List<HomeViewModel.State> seen = new ArrayList<>();
        vm.stateProperty().addListener((obs, old, now) -> seen.add(now));
        vm.verify();
        assertTrue(seen.isEmpty(), "no INSTALLING/VERIFYING: " + seen);
        assertFalse(backend.calls.contains("install"));
        assertEquals(ctx.messages.get("home.toast.nothingToVerify.title"), ctx.toasts.toasts().get(0).title());
        assertEquals(ctx.messages.get("error.nothingInstalled"), ctx.toasts.toasts().get(0).message(), "sign-in is configured: PLAY installs");

        // Without Microsoft sign-in PLAY stays disabled: point to the official Minecraft Launcher instead.
        backend.signInConfigured = false;
        ctx.session.refreshAll();
        vm.verify();
        assertEquals(ctx.messages.get("error.nothingInstalled.official"), ctx.toasts.toasts().get(1).message());
        assertFalse(ctx.toasts.toasts().get(1).message().contains("PLAY to install"), ctx.toasts.toasts().get(1).message());
        assertTrue(ctx.toasts.toasts().get(1).message().contains("Use with Minecraft Launcher"));
        assertFalse(backend.calls.contains("install"));

        // A stored account (accounts load without a client id) enables PLAY, so PLAY is what installs: same rule as the
        // client card's "Install now".
        ctx.session.setAccount(FakeBackend.microsoftAccount("Nova"));
        vm.verify();
        assertEquals(ctx.messages.get("error.nothingInstalled"), ctx.toasts.toasts().get(2).message());
        assertFalse(backend.calls.contains("install"));
        ctx.session.setAccount(null);

        // The deliberate install (the client card's "Install now") still installs; afterwards Verify is offered.
        vm.install();
        assertTrue(backend.calls.contains("install"));
        assertTrue(vm.verifyAvailableProperty().get());
    }

    @Test
    void cancelledInstallReturnsToReadyWithInfoToast() {
        signedInWithJava();
        final HomeViewModel vm = ctx.home();
        backend.installHook = vm::cancel;
        vm.play();
        assertEquals(HomeViewModel.State.READY, vm.state());
        assertEquals("", vm.errorTextProperty().get());
        assertEquals(ctx.messages.get("home.toast.cancelled.title"), ctx.toasts.toasts().get(0).title());
        assertFalse(backend.calls.contains("launch"));
    }

    @Test
    void losingTheAccountWhileReadyGoesBackToNotReady() {
        signedInWithJava();
        final HomeViewModel vm = ctx.home();
        assertEquals(HomeViewModel.State.READY, vm.state());
        ctx.session.signOut(() -> { });
        assertEquals(HomeViewModel.State.NOT_READY, vm.state());
        assertTrue(backend.calls.contains("removeAccount"));
    }

    @Test
    void offlineSessionFollowsCorePolicy() {
        backend.javaInstalls.add(backend.temurin21());
        ctx.session.refreshAll();
        final HomeViewModel vm = ctx.home();
        vm.startOfflineSession();
        assertEquals(HomeViewModel.State.NOT_READY, vm.state());
        assertEquals(ToastModel.Kind.ERROR, ctx.toasts.toasts().get(0).kind());

        backend.offlineAllowed = true;
        backend.accounts.add(FakeBackend.microsoftAccount("Nova"));
        vm.startOfflineSession();
        final Account offline = ctx.session.account().orElseThrow();
        assertEquals("Nova", offline.name());
        assertEquals(dev.vanta.launcher.core.auth.AccountType.OFFLINE, offline.type());
        assertEquals(HomeViewModel.State.READY, vm.state());
    }

    @Test
    void withoutSignInTheOfficialLauncherIsTheWayToPlay() {
        backend.signInConfigured = false;
        backend.javaInstalls.add(backend.temurin21());
        ctx.session.refreshAll();
        final HomeViewModel vm = ctx.home();
        assertEquals(HomeViewModel.State.NOT_READY, vm.state());
        assertEquals(ctx.messages.format("home.block.viaOfficial", "VANTA 1.21.11"), vm.blockReasonProperty().get(),
            "no 'sign in first' when sign-in is impossible: the button plays through the Minecraft Launcher");
        assertEquals(ctx.messages.format("home.lead.noSignIn", "VANTA 1.21.11"), vm.leadTextProperty().get());
        assertTrue(vm.officialPlayModeProperty().get(), "PLAY becomes 'PLAY via Minecraft Launcher'");

        final List<dev.vanta.launcher.core.install.OfficialProfileService.Plan> plans = new ArrayList<>();
        vm.prepareOfficialProfile(plans::add);
        assertEquals(1, plans.size());
        assertEquals("VANTA 1.21.11", plans.get(0).profileName());
        assertEquals(9, plans.get(0).files().size(), "5 files plus 4 performance pack mods (on by default)");
        for (var f : plans.get(0).files()) {
            assertFalse(vm.officialPlanLabel(f).isBlank());
        }
        assertTrue(plans.get(0).files().stream().anyMatch(f -> vm.officialPlanLabel(f).startsWith("Sodium mc1.21.11-0.8.14-fabric from Modrinth")));
        for (var kind : dev.vanta.launcher.core.install.OfficialProfileService.Kind.values()) {
            final var file = new dev.vanta.launcher.core.install.OfficialProfileService.PlannedFile(kind, "/x", "Sodium 0.8.14");
            assertFalse(vm.officialPlanLabel(file).contains("{"), kind + " label is fully formatted");
            assertFalse(vm.officialPlanLabel(new dev.vanta.launcher.core.install.OfficialProfileService.PlannedFile(kind, "/x")).contains("{"));
        }
        assertFalse(backend.calls.contains("installOfficialProfile"), "preparing only reads the plan");

        final List<HomeViewModel.State> seen = new ArrayList<>();
        vm.stateProperty().addListener((obs, old, now) -> seen.add(now));
        vm.installOfficialProfile();
        assertTrue(seen.contains(HomeViewModel.State.INSTALLING), "no account and no PLAY needed: " + seen);
        assertEquals(HomeViewModel.State.NOT_READY, vm.state(), "back to idle afterwards");
        assertFalse(vm.officialBusyProperty().get());
        assertEquals("Open the Minecraft Launcher, choose the profile 'VANTA 1.21.11' and press Play.", vm.officialDoneTextProperty().get());
        assertEquals(ToastModel.Kind.SUCCESS, ctx.toasts.toasts().get(0).kind());
        assertEquals("Profile 'VANTA 1.21.11' is ready", ctx.toasts.toasts().get(0).title());
        assertTrue(ctx.launcherLog.snapshot().stream().anyMatch(l -> l.text().contains("Added the Minecraft Launcher profile 'VANTA 1.21.11'")));
        assertFalse(backend.calls.contains("install"), "the regular install is not used");
        assertFalse(backend.calls.contains("launch"));
        assertEquals("1.0.0", ctx.session.installedClient().orElseThrow().version(),
            "the client jar now in mods/ (no instance.json) counts as installed for the card and the update check");
        assertTrue(ctx.session.instance().isEmpty());
    }

    @Test
    void installFromTheClientCardIsTheRegularInstallWithoutLaunching() {
        signedInWithJava();
        final HomeViewModel vm = ctx.home();
        assertTrue(ctx.session.installedClient().isEmpty(), "fresh launcher: nothing installed");
        final List<HomeViewModel.State> seen = new ArrayList<>();
        vm.stateProperty().addListener((obs, old, now) -> seen.add(now));
        vm.install();
        assertTrue(seen.contains(HomeViewModel.State.INSTALLING), String.valueOf(seen));
        assertEquals(HomeViewModel.State.READY, vm.state());
        assertTrue(backend.calls.contains("install"));
        assertFalse(backend.calls.contains("launch"), "installing from the card does not start the game");
        assertEquals("1.0.0", ctx.session.installedClient().orElseThrow().version(), "instance.json and the jar are now present");
        assertEquals(ctx.messages.get("home.toast.installed.title"), ctx.toasts.toasts().get(0).title());
    }

    @Test
    void officialLauncherNotSetUpIsExplained() {
        backend.officialPlanFailure = new dev.vanta.launcher.core.install.OfficialLauncherNotFoundException(tmp.resolve(".minecraft"));
        ctx.session.refreshAll();
        final HomeViewModel vm = ctx.home();
        final List<Object> plans = new ArrayList<>();
        vm.prepareOfficialProfile(plans::add);
        assertTrue(plans.isEmpty());
        assertEquals(ToastModel.Kind.ERROR, ctx.toasts.toasts().get(0).kind());
        assertEquals(ctx.messages.get("official.toast.failed.title"), ctx.toasts.toasts().get(0).title());
        assertTrue(ctx.toasts.toasts().get(0).message().contains("Start the Minecraft Launcher once"), ctx.toasts.toasts().get(0).message());
    }

    @Test
    void officialSetupFailureShowsTheError() {
        backend.officialInstallFailure = new dev.vanta.launcher.core.install.InstallException(
            dev.vanta.launcher.core.install.InstallStep.FABRIC_PROFILE, "Fetching Fabric Loader profile failed", new java.io.IOException("Tunnel failed, got: 403"));
        signedInWithJava();
        final HomeViewModel vm = ctx.home();
        vm.installOfficialProfile();
        assertEquals(HomeViewModel.State.ERROR, vm.state());
        assertTrue(vm.errorTextProperty().get().contains("A proxy refused the connection (Tunnel failed, got: 403)"), vm.errorTextProperty().get());
        assertTrue(vm.officialDoneTextProperty().get().isEmpty());
        assertFalse(vm.officialBusyProperty().get());
    }

    @Test
    void playViaMinecraftLauncherSetsUpThenOpensTheOfficialLauncher() {
        backend.signInConfigured = false;
        ctx.session.refreshAll();
        final HomeViewModel vm = ctx.home();
        assertTrue(vm.officialPlayModeProperty().get());
        assertEquals(ctx.messages.get("home.title.viaOfficial"), vm.titleTextProperty().get());
        assertEquals(ctx.messages.get("home.status.viaOfficial"), vm.statusTextProperty().get(), "not 'Not ready'");

        backend.runningLaunchers.add("MinecraftLauncher.exe (process 1234)");
        final List<List<String>> checks = new ArrayList<>();
        vm.checkOfficialLauncher(checks::add);
        assertEquals(List.of(List.of("MinecraftLauncher.exe (process 1234)")), checks, "the view asks the player to close it");
        assertTrue(ctx.launcherLog.snapshot().stream().anyMatch(l -> l.text().contains("The Minecraft Launcher is running")));
        backend.runningLaunchers.clear();
        vm.checkOfficialLauncher(checks::add);
        assertEquals(List.of(), checks.get(1));

        vm.installOfficialProfile(true);
        assertEquals(List.of("installOfficialProfile", "openOfficialLauncher"), backend.calls.stream()
            .filter(c -> c.equals("installOfficialProfile") || c.equals("openOfficialLauncher")).toList());
        assertEquals(ctx.messages.format("official.opened.message", "VANTA 1.21.11"), vm.officialDoneTextProperty().get());
        assertEquals(ctx.messages.get("official.opened.title"), ctx.toasts.toasts().get(ctx.toasts.toasts().size() - 1).title());
        assertFalse(backend.calls.contains("install"), "no sign-in, no regular install");
    }

    @Test
    void openingTheOfficialLauncherReportsWhatHappened() {
        ctx.session.refreshAll();
        final HomeViewModel vm = ctx.home();
        backend.openResult = new dev.vanta.launcher.core.install.OfficialLauncher.OpenResult(
            dev.vanta.launcher.core.install.OfficialLauncher.Outcome.NOT_FOUND, "", "No Minecraft Launcher was found");
        vm.openOfficialLauncher();
        assertEquals(ToastModel.Kind.ERROR, ctx.toasts.toasts().get(0).kind());
        assertEquals(ctx.messages.get("official.open.notFound"), ctx.toasts.toasts().get(0).message());
        backend.openResult = new dev.vanta.launcher.core.install.OfficialLauncher.OpenResult(
            dev.vanta.launcher.core.install.OfficialLauncher.Outcome.FAILED, "x", "Could not start x: Access is denied");
        vm.openOfficialLauncher();
        assertTrue(ctx.toasts.toasts().get(1).message().contains("Access is denied"));
        assertTrue(vm.officialDoneTextProperty().get().isEmpty());
    }

    @Test
    void theGameIsStartedAgainWhenItAsksForARestart() {
        signedInWithJava();
        final HomeViewModel vm = ctx.home();
        final List<Integer> exits = new ArrayList<>();
        final List<Long> started = new ArrayList<>();
        vm.onGameExited(exits::add);
        vm.onGameStarted(g -> started.add(g.pid()));
        vm.play();
        final FakeBackend.FakeGame first = backend.game;
        backend.restartRequests = 1;
        first.exit(0);
        assertEquals(HomeViewModel.State.RUNNING, vm.state(), "restarted, still running");
        assertTrue(backend.game != first, "a new process");
        assertEquals(2, started.size());
        assertTrue(exits.isEmpty(), "the window is not told about an exit in between");
        assertEquals(2, backend.calls.stream().filter("launch"::equals).count());
        assertEquals(1, backend.calls.stream().filter("install"::equals).count(), "a restart does not install again");
        assertEquals(ctx.messages.get("home.toast.gameRestarted.title"), ctx.toasts.toasts().get(ctx.toasts.toasts().size() - 1).title());

        backend.game.exit(0);
        assertEquals(List.of(0), exits);
        assertEquals(HomeViewModel.State.READY, vm.state());
    }

    @Test
    void restartsInARowAreLimited() {
        signedInWithJava();
        final HomeViewModel vm = ctx.home();
        vm.play();
        backend.restartRequests = 100;
        for (int i = 0; i <= HomeViewModel.MAX_RESTARTS; i++) {
            backend.game.exit(0);
        }
        assertEquals(HomeViewModel.MAX_RESTARTS + 1, backend.calls.stream().filter("launch"::equals).count());
        assertEquals(HomeViewModel.State.READY, vm.state(), "a client asking on every start does not loop forever");
    }

    /** At the limit the marker is still consumed (deleted), so it cannot restart the game after a later PLAY. */
    @Test
    void theRestartMarkerIsConsumedEvenWhenTheLimitIsReached() {
        signedInWithJava();
        final HomeViewModel vm = ctx.home();
        vm.play();
        backend.restartRequests = HomeViewModel.MAX_RESTARTS + 2;
        for (int i = 0; i <= HomeViewModel.MAX_RESTARTS; i++) {
            backend.game.exit(0);
        }
        assertEquals(HomeViewModel.State.READY, vm.state());
        assertEquals(HomeViewModel.MAX_RESTARTS + 1, backend.calls.stream().filter("launch"::equals).count());
        assertEquals(HomeViewModel.MAX_RESTARTS + 1, backend.calls.stream().filter("consumeRestartRequest"::equals).count(),
            "the exit at the limit consumes the marker instead of leaving it for the next PLAY");
        assertEquals(1, backend.restartRequests, "only the request that was never written remains");
    }

    // ---------------------------------------------------------------- start check

    private static final String SMART = "smart-fps-booster-1.0.0+mc1.21.4.jar";

    private StartupGuard.Report switchedOffByTheCheck() {
        return new StartupGuard.Report(3, List.of(backend.switchedOff(SMART, "Smart FPS Booster", "1.0.0+mc1.21.4", StartupGuard.Source.CHECK)));
    }

    private StartupGuard.Report switchedOffByACrashReport() {
        return new StartupGuard.Report(0, List.of(backend.switchedOff(SMART, "Smart FPS Booster", "1.0.0+mc1.21.4",
            StartupGuard.Source.CRASH_REPORT)));
    }

    private List<String> order(final String... names) {
        final List<String> wanted = List.of(names);
        return backend.calls.stream().filter(wanted::contains).toList();
    }

    private ToastModel.Toast lastToast() {
        return ctx.toasts.toasts().get(ctx.toasts.toasts().size() - 1);
    }

    @Test
    void playRunsTheStartCheckAfterTheInstallAndBeforeTheGameStarts() {
        signedInWithJava();
        backend.startupReport = switchedOffByTheCheck();
        final HomeViewModel vm = ctx.home();

        vm.play();

        assertEquals(List.of("install", "startupCheck", "refreshIfExpired", "launch"), order("install", "startupCheck", "refreshIfExpired", "launch"));
        assertEquals(HomeViewModel.State.RUNNING, vm.state());
        final ToastModel.Toast toast = ctx.toasts.toasts().stream().filter(t -> t.title().equals("Switched off Smart FPS Booster")).findFirst()
            .orElseThrow(() -> new AssertionError(ctx.toasts.toasts().toString()));
        assertEquals(ToastModel.Kind.WARNING, toast.kind());
        assertEquals("Smart FPS Booster 1.0.0+mc1.21.4 was switched off because it was built for an older Minecraft: it creates key bindings"
            + " the way Minecraft did before 1.21.9, so Minecraft 1.21.11 would stop while starting. You can switch it on again on the Mods page.",
            toast.message());
        assertTrue(ctx.launcherLog.snapshot().stream().anyMatch(l -> l.level() == LogLevel.WARN
            && l.text().startsWith("Startup check: switched off " + SMART + " (Smart FPS Booster 1.0.0+mc1.21.4): built for an older Minecraft")),
            ctx.launcherLog.snapshot().toString());
    }

    @Test
    void aStartCheckThatCannotRunNeverStopsPlay() {
        signedInWithJava();
        backend.startupCheckFailure = new java.io.IOException("mods/ is not readable");
        final HomeViewModel vm = ctx.home();
        vm.play();
        assertEquals(HomeViewModel.State.RUNNING, vm.state());
        assertTrue(ctx.launcherLog.snapshot().stream().anyMatch(l -> l.text().contains("Startup check could not run")));
    }

    @Test
    void playViaMinecraftLauncherRunsTheStartCheckAfterTheFilesAndBeforeOpeningIt() {
        backend.signInConfigured = false;
        ctx.session.refreshAll();
        backend.startupReport = switchedOffByTheCheck();
        final HomeViewModel vm = ctx.home();
        final List<Runnable> ticks = new ArrayList<>();
        vm.setWatchScheduler((period, task) -> {
            assertEquals(HomeViewModel.WATCH_PERIOD, period);
            ticks.add(task);
            return () -> ticks.clear();
        });

        vm.installOfficialProfile(true);

        assertEquals(List.of("installOfficialProfile", "startupCheck", "openOfficialLauncher"),
            order("installOfficialProfile", "startupCheck", "openOfficialLauncher"), "checked once, after the files, before opening");
        assertTrue(ctx.toasts.toasts().stream().anyMatch(t -> t.title().equals("Switched off Smart FPS Booster")), ctx.toasts.toasts().toString());
        assertTrue(vm.watchingCrashReports(), "crash-reports/ is watched while the Minecraft Launcher plays");
        assertEquals(1, ticks.size());

        // The Minecraft Launcher started the game and it crashed: the report names the mod.
        final List<ToastModel.Toast> before = List.copyOf(ctx.toasts.toasts());
        ticks.get(0).run();
        assertEquals(before, ctx.toasts.toasts(), "no new report, nothing to say");
        backend.crashReports.add(switchedOffByACrashReport());
        ticks.get(0).run();
        assertEquals("Switched off Smart FPS Booster", lastToast().title());
        assertEquals("Smart FPS Booster 1.0.0+mc1.21.4 was switched off because Minecraft stopped while starting because of it (crash report"
            + " crash-2026-10-04_12.00.00-client.txt). You can switch it on again on the Mods page. Press Play in the Minecraft Launcher again.",
            lastToast().message());
        assertEquals(2, backend.calls.stream().filter("crashReportCheck"::equals).count());
    }

    @Test
    void useWithMinecraftLauncherAndOpenMinecraftLauncherRunTheStartCheckToo() {
        ctx.session.refreshAll();
        final HomeViewModel vm = ctx.home();
        vm.setWatchScheduler((period, task) -> () -> { });
        backend.startupReport = switchedOffByTheCheck();
        vm.installOfficialProfile(false);
        assertEquals(List.of("installOfficialProfile", "startupCheck"), order("installOfficialProfile", "startupCheck", "openOfficialLauncher"));
        assertTrue(ctx.toasts.toasts().stream().anyMatch(t -> t.title().equals("Switched off Smart FPS Booster")));

        backend.calls.clear();
        vm.openOfficialLauncher();
        assertEquals(List.of("startupCheck", "openOfficialLauncher"), order("startupCheck", "openOfficialLauncher"));
        assertTrue(vm.watchingCrashReports());
        vm.stopWatchingCrashReports();
        assertFalse(vm.watchingCrashReports());
    }

    @Test
    void theCrashReportWatchStopsAfterThirtyMinutes() {
        ctx.session.refreshAll();
        final HomeViewModel vm = ctx.home();
        final List<Runnable> ticks = new ArrayList<>();
        final boolean[] stopped = {false};
        vm.setWatchScheduler((period, task) -> {
            ticks.add(task);
            return () -> stopped[0] = true;
        });
        vm.watchCrashReports();
        final long perHalfHour = HomeViewModel.WATCH_LIMIT.toMillis() / HomeViewModel.WATCH_PERIOD.toMillis();
        for (long i = 0; i < perHalfHour; i++) {
            ticks.get(0).run();
        }
        assertTrue(vm.watchingCrashReports());
        assertFalse(stopped[0]);
        ticks.get(0).run();
        assertFalse(vm.watchingCrashReports());
        assertTrue(stopped[0]);
        assertEquals(perHalfHour, backend.calls.stream().filter("crashReportCheck"::equals).count());
        ticks.get(0).run();
        assertEquals(perHalfHour, backend.calls.stream().filter("crashReportCheck"::equals).count(), "a stopped watch does nothing");
    }

    @Test
    void afterTheGameExitsTheNewestCrashReportIsChecked() {
        signedInWithJava();
        final HomeViewModel vm = ctx.home();
        vm.play();
        backend.crashReports.add(switchedOffByACrashReport());
        backend.game.exit(255);
        assertTrue(backend.calls.contains("crashReportCheck"));
        assertTrue(ctx.toasts.toasts().stream().anyMatch(t -> t.title().equals("Switched off Smart FPS Booster")
            && !t.message().contains("Minecraft Launcher")), ctx.toasts.toasts().toString());
        assertEquals(HomeViewModel.State.READY, vm.state());
    }

    @Test
    void theWindowRunsTheStartCheckOnceWhenItOpensAndAProtectedModIsOnlyReported() {
        ctx.session.refreshAll();
        final HomeViewModel vm = ctx.home();
        backend.startupReport = new StartupGuard.Report(0, List.of(new StartupGuard.Action(backend.paths().modsDir().resolve("vanta-client-1.2.0.jar"),
            "vanta", "VANTA", "1.2.0", "Minecraft stopped while starting because of it (crash report crash-1.txt); VANTA does not switch off vanta",
            StartupGuard.Source.CRASH_REPORT, false, "crash-1.txt")));
        vm.runStartupCheck();
        assertEquals(1, backend.calls.stream().filter("startupCheck"::equals).count());
        assertEquals(ctx.messages.get("startup.toast.notSwitchedOff.title"), lastToast().title());
        assertEquals("VANTA did not switch off VANTA 1.2.0: Minecraft stopped while starting because of it (crash report crash-1.txt)."
            + " The Logs page has the details.", lastToast().message());
        assertTrue(ctx.launcherLog.snapshot().stream().anyMatch(l -> l.text().startsWith("Startup check: did not switch off vanta-client-1.2.0.jar")));
    }

    private void signedInWithJava() {
        backend.accounts.add(FakeBackend.microsoftAccount("Nova"));
        backend.javaInstalls.add(backend.temurin21());
        ctx.session.refreshAll();
    }
}
