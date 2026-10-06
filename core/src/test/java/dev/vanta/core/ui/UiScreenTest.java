package dev.vanta.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.ui.layout.Column;
import dev.vanta.core.ui.widget.Button;
import org.junit.jupiter.api.Test;

class UiScreenTest {

    private final UiTestSupport t = new UiTestSupport();

    @Test
    void openTransitionFadesInOver160ms() {
        Column root = new Column();
        UiScreen screen = t.screen(root, 200, 100);
        assertEquals(0f, screen.transitionProgress(), 1e-6f);
        TestCanvas first = t.frame(screen, 0, 0);
        assertTrue(first.fills().isEmpty(), "fully transparent first frame draws nothing");
        t.clock.advance(80L);
        assertEquals(Easing.OUT_CUBIC.apply(0.5f), screen.transitionProgress(), 1e-5f);
        TestCanvas mid = t.frame(screen, 0, 0);
        assertTrue(mid.ops().stream().anyMatch(op -> op instanceof TestCanvas.TransformOp s && s.kind().equals("scale")),
                "mid-transition frames are zoomed");
        t.clock.advance(80L);
        assertEquals(1f, screen.transitionProgress(), 1e-6f);
        TestCanvas done = t.frame(screen, 0, 0);
        assertEquals(1, done.fills().size(), "only the dim layer for an empty screen");
        assertEquals(screen.theme().screenDim(), done.fills().get(0).argb());
        assertFalse(done.ops().stream().anyMatch(op -> op instanceof TestCanvas.TransformOp));
    }

    @Test
    void closeDeliversAfterTransitionAndSwallowsInput() {
        Column root = new Column();
        Button b = root.add(new Button("x", null));
        UiScreen screen = t.screen(root, 200, 100);
        t.clock.advance(1000L);
        screen.close();
        assertTrue(screen.isClosing());
        assertFalse(t.host.closed());
        t.frame(screen, 0, 0);
        assertFalse(t.host.closed(), "not delivered before the fade finished");
        assertTrue(screen.mouseDown(b.bounds().centerX(), b.bounds().centerY(), 0), "input swallowed while closing");
        assertFalse(screen.keyDown(Keys.ENTER, 0, 0));
        t.clock.advance(UiScreen.TRANSITION_MS);
        assertEquals(0f, screen.transitionProgress(), 1e-6f);
        t.frame(screen, 0, 0);
        assertTrue(t.host.closed());
        screen.close();
        t.frame(screen, 0, 0);
        assertEquals(1, t.host.events().stream().filter("closeScreen"::equals).count(), "delivered once");
    }

    @Test
    void reducedMotionClosesImmediately() {
        t.theme = Theme.DEFAULT.withReducedMotion(true);
        UiScreen screen = t.screen(new Column(), 200, 100);
        assertEquals(1f, screen.transitionProgress(), 1e-6f);
        screen.close();
        assertTrue(t.host.closed());
    }

    @Test
    void escapeClosesAndTabCyclesFocus() {
        Column root = new Column();
        Button a = root.add(new Button("a", null));
        Button b = root.add(new Button("b", null));
        UiScreen screen = t.screen(root, 200, 100);
        assertTrue(screen.keyDown(Keys.TAB, 0, 0));
        assertSame(a, screen.context().focus().focused());
        screen.keyDown(Keys.TAB, 0, 0);
        assertSame(b, screen.context().focus().focused());
        screen.keyDown(Keys.TAB, 0, 0);
        assertSame(a, screen.context().focus().focused());
        screen.keyDown(Keys.TAB, 0, Keys.MOD_SHIFT);
        assertSame(b, screen.context().focus().focused());
        assertTrue(screen.keyDown(Keys.UP, 0, 0));
        assertSame(a, screen.context().focus().focused(), "arrows move focus when nothing consumes them");
        assertTrue(screen.keyDown(Keys.ESCAPE, 0, 0));
        assertTrue(screen.isClosing());
    }

    @Test
    void escapeIsIgnoredWhenScreenDeclines() {
        UiScreen screen = new UiScreen() {
            @Override
            protected UiNode build(UiContext ctx) {
                return new Column();
            }

            @Override
            public boolean shouldCloseOnEsc() {
                return false;
            }
        };
        screen.attach(t.env());
        screen.init(100, 100);
        assertFalse(screen.keyDown(Keys.ESCAPE, 0, 0));
        assertFalse(screen.isClosing());
    }

    @Test
    void clickingEmptySpaceClearsFocusAndClicksReachButtons() {
        Column root = new Column();
        int[] clicks = {0};
        Button a = root.add(new Button("a", () -> clicks[0]++));
        a.size(60, 20);
        root.align(dev.vanta.core.ui.layout.Align.START);
        UiScreen screen = t.screen(root, 200, 100);
        UiTestSupport.click(screen, 10, 10);
        assertEquals(1, clicks[0]);
        assertSame(a, screen.context().focus().focused());
        assertFalse(screen.mouseDown(150, 90, Keys.MOUSE_LEFT));
        assertNull(screen.context().focus().focused());
    }

    @Test
    void effectiveScaleDividesHostCoordinates() {
        t.theme = Theme.DEFAULT.withScale(2f);
        Column root = new Column().align(dev.vanta.core.ui.layout.Align.START);
        int[] clicks = {0};
        Button a = root.add(new Button("a", () -> clicks[0]++));
        a.size(40, 20);
        UiScreen screen = t.screen(root, 800, 600);
        assertEquals(2f, screen.effectiveScale(), 1e-6f, "800x600 leaves 400x300 logical, nothing to clamp");
        assertEquals(400, screen.width());
        assertEquals(300, screen.height());
        assertEquals(new Rect(0, 0, 40, 20), a.bounds());
        UiTestSupport.click(screen, 70, 30);
        assertEquals(1, clicks[0], "host (70,30) maps to logical (35,15)");
        t.clock.advance(1000L);
        TestCanvas canvas = t.frame(screen, 70, 30);
        assertTrue(canvas.ops().stream().anyMatch(op -> op instanceof TestCanvas.TransformOp s
                && s.kind().equals("scale") && s.a() == 2f));
        assertTrue(a.isHovered());
    }

    /**
     * The vanilla minimum window at UI scale 1.25 would leave 256x192 logical pixels, less than any screen is
     * laid out for; the screen falls back to scale 1 and keeps 320x240.
     */
    @Test
    void zoomIsClampedToKeepTheMinimumLogicalSizeAt125() {
        assertZoomClamped(1.25f, 320, 240, 1f, 320, 240);
    }

    /** Same at 1.5 (would be 213x160). */
    @Test
    void zoomIsClampedToKeepTheMinimumLogicalSizeAt150() {
        assertZoomClamped(1.5f, 320, 240, 1f, 320, 240);
    }

    /** A slightly larger window keeps as much of the zoom as fits: 480x270 at 1.5 renders at 1.125 (427x240). */
    @Test
    void zoomIsOnlyReducedAsFarAsNeeded() {
        assertZoomClamped(1.5f, 480, 270, 1.125f, 427, 240);
        assertZoomClamped(1.25f, 427, 240, 1f, 427, 240);
        assertZoomClamped(1.25f, 1280, 720, 1.25f, 1024, 576);
    }

    /** Scales below 1 enlarge the logical area and are never touched by the clamp. */
    @Test
    void zoomBelowOneIsNotClamped() {
        assertZoomClamped(0.75f, 320, 240, 0.75f, 427, 320);
    }

    /** Large text multiplies the theme scale and is clamped the same way. */
    @Test
    void largeTextIsClampedLikeTheScale() {
        t.theme = Theme.DEFAULT.withLargeText(true);
        UiScreen screen = t.screen(new Column(), 320, 240);
        assertEquals(1f, screen.effectiveScale(), 1e-6f);
        assertEquals(320, screen.width());
        assertEquals(240, screen.height());
        screen.resize(1280, 720);
        assertEquals(1.15f, screen.effectiveScale(), 1e-6f, "the clamp lifts as soon as the window allows it");
    }

    private void assertZoomClamped(float themeScale, int hostW, int hostH, float expectedScale, int expectedW,
                                   int expectedH) {
        t.theme = Theme.DEFAULT.withScale(themeScale);
        int[] clicks = {0};
        Button a = new Button("a", () -> clicks[0]++);
        // A second button at the far corner proves the clamped layout still fits the host.
        Button far = new Button("far", () -> clicks[0] += 10);
        UiNode root = new UiNode() {
            @Override
            public void layout(UiContext ctx) {
                a.setBounds(0, 0, 40, 20);
                far.setBounds(bounds().right() - 40, bounds().bottom() - 20, 40, 20);
                super.layout(ctx);
            }
        };
        root.add(a);
        root.add(far);
        UiScreen screen = t.screen(root, hostW, hostH);
        assertEquals(expectedScale, screen.effectiveScale(), 1e-6f, "used scale");
        assertEquals(expectedW, screen.width(), "logical width");
        assertEquals(expectedH, screen.height(), "logical height");
        assertTrue(screen.width() >= UiScreen.MIN_LOGICAL_WIDTH || themeScale < 1f);
        assertTrue(screen.height() >= UiScreen.MIN_LOGICAL_HEIGHT || themeScale < 1f);
        assertEquals(new Rect(0, 0, 40, 20), a.bounds());
        float s = screen.effectiveScale();
        UiTestSupport.click(screen, 20 * s, 10 * s);
        assertEquals(1, clicks[0], "host click maps to the button through the same scale");
        Rect fb = far.bounds();
        assertTrue(fb.right() * s <= hostW + 0.5f && fb.bottom() * s <= hostH + 0.5f, "far corner inside the host");
        UiTestSupport.click(screen, fb.centerX() * s, fb.centerY() * s);
        assertEquals(11, clicks[0], "a node at the far corner still receives clicks");
        t.clock.advance(1000L);
        TestCanvas canvas = t.frame(screen, Math.round(20 * s), Math.round(10 * s));
        boolean scaled = canvas.ops().stream().anyMatch(op -> op instanceof TestCanvas.TransformOp op2
                && op2.kind().equals("scale") && Math.abs(op2.a() - s) < 1e-6f);
        assertEquals(s != 1f, scaled, "rendering pushes exactly the used scale");
        assertTrue(a.isHovered(), "hover uses the same scale");
    }

    @Test
    void layoutReRunsWhenInvalidated() {
        int[] layouts = {0};
        UiNode counting = new UiNode() {
            @Override
            public void layout(UiContext ctx) {
                layouts[0]++;
            }
        };
        UiScreen screen = t.screen(counting, 100, 100);
        assertEquals(1, layouts[0]);
        t.frame(screen, 0, 0);
        assertEquals(1, layouts[0]);
        screen.invalidateLayout();
        t.frame(screen, 0, 0);
        assertEquals(2, layouts[0]);
        screen.context().requestLayout();
        t.frame(screen, 0, 0);
        assertEquals(3, layouts[0]);
        screen.resize(120, 80);
        assertEquals(4, layouts[0]);
        assertEquals(new Rect(0, 0, 120, 80), counting.bounds());
        screen.setTheme(Theme.highContrast());
        assertEquals(5, layouts[0]);
        assertTrue(screen.theme().isHighContrast());
    }

    @Test
    void lifecycleGuards() {
        UiScreen screen = new UiScreen() {
            @Override
            protected UiNode build(UiContext ctx) {
                return new Column();
            }
        };
        assertThrows(IllegalStateException.class, () -> screen.init(10, 10));
        screen.attach(t.env());
        assertFalse(screen.isInitialised());
        assertThrows(IllegalStateException.class, () -> screen.render(new TestCanvas(), 0, 0, 0f));
        assertFalse(screen.mouseDown(0, 0, 0));
        screen.init(10, 10);
        assertTrue(screen.isInitialised());
        assertEquals("VANTA", screen.title());
        assertTrue(screen.isPauseScreen());
        assertTrue(screen.wantsBlur());
        screen.tick();
    }

    @Test
    void hoverTracksTopMostNodeAndCapture() {
        Column root = new Column().align(dev.vanta.core.ui.layout.Align.START);
        Button a = root.add(new Button("a", null));
        a.size(40, 20);
        Button b = root.add(new Button("b", null));
        b.size(40, 20);
        UiScreen screen = t.screen(root, 200, 100);
        t.frame(screen, 10, 10);
        assertSame(a, screen.hoveredNode());
        assertTrue(a.isHovered());
        assertFalse(b.isHovered());
        t.frame(screen, 10, 30);
        assertSame(b, screen.hoveredNode());
        assertFalse(a.isHovered());
        screen.mouseDown(10, 30, Keys.MOUSE_LEFT);
        t.frame(screen, 150, 90);
        assertSame(b, screen.hoveredNode(), "captured node stays hovered while pressed");
        screen.mouseUp(150, 90, Keys.MOUSE_LEFT);
        t.frame(screen, 150, 90);
        assertSame(root, screen.hoveredNode(), "empty space hovers the root");
        assertFalse(b.isHovered());
    }
}
