package dev.vanta.launcher.ui.model;

import dev.vanta.launcher.LauncherVersion;
import dev.vanta.launcher.core.install.VantaClientService;
import dev.vanta.launcher.core.model.ReleaseManifest;
import dev.vanta.launcher.ui.testutil.FakeBackend;
import dev.vanta.launcher.ui.testutil.TestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionsViewModelTest {

    @TempDir
    Path tmp;
    private TestContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new TestContext(tmp);
    }

    @Test
    void rowsReflectNothingInstalled() {
        final VersionsViewModel vm = new VersionsViewModel(ctx.session, ctx.backend, ctx.executors, ctx.messages, ctx.formats, ctx.toasts);
        assertEquals(5, vm.rows().size());
        assertEquals(VersionsViewModel.RowStatus.NOT_INSTALLED, vm.rows().get(0).status());
        assertEquals(LauncherVersion.MINECRAFT, vm.rows().get(0).version(), "pinned version is shown even before the install");
        assertEquals(VersionsViewModel.RowStatus.MISSING, vm.rows().get(4).status());
        assertFalse(vm.hasInstance());
        vm.refresh();
        assertEquals("", vm.sha256Property().get());
        assertTrue(vm.kept().isEmpty());
    }

    @Test
    void rowsShaAndKeptVersionsAfterInstall() {
        ctx.backend.instance = FakeBackend.installedInstance("1.0.0");
        ctx.backend.activeJar = tmp.resolve("mods/vanta-client-1.0.0.jar");
        ctx.backend.javaInstalls.add(ctx.backend.temurin21());
        final var manifest = FakeBackend.update("client", "0.0.0", "0.9.0", true).manifest();
        ctx.backend.kept.add(new VantaClientService.KeptVersion("1.0.0", manifest, tmp.resolve("v/1.0.0.jar"), Instant.parse("2026-10-03T18:42:00Z")));
        ctx.backend.kept.add(new VantaClientService.KeptVersion("0.9.0", manifest, tmp.resolve("v/0.9.0.jar"), Instant.parse("2026-09-01T10:00:00Z")));
        ctx.backend.kept.add(new VantaClientService.KeptVersion("0.8.0", manifest, null, Instant.parse("2026-08-01T10:00:00Z")));
        ctx.session.refreshAll();
        final VersionsViewModel vm = new VersionsViewModel(ctx.session, ctx.backend, ctx.executors, ctx.messages, ctx.formats, ctx.toasts);
        vm.refresh();
        assertEquals(VersionsViewModel.RowStatus.INSTALLED, vm.rows().get(0).status());
        assertEquals("1.0.0", vm.rows().get(3).version());
        assertEquals(VersionsViewModel.RowStatus.DETECTED, vm.rows().get(4).status());
        assertEquals("21.0.4", vm.rows().get(4).version());
        assertEquals(ctx.backend.sha256, vm.sha256Property().get());
        assertEquals("vanta-client-1.0.0.jar", vm.jarNameProperty().get());
        assertEquals(3, vm.kept().size());
        assertTrue(vm.kept().get(0).active());
        assertTrue(vm.kept().get(1).available());
        assertFalse(vm.kept().get(2).available());

        vm.rollback("0.9.0");
        assertTrue(ctx.backend.calls.contains("rollback:0.9.0"));
        assertEquals("0.9.0", ctx.session.instance().orElseThrow().vantaClientVersion());
        assertEquals(ctx.messages.format("versions.rollback.toast.title", "0.9.0"), ctx.toasts.toasts().get(0).title());
        assertTrue(vm.kept().get(1).active());

        vm.rollback("0.7.0");
        assertEquals(ToastModel.Kind.ERROR, ctx.toasts.toasts().get(1).kind());
    }

    @Test
    void localCopiesAreMarkedAsSuch() {
        // A jar that was in mods/ (for example put there by hand) and was copied before an update replaced it: its
        // manifest was written from the jar itself, without a download URL. It is not a downloaded, verified release.
        ctx.backend.instance = FakeBackend.installedInstance("1.1.0");
        ctx.backend.activeJar = tmp.resolve("mods/vanta-client-1.1.0.jar");
        final var release = FakeBackend.update("client", "1.0.0", "1.1.0", true).manifest();
        final var local = VantaClientService.localCopyManifest("1.0.0",
            new ReleaseManifest.ReleaseFile("vanta-client-1.0.0.jar", "", 640, ctx.backend.sha256));
        ctx.backend.kept.add(new VantaClientService.KeptVersion("1.1.0", release, tmp.resolve("v/1.1.0.jar"), Instant.parse("2026-10-04T10:00:00Z")));
        ctx.backend.kept.add(new VantaClientService.KeptVersion("1.0.0", local, tmp.resolve("v/1.0.0.jar"), Instant.parse("2026-10-04T10:00:00Z")));
        ctx.session.refreshAll();
        final VersionsViewModel vm = new VersionsViewModel(ctx.session, ctx.backend, ctx.executors, ctx.messages, ctx.formats, ctx.toasts);
        vm.refresh();
        assertEquals(2, vm.kept().size());
        assertFalse(vm.kept().get(0).localCopy(), "a downloaded release");
        assertTrue(vm.kept().get(1).localCopy());
        assertTrue(vm.kept().get(1).available(), "it can still be rolled back to");
        assertFalse(ctx.messages.get("versions.previous.caption").contains("verified releases"),
            "the caption does not call every kept version a verified release");
    }

    @Test
    void developmentJarIsLabelled() {
        ctx.backend.instance = FakeBackend.installedInstance(VantaClientService.DEV_VERSION);
        ctx.session.refreshInstance();
        final VersionsViewModel vm = new VersionsViewModel(ctx.session, ctx.backend, ctx.executors, ctx.messages, ctx.formats, ctx.toasts);
        assertEquals(ctx.messages.get("client.card.dev"), vm.rows().get(3).version());
    }

    @Test
    void emptyStatePointsToTheOfficialLauncherWhenPlayCannotWork() {
        final VersionsViewModel vm = new VersionsViewModel(ctx.session, ctx.backend, ctx.executors, ctx.messages, ctx.formats, ctx.toasts);
        ctx.session.refreshAll();
        assertEquals(ctx.messages.get("versions.empty.title"), vm.emptyTitleProperty().get());
        assertEquals(ctx.messages.format("versions.empty.text", LauncherVersion.MINECRAFT, LauncherVersion.FABRIC_LOADER),
            vm.emptyTextProperty().get(), "sign-in is configured: PLAY installs");
        assertTrue(vm.emptyTextProperty().get().contains("Press PLAY"));

        // 1.0.1 said "Press PLAY" although PLAY can never be enabled without Microsoft sign-in.
        ctx.backend.signInConfigured = false;
        ctx.session.refreshAll();
        final String text = vm.emptyTextProperty().get();
        assertFalse(text.contains("Press PLAY"), text);
        assertTrue(text.contains("Use with Minecraft Launcher"), text);
        assertEquals(ctx.messages.format("versions.empty.text.official", LauncherVersion.MINECRAFT, LauncherVersion.FABRIC_LOADER), text);

        // A stored account loads without a client id and enables PLAY (as on the Home client card): say "Press PLAY".
        // The text follows the account without another refresh.
        ctx.session.setAccount(FakeBackend.microsoftAccount("Nova"));
        assertEquals(ctx.messages.format("versions.empty.text", LauncherVersion.MINECRAFT, LauncherVersion.FABRIC_LOADER),
            vm.emptyTextProperty().get(), "an account is present: PLAY installs");
        ctx.session.setAccount(null);
        assertEquals(ctx.messages.format("versions.empty.text.official", LauncherVersion.MINECRAFT, LauncherVersion.FABRIC_LOADER),
            vm.emptyTextProperty().get(), "signed out again: PLAY cannot work");

        // After "Use with Minecraft Launcher": the client jar is in mods/ but there is no instance.json.
        ctx.backend.activeJar = ctx.backend.paths().modsDir().resolve("vanta-client-1.0.1.jar");
        ctx.session.refreshInstance();
        assertEquals(ctx.messages.get("versions.empty.officialDone.title"), vm.emptyTitleProperty().get());
        assertTrue(vm.emptyTextProperty().get().contains("VANTA Client 1.0.1"), vm.emptyTextProperty().get());
        assertTrue(vm.emptyTextProperty().get().contains("VANTA " + LauncherVersion.MINECRAFT), vm.emptyTextProperty().get());
        assertFalse(vm.emptyTextProperty().get().contains("{"), vm.emptyTextProperty().get());
    }
}
