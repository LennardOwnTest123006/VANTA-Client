package dev.vanta.core.preview.previews;

import dev.vanta.core.bridge.FakeClipboardBridge;
import dev.vanta.core.bridge.FakeGameBridge;
import dev.vanta.core.bridge.FakeKeybindBridge;
import dev.vanta.core.bridge.FakeOptionsBridge;
import dev.vanta.core.bridge.FakeResourcePackBridge;
import dev.vanta.core.bridge.FakeScreenshotBridge;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.screen.Screens1;
import dev.vanta.core.screen.VantaServices;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Builds a fully loaded {@link VantaServices} on the test fakes for screen previews: a temporary config directory,
 * a fake game that is in a singleplayer world, vanilla default options, the sample key mappings and resource packs.
 * Every call returns an independent instance so one preview's changes never leak into another.
 */
public final class PreviewServices {
    /** Fake game bridge exposed for previews that need to tweak the world state. */
    public final FakeGameBridge game = new FakeGameBridge();
    /** Fake options bridge. */
    public final FakeOptionsBridge options = new FakeOptionsBridge();
    /** Fake resource packs. */
    public final FakeResourcePackBridge packs = new FakeResourcePackBridge();
    /** Deterministic clock (2026-10-04T12:00Z). */
    public final MutableClock clock = MutableClock.standard();
    /** The services. */
    public final VantaServices services;

    private PreviewServices() {
        Path dir;
        try {
            dir = Files.createTempDirectory("vanta-preview");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        services = VantaServices.create(VantaPaths.inGameDirectory(dir), game, options, new FakeKeybindBridge(), packs,
                new FakeScreenshotBridge(), new FakeClipboardBridge(), clock);
        services.load();
        Screens1.register(services);
    }

    /** Fresh services with the screens of {@link Screens1} registered. */
    public static PreviewServices create() {
        return new PreviewServices();
    }

    /**
     * Records a realistic frame-time history (about 120 fps with a few stutters) so the Performance Center graph
     * and frame statistics have data when the screen opens.
     */
    public PreviewServices withFrameHistory() {
        for (int i = 0; i < 120; i++) {
            double base = 8.3 + Math.sin(i / 7.0) * 1.4;
            double stutter = i % 37 == 0 ? 11.0 : 0.0;
            services.performance().onFrame(base + stutter);
        }
        return this;
    }

    /** Puts the fake game on the title screen (no world loaded). */
    public PreviewServices onTitleScreen() {
        game.onTitleScreen();
        game.lastWorldName = java.util.Optional.of("Survival World");
        return this;
    }
}
