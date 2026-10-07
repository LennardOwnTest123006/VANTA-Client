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

    /** Swapping the theme while the screen is open goes through the same clamp as {@link UiScreen#init}. */
    @Test
    void zoomClampFollowsARuntimeThemeSwap() {
        int[] clicks = {0};
        Button a = new Button("a", () -> clicks[0]++);
        Button far = new Button("far", () -> clicks[0] += 10);
        UiScreen screen = t.screen(cornerRoot(a, far), 320, 240);
        assertEquals(1f, screen.effectiveScale(), 1e-6f);
        screen.setTheme(Theme.DEFAULT.withScale(1.5f));
        assertEquals(1f, screen.effectiveScale(), 1e-6f, "clamped after a runtime theme swap");
        assertEquals(320, screen.width());
        assertEquals(240, screen.height());
        assertEquals(new Rect(280, 220, 40, 20), far.bounds());
        UiTestSupport.click(screen, far.bounds().centerX(), far.bounds().centerY());
        assertEquals(10, clicks[0]);
        screen.resize(1280, 720);
        assertEquals(1.5f, screen.effectiveScale(), 1e-6f);
        assertEquals(853, screen.width());
        assertEquals(480, screen.height());
        UiTestSupport.click(screen, far.bounds().centerX() * 1.5f, far.bounds().centerY() * 1.5f);
        assertEquals(20, clicks[0]);
        screen.setTheme(Theme.DEFAULT.withScale(0.5f));
        assertEquals(0.5f, screen.effectiveScale(), 1e-6f, "below 1 is never touched");
        assertEquals(2560, screen.width());
    }

    /**
     * Odd window sizes and zooms: the used scale stays within [1, zoom], the logical area never drops below
     * 320x240 when the host offers it, covers the host (within rounding), and both corners are clickable and
     * hoverable through host coordinates.
     */
    @Test
    void zoomClampHoldsAtOddSizes() {
        int[][] sizes = {{321, 241}, {322, 242}, {333, 250}, {359, 269}, {367, 275}, {368, 276}, {369, 277},
            {640, 240}, {320, 480}, {400, 225}, {455, 256}, {319, 239}};
        for (float zoom : new float[] {1.05f, 1.1f, 1.15f, 1.25f, 1.4375f, 1.5f, 1.725f, 2f, 3f}) {
            t.theme = Theme.DEFAULT.withScale(zoom);
            for (int[] size : sizes) {
                int[] clicks = {0};
                Button a = new Button("a", () -> clicks[0]++);
                Button far = new Button("far", () -> clicks[0] += 10);
                UiScreen screen = t.screen(cornerRoot(a, far), size[0], size[1]);
                float s = screen.effectiveScale();
                String what = size[0] + "x" + size[1] + " @ " + zoom + " -> " + s + " (" + screen.width() + "x"
                        + screen.height() + ")";
                assertTrue(s >= 1f && s <= zoom + 1e-6f, what);
                if (size[0] >= UiScreen.MIN_LOGICAL_WIDTH && size[1] >= UiScreen.MIN_LOGICAL_HEIGHT) {
                    assertTrue(screen.width() >= UiScreen.MIN_LOGICAL_WIDTH
                            && screen.height() >= UiScreen.MIN_LOGICAL_HEIGHT, what);
                } else {
                    assertEquals(1f, s, 1e-6f, what);
                }
                assertTrue(Math.abs(screen.width() * s - size[0]) <= s + 0.5f, what);
                assertTrue(Math.abs(screen.height() * s - size[1]) <= s + 0.5f, what);
                Rect fb = far.bounds();
                UiTestSupport.click(screen, fb.centerX() * s, fb.centerY() * s);
                UiTestSupport.click(screen, s, s);
                assertEquals(11, clicks[0], what);
                t.frame(screen, Math.round(fb.centerX() * s), Math.round(fb.centerY() * s));
                assertTrue(far.isHovered(), what);
                assertFalse(a.isHovered(), what);
            }
        }
    }

    /** Windows with room for the zoom are rendered at the full theme scale. */
    @Test
    void zoomIsKeptInLargeWindows() {
        t.theme = Theme.DEFAULT.withScale(2f);
        UiScreen screen = t.screen(new Column(), 3840, 2160);
        assertEquals(2f, screen.effectiveScale(), 1e-6f);
        assertEquals(1920, screen.width());
        assertEquals(1080, screen.height());
        t.theme = Theme.DEFAULT.withScale(3f).withLargeText(true);
        screen = t.screen(new Column(), 1920, 1080);
        assertEquals(3f * 1.15f, screen.effectiveScale(), 1e-6f, "3.45 fits (1080/240 allows 4.5)");
    }

    /** Drag deltas and wheel positions divide by the clamped scale too. */
    @Test
    void dragAndScrollUseTheClampedScale() {
        t.theme = Theme.DEFAULT.withScale(1.5f);
        double[] drag = new double[4];
        double[] scroll = new double[2];
        UiNode root = new UiNode() {
            @Override
            public boolean mouseDown(UiContext ctx, double x, double y, int button) {
                ctx.captureMouse(this);
                return true;
            }

            @Override
            public boolean mouseDrag(UiContext ctx, double x, double y, int button, double dx, double dy) {
                drag[0] = x;
                drag[1] = y;
                drag[2] = dx;
                drag[3] = dy;
                return true;
            }

            @Override
            public boolean mouseScroll(UiContext ctx, double x, double y, double sx, double sy) {
                scroll[0] = x;
                scroll[1] = y;
                return true;
            }
        };
        UiScreen screen = t.screen(root, 480, 270);
        assertEquals(1.125f, screen.effectiveScale(), 1e-6f);
        assertTrue(screen.mouseDown(450, 225, Keys.MOUSE_LEFT));
        assertTrue(screen.mouseDrag(459, 234, Keys.MOUSE_LEFT, 9, 9));
        assertEquals(408, drag[0], 1e-6);
        assertEquals(208, drag[1], 1e-6);
        assertEquals(8, drag[2], 1e-6);
        assertEquals(8, drag[3], 1e-6);
        screen.mouseUp(459, 234, Keys.MOUSE_LEFT);
        assertTrue(screen.mouseScroll(225, 135, 0, 1));
        assertEquals(200, scroll[0], 1e-6);
        assertEquals(120, scroll[1], 1e-6);
    }

    /** A root with {@code a} at the origin and {@code far} in the bottom-right corner, whatever the size. */
    private static UiNode cornerRoot(Button a, Button far) {
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
        return root;
    }

    private void assertZoomClamped(float themeScale, int hostW, int hostH, float expectedScale, int expectedW,
                                   int expectedH) {
        t.theme = Theme.DEFAULT.withScale(themeScale);
        int[] clicks = {0};
        Button a = new Button("a", () -> clicks[0]++);
        // A second button at the far corner proves the clamped layout still fits the host.
        Button far = new Button("far", () -> clicks[0] += 10);
        UiScreen screen = t.screen(cornerRoot(a, far), hostW, hostH);
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
    void layoutRequestedDuringAPassRunsAgainInTheSameLayout() {
        int[] layouts = {0};
        // Like CardGrid / ResponsiveGrid: measured before the width is known, asks for another pass on a new width.
        UiNode grid = new UiNode() {
            private int lastWidth = -1;

            @Override
            public void layout(UiContext ctx) {
                layouts[0]++;
                if (bounds().w() != lastWidth) {
                    lastWidth = bounds().w();
                    ctx.requestLayout();
                }
            }
        };
        UiScreen screen = t.screen(grid, 100, 100);
        assertEquals(2, layouts[0], "the pass requested during layout runs before the first frame");
        t.frame(screen, 0, 0);
        assertEquals(2, layouts[0], "nothing more once the layout settled");
        screen.invalidateLayout();
        t.frame(screen, 0, 0);
        assertEquals(3, layouts[0], "an unchanged width asks for no second pass");
        screen.resize(120, 80);
        assertEquals(5, layouts[0], "a new width gets its second pass within the resize");
        t.frame(screen, 0, 0);
        assertEquals(5, layouts[0]);
    }

    @Test
    void layoutPassesAreBounded() {
        int[] layouts = {0};
        UiNode restless = new UiNode() {
            @Override
            public void layout(UiContext ctx) {
                layouts[0]++;
                ctx.requestLayout();
            }
        };
        UiScreen screen = t.screen(restless, 100, 100);
        assertEquals(UiScreen.MAX_LAYOUT_PASSES, layouts[0]);
        t.frame(screen, 0, 0);
        assertEquals(UiScreen.MAX_LAYOUT_PASSES, layouts[0], "a layout that never settles does not re-run every frame");
        screen.invalidateLayout();
        t.frame(screen, 0, 0);
        assertEquals(UiScreen.MAX_LAYOUT_PASSES * 2, layouts[0]);
    }

    @Test
    void screenShortcutsWaitWhileAPopupIsOpen() {
        int[] hooks = {0};
        Column root = new Column();
        Button behind = root.add(new Button("behind", null));
        UiScreen screen = new UiScreen() {
            @Override
            protected UiNode build(UiContext context) {
                return root;
            }

            @Override
            protected boolean onKeyDown(int key, int scancode, int mods) {
                hooks[0]++;
                context().focus().focus(context(), behind);
                return true;
            }
        };
        screen.attach(t.env());
        screen.init(200, 100);
        assertTrue(screen.keyDown(Keys.F, 0, Keys.MOD_CONTROL));
        assertEquals(1, hooks[0]);
        assertSame(behind, screen.context().focus().focused());

        Column popup = new Column();
        Button inside = popup.add(new Button("inside", null));
        popup.setBounds(10, 10, 100, 50);
        screen.context().popups().open(screen.context(), popup, true, null);
        assertSame(inside, screen.context().focus().focused());
        assertFalse(screen.keyDown(Keys.F, 0, Keys.MOD_CONTROL), "the hook is not consulted behind a popup");
        assertEquals(1, hooks[0]);
        t.frame(screen, 0, 0);
        assertSame(inside, screen.context().focus().focused(), "focus stays in the popup");
        screen.context().popups().closeAll(screen.context());
        assertTrue(screen.keyDown(Keys.F, 0, Keys.MOD_CONTROL));
        assertEquals(2, hooks[0]);
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
