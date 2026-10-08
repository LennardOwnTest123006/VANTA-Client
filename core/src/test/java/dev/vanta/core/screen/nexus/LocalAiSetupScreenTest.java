package dev.vanta.core.screen.nexus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.ai.FakeArchives;
import dev.vanta.core.ai.FakeProcess;
import dev.vanta.core.ai.InstallStep;
import dev.vanta.core.ai.LocalAiInstaller;
import dev.vanta.core.ai.LocalAiManifest;
import dev.vanta.core.ai.LocalAiService;
import dev.vanta.core.ai.LocalAiStatus;
import dev.vanta.core.ai.LocalFileServer;
import dev.vanta.core.ai.ManifestFixtures;
import dev.vanta.core.ai.Platform;
import dev.vanta.core.bridge.FakeClipboardBridge;
import dev.vanta.core.bridge.FakeGameBridge;
import dev.vanta.core.bridge.FakeKeybindBridge;
import dev.vanta.core.bridge.FakeOptionsBridge;
import dev.vanta.core.bridge.FakeResourcePackBridge;
import dev.vanta.core.bridge.FakeScreenshotBridge;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.screen.ReachabilityWalker;
import dev.vanta.core.screen.ScreenBootstrap;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.common.ScreenTestSupport;
import dev.vanta.core.screen.common.ThemeFactory;
import dev.vanta.core.ui.ManualClock;
import dev.vanta.core.ui.ScrollIntoView;
import dev.vanta.core.ui.TestCanvas;
import dev.vanta.core.ui.TestHost;
import dev.vanta.core.ui.UiEnvironment;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.UiScreen;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Local AI setup screen against the real {@link LocalAiService} with a resolved manifest served from a loopback
 * server, fake archives and a fake llama-server process: nothing downloads before Install, the steps fill in, the
 * runtime is started afterwards and "Vanta Nexus ready" appears; a checksum failure shows the reason and Retry runs
 * again; the Nexus install card opens the setup screen.
 */
class LocalAiSetupScreenTest {
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Executor DIRECT = Runnable::run;
    private static final Platform LINUX = Platform.parse("linux-x64").orElseThrow();
    private static final String CANNED = "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\","
            + "\"content\":\"{\\\"message\\\":\\\"done\\\",\\\"actions\\\":[]}\"}}]}";

    @TempDir
    Path dir;
    private LocalFileServer server;
    private final MutableClock clock = MutableClock.standard();
    private final ManualClock uiClock = new ManualClock(1_000L);
    private final TestHost host = new TestHost();
    private final FakeGameBridge game = new FakeGameBridge();
    private final FakeProcess.Factory processes = new FakeProcess.Factory();
    private final byte[] model = FakeArchives.randomBytes(200_000, 8);
    private VantaServices services;

    @BeforeEach
    void setUp() throws IOException {
        server = new LocalFileServer();
        server.json(dev.vanta.core.ai.LocalAiClient.CHAT_PATH, body -> CANNED);
    }

    @AfterEach
    void tearDown() {
        if (services != null) {
            services.shutdown();
        }
        server.close();
    }

    private VantaServices services(LocalAiManifest manifest) {
        LocalAiService.Dependencies deps = new LocalAiService.Dependencies(Optional.of(LINUX), Optional.of(manifest), "",
                () -> HTTP, processes, server::port, duration -> clock.advance(duration.toMillis()), DIRECT, 8, false,
                LocalAiInstaller.Config.defaults("test"));
        services = VantaServices.create(VantaPaths.inGameDirectory(dir), game, new FakeOptionsBridge(),
                new FakeKeybindBridge(), new FakeResourcePackBridge(), new FakeScreenshotBridge(),
                new FakeClipboardBridge(), clock, deps);
        // Service callbacks run inline: the test drives the screen, not the game's tick that drains the queue.
        services.setMainThreadExecutor(DIRECT);
        services.load();
        services.profiles().activate("default");
        services.packOffer().suppress();
        ScreenBootstrap.registerAll(services);
        return services;
    }

    private LocalAiManifest goodManifest() throws Exception {
        return ManifestFixtures.serve(server, FakeArchives.windowsRuntimeZip(), FakeArchives.linuxRuntimeTarGz(), model);
    }

    @SuppressWarnings("unchecked")
    private <T extends UiScreen> T show(ScreenId id, int w, int h) {
        UiScreen screen = (UiScreen) services.screens().create(id).orElseThrow();
        screen.attach(new UiEnvironment(host, ThemeFactory.themeFor(services), uiClock, TestCanvas.metrics(), Lang::tr));
        screen.init(w, h);
        uiClock.advance(UiScreen.TRANSITION_MS + 1);
        settle(screen);
        return (T) screen;
    }

    /** Scrolls a button of the Local AI card into the viewport (it sits below the setting rows) and lets the scroll land. */
    private void reveal(UiScreen screen, UiNode node) {
        ScrollIntoView.reveal(screen.context(), node);
        for (int i = 0; i < 4; i++) {
            uiClock.advance(200);
            settle(screen);
        }
    }

    private static void settle(UiScreen screen) {
        for (int i = 0; i < 3; i++) {
            screen.tick();
        }
        screen.render(new TestCanvas(screen.width(), screen.height()), -1000, -1000, 0f);
        screen.render(new TestCanvas(screen.width(), screen.height()), -1000, -1000, 0f);
    }

    @Test
    void installsOnClickStartsTheRuntimeAndReportsReady() throws Exception {
        services(goodManifest());
        LocalAiSetupScreen screen = show(ScreenId.LOCAL_AI_SETUP, 854, 480);
        assertEquals(LocalAiSetupScreen.Phase.IDLE, screen.phase());
        assertEquals(LocalAiStatus.NOT_INSTALLED, services.localAi().status());
        assertTrue(screen.installButton().isVisible() && screen.installButton().isEnabled());
        assertFalse(screen.cancelButton().isVisible());
        assertFalse(screen.retryButton().isVisible());
        assertFalse(screen.openAssistantButton().isVisible());
        assertEquals(0, server.requestCount(ManifestFixtures.MODEL_PATH), "nothing downloads before Install");
        TestCanvas idle = new TestCanvas(854, 480);
        screen.render(idle, -1000, -1000, 0f);
        assertTrue(idle.hasTextContaining("llama.cpp b11429"), "runtime from the manifest");
        assertTrue(idle.hasTextContaining("Qwen3-1.7B Q8_0"), "model from the manifest");
        assertTrue(idle.hasTextContaining("MIT"), "licences from the manifest");
        assertTrue(idle.hasTextContaining("127.0.0.1"), "the download hosts and the loopback note are drawn");
        assertEquals(List.of(), ReachabilityWalker.walk(screen, "LOCAL_AI_SETUP", "854x480").failures());

        ScreenTestSupport.click(screen, screen.installButton());
        settle(screen);
        assertTrue(server.requestCount(ManifestFixtures.MODEL_PATH) >= 1, "the model was downloaded after the click");
        assertEquals(LocalAiSetupScreen.Phase.READY, screen.phase(), screen.failureText());
        assertEquals(LocalAiStatus.READY, services.localAi().status(), "the runtime was started right after the install");
        for (InstallStep step : InstallStep.values()) {
            assertTrue(screen.step(step).isDone(), step + " done");
        }
        assertTrue(screen.openAssistantButton().isVisible());
        assertFalse(screen.installButton().isVisible());
        assertEquals(Lang.tr("vanta.localai.step.ready"), screen.messageText());
        assertTrue(Files.isRegularFile(services.localAi().paths().installedFile()));
        ScreenTestSupport.click(screen, screen.openAssistantButton());
        assertTrue(host.events().contains("openScreen:NEXUS"));

        // Reopening the screen with an install present shows it as done.
        LocalAiSetupScreen again = show(ScreenId.LOCAL_AI_SETUP, 427, 240);
        assertEquals(LocalAiSetupScreen.Phase.READY, again.phase());
        assertEquals(List.of(), ReachabilityWalker.walk(again, "LOCAL_AI_SETUP", "427x240").failures());
    }

    @Test
    void progressRowsShowBytesAndSpeedWhileInstalling() throws Exception {
        services(goodManifest());
        LocalAiSetupScreen screen = show(ScreenId.LOCAL_AI_SETUP, 854, 480);
        screen.showProgress(InstallStep.DOWNLOADING_MODEL, 500L << 20, 1800L << 20, 8.5 * (1 << 20));
        settle(screen);
        assertEquals(LocalAiSetupScreen.Phase.INSTALLING, screen.phase());
        assertTrue(screen.step(InstallStep.CHECKING).isDone());
        assertTrue(screen.step(InstallStep.DOWNLOADING_RUNTIME).isDone());
        assertTrue(screen.step(InstallStep.DOWNLOADING_MODEL).isActive());
        assertFalse(screen.step(InstallStep.VERIFYING).isDone());
        assertTrue(screen.step(InstallStep.DOWNLOADING_MODEL).progressBar().isVisible());
        assertEquals(500f / 1800f, screen.step(InstallStep.DOWNLOADING_MODEL).progressBar().fraction(), 1e-4f);
        String detail = screen.step(InstallStep.DOWNLOADING_MODEL).detail();
        assertTrue(detail.contains("500 MB"), detail);
        assertTrue(detail.contains("1.76 GB"), detail);
        assertTrue(detail.contains("8.5 MB/s"), detail);
        TestCanvas canvas = new TestCanvas(854, 480);
        screen.render(canvas, -1000, -1000, 0f);
        assertTrue(canvas.hasTextContaining("500 MB"));
        assertTrue(screen.cancelButton().isVisible());
        assertFalse(screen.installButton().isVisible());
        assertEquals(List.of(), ReachabilityWalker.walk(screen, "LOCAL_AI_SETUP", "854x480 [installing]").failures());
    }

    @Test
    void failureShowsTheReasonAndRetryRunsAgain() throws Exception {
        LocalAiManifest bad = ManifestFixtures.serveWithBadModelHash(server, FakeArchives.windowsRuntimeZip(),
                FakeArchives.linuxRuntimeTarGz(), model);
        services(bad);
        LocalAiSetupScreen screen = show(ScreenId.LOCAL_AI_SETUP, 854, 480);
        ScreenTestSupport.click(screen, screen.installButton());
        settle(screen);
        assertEquals(LocalAiSetupScreen.Phase.FAILED, screen.phase());
        assertTrue(screen.failureText().startsWith(Lang.tr("vanta.localai.error.hash_mismatch")), screen.failureText());
        assertEquals(screen.failureText(), screen.messageText());
        assertTrue(screen.retryButton().isVisible() && screen.retryButton().isEnabled());
        assertFalse(screen.installButton().isVisible());
        assertFalse(screen.openAssistantButton().isVisible());
        long downloads = server.requestCount(ManifestFixtures.MODEL_PATH);
        assertEquals(List.of(), ReachabilityWalker.walk(screen, "LOCAL_AI_SETUP", "854x480 [failed]").failures());
        ScreenTestSupport.click(screen, screen.retryButton());
        settle(screen);
        assertTrue(server.requestCount(ManifestFixtures.MODEL_PATH) > downloads, "Retry downloads again");
        assertEquals(LocalAiSetupScreen.Phase.FAILED, screen.phase(), "the same bad manifest fails the same way");
        assertEquals(LocalAiStatus.PARTIAL, services.localAi().status());
    }

    @Test
    void nexusInstallCardOpensTheSetupScreenAndSettingsOfferRemoveAfterwards() throws Exception {
        services(goodManifest());
        NexusScreen nexus = show(ScreenId.NEXUS, 854, 480);
        AssistantPanel panel = nexus.panel(NexusSection.ASSISTANT);
        assertTrue(panel.statusPill().text().contains(Lang.tr("vanta.localai.status.not_installed")),
                panel.statusPill().text());
        assertTrue(panel.installButton().isPresent(), "the Local AI card offers Install");
        assertNotNull(nexus.root().findById("nexus.assistant.localai"));
        TestCanvas canvas = new TestCanvas(854, 480);
        nexus.render(canvas, -1000, -1000, 0f);
        assertTrue(canvas.hasTextContaining("127.0.0.1"), "the download hosts are named (the fixture serves from loopback)");
        assertTrue(canvas.hasTextContaining("Apache-2.0"), "licences are named");
        ScreenTestSupport.click(nexus, panel.installButton().orElseThrow());
        assertTrue(host.events().contains("openScreen:LOCAL_AI_SETUP"));
        assertEquals(0, server.requestCount(ManifestFixtures.MODEL_PATH), "opening the setup downloads nothing");

        nexus.select(NexusSection.SETTINGS);
        settle(nexus);
        NexusSettingsPanel settings = nexus.panel(NexusSection.SETTINGS);
        assertTrue(settings.installButton().isVisible());
        assertFalse(settings.verifyButton().isVisible());
        assertFalse(settings.removeButton().isVisible());

        assertTrue(services.localAi().install());
        settle(nexus);
        assertTrue(services.localAi().isInstalled());
        assertFalse(settings.installButton().isVisible());
        assertTrue(settings.verifyButton().isVisible());
        assertTrue(settings.reinstallButton().isVisible());
        assertTrue(settings.removeButton().isVisible());
        assertEquals(List.of(), ReachabilityWalker.walk(nexus, "NEXUS", "854x480 [settings installed]").failures());
        nexus.select(NexusSection.ASSISTANT);
        settle(nexus);
        assertTrue(panel.installButton().isEmpty(), "the install card is gone once installed");

        reveal(nexus, settings.verifyButton());
        long verifications = server.requestCount(ManifestFixtures.MODEL_PATH);
        ScreenTestSupport.click(nexus, settings.verifyButton());
        settle(nexus);
        assertTrue(services.localAi().isInstalled(), "verification keeps a good install");
        assertEquals(verifications, server.requestCount(ManifestFixtures.MODEL_PATH), "verifying downloads nothing");
        nexus.select(NexusSection.SETTINGS);
        settle(nexus);
        reveal(nexus, settings.removeButton());
        ScreenTestSupport.click(nexus, settings.removeButton());
        assertNotNull(ScreenTestSupport.openDialog(nexus), "Remove asks first");
        ScreenTestSupport.confirmDialog(nexus);
        settle(nexus);
        assertEquals(LocalAiStatus.NOT_INSTALLED, services.localAi().status());
        assertFalse(Files.exists(services.localAi().paths().root()));
        assertTrue(settings.installButton().isVisible());
    }
}
