package dev.vanta.core.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.bridge.VanillaOption;
import dev.vanta.core.config.JsonStore;
import dev.vanta.core.config.MutableClock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SmartBoostStateTest {
    @TempDir
    Path dir;

    @Test
    void roundTripsEveryField() {
        SmartBoostState state = new SmartBoostState();
        state.own(VanillaOption.RENDER_DISTANCE, 10, 12);
        state.own(VanillaOption.PARTICLES, "DECREASED", "ALL");
        state.own(VanillaOption.ENTITY_DISTANCE_SCALING, 1.0, 1.25);
        state.own(VanillaOption.SMOOTH_LIGHTING, true, false);
        state.own(VanillaOption.CLOUDS, "FAST", "FANCY");
        state.release(VanillaOption.CLOUDS);
        state.setGraphicsAtWrite("");
        state.setAdaptiveCeiling(10);
        state.completeRun("1.3.0", 1_700_000_000_000L,
                new SmartBoostState.Result(PerformancePreset.BALANCED, 60, 141.26, 77.04));

        JsonStore store = new JsonStore(MutableClock.standard());
        Path file = dir.resolve("smart-boost.json");
        state.save(store, file);
        assertTrue(Files.exists(file));
        SmartBoostState read = SmartBoostState.load(store, file);

        assertEquals(Optional.of("1.3.0"), read.lastRunClientVersion());
        assertEquals(1_700_000_000_000L, read.lastRunAtMillis());
        SmartBoostState.Result result = read.result().orElseThrow();
        assertEquals(PerformancePreset.BALANCED, result.preset());
        assertEquals(60, result.targetFps());
        assertEquals(141.3, result.p50Fps(), 1e-9);
        assertEquals(10, read.owned().get(VanillaOption.RENDER_DISTANCE));
        assertEquals("DECREASED", read.owned().get(VanillaOption.PARTICLES));
        assertEquals(1.0, read.owned().get(VanillaOption.ENTITY_DISTANCE_SCALING));
        assertEquals(true, read.owned().get(VanillaOption.SMOOTH_LIGHTING));
        assertEquals(12, read.undo().get(VanillaOption.RENDER_DISTANCE));
        assertEquals(1.25, read.undo().get(VanillaOption.ENTITY_DISTANCE_SCALING));
        assertEquals(Set.of(VanillaOption.CLOUDS), read.released());
        assertFalse(read.isOwned(VanillaOption.CLOUDS));
        assertFalse(read.undo().containsKey(VanillaOption.CLOUDS), "a released option has nothing to undo");
        assertEquals(Optional.of(""), read.graphicsAtWrite());
        assertEquals(10, read.adaptiveCeiling());
    }

    @Test
    void undoKeepsTheValueFromBeforeTheFirstWrite() {
        SmartBoostState state = new SmartBoostState();
        state.own(VanillaOption.RENDER_DISTANCE, 16, 12);
        state.own(VanillaOption.RENDER_DISTANCE, 10, 16);
        assertEquals(12, state.undo().get(VanillaOption.RENDER_DISTANCE));
        assertEquals(10, state.owned().get(VanillaOption.RENDER_DISTANCE));
    }

    @Test
    void missingOrBrokenFilesGiveAFreshState() throws Exception {
        JsonStore store = new JsonStore(MutableClock.standard());
        Path file = dir.resolve("smart-boost.json");
        SmartBoostState missing = SmartBoostState.load(store, file);
        assertTrue(missing.lastRunClientVersion().isEmpty());
        assertTrue(missing.owned().isEmpty());

        Files.writeString(file, "{ this is not json");
        SmartBoostState broken = SmartBoostState.load(store, file);
        assertTrue(broken.result().isEmpty());
        assertFalse(Files.exists(file), "the broken file was moved aside");
        assertEquals(1, store.backups(file).size());

        Files.writeString(file, "{\"owned\":{\"render_distance\":\"far\",\"no_such_option\":3,"
                + "\"particles\":\"MINIMAL\"},\"released\":[\"fov\",42],\"result\":{\"preset\":\"warp\"}}");
        SmartBoostState partial = SmartBoostState.load(store, file);
        assertFalse(partial.isOwned(VanillaOption.RENDER_DISTANCE), "wrong type skipped");
        assertEquals("MINIMAL", partial.owned().get(VanillaOption.PARTICLES));
        assertTrue(partial.isReleased(VanillaOption.FOV));
        assertTrue(partial.result().isEmpty(), "unknown preset skipped");
    }
}
