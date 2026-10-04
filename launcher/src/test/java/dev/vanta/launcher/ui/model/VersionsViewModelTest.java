package dev.vanta.launcher.ui.model;

import dev.vanta.launcher.LauncherVersion;
import dev.vanta.launcher.core.install.VantaClientService;
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
    void developmentJarIsLabelled() {
        ctx.backend.instance = FakeBackend.installedInstance(VantaClientService.DEV_VERSION);
        ctx.session.refreshInstance();
        final VersionsViewModel vm = new VersionsViewModel(ctx.session, ctx.backend, ctx.executors, ctx.messages, ctx.formats, ctx.toasts);
        assertEquals(ctx.messages.get("client.card.dev"), vm.rows().get(3).version());
    }
}
