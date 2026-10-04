package dev.vanta.launcher.ui.model;

import dev.vanta.launcher.LauncherVersion;
import dev.vanta.launcher.core.auth.Account;
import dev.vanta.launcher.core.install.InstallException;
import dev.vanta.launcher.core.install.InstallStep;
import dev.vanta.launcher.core.install.NotPublishedException;
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
        assertTrue(steps.stream().anyMatch(s -> s.contains("Step 4 of 10 \u00B7 Downloading libraries")), steps.toString());
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
    void verifyWorksWithoutAccount() {
        backend.javaInstalls.add(backend.temurin21());
        ctx.session.refreshAll();
        final HomeViewModel vm = ctx.home();
        assertEquals(HomeViewModel.State.NOT_READY, vm.state());
        vm.verify();
        assertEquals(HomeViewModel.State.NOT_READY, vm.state(), "verifying does not need an account and returns to the idle state");
        assertTrue(backend.calls.contains("install"));
        assertEquals(ctx.messages.get("home.toast.installed.title"), ctx.toasts.toasts().get(0).title());
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

    private void signedInWithJava() {
        backend.accounts.add(FakeBackend.microsoftAccount("Nova"));
        backend.javaInstalls.add(backend.temurin21());
        ctx.session.refreshAll();
    }
}
