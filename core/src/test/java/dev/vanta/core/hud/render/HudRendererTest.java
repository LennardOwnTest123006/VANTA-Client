package dev.vanta.core.hud.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.bridge.FakeGameBridge;
import dev.vanta.core.bridge.FakeKeybindBridge;
import dev.vanta.core.bridge.FakeOptionsBridge;
import dev.vanta.core.bridge.FakeResourcePackBridge;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.cosmetics.HudTheme;
import dev.vanta.core.hud.HudLayout;
import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.TestCanvas;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HudRendererTest {
    private static final int W = 854;
    private static final int H = 480;

    @TempDir
    Path dir;
    private VantaServices services;
    private FakeGameBridge game;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        clock = MutableClock.standard();
        game = new FakeGameBridge();
        services = VantaServices.create(VantaPaths.inGameDirectory(dir), game, new FakeOptionsBridge(),
                new FakeKeybindBridge(), new FakeResourcePackBridge(), clock);
        services.load();
    }

    private TestCanvas render(HudRenderer renderer) {
        TestCanvas canvas = new TestCanvas(W, H);
        renderer.render(canvas, W, H, 0f);
        return canvas;
    }

    @Test
    void rendersTheDefaultLayoutFromLiveDataWithoutTheCrosshair() {
        HudRenderer renderer = new HudRenderer(services);
        assertFalse(renderer.isSample());
        renderer.tick();
        TestCanvas canvas = render(renderer);
        assertTrue(canvas.hasText("FPS"));
        assertTrue(canvas.hasText("120"), "fps from the game bridge");
        assertTrue(canvas.hasText("Plains"));
        assertFalse(canvas.isFilled(W / 2, H / 2 - 2, 0xFFFFFFFF), "the crosshair is left to CrosshairRenderer");
        TestCanvas withCrosshair = new TestCanvas(W, H);
        renderer.renderLayout(withCrosshair, services.hud().layout(), W, H, renderer.data(), true);
        assertTrue(withCrosshair.fills().size() > canvas.fills().size());
    }

    /**
     * The client's {@code FrameTimer} is the single source of frame time samples (HUD element in a world, VantaScreen
     * otherwise), so the core render pass must not record a second sample per frame.
     */
    @Test
    void renderRecordsNoFrameTimeSample() {
        HudRenderer renderer = new HudRenderer(services);
        renderer.tick();
        for (int frame = 0; frame < 10; frame++) {
            render(renderer);
        }
        assertEquals(0, services.performance().frameTimes().size(), "frame times come from the client FrameTimer");
        services.performance().onFrame(game.frameTimeMillis);
        assertEquals(1, services.performance().frameTimes().size(), "one FrameTimer call is one sample");
    }

    @Test
    void doesNothingWhenDisabledOrOutsideAWorld() {
        HudRenderer renderer = new HudRenderer(services);
        renderer.tick();
        services.settings().set(VantaSettings.HUD_ENABLED, false);
        assertTrue(render(renderer).ops().isEmpty());
        services.settings().set(VantaSettings.HUD_ENABLED, true);
        game.onTitleScreen();
        assertTrue(render(renderer).ops().isEmpty());
    }

    @Test
    void respectsEnabledFlagGlobalScaleAndOpacity() {
        HudRenderer renderer = new HudRenderer(services);
        renderer.tick();
        HudWidgetState fps = services.hud().layout().find("fps").orElseThrow();
        services.hud().setLayout(services.hud().layout().with(fps.withEnabled(false)));
        assertFalse(render(renderer).hasText("FPS"));
        services.hud().setLayout(services.hud().layout().with(fps.withEnabled(true)));

        services.settings().set(VantaSettings.HUD_GLOBAL_SCALE, 2.0);
        TestCanvas scaled = render(renderer);
        assertTrue(scaled.ops().stream().anyMatch(op -> op instanceof TestCanvas.TransformOp t
                && t.kind().equals("scale") && t.a() == 2f), "global scale is pushed as a transform");
        services.settings().set(VantaSettings.HUD_GLOBAL_SCALE, 1.0);

        services.settings().set(VantaSettings.HUD_GLOBAL_OPACITY, 0.5);
        TestCanvas faded = render(renderer);
        TestCanvas.Text text = faded.texts().stream().filter(t -> t.text().equals("FPS")).findFirst().orElseThrow();
        int alpha = text.argb() >>> 24;
        assertTrue(alpha >= 120 && alpha <= 136, "opacity halves the alpha: " + alpha);
    }

    @Test
    void mouseClicksFeedTheClickCounters() {
        HudLayout layout = new HudLayout(List.of(HudWidgetState.defaults("cps", HudWidgetType.CPS)));
        services.hud().setLayout(layout);
        HudRenderer renderer = new HudRenderer(services);
        renderer.onMouseClick(Keys.MOUSE_LEFT);
        renderer.onMouseClick(Keys.MOUSE_LEFT);
        renderer.onMouseClick(Keys.MOUSE_RIGHT);
        renderer.onMouseClick(Keys.MOUSE_MIDDLE);
        renderer.tick();
        assertTrue(render(renderer).hasText("2 | 1"));
        clock.advance(1_500);
        renderer.tick();
        assertTrue(render(renderer).hasText("0 | 0"), "clicks fall out of the one second window");
    }

    @Test
    void sampleSourceShowsSampleDataAndNeverTouchesTheGame() {
        HudRenderer renderer = new HudRenderer(services, new SampleGameData());
        assertTrue(renderer.isSample());
        game.onTitleScreen();
        renderer.tick();
        assertTrue(renderer.data().isSample());
        TestCanvas canvas = render(renderer);
        assertTrue(canvas.hasText("144"));
        assertTrue(canvas.hasText("Cherry Grove"));
        assertEquals(0, services.performance().frameTimes().size(), "sample frames are not recorded");
        Size size = renderer.preferredSize(TestCanvas.metrics(), services.hud().layout().find("fps").orElseThrow(),
                renderer.data());
        assertTrue(size.w() > 0 && size.h() > 0);
    }

    @Test
    void paintFollowsTheHudThemeAndShadowSetting() {
        HudRenderer renderer = new HudRenderer(services);
        renderer.tick();
        assertEquals(HudTheme.CLEAN, renderer.paint().theme());
        assertTrue(renderer.paint().textShadow());
        services.settings().set(VantaSettings.COSMETICS_HUD_THEME, HudTheme.MINIMAL);
        services.settings().set(VantaSettings.HUD_TEXT_SHADOW, false);
        renderer.tick();
        assertEquals(HudTheme.MINIMAL, renderer.paint().theme());
        assertFalse(renderer.paint().textShadow());
        TestCanvas canvas = render(renderer);
        assertTrue(canvas.texts().stream().noneMatch(TestCanvas.Text::shadow));
    }

    @Test
    void renderWithoutTickCapturesOnDemand() {
        HudRenderer renderer = new HudRenderer(services);
        TestCanvas canvas = render(renderer);
        assertTrue(canvas.hasText("FPS"));
        assertTrue(renderer.data().inWorld());
    }
}
