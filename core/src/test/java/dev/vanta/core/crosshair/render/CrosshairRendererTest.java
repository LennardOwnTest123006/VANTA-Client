package dev.vanta.core.crosshair.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.bridge.FakeGameBridge;
import dev.vanta.core.bridge.FakeKeybindBridge;
import dev.vanta.core.bridge.FakeOptionsBridge;
import dev.vanta.core.bridge.FakeResourcePackBridge;
import dev.vanta.core.bridge.KeyStates;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.crosshair.CrosshairShape;
import dev.vanta.core.crosshair.CrosshairStyle;
import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.TestCanvas;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CrosshairRendererTest {
    @TempDir
    Path dir;

    private static TestCanvas draw(CrosshairStyle style) {
        TestCanvas canvas = new TestCanvas(100, 100);
        CrosshairRenderer.draw(canvas, style, 50, 50, 0);
        return canvas;
    }

    @Test
    void crossDrawsFourArmsAndTheirOutlinesBehind() {
        CrosshairStyle style = CrosshairStyle.DEFAULT; // 5 px arms, 1 px thick, gap 1, 1 px black outline
        TestCanvas canvas = draw(style);
        List<TestCanvas.Fill> fills = canvas.fills();
        assertEquals(8, fills.size());
        for (int i = 0; i < 4; i++) {
            assertEquals(0xFF000000, fills.get(i).argb(), "outline first");
            assertEquals(0xFFFFFFFF, fills.get(4 + i).argb(), "arms on top");
        }
        assertFalse(canvas.isFilled(50, 50, 0xFFFFFFFF), "the centre stays empty");
        assertTrue(canvas.isFilled(50, 50 - 2, 0xFFFFFFFF), "top arm starts after the gap");
        assertTrue(canvas.isFilled(50, 50 - 6, 0xFFFFFFFF));
        assertFalse(canvas.isFilled(50, 50 - 7, 0xFFFFFFFF), "arm length is five pixels");
        assertTrue(canvas.isFilled(50 + 6, 50, 0xFFFFFFFF));
        assertTrue(canvas.isFilled(50, 50 - 7, 0xFF000000), "outline extends one pixel beyond the arm");
    }

    @Test
    void dotSquareChevronAndPlusDotShapes() {
        TestCanvas dot = draw(new CrosshairStyle(CrosshairShape.DOT, 3, 1, 0, false, 1, 1.0, 0xFF00FF00, 0, false, false));
        assertEquals(1, dot.fills().size());
        assertTrue(dot.isFilled(50, 50, 0xFF00FF00));
        assertEquals(3, dot.fills().get(0).w());

        TestCanvas square = draw(new CrosshairStyle(CrosshairShape.SQUARE, 4, 1, 0, false, 1, 1.0, 0xFFFFFFFF, 0, false, false));
        assertEquals(4, square.fills().size());
        assertTrue(square.isFilled(46, 46));
        assertFalse(square.isFilled(50, 50), "square is hollow");

        TestCanvas chevron = draw(new CrosshairStyle(CrosshairShape.CHEVRON, 4, 1, 1, false, 1, 1.0, 0xFFFFFFFF, 0, false, false));
        assertEquals(8, chevron.fills().size());
        assertTrue(chevron.fillBounds().y() >= 50, "chevron sits below the centre");

        TestCanvas plusDot = draw(new CrosshairStyle(CrosshairShape.PLUS_DOT, 5, 1, 2, false, 1, 1.0, 0xFFFFFFFF, 0, false, false));
        assertEquals(5, plusDot.fills().size());
        assertTrue(plusDot.isFilled(50, 50));
    }

    @Test
    void ringCoversTheRimButNotTheCentre() {
        TestCanvas canvas = new TestCanvas(60, 60);
        CrosshairRenderer.ring(canvas, 30, 30, 8, 2, 0xFFFF0000);
        assertTrue(canvas.isFilled(38, 30));
        assertTrue(canvas.isFilled(37, 30));
        assertFalse(canvas.isFilled(30, 30));
        assertFalse(canvas.isFilled(33, 30));
        assertTrue(canvas.isFilled(30, 22));

        TestCanvas circle = draw(new CrosshairStyle(CrosshairShape.CIRCLE, 6, 1, 0, false, 1, 1.0, 0xFFFFFFFF, 0, false, false));
        assertFalse(circle.fills().isEmpty());
        assertFalse(circle.isFilled(50, 50));
        assertTrue(circle.isFilled(56, 50));
    }

    @Test
    void opacityScalesTheColours() {
        CrosshairStyle half = CrosshairStyle.DEFAULT.withOpacity(0.5).withOutline(false, 1);
        TestCanvas canvas = draw(half);
        assertEquals(0x80FFFFFF, canvas.fills().get(0).argb());
    }

    @Test
    void dynamicExpansionDependsOnMovementAndActions() {
        CrosshairStyle dynamic = CrosshairStyle.DEFAULT.withDynamic(true);
        assertEquals(0, CrosshairRenderer.targetExpansion(dynamic, KeyStates.NONE));
        assertEquals(CrosshairRenderer.MOVE_EXPANSION,
                CrosshairRenderer.targetExpansion(dynamic, new KeyStates(true, false, false, false, false, false, false, false)));
        assertEquals(CrosshairRenderer.MOVE_EXPANSION + CrosshairRenderer.ACTION_EXPANSION,
                CrosshairRenderer.targetExpansion(dynamic, new KeyStates(false, false, false, true, false, false, true, false)));
        assertEquals(0, CrosshairRenderer.targetExpansion(CrosshairStyle.DEFAULT,
                new KeyStates(true, true, true, true, true, true, true, true)), "static styles never expand");
        assertEquals(0, CrosshairRenderer.targetExpansion(dynamic, null));
    }

    @Test
    void rendererHonoursHudSettingWidgetStateAndThirdPerson() {
        MutableClock clock = MutableClock.standard();
        FakeGameBridge game = new FakeGameBridge();
        VantaServices services = VantaServices.create(VantaPaths.inGameDirectory(dir), game, new FakeOptionsBridge(),
                new FakeKeybindBridge(), new FakeResourcePackBridge(), clock);
        services.load();
        CrosshairRenderer renderer = new CrosshairRenderer(services);
        assertTrue(renderer.isCustomEnabled(), "default preset ships an enabled crosshair widget");

        TestCanvas canvas = new TestCanvas(200, 100);
        renderer.render(canvas, 200, 100, 0f);
        assertFalse(canvas.fills().isEmpty());
        assertTrue(canvas.isFilled(100, 50 - 2, 0xFFFFFFFF), "centred on the screen");

        services.settings().set(VantaSettings.HUD_ENABLED, false);
        assertFalse(renderer.isCustomEnabled());
        canvas.clear();
        renderer.render(canvas, 200, 100, 0f);
        assertTrue(canvas.fills().isEmpty());
        services.settings().set(VantaSettings.HUD_ENABLED, true);

        HudWidgetState crosshair = services.hud().layout().byType(HudWidgetType.CROSSHAIR).get(0);
        services.hud().setLayout(services.hud().layout().with(crosshair.withEnabled(false)));
        assertFalse(renderer.isCustomEnabled(), "disabled widget means vanilla crosshair");
        services.hud().setLayout(services.hud().layout().with(crosshair.withEnabled(true)));

        services.crosshair().setStyle(CrosshairStyle.DEFAULT.withHideOnThirdPerson(true));
        renderer.setThirdPerson(true);
        canvas.clear();
        renderer.render(canvas, 200, 100, 0f);
        assertTrue(canvas.fills().isEmpty(), "hidden in third person");
        renderer.setThirdPerson(false);

        game.onTitleScreen();
        canvas.clear();
        renderer.render(canvas, 200, 100, 0f);
        assertTrue(canvas.fills().isEmpty(), "nothing outside a world");
    }

    @Test
    void expansionEasesTowardsTheTarget() {
        MutableClock clock = MutableClock.standard();
        FakeGameBridge game = new FakeGameBridge();
        game.keyStates = new KeyStates(true, false, false, false, false, false, false, false);
        VantaServices services = VantaServices.create(VantaPaths.inGameDirectory(dir), game, new FakeOptionsBridge(),
                new FakeKeybindBridge(), new FakeResourcePackBridge(), clock);
        services.load();
        services.crosshair().setStyle(CrosshairStyle.DEFAULT.withDynamic(true));
        CrosshairRenderer renderer = new CrosshairRenderer(services);
        TestCanvas canvas = new TestCanvas(200, 100);
        renderer.render(canvas, 200, 100, 0f);
        assertEquals(CrosshairRenderer.MOVE_EXPANSION, renderer.currentExpansion(),
                "first frame samples the inputs itself and snaps");
        game.keyStates = KeyStates.NONE;
        clock.advance(30);
        renderer.render(canvas, 200, 100, 0f);
        assertEquals(CrosshairRenderer.MOVE_EXPANSION, renderer.currentExpansion(),
                "inputs are sampled per tick, not per frame");
        renderer.tick();
        renderer.render(canvas, 200, 100, 0f);
        assertTrue(renderer.currentExpansion() <= CrosshairRenderer.MOVE_EXPANSION);
        clock.advance(500);
        renderer.render(canvas, 200, 100, 0f);
        assertEquals(0, renderer.currentExpansion(), "settles back to zero");
    }
}
