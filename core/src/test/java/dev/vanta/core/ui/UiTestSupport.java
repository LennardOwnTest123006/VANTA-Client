package dev.vanta.core.ui;

import java.util.function.Function;

/**
 * Shared fixture for UI tests: a manual clock, a recording host, fixed metrics and helpers to lay out nodes or
 * host them in a minimal screen.
 */
public final class UiTestSupport {

    public final ManualClock clock = new ManualClock(1_000L);
    public final TestHost host = new TestHost();
    public Theme theme = Theme.DEFAULT;
    private UiContext ctx;

    /** Environment built from this fixture. */
    public UiEnvironment env() {
        return new UiEnvironment(host, theme, clock, TestCanvas.metrics(), Function.identity());
    }

    /** A standalone context (created once per fixture) with a 400x300 screen. */
    public UiContext ctx() {
        if (ctx == null) {
            ctx = new UiContext(env(), () -> { });
            ctx.setScreenSize(400, 300);
        }
        return ctx;
    }

    /** Sets bounds and lays out the node with this fixture's context. */
    public <T extends UiNode> T place(T node, int x, int y, int w, int h) {
        node.setBounds(x, y, w, h);
        node.layout(ctx());
        return node;
    }

    /** Renders the node to a fresh recording canvas. */
    public TestCanvas render(UiNode node) {
        TestCanvas canvas = new TestCanvas();
        node.render(canvas, ctx());
        return canvas;
    }

    /** Builds, attaches and initialises a screen whose root is {@code root}. */
    public UiScreen screen(UiNode root, int width, int height) {
        UiScreen screen = new UiScreen() {
            @Override
            protected UiNode build(UiContext context) {
                return root;
            }
        };
        screen.attach(env());
        screen.init(width, height);
        return screen;
    }

    /** Builds a screen with a custom builder. */
    public UiScreen screen(Function<UiContext, UiNode> builder, int width, int height) {
        UiScreen screen = new UiScreen() {
            @Override
            protected UiNode build(UiContext context) {
                return builder.apply(context);
            }
        };
        screen.attach(env());
        screen.init(width, height);
        return screen;
    }

    /** Simulates a full left click at the point on a screen. */
    public static void click(UiScreen screen, double x, double y) {
        screen.mouseDown(x, y, Keys.MOUSE_LEFT);
        screen.mouseUp(x, y, Keys.MOUSE_LEFT);
    }

    /** Simulates a full left click on a node hosted in a context (no screen). */
    public void click(UiNode node) {
        Rect b = node.bounds();
        double x = b.centerX();
        double y = b.centerY();
        node.mouseDown(ctx(), x, y, Keys.MOUSE_LEFT);
        UiNode capture = ctx().mouseCapture();
        if (capture != null) {
            capture.mouseUp(ctx(), x, y, Keys.MOUSE_LEFT);
            ctx().releaseMouse();
        } else {
            node.mouseUp(ctx(), x, y, Keys.MOUSE_LEFT);
        }
    }

    /** Presses a key on the focused node chain of a context (mirrors the screen's routing). */
    public boolean key(int key) {
        return key(key, 0);
    }

    /** Presses a key with modifiers. */
    public boolean key(int key, int mods) {
        for (UiNode n = ctx().focus().focused(); n != null; n = n.parent()) {
            if (n.keyDown(ctx(), key, 0, mods)) {
                return true;
            }
        }
        return false;
    }

    /** Types a string into the focused node. */
    public void type(String text) {
        text.codePoints().forEach(cp -> {
            for (UiNode n = ctx().focus().focused(); n != null; n = n.parent()) {
                if (n.charTyped(ctx(), cp, 0)) {
                    break;
                }
            }
        });
    }

    /** Renders a screen frame at the current clock time with the cursor at the point. */
    public TestCanvas frame(UiScreen screen, int mouseX, int mouseY) {
        TestCanvas canvas = new TestCanvas(screen.width(), screen.height());
        screen.render(canvas, mouseX, mouseY, 0f);
        return canvas;
    }
}
