package dev.vanta.core.preview.previews;

import dev.vanta.core.ai.FakeArchives;
import dev.vanta.core.ai.FakeProcess;
import dev.vanta.core.ai.InstallStep;
import dev.vanta.core.ai.LocalAiClient;
import dev.vanta.core.ai.LocalAiInstaller;
import dev.vanta.core.ai.LocalAiManifest;
import dev.vanta.core.ai.LocalAiService;
import dev.vanta.core.ai.LocalFileServer;
import dev.vanta.core.ai.ManifestFixtures;
import dev.vanta.core.ai.NexusTranscript;
import dev.vanta.core.ai.Platform;
import dev.vanta.core.bridge.FakeClipboardBridge;
import dev.vanta.core.bridge.FakeGameBridge;
import dev.vanta.core.bridge.FakeKeybindBridge;
import dev.vanta.core.bridge.FakeOptionsBridge;
import dev.vanta.core.bridge.FakeResourcePackBridge;
import dev.vanta.core.bridge.FakeScreenshotBridge;
import dev.vanta.core.bridge.Vec3d;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.preview.PreviewProvider;
import dev.vanta.core.preview.ScreenPreview;
import dev.vanta.core.preview.ScreenPreviews;
import dev.vanta.core.screen.ScreenBootstrap;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.common.ScreenNavigator;
import dev.vanta.core.screen.nexus.LocalAiSetupScreen;
import dev.vanta.core.screen.nexus.NexusScreen;
import dev.vanta.core.screen.nexus.NexusSection;
import dev.vanta.core.screen.nexus.WaypointsPanel;
import dev.vanta.core.ui.UiScreen;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Previews of Vanta Nexus (every section, desktop and 427x240) and the Local AI setup screen in its four states.
 * The "not installed" and setup previews run the real {@link LocalAiService} against a loopback server that serves a
 * resolved manifest with small fake archives, so the card shows real manifest values; the default previews use the
 * build's bundled manifest (the unresolved template until the resolver ran).
 */
public final class NexusPreviews implements PreviewProvider {
    private static final int DESKTOP_W = 854;
    private static final int DESKTOP_H = 480;
    private static final int SMALL_W = 427;
    private static final int SMALL_H = 240;
    private static final String CANNED = "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\","
            + "\"content\":\"{\\\"message\\\":\\\"done\\\",\\\"actions\\\":[]}\"}}]}";

    /** Public no-arg constructor for {@link java.util.ServiceLoader}. */
    public NexusPreviews() {
    }

    private static ScreenPreview.Options desktop() {
        return ScreenPreview.Options.standard().size(DESKTOP_W, DESKTOP_H).scale(2).lang(Lang::tr);
    }

    private static ScreenPreview.Options small() {
        return ScreenPreview.Options.standard().size(SMALL_W, SMALL_H).scale(2).lang(Lang::tr);
    }

    /** Services on the bundled manifest with every screen registered and a canned transcript. */
    private static PreviewServices services() {
        PreviewServices p = PreviewServices.create();
        ScreenBootstrap.registerAll(p.services);
        return p;
    }

    /**
     * Services whose Local AI reads a resolved manifest from a loopback server (small fake archives, a fake
     * llama-server process, a direct executor): the install card and the setup screen show real values and an
     * install actually runs.
     */
    private static VantaServices localAiServices(boolean install) {
        try {
            LocalFileServer server = new LocalFileServer();
            server.json(LocalAiClient.CHAT_PATH, body -> CANNED);
            byte[] model = FakeArchives.randomBytes(200_000, 8);
            LocalAiManifest manifest = ManifestFixtures.serve(server, FakeArchives.windowsRuntimeZip(),
                    FakeArchives.linuxRuntimeTarGz(), model);
            MutableClock clock = MutableClock.standard();
            HttpClient http = HttpClient.newHttpClient();
            LocalAiService.Dependencies deps = new LocalAiService.Dependencies(Platform.parse("linux-x64"),
                    Optional.of(manifest), "", () -> http, new FakeProcess.Factory(), server::port,
                    duration -> clock.advance(duration.toMillis()), Runnable::run, 8, false,
                    LocalAiInstaller.Config.defaults("preview"));
            Path dir = Files.createTempDirectory("vanta-preview-nexus");
            FakeGameBridge game = new FakeGameBridge();
            VantaServices services = VantaServices.create(VantaPaths.inGameDirectory(dir), game,
                    new FakeOptionsBridge(), new FakeKeybindBridge(), new FakeResourcePackBridge(),
                    new FakeScreenshotBridge(), new FakeClipboardBridge(), clock, deps);
            services.setMainThreadExecutor(Runnable::run);
            services.load();
            services.profiles().activate("default");
            services.packOffer().suppress();
            ScreenBootstrap.registerAll(services);
            if (install) {
                services.localAi().install();
                services.localAi().start();
            }
            // A static preview never downloads or chats after this point; closing the server lets the preview JVM
            // exit (its dispatcher thread is not a daemon thread).
            server.close();
            return services;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static UiScreen nexus(VantaServices services, NexusSection section) {
        ScreenNavigator.forServices(services).stageNexusSection(section.id());
        return (UiScreen) services.screens().create(ScreenId.NEXUS).orElseThrow();
    }

    private static void sampleChat(VantaServices services) {
        NexusTranscript transcript = services.nexusTranscript();
        long at = services.clock().millis();
        transcript.append(NexusTranscript.Entry.user("Only show FPS and coordinates", at - 60_000));
        transcript.append(NexusTranscript.Entry.assistant("Done: the HUD now shows FPS and coordinates only.",
                at - 59_000, List.of("HUD shows only fps, coordinates (armor, clock, effects hidden)"), List.of()));
        transcript.append(NexusTranscript.Entry.user("Add a radar", at - 30_000));
        transcript.append(NexusTranscript.Entry.assistant("There is no radar widget; VANTA never shows other players.",
                at - 29_000, List.of(), List.of("No HUD widget named “radar”")));
    }

    private static void sampleWaypoints(VantaServices services, FakeGameBridge game) {
        String world = game.worldKey();
        String dim = game.dimensionId().orElse("minecraft:overworld");
        services.waypoints().add(world, dim, "Home", new Vec3d(120, 64, -200), "Home", 0xFF7C5CFF);
        services.waypoints().add(world, dim, "Iron farm", new Vec3d(640, 70, -980), "Farm", 0xFF4CC9F0);
        services.waypoints().add(world, dim, "Nether portal", new Vec3d(-40, 62, 15), "Portal", 0xFFF72585);
        services.waypoints().add("mp:play.example.net", dim, "Spawn", new Vec3d(0, 70, 0), "Base", 0xFFFFC857);
    }

    @Override
    public List<ScreenPreviews.Entry> previews() {
        List<ScreenPreviews.Entry> out = new ArrayList<>();

        out.add(ScreenPreviews.Entry.of("nexus-assistant", () -> {
            PreviewServices p = services();
            sampleChat(p.services);
            return nexus(p.services, NexusSection.ASSISTANT);
        }, desktop()));
        out.add(ScreenPreviews.Entry.of("nexus-assistant-empty", () -> nexus(services().services, NexusSection.ASSISTANT),
                desktop()));
        out.add(ScreenPreviews.Entry.of("nexus-assistant-not-installed",
                () -> nexus(localAiServices(false), NexusSection.ASSISTANT), desktop()));
        out.add(ScreenPreviews.Entry.of("nexus-assistant-ready", () -> {
            VantaServices services = localAiServices(true);
            sampleChat(services);
            return nexus(services, NexusSection.ASSISTANT);
        }, desktop()));
        out.add(ScreenPreviews.Entry.of("nexus-assistant-small", () -> {
            PreviewServices p = services();
            sampleChat(p.services);
            return nexus(p.services, NexusSection.ASSISTANT);
        }, small()));
        out.add(ScreenPreviews.Entry.of("nexus-hud-designer", () -> nexus(services().services, NexusSection.HUD_DESIGNER),
                desktop()));
        out.add(ScreenPreviews.Entry.of("nexus-hud-designer-small",
                () -> nexus(services().services, NexusSection.HUD_DESIGNER), small()));
        out.add(ScreenPreviews.Entry.of("nexus-profiles", () -> nexus(services().services, NexusSection.PROFILES),
                desktop()));
        out.add(ScreenPreviews.Entry.of("nexus-performance", () -> {
            PreviewServices p = services().withFrameHistory();
            return nexus(p.services, NexusSection.PERFORMANCE);
        }, desktop()));
        out.add(ScreenPreviews.Entry.of("nexus-waypoints", () -> {
            PreviewServices p = services();
            sampleWaypoints(p.services, p.game);
            return nexus(p.services, NexusSection.WAYPOINTS);
        }, desktop()));
        out.add(ScreenPreviews.Entry.of("nexus-waypoints-all-worlds", () -> {
            PreviewServices p = services();
            sampleWaypoints(p.services, p.game);
            NexusScreen screen = (NexusScreen) nexus(p.services, NexusSection.WAYPOINTS);
            screen.onceInitialised(() -> {
                WaypointsPanel panel = screen.panel(NexusSection.WAYPOINTS);
                panel.allWorldsToggle().set(screen.context(), true);
            });
            return screen;
        }, desktop()));
        out.add(ScreenPreviews.Entry.of("nexus-waypoints-dialog", () -> {
            PreviewServices p = services();
            sampleWaypoints(p.services, p.game);
            NexusScreen screen = (NexusScreen) nexus(p.services, NexusSection.WAYPOINTS);
            screen.onceInitialised(() -> {
                WaypointsPanel panel = screen.panel(NexusSection.WAYPOINTS);
                panel.openAddDialog().ifPresent(d -> d.nameField().setText("Village"));
            });
            return screen;
        }, desktop()));
        out.add(ScreenPreviews.Entry.of("nexus-waypoints-small", () -> {
            PreviewServices p = services();
            sampleWaypoints(p.services, p.game);
            return nexus(p.services, NexusSection.WAYPOINTS);
        }, small()));
        out.add(ScreenPreviews.Entry.of("nexus-lab", () -> nexus(services().services, NexusSection.LAB), desktop()));
        out.add(ScreenPreviews.Entry.of("nexus-settings", () -> nexus(services().services, NexusSection.SETTINGS),
                desktop()));
        out.add(ScreenPreviews.Entry.of("nexus-settings-installed",
                () -> nexus(localAiServices(true), NexusSection.SETTINGS), desktop()));

        out.add(ScreenPreviews.Entry.of("local-ai-setup", () -> setup(localAiServices(false)), desktop()));
        out.add(ScreenPreviews.Entry.of("local-ai-setup-progress", () -> {
            LocalAiSetupScreen screen = setup(localAiServices(false));
            screen.onceInitialised(() -> screen.showProgress(InstallStep.DOWNLOADING_MODEL, 612L << 20, 1830L << 20,
                    14.2 * (1 << 20)));
            return screen;
        }, desktop()));
        out.add(ScreenPreviews.Entry.of("local-ai-setup-failed", () -> {
            LocalAiSetupScreen screen = setup(localAiServices(false));
            screen.onceInitialised(() -> screen.showFailure(Lang.tr("vanta.localai.error.network")
                    + ": huggingface.co did not answer"));
            return screen;
        }, desktop()));
        out.add(ScreenPreviews.Entry.of("local-ai-setup-ready", () -> setup(localAiServices(true)), desktop()));
        out.add(ScreenPreviews.Entry.of("local-ai-setup-small", () -> setup(localAiServices(false)), small()));
        return out;
    }

    private static LocalAiSetupScreen setup(VantaServices services) {
        return (LocalAiSetupScreen) services.screens().create(ScreenId.LOCAL_AI_SETUP).orElseThrow();
    }
}
