package dev.vanta.core.screen.common;

import dev.vanta.core.bridge.FakeClipboardBridge;
import dev.vanta.core.bridge.FakeGameBridge;
import dev.vanta.core.bridge.FakeKeybindBridge;
import dev.vanta.core.bridge.FakeOptionsBridge;
import dev.vanta.core.bridge.FakeResourcePackBridge;
import dev.vanta.core.bridge.FakeScreenshotBridge;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.Screens1;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.ManualClock;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.TestCanvas;
import dev.vanta.core.ui.TestHost;
import dev.vanta.core.ui.UiEnvironment;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.UiScreen;
import dev.vanta.core.ui.widget.Button;
import dev.vanta.core.ui.widget.Dialog;
import java.nio.file.Path;
import java.util.List;

/**
 * Fixture for screen tests: loaded {@link VantaServices} on the fakes in a temporary directory, the screens of
 * {@link Screens1} registered, a recording {@link TestHost}, a manual UI clock and helpers to show screens, render
 * frames and drive input in host pixels. Shared with the other screen packages' tests.
 */
public final class ScreenTestSupport {
    public final FakeGameBridge game = new FakeGameBridge();
    public final FakeOptionsBridge options = new FakeOptionsBridge();
    public final FakeKeybindBridge keybinds = new FakeKeybindBridge();
    public final FakeResourcePackBridge packs = new FakeResourcePackBridge();
    public final MutableClock clock = MutableClock.standard();
    public final ManualClock uiClock = new ManualClock(1_000L);
    public final TestHost host = new TestHost();
    public final VantaServices services;
    public final ScreenNavigator navigator;

    private ScreenTestSupport(Path dir) {
        services = VantaServices.create(VantaPaths.inGameDirectory(dir), game, options, keybinds, packs,
                new FakeScreenshotBridge(), new FakeClipboardBridge(), clock);
        services.load();
        Screens1.register(services);
        navigator = ScreenNavigator.forServices(services);
    }

    /** Creates the fixture with the config root inside {@code dir}. */
    public static ScreenTestSupport create(Path dir) {
        return new ScreenTestSupport(dir);
    }

    /** Environment with the theme resolved from the services. */
    public UiEnvironment env() {
        return new UiEnvironment(host, ThemeFactory.themeFor(services), uiClock, TestCanvas.metrics(), Lang::tr);
    }

    /** Creates a registered screen. */
    @SuppressWarnings("unchecked")
    public <T extends UiScreen> T create(ScreenId id) {
        return (T) services.screens().create(id).orElseThrow();
    }

    /**
     * Attaches, initialises and renders one settling frame of a screen. The UI clock is moved past the open
     * transition first so frames rendered by the test draw at full opacity.
     */
    public <T extends UiScreen> T show(T screen, int width, int height) {
        screen.attach(env());
        screen.init(width, height);
        uiClock.advance(UiScreen.TRANSITION_MS + 1);
        frame(screen, -1000, -1000);
        return screen;
    }

    /** Creates and shows a registered screen. */
    public <T extends UiScreen> T show(ScreenId id, int width, int height) {
        T screen = create(id);
        return show(screen, width, height);
    }

    /** Renders a frame at the current UI clock time with the cursor at the point. */
    public TestCanvas frame(UiScreen screen, int mouseX, int mouseY) {
        TestCanvas canvas = new TestCanvas(screen.width(), screen.height());
        screen.render(canvas, mouseX, mouseY, 0f);
        return canvas;
    }

    /** Advances the UI clock and renders. */
    public TestCanvas advance(UiScreen screen, long millis) {
        uiClock.advance(millis);
        return frame(screen, -1000, -1000);
    }

    /** Full left click on the centre of a node through the screen's input routing. */
    public static void click(UiScreen screen, UiNode node) {
        Rect b = node.bounds();
        float s = screen.effectiveScale();
        double x = b.centerX() * s;
        double y = b.centerY() * s;
        screen.mouseDown(x, y, Keys.MOUSE_LEFT);
        screen.mouseUp(x, y, Keys.MOUSE_LEFT);
    }

    /** Key press without modifiers. */
    public static boolean key(UiScreen screen, int key) {
        return screen.keyDown(key, 0, 0);
    }

    /** Key press with modifiers. */
    public static boolean key(UiScreen screen, int key, int mods) {
        return screen.keyDown(key, 0, mods);
    }

    /** Types text into the focused node. */
    public static void type(UiScreen screen, String text) {
        text.codePoints().forEach(cp -> screen.charTyped(cp, 0));
    }

    /** The open modal dialog, if any. */
    public static Dialog openDialog(UiScreen screen) {
        if (!screen.context().popups().isOpen()) {
            return null;
        }
        UiNode node = screen.context().popups().top().node();
        return node instanceof Dialog d ? d : null;
    }

    /** Presses the confirm (last) button of the open dialog. */
    public static void confirmDialog(UiScreen screen) {
        Dialog dialog = openDialog(screen);
        if (dialog == null) {
            throw new IllegalStateException("no dialog open");
        }
        List<UiNode> buttons = dialog.buttonRow().children();
        ((Button) buttons.get(buttons.size() - 1)).click(screen.context());
    }

    /** Presses the cancel (first) button of the open dialog. */
    public static void cancelDialog(UiScreen screen) {
        Dialog dialog = openDialog(screen);
        if (dialog == null) {
            throw new IllegalStateException("no dialog open");
        }
        ((Button) dialog.buttonRow().children().get(0)).click(screen.context());
    }
}
