package dev.vanta.core.notifications.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.bridge.FakeGameBridge;
import dev.vanta.core.bridge.FakeKeybindBridge;
import dev.vanta.core.bridge.FakeOptionsBridge;
import dev.vanta.core.bridge.FakeResourcePackBridge;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.notifications.Notification;
import dev.vanta.core.notifications.NotificationCenter;
import dev.vanta.core.notifications.NotificationKind;
import dev.vanta.core.notifications.NotificationPosition;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.TestCanvas;
import dev.vanta.core.ui.widget.Toast;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NotificationOverlayTest {
    private static final int W = 400;
    private static final int H = 300;

    @TempDir
    Path dir;
    private VantaServices services;
    private MutableClock clock;
    private NotificationCenter center;
    private NotificationOverlay overlay;

    @BeforeEach
    void setUp() {
        clock = MutableClock.standard();
        services = VantaServices.create(VantaPaths.inGameDirectory(dir), new FakeGameBridge(), new FakeOptionsBridge(),
                new FakeKeybindBridge(), new FakeResourcePackBridge(), clock);
        services.load();
        center = services.notifications();
        overlay = new NotificationOverlay(services);
    }

    private TestCanvas render() {
        TestCanvas canvas = new TestCanvas(W, H);
        overlay.render(canvas, W, H, 0f);
        return canvas;
    }

    @Test
    void toastsSlideInAndStackFromTheTopRightByDefault() {
        center.post(NotificationKind.INFO, "First", "Body one");
        center.post(NotificationKind.SUCCESS, "Second", "");
        assertEquals(2, overlay.slots().size());
        TestCanvas initial = render();
        assertTrue(initial.texts().isEmpty(), "fully transparent at the first frame of the slide-in");
        clock.advance(NotificationOverlay.IN_MS + 50);
        TestCanvas settled = render();
        assertTrue(settled.hasText("First"));
        assertTrue(settled.hasText("Body one"));
        assertTrue(settled.hasText("Second"));
        Rect first = overlay.slots().get(0).toast().bounds();
        Rect second = overlay.slots().get(1).toast().bounds();
        assertEquals(W - NotificationOverlay.MARGIN - NotificationOverlay.WIDTH, first.x());
        assertEquals(NotificationOverlay.MARGIN, first.y());
        assertEquals(first.bottom() + NotificationOverlay.GAP, second.y());
        assertEquals(NotificationPosition.TOP_RIGHT, overlay.position());
        assertFalse(settled.fills().isEmpty());
    }

    @Test
    void bottomLeftStacksUpwardsFromTheBottomEdge() {
        services.settings().set(VantaSettings.GENERAL_NOTIFICATIONS_POSITION, NotificationPosition.BOTTOM_LEFT);
        center.post(NotificationKind.WARNING, "Low", "");
        center.post(NotificationKind.ERROR, "Boom", "x");
        clock.advance(500);
        render();
        Rect first = overlay.slots().get(0).toast().bounds();
        Rect second = overlay.slots().get(1).toast().bounds();
        assertEquals(NotificationOverlay.MARGIN, first.x());
        assertEquals(H - NotificationOverlay.MARGIN, first.bottom());
        assertEquals(first.y() - NotificationOverlay.GAP, second.bottom());
    }

    @Test
    void expiredToastsFadeOutAndAreRemoved() {
        center.post(NotificationKind.INFO, "Gone soon", "", 1_000);
        clock.advance(400);
        assertTrue(render().hasText("Gone soon"));
        clock.advance(700);
        services.tick();
        assertTrue(center.visible().isEmpty(), "the centre expired it");
        render();
        assertEquals(1, overlay.slots().size());
        assertTrue(overlay.slots().get(0).isExiting());
        clock.advance(NotificationOverlay.OUT_MS / 2);
        TestCanvas fading = render();
        TestCanvas.Text title = fading.texts().stream().filter(t -> t.text().equals("Gone soon")).findFirst().orElseThrow();
        int alpha = title.argb() >>> 24;
        assertTrue(alpha > 0 && alpha < 255, "half-way through the fade: " + alpha);
        clock.advance(NotificationOverlay.OUT_MS);
        render();
        assertTrue(overlay.slots().isEmpty());
    }

    @Test
    void dismissalFromTheCentreStartsTheExit() {
        Notification n = center.post(NotificationKind.INFO, "Dismiss me", "").orElseThrow();
        clock.advance(300);
        render();
        center.dismiss(n.id());
        assertTrue(overlay.slots().get(0).isExiting());
        clock.advance(NotificationOverlay.OUT_MS + 1);
        overlay.tick();
        assertTrue(overlay.slots().isEmpty());
    }

    @Test
    void progressToastsShowTheirProgressAndTimedOnesTheRemainingTime() {
        Notification progress = center.postProgress(NotificationKind.INFO, "Downloading", "").orElseThrow();
        clock.advance(300);
        render();
        assertEquals(0f, overlay.slots().get(0).toast().progress(), 1e-6);
        center.updateProgress(progress.id(), 0.5);
        render();
        assertEquals(0.5f, overlay.slots().get(0).toast().progress(), 1e-6);
        clock.advance(1_100);
        Notification timed = center.post(NotificationKind.SUCCESS, "Timed", "", 2_000).orElseThrow();
        clock.advance(500);
        render();
        NotificationOverlay.Slot slot = overlay.slots().stream().filter(s -> s.notification().id() == timed.id())
                .findFirst().orElseThrow();
        assertEquals(0.75f, slot.toast().progress(), 0.01f);
        assertEquals(-1f, NotificationOverlay.progressOf(
                new Notification(9, NotificationKind.INFO, "Sticky", "", clock.millis(), 0,
                        java.util.OptionalDouble.empty(), null), clock.millis()));
    }

    @Test
    void reducedMotionSnapsAndReducedTransparencyKeepsRendering() {
        services.settings().set(VantaSettings.ACCESSIBILITY_REDUCED_MOTION, true);
        services.settings().set(VantaSettings.ACCESSIBILITY_REDUCED_TRANSPARENCY, true);
        overlay.tick();
        assertTrue(overlay.theme().reducedMotion());
        assertTrue(overlay.theme().reducedTransparency());
        center.post(NotificationKind.INFO, "Instant", "");
        TestCanvas canvas = render();
        assertTrue(canvas.hasText("Instant"), "no slide-in under reduced motion");
        TestCanvas.Text title = canvas.texts().stream().filter(t -> t.text().equals("Instant")).findFirst().orElseThrow();
        assertEquals(255, title.argb() >>> 24);
    }

    @Test
    void neverShowsMoreThanTheCentreAllows() {
        for (int i = 0; i < 6; i++) {
            center.post(NotificationKind.INFO, "Toast " + i, "");
        }
        assertEquals(NotificationCenter.MAX_VISIBLE, center.visible().size());
        clock.advance(500);
        TestCanvas canvas = render();
        assertEquals(NotificationCenter.MAX_VISIBLE, overlay.slots().size());
        assertTrue(canvas.hasText("Toast 3"));
        assertFalse(canvas.hasText("Toast 4"));
    }

    @Test
    void iconAndKindMappings() {
        assertEquals(Icons.DOWNLOAD, NotificationOverlay.iconFor("download"));
        assertEquals(Icons.SUCCESS, NotificationOverlay.iconFor("success"));
        assertNull(NotificationOverlay.iconFor("not-an-icon"));
        assertNull(NotificationOverlay.iconFor(""));
        assertEquals(Toast.Kind.ERROR, NotificationOverlay.toastKind(NotificationKind.ERROR));
        assertEquals(Toast.Kind.WARNING, NotificationOverlay.toastKind(NotificationKind.WARNING));
        assertEquals(Toast.Kind.SUCCESS, NotificationOverlay.toastKind(NotificationKind.SUCCESS));
        assertEquals(Toast.Kind.INFO, NotificationOverlay.toastKind(NotificationKind.INFO));
    }

    @Test
    void existingToastsAreAdoptedAndDisposeStopsListening() {
        center.post(NotificationKind.INFO, "Early", "");
        NotificationOverlay late = new NotificationOverlay(services);
        assertEquals(1, late.slots().size());
        late.dispose();
        clock.advance(1_100);
        center.post(NotificationKind.INFO, "After dispose", "");
        assertEquals(List.of("Early"), late.slots().stream().map(s -> s.notification().title()).toList());
    }
}
