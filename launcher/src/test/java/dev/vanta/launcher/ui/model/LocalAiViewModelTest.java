package dev.vanta.launcher.ui.model;

import dev.vanta.launcher.core.ai.LocalAiState;
import dev.vanta.launcher.ui.testutil.TestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalAiViewModelTest {

    @TempDir
    Path tmp;
    private TestContext ctx;
    private LocalAiViewModel vm;

    @BeforeEach
    void setUp() {
        ctx = new TestContext(tmp);
        ctx.session.refreshAll();
        vm = ctx.localAi;
    }

    @Test
    void statusTextsFollowTheReport() {
        assertEquals(ctx.messages.get("localai.status.checking"), vm.statusTextProperty().get(), "before the first refresh");
        vm.refresh();
        assertEquals(LocalAiViewModel.Activity.IDLE, vm.activityProperty().get());
        assertEquals("Local AI: not installed", vm.statusTextProperty().get());
        assertEquals("Available: llama.cpp b11429, Qwen3-1.7B Q8_0", vm.detailTextProperty().get());
        assertTrue(vm.sizeTextProperty().get().startsWith("Download: 1.9 GB"), vm.sizeTextProperty().get());
        assertTrue(vm.installableProperty().get());
        assertFalse(vm.installedProperty().get());
        assertFalse(vm.removableProperty().get());
        assertEquals(ctx.backend.paths().localAiDir(), vm.folder());

        ctx.backend.localAiState = LocalAiState.INSTALLED;
        vm.refresh();
        assertEquals("Local AI: ready", vm.statusTextProperty().get());
        assertTrue(vm.detailTextProperty().get().startsWith("llama.cpp b11429, Qwen3-1.7B Q8_0 · installed "), vm.detailTextProperty().get());
        assertTrue(vm.sizeTextProperty().get().endsWith(" on disk"), vm.sizeTextProperty().get());
        assertTrue(vm.installedProperty().get());
        assertTrue(vm.removableProperty().get());

        ctx.backend.localAiOutdated = true;
        vm.refresh();
        assertEquals("Local AI: ready, update available", vm.statusTextProperty().get());

        ctx.backend.localAiState = LocalAiState.PARTIAL;
        ctx.backend.localAiOutdated = false;
        vm.refresh();
        assertEquals("Local AI: incomplete", vm.statusTextProperty().get());
        assertTrue(vm.removableProperty().get(), "partial files can be removed");
        assertEquals(1, vm.problems().size());

        ctx.backend.localAiState = LocalAiState.UNSUPPORTED_PLATFORM;
        vm.refresh();
        assertEquals("Local AI: not available for linux/x86", vm.statusTextProperty().get());
        assertFalse(vm.installableProperty().get());
        assertTrue(vm.offer().isEmpty(), "nothing to offer on an unsupported system");

        ctx.backend.localAiState = LocalAiState.UNAVAILABLE;
        vm.refresh();
        assertEquals("Local AI: not available in this build", vm.statusTextProperty().get());
        assertEquals(ctx.messages.get("localai.detail.none"), vm.detailTextProperty().get());
    }

    @Test
    void installReportsProgressRecordsConsentAndToasts() {
        vm.refresh();
        final List<String> progress = new ArrayList<>();
        vm.progressTextProperty().addListener((obs, old, now) -> progress.add(now));
        final AtomicReference<String> busyStatus = new AtomicReference<>();
        ctx.backend.localAiInstallHook = () -> busyStatus.set(vm.statusTextProperty().get());

        vm.install();

        assertTrue(busyStatus.get().startsWith("Local AI: installing…"), busyStatus.get());
        assertEquals(LocalAiViewModel.Activity.IDLE, vm.activityProperty().get());
        assertTrue(vm.installedProperty().get());
        assertEquals("Local AI: ready", vm.statusTextProperty().get());
        assertTrue(progress.stream().anyMatch(p -> p.contains("Downloading Local AI runtime")), progress.toString());
        assertTrue(ctx.backend.calls.contains("installLocalAi"));
        assertTrue(ctx.backend.settings.localAiConsent(), "an explicit install counts as consent");
        assertEquals(ToastModel.Kind.SUCCESS, ctx.toasts.toasts().get(0).kind());
        assertEquals(ctx.messages.get("localai.toast.installed.title"), ctx.toasts.toasts().get(0).title());
        assertTrue(ctx.launcherLog.snapshot().stream().anyMatch(l -> l.text().startsWith("Downloaded Local AI runtime")));
    }

    @Test
    void failuresAreToastsAndTheStateIsRefreshed() {
        vm.refresh();
        ctx.backend.localAiFailure = new IOException("Size mismatch for the model");
        vm.install();
        assertEquals(LocalAiViewModel.Activity.IDLE, vm.activityProperty().get());
        assertFalse(vm.installedProperty().get());
        assertEquals(ToastModel.Kind.ERROR, ctx.toasts.toasts().get(0).kind());
        assertEquals(ctx.messages.get("localai.toast.failed.title"), ctx.toasts.toasts().get(0).title());
        assertFalse(ctx.backend.settings.localAiConsent(), "a failed install records no consent");
    }

    @Test
    void verifyToastsTheOutcome() {
        ctx.backend.localAiState = LocalAiState.INSTALLED;
        vm.refresh();
        vm.verify();
        assertTrue(ctx.backend.calls.contains("verifyLocalAi"));
        assertEquals(ctx.messages.get("localai.toast.verified.title"), ctx.toasts.toasts().get(0).title());
        ctx.toasts.clear();
        ctx.backend.localAiState = LocalAiState.PARTIAL;
        vm.verify();
        assertEquals(ToastModel.Kind.WARNING, ctx.toasts.toasts().get(0).kind());
        assertEquals("Local AI: incomplete", vm.statusTextProperty().get());
    }

    @Test
    void removeDeletesAndSwitchesTheAutomaticInstallOff() {
        ctx.backend.localAiState = LocalAiState.INSTALLED;
        ctx.backend.settings = ctx.backend.settings.withLocalAiAccepted(true);
        vm.refresh();
        assertTrue(ctx.backend.settings.localAiAutoInstall());

        vm.remove();

        assertTrue(ctx.backend.calls.contains("removeLocalAi"));
        assertEquals(LocalAiState.NOT_INSTALLED, ctx.backend.localAiState);
        assertEquals("Local AI: not installed", vm.statusTextProperty().get());
        assertFalse(ctx.backend.settings.localAiAutoInstall(), "the next start must not download it again");
        assertTrue(ctx.backend.settings.localAiConsent(), "the consent given earlier stays recorded");
        assertEquals(ctx.messages.get("localai.toast.removed.title"), ctx.toasts.toasts().get(0).title());
        assertTrue(ctx.toasts.toasts().get(0).message().contains("1.9 GB"), ctx.toasts.toasts().get(0).message());
    }

    @Test
    void theOfferDescribesWhatIsDownloadedFromWhereAndHowBig() {
        vm.refresh();
        final Optional<LocalAiViewModel.Offer> offer = vm.offer();
        assertTrue(offer.isPresent());
        final LocalAiViewModel.Offer o = offer.get();
        assertEquals("Runtime: llama.cpp b11429 (llama-b11429-bin-ubuntu-x64.tar.gz, 45.1 MB, licence MIT) from github.com", o.runtimeLine());
        assertEquals("Model: Qwen3-1.7B Q8_0 (Qwen3-1.7B-Q8_0.gguf, 1.8 GB, licence Apache-2.0) from huggingface.co", o.modelLine());
        assertEquals("1.9 GB", o.totalSize());
        assertEquals(ctx.backend.paths().localAiDir().toString(), o.folder());
        assertTrue(o.requirements().contains("disk space"), o.requirements());
    }
}
