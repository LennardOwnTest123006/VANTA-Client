package dev.vanta.launcher.ui;

import dev.vanta.launcher.ui.model.NavigationModel;
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
        assertTrue(UiSmoke.fromEnvironment(Map.of(UiSmoke.PAGE_ENV, "mods", UiSmoke.SIZE_ENV, "1000x600")).isEmpty(),
            "page and size alone change nothing: they only steer a screenshot / exit run");
    }

    @Test
    void screenshotAndExitAreParsed() {
        final UiSmoke smoke = UiSmoke.fromEnvironment(Map.of(UiSmoke.SCREENSHOT_ENV, "out/shot.png", UiSmoke.EXIT_AFTER_ENV, " 20 ")).orElseThrow();
        assertEquals(Path.of("out/shot.png").toAbsolutePath(), smoke.screenshot().orElseThrow());
        assertEquals(20, smoke.exitAfterSeconds().orElseThrow());
        assertTrue(smoke.page().isEmpty(), "no page variable: the page stays as restored from the preferences");
        assertTrue(smoke.size().isEmpty(), "no size variable: the window keeps its size");
        assertEquals(1, UiSmoke.fromEnvironment(Map.of(UiSmoke.EXIT_AFTER_ENV, "0")).orElseThrow().exitAfterSeconds().orElseThrow());
        assertTrue(UiSmoke.fromEnvironment(Map.of(UiSmoke.SCREENSHOT_ENV, "a.png")).orElseThrow().exitAfterSeconds().isEmpty());
    }

    @Test
    void pageAndSizeAreParsed() {
        final UiSmoke smoke = UiSmoke.fromEnvironment(Map.of(UiSmoke.SCREENSHOT_ENV, "a.png", UiSmoke.PAGE_ENV, " Mods ",
            UiSmoke.SIZE_ENV, "1000x600")).orElseThrow();
        assertEquals(NavigationModel.Page.MODS, smoke.page().orElseThrow(), "page names are matched ignoring case and blanks");
        assertEquals(new UiSmoke.Size(1000, 600), smoke.size().orElseThrow());
        assertEquals(NavigationModel.Page.SETTINGS, UiSmoke.parsePage("settings").orElseThrow());
        assertEquals(new UiSmoke.Size(960, 600), UiSmoke.parseSize(" 960 X 600 ").orElseThrow(), "'X', blanks and '×' are accepted");
        assertEquals(new UiSmoke.Size(1120, 720), UiSmoke.parseSize("1120×720").orElseThrow());
    }

    @Test
    void unknownPageOrSizeIsIgnored() {
        final UiSmoke smoke = UiSmoke.fromEnvironment(Map.of(UiSmoke.SCREENSHOT_ENV, "a.png", UiSmoke.PAGE_ENV, "shop",
            UiSmoke.SIZE_ENV, "big")).orElseThrow();
        assertTrue(smoke.page().isEmpty(), "an unknown page is ignored, the run still happens");
        assertTrue(smoke.size().isEmpty(), "an unreadable size is ignored, the run still happens");
        assertTrue(UiSmoke.parseSize("1000").isEmpty());
        assertTrue(UiSmoke.parseSize("1000x").isEmpty());
        assertTrue(UiSmoke.parseSize("x600").isEmpty());
        assertTrue(UiSmoke.parseSize("1x1").isEmpty(), "single-digit sizes are not window sizes");
    }
}
