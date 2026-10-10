package dev.vanta.core.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vanta.core.bridge.FakeGameBridge;
import dev.vanta.core.bridge.FakeKeybindBridge;
import dev.vanta.core.bridge.FakeOptionsBridge;
import dev.vanta.core.bridge.FakeResourcePackBridge;
import dev.vanta.core.bridge.VanillaOption;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.notifications.Notification;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.common.ScreenTestSupport;
import dev.vanta.core.screen.common.SettingFormats;
import dev.vanta.core.settings.VantaSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The one-time frame-rate uncap of client 1.5.0 through the real composition root: it runs once on a game folder
 * with Minecraft's defaults, touches only Max Framerate and VSync, and leaves the player's later choice alone on every
 * following start, also after an update.
 */
class FrameRateUncapTest {
    @TempDir
    Path dir;

    private final MutableClock clock = MutableClock.standard();
    private final FakeGameBridge game = new FakeGameBridge().onTitleScreen();

    private VantaServices start(FakeOptionsBridge options) {
        VantaServices services = VantaServices.create(VantaPaths.inGameDirectory(dir), game, options,
                new FakeKeybindBridge(), new FakeResourcePackBridge(), clock);
        services.load();
        return services;
    }

    private static List<String> titles(VantaServices services) {
        return services.notifications().history().stream().map(Notification::title).toList();
    }

    private static final String TITLE = "Frame rate uncapped";

    @Test
    void minecraftDefaultsAreUncappedOnceWithOnlyLimitAndVsyncWritten() throws IOException {
        game.clientVersion = "1.5.0";
        FakeOptionsBridge options = new FakeOptionsBridge(); // Minecraft's defaults: VSync on, Max Framerate 120
        Map<VanillaOption, Object> before = Map.copyOf(options.values());
        VantaServices services = start(options);
        assertTrue(options.setOrder.isEmpty(), "loading touches no option");

        assertEquals(FrameRateUncap.Outcome.UNCAPPED, services.onGameStarted());

        assertFalse(options.getBoolean(VanillaOption.VSYNC, true), "VSync off");
        assertEquals(FpsLimitPreset.VANILLA_UNLIMITED, options.getInt(VanillaOption.FRAMERATE_LIMIT, -1));
        assertEquals(List.of(VanillaOption.FRAMERATE_LIMIT, VanillaOption.VSYNC), options.setOrder,
                "only the limit and VSync are written");
        for (Map.Entry<VanillaOption, Object> entry : before.entrySet()) {
            if (entry.getKey() != VanillaOption.FRAMERATE_LIMIT && entry.getKey() != VanillaOption.VSYNC) {
                assertEquals(entry.getValue(), options.values().get(entry.getKey()), entry.getKey() + " untouched");
            }
        }
        assertEquals(1, options.saveCount, "options.txt saved once");
        assertEquals(FpsLimitPreset.UNLIMITED, services.settings().get(VantaSettings.PERFORMANCE_FPS_LIMIT_PRESET));
        assertFalse(services.settings().isDirty(), "settings.json written with the choice");

        FrameRateUncap.Record record = services.frameRateUncap().record().orElseThrow();
        assertEquals("1.5.0", record.clientVersion());
        assertTrue(record.vsyncBefore());
        assertEquals(120, record.limitBefore());
        assertTrue(record.changed());
        JsonObject file = JsonParser.parseString(Files.readString(services.paths().frameRateFile()))
                .getAsJsonObject();
        assertEquals("1.5.0", file.get("uncappedFor").getAsString());
        assertEquals(FrameRateUncap.SCHEMA, file.get("schemaVersion").getAsInt());

        // The notice waits for a screen the player sees (the loading screen covers the start-up).
        assertTrue(services.frameRateUncap().isNoticePending());
        assertFalse(titles(services).contains(TITLE));
        services.tick();
        assertFalse(titles(services).contains(TITLE), "not on the title screen tick (no world, no VANTA menu)");
        game.inWorld = true;
        services.tick();
        assertEquals(1, titles(services).stream().filter(TITLE::equals).count(), "shown once in the world");
        clock.advance(2_000L);
        services.tick();
        assertEquals(1, titles(services).stream().filter(TITLE::equals).count(), "never twice");
        assertFalse(services.frameRateUncap().postPendingNotice());

        assertEquals(FrameRateUncap.Outcome.ALREADY_DONE, services.onGameStarted(), "a second call is a no-op");
        assertEquals(1, options.saveCount);
    }

    @Test
    void theNotificationSaysWhatChangedHowToUndoItAndThatMenusStayAt60() {
        VantaServices services = start(new FakeOptionsBridge());
        services.notifications().frameRateUncapped();
        Notification toast = services.notifications().history().get(0);
        assertEquals(TITLE, toast.title());
        assertEquals("VSync is off and Max Framerate is Unlimited. If you see screen tearing, turn VSync on in "
                + "Settings > Performance. Menus without a world stay at 60 FPS (Minecraft's own menu limit); worlds "
                + "are not capped.", toast.body());
    }

    @Test
    void aLaterVsyncOrLimitChoiceStaysOnEveryFollowingStartAlsoAfterAnUpdate() {
        game.clientVersion = "1.5.0";
        FakeOptionsBridge options = new FakeOptionsBridge();
        VantaServices first = start(options);
        assertEquals(FrameRateUncap.Outcome.UNCAPPED, first.onGameStarted());
        // The player wants VSync back (screen tearing), through VANTA's own frame-rate row.
        first.performance().applyFpsLimit(FpsLimitPreset.VSYNC);
        first.shutdown();
        assertTrue(options.getBoolean(VanillaOption.VSYNC, false));

        options.setOrder.clear();
        int saves = options.saveCount;
        VantaServices second = start(options);
        assertEquals(FrameRateUncap.Outcome.ALREADY_DONE, second.onGameStarted());
        assertTrue(options.getBoolean(VanillaOption.VSYNC, false), "the player's VSync stays on");
        assertTrue(options.setOrder.isEmpty());
        assertEquals(saves, options.saveCount);
        assertFalse(second.frameRateUncap().isNoticePending());
        second.shutdown();

        // A limit set in vanilla Video Settings, then an update to a later client: still the player's choice.
        options.setFromGame(VanillaOption.VSYNC, false);
        options.setFromGame(VanillaOption.FRAMERATE_LIMIT, 60);
        game.clientVersion = "1.6.0";
        VantaServices updated = start(options);
        assertEquals(FrameRateUncap.Outcome.ALREADY_DONE, updated.onGameStarted());
        assertEquals(60, options.getInt(VanillaOption.FRAMERATE_LIMIT, -1));
        assertEquals("1.5.0", updated.frameRateUncap().record().orElseThrow().clientVersion());
    }

    @Test
    void eitherCapAloneIsRemoved() {
        FakeOptionsBridge limitOnly = new FakeOptionsBridge();
        limitOnly.setFromGame(VanillaOption.VSYNC, false); // Max Framerate 120, VSync off
        assertEquals(FrameRateUncap.Outcome.UNCAPPED, start(limitOnly).onGameStarted());
        assertEquals(260, limitOnly.getInt(VanillaOption.FRAMERATE_LIMIT, -1));
        assertFalse(limitOnly.getBoolean(VanillaOption.VSYNC, true));
    }

    @Test
    void vsyncAloneIsRemovedToo(@TempDir Path other) {
        FakeOptionsBridge vsyncOnly = new FakeOptionsBridge();
        vsyncOnly.setFromGame(VanillaOption.FRAMERATE_LIMIT, 260); // Max Framerate Unlimited, VSync on: 60 Hz = 60 FPS
        VantaServices services = VantaServices.create(VantaPaths.inGameDirectory(other), game, vsyncOnly,
                new FakeKeybindBridge(), new FakeResourcePackBridge(), clock);
        services.load();
        assertEquals(FpsLimitPreset.VSYNC, services.performance().liveFpsLimit().orElseThrow());
        assertEquals(FrameRateUncap.Outcome.UNCAPPED, services.onGameStarted());
        assertFalse(vsyncOnly.getBoolean(VanillaOption.VSYNC, true));
        assertEquals(FpsLimitPreset.UNLIMITED, services.settings().get(VantaSettings.PERFORMANCE_FPS_LIMIT_PRESET));
    }

    @Test
    void alreadyUncappedOptionsAreOnlyRecorded() {
        FakeOptionsBridge options = new FakeOptionsBridge();
        options.setFromGame(VanillaOption.VSYNC, false);
        options.setFromGame(VanillaOption.FRAMERATE_LIMIT, 260);
        VantaServices services = start(options);
        services.settings().set(VantaSettings.PERFORMANCE_FPS_LIMIT_PRESET, FpsLimitPreset.UNLIMITED);
        options.setOrder.clear();

        assertEquals(FrameRateUncap.Outcome.ALREADY_UNCAPPED, services.onGameStarted());

        assertTrue(options.setOrder.isEmpty(), "nothing written");
        assertEquals(0, options.saveCount);
        assertFalse(services.frameRateUncap().isNoticePending(), "no notification without a change");
        FrameRateUncap.Record record = services.frameRateUncap().record().orElseThrow();
        assertFalse(record.changed());
        assertTrue(Files.exists(services.paths().frameRateFile()));
    }

    @Test
    void withoutTheGameOptionsNothingIsRecordedAndTheNextStartRetries() {
        FakeOptionsBridge missing = new FakeOptionsBridge().withoutSupportFor(VanillaOption.VSYNC);
        VantaServices services = start(missing);
        assertEquals(FrameRateUncap.Outcome.OPTIONS_UNAVAILABLE, services.onGameStarted());
        assertTrue(services.frameRateUncap().record().isEmpty());
        assertFalse(Files.exists(services.paths().frameRateFile()));
        assertTrue(missing.setOrder.isEmpty());

        FakeOptionsBridge options = new FakeOptionsBridge();
        assertEquals(FrameRateUncap.Outcome.UNCAPPED, start(options).onGameStarted());
    }

    @Test
    void theVantaMainMenuPostsThePendingNotification(@TempDir Path other) {
        ScreenTestSupport t = ScreenTestSupport.create(other);
        t.game.onTitleScreen();
        assertEquals(FrameRateUncap.Outcome.UNCAPPED, t.services.onGameStarted());
        assertFalse(titles(t.services).contains(TITLE), "posted only where the player can see it");
        t.show(ScreenId.MAIN_MENU, 854, 480);
        assertEquals(1, titles(t.services).stream().filter(TITLE::equals).count());
        assertFalse(t.services.frameRateUncap().isNoticePending());
        t.clock.advance(2_000L);
        t.show(ScreenId.MAIN_MENU, 427, 240);
        assertEquals(1, titles(t.services).stream().filter(TITLE::equals).count(), "once per uncap");
    }

    @Test
    void recordJsonRoundTripsAndBrokenFilesCountAsNoRecord() {
        FrameRateUncap.Record record = new FrameRateUncap.Record("1.5.0", 1234L, true, 120, true);
        assertEquals(record, FrameRateUncap.fromJson(FrameRateUncap.toJson(record)).orElseThrow());
        assertTrue(FrameRateUncap.fromJson(new JsonObject()).isEmpty());
        assertTrue(FrameRateUncap.fromJson(JsonParser.parseString("{\"uncappedFor\":\" \"}").getAsJsonObject())
                .isEmpty());
        assertTrue(FrameRateUncap.fromJson(JsonParser.parseString("{\"uncappedFor\":5}").getAsJsonObject())
                .isEmpty());
        assertTrue(FrameRateUncap.fromJson(null).isEmpty());
        assertTrue(FrameRateUncap.isCapped(true, 260));
        assertTrue(FrameRateUncap.isCapped(false, 120));
        assertFalse(FrameRateUncap.isCapped(false, 260));
    }

    @Test
    void labelsSayWhatUnlimitedAndVsyncDo() {
        assertEquals("Unlimited (VSync off)", Lang.tr(FpsLimitPreset.UNLIMITED.langKey()));
        assertEquals("VSync (monitor refresh rate)", Lang.tr(FpsLimitPreset.VSYNC.langKey()));
        assertFalse(FpsLimitPreset.UNLIMITED.vsync(), "Unlimited turns VSync off");
        assertEquals(FpsLimitPreset.VANILLA_UNLIMITED, FpsLimitPreset.UNLIMITED.framerateLimit());
        assertTrue(Lang.tr("vanta.setting.video.vsync.description").contains("monitor refresh rate"));
        assertTrue(Lang.tr("vanta.setting.performance.fpsLimitPreset.description").contains("60 FPS"));
        // The Max Framerate slider's own value stays a plain "Unlimited" (VSync can be on again next to it).
        assertEquals("Unlimited", SettingFormats.formatVanilla(
                VanillaOption.FRAMERATE_LIMIT, 260));
        assertEquals("60 FPS", SettingFormats.formatVanilla(
                VanillaOption.FRAMERATE_LIMIT, 60));
    }
}
