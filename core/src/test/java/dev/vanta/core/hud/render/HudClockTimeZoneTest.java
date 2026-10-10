package dev.vanta.core.hud.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.bridge.FakeGameBridge;
import dev.vanta.core.bridge.FakeKeybindBridge;
import dev.vanta.core.bridge.FakeOptionsBridge;
import dev.vanta.core.bridge.FakeResourcePackBridge;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.hud.HudLayout;
import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.hud.widgets.ClockWidget;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.time.DefaultZone;
import dev.vanta.core.time.TimeZoneChoice;
import dev.vanta.core.ui.TestCanvas;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The HUD clock shows the player's local time: VANTA 1.5.0 formatted the system time in the UTC zone of the services
 * clock, so a player in Germany saw 6:00 AM at 8:00 AM. From client 1.6.0 on the clock uses
 * {@link VantaServices#timeZone()}: this PC's zone by default, or the zone picked in the time zone setting.
 */
class HudClockTimeZoneTest {
    private static final int W = 854;
    private static final int H = 480;
    /** 2026-07-15T06:00:00Z, 8:00 AM in Berlin (summer time). */
    private static final long SUMMER_6_UTC = Instant.parse("2026-07-15T06:00:00Z").toEpochMilli();
    /** 2026-01-15T06:00:00Z, 7:00 AM in Berlin (winter time). */
    private static final long WINTER_6_UTC = Instant.parse("2026-01-15T06:00:00Z").toEpochMilli();

    @TempDir
    Path dir;
    private VantaServices services;
    private FakeGameBridge game;
    private HudRenderer renderer;

    @BeforeEach
    void setUp() {
        game = new FakeGameBridge();
        // The services clock stays UTC, as in the client (Clock.systemUTC()): only the formatting zone changes.
        services = VantaServices.create(VantaPaths.inGameDirectory(dir), game, new FakeOptionsBridge(),
                new FakeKeybindBridge(), new FakeResourcePackBridge(), MutableClock.standard());
        services.load();
        services.hud().setLayout(new HudLayout(List.of(HudWidgetState.defaults("clock", HudWidgetType.CLOCK))));
        renderer = new HudRenderer(services);
    }

    private String clockText() {
        renderer.tick();
        return renderer.data().clock12(false);
    }

    @Test
    void theDefaultIsThisPcsZoneSoGermanyReadsEightNotSix() {
        assertEquals(TimeZoneChoice.AUTO, services.settings().get(VantaSettings.GENERAL_TIME_ZONE));
        assertEquals(ZoneOffset.UTC, services.clock().getZone(), "timestamps stay UTC");
        game.currentTimeMillis = SUMMER_6_UTC;
        DefaultZone.with("Europe/Berlin", () -> {
            assertEquals(ZoneId.of("Europe/Berlin"), services.timeZone());
            assertEquals("8:00 AM", clockText());
            assertEquals(ZoneId.of("Europe/Berlin"), renderer.data().zone());
            game.currentTimeMillis = WINTER_6_UTC;
            assertEquals("7:00 AM", clockText(), "winter time without a restart");
        });
    }

    @Test
    void autoFollowsAChangedDefaultZoneOnTheNextTick() {
        game.currentTimeMillis = SUMMER_6_UTC;
        DefaultZone.with("Asia/Tokyo", () -> assertEquals("3:00 PM", clockText()));
        DefaultZone.with("America/New_York", () -> assertEquals("2:00 AM", clockText()));
    }

    @Test
    void aChosenZoneWinsOverThePcsZoneAndAppliesOnTheNextTick() {
        game.currentTimeMillis = SUMMER_6_UTC;
        DefaultZone.with("America/Los_Angeles", () -> {
            assertEquals("11:00 PM", clockText(), "auto: Los Angeles, the evening before");
            services.settings().set(VantaSettings.GENERAL_TIME_ZONE, TimeZoneChoice.EUROPE_BERLIN);
            assertEquals("8:00 AM", clockText());
            assertEquals("8:00:00 AM", renderer.data().clock12(true));
            services.settings().set(VantaSettings.GENERAL_TIME_ZONE, TimeZoneChoice.UTC);
            assertEquals("6:00 AM", clockText());
            services.settings().set(VantaSettings.GENERAL_TIME_ZONE, TimeZoneChoice.ASIA_KOLKATA);
            assertEquals("11:30 AM", clockText());
        });
    }

    @Test
    void theClockWidgetDrawsTheLocalTime() {
        game.currentTimeMillis = SUMMER_6_UTC;
        services.settings().set(VantaSettings.GENERAL_TIME_ZONE, TimeZoneChoice.EUROPE_BERLIN);
        renderer.tick();
        TestCanvas canvas = new TestCanvas(W, H);
        renderer.render(canvas, W, H, 0f);
        assertTrue(canvas.hasText("8:00 AM"), "the HUD shows 8:00 AM in Berlin at 06:00 UTC");
    }

    @Test
    void theInGameTimeDoesNotDependOnAnyZone() {
        services.hud().setLayout(new HudLayout(List.of(HudWidgetState.defaults("clock", HudWidgetType.CLOCK)
                .withProp("format", ClockWidget.GAME_TIME))));
        game.gameTicks = 0L;
        for (TimeZoneChoice choice : List.of(TimeZoneChoice.UTC, TimeZoneChoice.ASIA_TOKYO,
                TimeZoneChoice.AMERICA_LOS_ANGELES)) {
            services.settings().set(VantaSettings.GENERAL_TIME_ZONE, choice);
            renderer.tick();
            assertEquals("6:00 AM", renderer.data().gameClock(), "tick 0 is 6:00 AM in every zone");
            TestCanvas canvas = new TestCanvas(W, H);
            renderer.render(canvas, W, H, 0f);
            assertTrue(canvas.hasText("6:00 AM"));
        }
    }

    @Test
    void aCaptureWithoutAZoneUsesThePcsZone() {
        game.currentTimeMillis = SUMMER_6_UTC;
        DefaultZone.with("Europe/Berlin", () -> {
            HudData data = HudData.capture(game, 0, 0, null, null);
            assertEquals(ZoneId.of("Europe/Berlin"), data.zone());
            assertEquals("8:00 AM", data.clock12(false));
        });
    }

    @Test
    void theEditorSampleKeepsItsFixedUtcTimeForStableScreenshots() {
        services.settings().set(VantaSettings.GENERAL_TIME_ZONE, TimeZoneChoice.ASIA_TOKYO);
        DefaultZone.with("Europe/Berlin", () -> {
            assertEquals("2:32 PM", HudData.sample().clock12(false));
            HudRenderer sample = new HudRenderer(services, new SampleGameData());
            sample.tick();
            assertEquals("2:32 PM", sample.data().clock12(false));
        });
    }
}
