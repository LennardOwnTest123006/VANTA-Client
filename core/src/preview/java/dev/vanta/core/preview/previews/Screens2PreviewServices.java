package dev.vanta.core.preview.previews;

import dev.vanta.core.bridge.FakeClipboardBridge;
import dev.vanta.core.bridge.FakeGameBridge;
import dev.vanta.core.bridge.FakeKeybindBridge;
import dev.vanta.core.bridge.FakeOptionsBridge;
import dev.vanta.core.bridge.FakeResourcePackBridge;
import dev.vanta.core.bridge.FakeScreenshotBridge;
import dev.vanta.core.bridge.KeyRef;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.crosshair.CrosshairPresets;
import dev.vanta.core.screen.Screens2;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.profiles.ProfileActions;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.stats.SessionRecord;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

/**
 * Builds a fully loaded {@link VantaServices} on the test fakes for the previews of the screens registered by
 * {@link Screens2}: a temporary config directory, a fake game in a singleplayer world, the sample key mappings and
 * a deterministic clock. Helpers add sample statistics, key conflicts, unsaved profile changes, import files and a
 * cosmetic pack so every state of the four screens can be captured.
 */
public final class Screens2PreviewServices {
    /** Fake game bridge. */
    public final FakeGameBridge game = new FakeGameBridge();
    /** Fake options bridge. */
    public final FakeOptionsBridge options = new FakeOptionsBridge();
    /** Fake key mappings (mutable so previews can create conflicts). */
    public final FakeKeybindBridge keys = new FakeKeybindBridge();
    /** In-memory clipboard. */
    public final FakeClipboardBridge clipboard = new FakeClipboardBridge();
    /** Deterministic clock (2026-10-04T12:00Z). */
    public final MutableClock clock = MutableClock.standard();
    /** The services. */
    public final VantaServices services;
    /** Config root of this instance. */
    public final Path configDir;

    private Screens2PreviewServices() {
        Path dir;
        try {
            dir = Files.createTempDirectory("vanta-preview-screens2");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        VantaPaths paths = VantaPaths.inGameDirectory(dir);
        configDir = paths.root();
        services = VantaServices.create(paths, game, options, keys, new FakeResourcePackBridge(),
                new FakeScreenshotBridge(), clipboard, clock);
        services.load();
        // Make the live configuration equal to the active profile so the "unsaved changes" banner starts hidden.
        services.profiles().activate("default");
        Screens2.register(services);
    }

    /** Fresh services with the {@link Screens2} screens registered. */
    public static Screens2PreviewServices create() {
        return new Screens2PreviewServices();
    }

    /** Puts the fake game on the title screen. */
    public Screens2PreviewServices onTitleScreen() {
        game.onTitleScreen();
        game.lastWorldName = java.util.Optional.of("Survival World");
        return this;
    }

    /** Records fourteen realistic sessions spread over the last three weeks. */
    public Screens2PreviewServices withSampleStats() {
        Random random = new Random(7);
        long now = clock.millis();
        String[] worlds = {"Survival World", "Creative Plots", "Skyblock"};
        String[] servers = {"play.example.net", "mc.friends-server.org"};
        for (int i = 13; i >= 0; i--) {
            long end = now - (long) i * 36 * 60 * 60 * 1000L - random.nextInt(3 * 60 * 60 * 1000);
            long playtime = (20 + random.nextInt(150)) * 60 * 1000L;
            long start = end - playtime - 60_000L;
            double avg = 95 + random.nextInt(70) + random.nextDouble();
            int max = (int) (avg + 40 + random.nextInt(80));
            boolean server = i % 3 == 1;
            List<String> w = server ? List.of() : List.of(worlds[i % worlds.length]);
            List<String> s = server ? List.of(servers[i % servers.length]) : List.of();
            double distance = 400 + random.nextInt(6000) + random.nextDouble();
            services.statsStore().record(new SessionRecord(start, end, playtime, avg, max, w, s, distance,
                    40 + random.nextInt(900), 20 + random.nextInt(600), random.nextInt(4)));
        }
        services.statsStore().save();
        return this;
    }

    /** Gives the running session some progress (ten minutes, a few blocks). */
    public Screens2PreviewServices withLiveSession() {
        for (int i = 0; i < 12_000; i++) {
            clock.advance(50);
            services.tick();
        }
        for (int i = 0; i < 37; i++) {
            services.stats().onBlockBroken();
        }
        for (int i = 0; i < 12; i++) {
            services.stats().onBlockPlaced();
        }
        return this;
    }

    /** Creates one high-severity and one medium-severity key conflict plus a changed binding. */
    public Screens2PreviewServices withKeyConflicts() {
        keys.setKey("key.vanta.hud_editor", KeyRef.keyboard(344, "key.keyboard.right.shift"));
        keys.setKey("key.vanta.toggle_hud", KeyRef.keyboard(294, "key.keyboard.f5"));
        keys.setKey("key.sprint", KeyRef.keyboard(82, "key.keyboard.r"));
        services.keybinds().refresh();
        return this;
    }

    /** Changes a setting and the crosshair so the live configuration differs from the active profile. */
    public Screens2PreviewServices withUnsavedChanges() {
        services.settings().set(VantaSettings.HUD_GLOBAL_SCALE, 1.25);
        services.crosshair().applyPreset(CrosshairPresets.find(CrosshairPresets.CIRCLE).orElseThrow());
        return this;
    }

    /** Drops two importable profile files into {@code config/vanta/imports}. */
    public Screens2PreviewServices withImportFiles() {
        ProfileActions actions = new ProfileActions(services);
        Path dir = actions.ensureImportsDir();
        services.profiles().exportTo("pvp", dir.resolve("friend-pvp-setup.json"));
        services.profiles().exportTo("recording", dir.resolve("stream-layout.json"));
        return this;
    }

    /** Installs a sample cosmetic pack with two themes. */
    public Screens2PreviewServices withCosmeticPack() {
        String json = """
                {"schemaVersion": 1, "id": "community-pack", "name": "Community Pack", "author": "Preview",
                 "themes": [
                   {"id": "sunset", "name": "Sunset", "tokens": {"accent.violet": "#FF8A5B", "accent.violetHover": "#FFA883",
                     "accent.violetPressed": "#D96A3E", "accent.blue": "#FFC857", "bg.void": "#0B0708", "bg.base": "#120B0D",
                     "surface.1": "#1A1012", "surface.2": "#221518", "surface.3": "#2B1B1F"}},
                   {"id": "ocean", "name": "Ocean", "tokens": {"accent.violet": "#2EC4B6", "accent.violetHover": "#5FD6CB",
                     "accent.blue": "#3A86FF", "bg.void": "#040A0F", "bg.base": "#071017", "surface.1": "#0C1821"}}
                 ]}
                """;
        try {
            Files.createDirectories(services.paths().cosmeticPacksDir());
            Files.writeString(services.paths().cosmeticPacksDir().resolve("community-pack.json"), json,
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        services.cosmetics().load();
        return this;
    }

    /** Turns statistics off in the privacy settings. */
    public Screens2PreviewServices withStatsDisabled() {
        services.settings().set(VantaSettings.PRIVACY_STATS_ENABLED, false);
        return this;
    }
}
