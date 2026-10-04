package dev.vanta.core.screen.cosmetics;

import dev.vanta.core.bridge.FakeClipboardBridge;
import dev.vanta.core.bridge.FakeGameBridge;
import dev.vanta.core.bridge.FakeKeybindBridge;
import dev.vanta.core.bridge.FakeOptionsBridge;
import dev.vanta.core.bridge.FakeResourcePackBridge;
import dev.vanta.core.bridge.FakeScreenshotBridge;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.Screens2;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.stats.SessionRecord;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.UiEnvironment;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.UiScreen;
import dev.vanta.core.ui.UiTestSupport;
import dev.vanta.core.ui.widget.Button;
import dev.vanta.core.ui.widget.Dialog;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

/**
 * Test fixture for the {@link Screens2} screens: loaded services on the fakes in a temporary config directory
 * (active profile applied so no "unsaved changes" exist), the UI test environment and helpers to open a screen,
 * click nodes, type text and press buttons of an open dialog.
 */
public final class ServicesFixture {
    public final MutableClock clock = MutableClock.standard();
    public final FakeGameBridge game = new FakeGameBridge();
    public final FakeOptionsBridge options = new FakeOptionsBridge();
    public final FakeKeybindBridge keys = new FakeKeybindBridge();
    public final FakeClipboardBridge clipboard = new FakeClipboardBridge();
    public final FakeScreenshotBridge screenshots = new FakeScreenshotBridge();
    public final VantaServices services;
    public final UiTestSupport ui = new UiTestSupport();

    /** Services below {@code dir/config/vanta}. */
    public ServicesFixture(Path dir) {
        services = VantaServices.create(VantaPaths.inGameDirectory(dir), game, options, keys,
                new FakeResourcePackBridge(), screenshots, clipboard, clock);
        services.load();
        services.profiles().activate("default");
        Screens2.register(services);
    }

    /** UI environment with the translation bundle (so labels are real text). */
    public UiEnvironment env() {
        return new UiEnvironment(ui.host, ui.theme, ui.clock, dev.vanta.core.ui.TestCanvas.metrics(),
                (Function<String, String>) dev.vanta.core.i18n.Lang::tr);
    }

    /** Creates, attaches and initialises a registered screen at 854x480. */
    @SuppressWarnings("unchecked")
    public <T extends UiScreen> T open(ScreenId id) {
        return (T) open(id, 854, 480);
    }

    /** Creates, attaches and initialises a registered screen. */
    @SuppressWarnings("unchecked")
    public <T extends UiScreen> T open(ScreenId id, int width, int height) {
        UiScreen screen = (UiScreen) services.screens().create(id).orElseThrow();
        screen.attach(env());
        screen.init(width, height);
        // Move past the open transition so frames draw at full alpha (the recording canvas drops alpha-0 draws).
        ui.clock.advance(UiScreen.TRANSITION_MS * 4);
        frame(screen);
        return (T) screen;
    }

    /** Renders one frame (runs pending layout). */
    public void frame(UiScreen screen) {
        ui.frame(screen, -100, -100);
    }

    /** Full left click at the centre of a node through the screen's input path. */
    public void click(UiScreen screen, UiNode node) {
        frame(screen);
        Rect b = node.bounds();
        double x = b.centerX();
        double y = b.centerY();
        screen.mouseDown(x, y, Keys.MOUSE_LEFT);
        screen.mouseUp(x, y, Keys.MOUSE_LEFT);
        frame(screen);
    }

    /** Types text into the focused node. */
    public void type(UiScreen screen, String text) {
        text.codePoints().forEach(cp -> screen.charTyped(cp, 0));
    }

    /** Presses a key. */
    public void key(UiScreen screen, int key) {
        screen.keyDown(key, 0, 0);
        screen.keyUp(key, 0, 0);
    }

    /** The top-most open dialog, or {@code null}. */
    public Dialog topDialog(UiScreen screen) {
        var top = screen.context().popups().top();
        return top != null && top.node() instanceof Dialog d ? d : null;
    }

    /** Presses the confirm button (the last button) of the top dialog. */
    public void confirmDialog(UiScreen screen) {
        Dialog d = topDialog(screen);
        if (d == null) {
            throw new AssertionError("no dialog open");
        }
        List<UiNode> buttons = d.buttonRow().children();
        ((Button) buttons.get(buttons.size() - 1)).click(screen.context());
        frame(screen);
    }

    /** Presses the cancel button (the first button) of the top dialog. */
    public void cancelDialog(UiScreen screen) {
        Dialog d = topDialog(screen);
        if (d == null) {
            throw new AssertionError("no dialog open");
        }
        ((Button) d.buttonRow().children().get(0)).click(screen.context());
        frame(screen);
    }

    /** Records {@code n} sessions one day apart (oldest first), each {@code minutes} long. */
    public void recordSessions(int n, int minutes, double avgFps) {
        long now = clock.millis();
        for (int i = n - 1; i >= 0; i--) {
            long end = now - i * 86_400_000L;
            long playtime = minutes * 60_000L;
            services.statsStore().record(new SessionRecord(end - playtime, end, playtime, avgFps, (int) avgFps + 50,
                    List.of("World " + i), List.of(), 1000 + i * 100, 10 + i, 5 + i, i % 2));
        }
    }
}
