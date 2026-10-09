package dev.vanta.core.hud.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.bridge.Cardinal;
import dev.vanta.core.bridge.FakeGameBridge;
import dev.vanta.core.hud.FrameTimeTracker;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class HudDataTest {

    @Test
    void sampleSnapshotFormatsEveryValueDeterministically() {
        HudData data = HudData.sample();
        assertTrue(data.isSample());
        assertTrue(data.inWorld());
        assertEquals(144, data.fps());
        assertEquals("128", data.coordinate(0, 0));
        assertEquals("64", data.coordinate(1, 0));
        assertEquals("-221", data.coordinate(2, 0), "zero decimals floor like F3");
        assertEquals("-220.3", data.coordinate(2, 1));
        assertEquals("128.50", data.coordinate(0, 2));
        assertSame(data.coordinate(0, 0), data.coordinate(0, 0), "memoised per tick");
        assertEquals("2:32 PM", data.clock12(false));
        assertEquals("2:32:07 PM", data.clock12(true));
        assertEquals("12:00 PM", data.gameClock());
        assertEquals("Cherry Grove", data.biomeName());
        assertEquals("minecraft", data.biomeNamespace());
        assertEquals("Overworld", data.dimensionName());
        assertEquals(Optional.of(Cardinal.NORTH), data.facing());
        assertEquals(75, data.memoryPercent());
        assertEquals(0.75, data.memoryFraction(), 1e-9);
        assertEquals(OptionalInt.of(32), data.pingMillis());
        assertEquals(SampleGameData.CPS_LEFT, data.cpsLeft());
        assertEquals(SampleGameData.ONE_PERCENT_LOW_FPS, data.onePercentLowFps());
    }

    @Test
    void capturesLiveDataAndFrameHistory() {
        FakeGameBridge game = new FakeGameBridge();
        game.cpuLoad = java.util.OptionalDouble.empty();
        FrameTimeTracker frames = new FrameTimeTracker();
        for (int i = 0; i < 100; i++) {
            frames.record(i == 99 ? 50.0 : 10.0);
        }
        HudData data = HudData.capture(game, 4, 2, frames, ZoneOffset.UTC);
        assertFalse(data.isSample());
        assertEquals(120, data.fps());
        assertEquals(4, data.cpsLeft());
        assertEquals(2, data.cpsRight());
        assertEquals(20.0, data.onePercentLowFps(), 1e-9, "slowest frame of 50 ms gives 20 fps");
        assertTrue(data.cpuLoad().isEmpty());
        assertTrue(data.singleplayer());

        game.onTitleScreen();
        HudData outside = HudData.capture(game, 0, 0, null, null);
        assertFalse(outside.inWorld());
        assertEquals("—", outside.coordinate(0, 0));
        assertEquals("—", outside.biomeName());
        assertTrue(outside.facing().isEmpty());
    }

    @Test
    void staticFormattersCoverEdgeCases() {
        assertEquals("6:00 AM", HudData.formatGameTime(0));
        assertEquals("12:00 PM", HudData.formatGameTime(6_000));
        assertEquals("6:00 PM", HudData.formatGameTime(12_000));
        assertEquals("12:00 AM", HudData.formatGameTime(18_000));
        assertEquals("6:00 AM", HudData.formatGameTime(24_000));
        assertEquals("1:30 PM", HudData.formatGameTime(7_500));
        assertEquals("11:59 PM", HudData.formatGameTime(17_999));
        assertEquals("Dark Forest", HudData.prettyName("minecraft:dark_forest"));
        assertEquals("Cherry Grove", HudData.prettyName("cherry_grove"));
        assertEquals("", HudData.prettyName(""));
        assertEquals("", HudData.namespace("plains"));
        assertEquals("modid", HudData.namespace("modid:biome"));
        assertEquals(1536, HudData.toMiB(1_536L << 20));
        assertEquals("-1", HudData.formatCoordinate(-0.5, 0));
        assertEquals("-0.5", HudData.formatCoordinate(-0.5, 1));
    }

    @Test
    void memoCacheStoresStringsPerKey() {
        HudData data = HudData.sample();
        assertEquals(null, data.cached("fps"));
        assertEquals("x", data.cache("fps", "x"));
        assertEquals("x", data.cached("fps"));
    }
}
