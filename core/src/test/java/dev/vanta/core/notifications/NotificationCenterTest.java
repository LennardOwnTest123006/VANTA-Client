package dev.vanta.core.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.config.MutableClock;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NotificationCenterTest {
    private final MutableClock clock = MutableClock.standard();
    private final NotificationCenter center = new NotificationCenter(clock, () -> 4000);

    @Test
    void postsExpireAfterDuration() {
        Notification n = center.post(NotificationKind.INFO, "Hello", "World").orElseThrow();
        assertEquals(1, center.visible().size());
        assertEquals(clock.millis(), n.createdAt());
        assertEquals(4000, n.durationMs());
        clock.advance(3999);
        center.tick();
        assertEquals(1, center.visible().size());
        assertEquals(0.99975, center.visible().get(0).elapsedFraction(clock.millis()), 1e-6);
        clock.advance(1);
        center.tick();
        assertTrue(center.visible().isEmpty());
        assertEquals(1, center.history().size());
    }

    @Test
    void dedupesSameTitleWithinOneSecond() {
        assertTrue(center.post(NotificationKind.INFO, "Saved", "a").isPresent());
        clock.advance(500);
        assertTrue(center.post(NotificationKind.INFO, "Saved", "b").isEmpty(), "duplicate suppressed");
        assertTrue(center.post(NotificationKind.INFO, "Other", "c").isPresent());
        clock.advance(1000);
        assertTrue(center.post(NotificationKind.INFO, "Saved", "d").isPresent(), "allowed again after 1 s");
        assertEquals(3, center.visible().size());
    }

    @Test
    void queuesBeyondFourVisibleAndPromotes() {
        for (int i = 0; i < 6; i++) {
            clock.advance(1100);
            center.post(NotificationKind.SUCCESS, "Toast " + i, "", 0);
        }
        assertEquals(NotificationCenter.MAX_VISIBLE, center.visible().size());
        assertEquals(2, center.queuedCount());
        assertTrue(center.dismiss(center.visible().get(0).id()));
        assertEquals(4, center.visible().size(), "queued toast promoted on dismiss");
        assertEquals(1, center.queuedCount());
        assertEquals("Toast 4", center.visible().get(3).title());
        assertEquals(clock.millis(), center.visible().get(3).createdAt(), "shown time set at promotion");
        center.dismissAll();
        assertTrue(center.visible().isEmpty());
        assertEquals(0, center.queuedCount());
    }

    @Test
    void stickyAndProgressToasts() {
        Notification sticky = center.post(NotificationKind.WARNING, "Update", "", 0).orElseThrow();
        clock.advance(100_000);
        center.tick();
        assertEquals(1, center.visible().size(), "sticky toast never expires");
        assertTrue(sticky.isSticky());
        clock.advance(1100);
        Notification progress = center.postProgress(NotificationKind.INFO, "Download", "").orElseThrow();
        assertTrue(center.updateProgress(progress.id(), 0.5));
        assertEquals(0.5, center.visible().get(1).progress().orElseThrow());
        assertTrue(center.updateProgress(progress.id(), 1.0));
        assertTrue(center.visible().get(1).isComplete());
        assertEquals(4000, center.visible().get(1).durationMs(), "completion starts the fade-out timer");
        clock.advance(4000);
        center.tick();
        assertEquals(1, center.visible().size());
        assertTrue(center.updateBody(sticky.id(), "new body"));
        assertEquals("new body", center.visible().get(0).body());
        assertFalse(center.updateProgress(999, 0.1));
    }

    @Test
    void historyIsBoundedAndListenersFire() {
        List<String> events = new ArrayList<>();
        center.addListener(new NotificationCenter.Listener() {
            @Override
            public void onShown(Notification notification) {
                events.add("shown:" + notification.title());
            }

            @Override
            public void onDismissed(Notification notification) {
                events.add("dismissed:" + notification.title());
            }
        });
        for (int i = 0; i < 60; i++) {
            clock.advance(5000);
            center.tick();
            center.post(NotificationKind.INFO, "N" + i, "");
        }
        assertEquals(NotificationCenter.HISTORY_SIZE, center.history().size());
        assertEquals("N59", center.history().get(0).title(), "newest first");
        assertTrue(events.contains("shown:N0"));
        assertTrue(events.contains("dismissed:N0"));
        center.clearHistory();
        assertTrue(center.history().isEmpty());
    }

    @Test
    void standardFactoriesUseTranslations() {
        Notification n = center.profileLoaded("PvP").orElseThrow();
        assertEquals(NotificationKind.SUCCESS, n.kind());
        assertEquals("Profile loaded", n.title());
        assertEquals("“PvP” is now active", n.body());
        clock.advance(1100);
        assertEquals("3 keys share a binding", center.keybindConflict(3).orElseThrow().body());
        clock.advance(1100);
        assertEquals("shot.png", center.screenshotSaved(Path.of("a", "shot.png")).orElseThrow().body());
        clock.advance(1100);
        Optional<Notification> update = center.updateAvailable("1.1.0");
        assertTrue(update.orElseThrow().isSticky());
        clock.advance(1100);
        assertEquals("success", center.settingsSaved().orElseThrow().iconId());
        assertEquals(Long.MAX_VALUE, update.orElseThrow().expiresAt());
    }
}
