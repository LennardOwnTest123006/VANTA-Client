package dev.vanta.launcher.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The CI smoke hook is configured only by its environment variables.
 */
class UiSmokeTest {

    @Test
    void nothingHappensWithoutTheVariables() {
        assertTrue(UiSmoke.fromEnvironment(Map.of()).isEmpty());
        assertTrue(UiSmoke.fromEnvironment(Map.of(UiSmoke.SCREENSHOT_ENV, " ", UiSmoke.EXIT_AFTER_ENV, "")).isEmpty());
        assertTrue(UiSmoke.fromEnvironment(Map.of(UiSmoke.EXIT_AFTER_ENV, "soon")).isEmpty(), "an unusable number is ignored");
    }

    @Test
    void screenshotAndExitAreParsed() {
        final UiSmoke smoke = UiSmoke.fromEnvironment(Map.of(UiSmoke.SCREENSHOT_ENV, "out/shot.png", UiSmoke.EXIT_AFTER_ENV, " 20 ")).orElseThrow();
        assertEquals(Path.of("out/shot.png").toAbsolutePath(), smoke.screenshot().orElseThrow());
        assertEquals(20, smoke.exitAfterSeconds().orElseThrow());
        assertEquals(1, UiSmoke.fromEnvironment(Map.of(UiSmoke.EXIT_AFTER_ENV, "0")).orElseThrow().exitAfterSeconds().orElseThrow());
        assertTrue(UiSmoke.fromEnvironment(Map.of(UiSmoke.SCREENSHOT_ENV, "a.png")).orElseThrow().exitAfterSeconds().isEmpty());
    }
}
