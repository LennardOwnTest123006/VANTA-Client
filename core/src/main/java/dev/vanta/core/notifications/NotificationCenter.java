package dev.vanta.core.notifications;

import dev.vanta.core.config.CoreLog;
import dev.vanta.core.i18n.Lang;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntSupplier;

/**
 * Queue of toasts: at most {@value #MAX_VISIBLE} visible at once, the rest wait; visible toasts expire after their
 * duration; a toast with the same title as one shown in the last second is dropped (dedupe); the last
 * {@value #HISTORY_SIZE} toasts stay in a history list for the statistics/about screens.
 * <p>
 * Call {@link #tick()} once per client tick (or per frame).
 */
public final class NotificationCenter {
    /** Maximum simultaneously visible toasts. */
    public static final int MAX_VISIBLE = 4;
    /** History length. */
    public static final int HISTORY_SIZE = 50;
    /** Window for duplicate suppression. */
    public static final long DEDUPE_WINDOW_MS = 1000L;

    /** Observer of posts and dismissals. */
    public interface Listener {
        /** A toast became visible. */
        default void onShown(Notification notification) {
        }

        /** A toast left the screen (expired or dismissed). */
        default void onDismissed(Notification notification) {
        }
    }

    private final Clock clock;
    private final IntSupplier defaultDurationMs;
    private final List<Notification> visible = new ArrayList<>();
    private final Deque<Notification> queue = new ArrayDeque<>();
    private final Deque<Notification> history = new ArrayDeque<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private long nextId = 1;
    private long lastPostAt;
    private String lastPostTitle;

    /**
     * @param clock             time source
     * @param defaultDurationMs supplies the configured default duration (settings)
     */
    public NotificationCenter(Clock clock, IntSupplier defaultDurationMs) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.defaultDurationMs = Objects.requireNonNull(defaultDurationMs, "defaultDurationMs");
    }

    /** Center with a fixed 4 s duration. */
    public NotificationCenter(Clock clock) {
        this(clock, () -> 4000);
    }

    // ---- posting -------------------------------------------------------------------------------------------------

    /** Posts a toast with the default duration. */
    public Optional<Notification> post(NotificationKind kind, String title, String body) {
        return post(kind, title, body, defaultDurationMs.getAsInt());
    }

    /** Posts a toast with an explicit duration (0 = sticky). */
    public Optional<Notification> post(NotificationKind kind, String title, String body, long durationMs) {
        return post(new Notification(0, kind, title, body, 0, durationMs, OptionalDouble.empty(), null));
    }

    /** Posts a progress toast (sticky until {@link #updateProgress} reaches 1). */
    public Optional<Notification> postProgress(NotificationKind kind, String title, String body) {
        return post(new Notification(0, kind, title, body, 0, 0, OptionalDouble.of(0.0), null));
    }

    /**
     * Posts a prepared toast. The id and creation time are assigned here.
     *
     * @return the stored toast, or empty when it was suppressed as a duplicate
     */
    public Optional<Notification> post(Notification draft) {
        Objects.requireNonNull(draft, "draft");
        long now = clock.millis();
        if (draft.title().equals(lastPostTitle) && now - lastPostAt < DEDUPE_WINDOW_MS) {
            CoreLog.debug("Suppressed duplicate notification '{}'", draft.title());
            return Optional.empty();
        }
        lastPostAt = now;
        lastPostTitle = draft.title();
        Notification stored = new Notification(nextId++, draft.kind(), draft.title(), draft.body(), 0,
                draft.durationMs(), draft.progress(), draft.iconId());
        if (visible.size() < MAX_VISIBLE) {
            show(stored, now);
            return Optional.of(visible.get(visible.size() - 1));
        }
        queue.addLast(stored);
        return Optional.of(stored);
    }

    private void show(Notification notification, long now) {
        Notification shown = notification.shownAt(now);
        visible.add(shown);
        remember(shown);
        for (Listener listener : listeners) {
            listener.onShown(shown);
        }
    }

    private void remember(Notification notification) {
        history.addFirst(notification);
        while (history.size() > HISTORY_SIZE) {
            history.removeLast();
        }
    }

    // ---- lifecycle -----------------------------------------------------------------------------------------------

    /** Expires visible toasts and promotes queued ones. */
    public void tick() {
        long now = clock.millis();
        List<Notification> expired = new ArrayList<>();
        for (Notification notification : visible) {
            if (notification.isExpired(now)) {
                expired.add(notification);
            }
        }
        for (Notification notification : expired) {
            remove(notification);
        }
        while (visible.size() < MAX_VISIBLE && !queue.isEmpty()) {
            show(queue.pollFirst(), now);
        }
    }

    /** Dismisses one toast (visible or queued). */
    public boolean dismiss(long id) {
        for (Notification notification : visible) {
            if (notification.id() == id) {
                remove(notification);
                tick();
                return true;
            }
        }
        return queue.removeIf(n -> n.id() == id);
    }

    /** Dismisses everything. */
    public void dismissAll() {
        for (Notification notification : new ArrayList<>(visible)) {
            remove(notification);
        }
        queue.clear();
    }

    /**
     * Updates a progress toast. Reaching 1.0 switches it to the default duration so it fades out.
     */
    public boolean updateProgress(long id, double progress) {
        for (int i = 0; i < visible.size(); i++) {
            Notification n = visible.get(i);
            if (n.id() == id) {
                Notification updated = n.withProgress(progress);
                if (updated.isComplete() && updated.isSticky()) {
                    updated = updated.withDuration(defaultDurationMs.getAsInt()).shownAt(clock.millis());
                }
                visible.set(i, updated);
                return true;
            }
        }
        return false;
    }

    /** Replaces the body text of a visible toast. */
    public boolean updateBody(long id, String body) {
        for (int i = 0; i < visible.size(); i++) {
            Notification n = visible.get(i);
            if (n.id() == id) {
                visible.set(i, n.withBody(body));
                return true;
            }
        }
        return false;
    }

    private void remove(Notification notification) {
        visible.remove(notification);
        for (Listener listener : listeners) {
            listener.onDismissed(notification);
        }
    }

    // ---- queries -------------------------------------------------------------------------------------------------

    /** Visible toasts, oldest first. */
    public List<Notification> visible() {
        return Collections.unmodifiableList(new ArrayList<>(visible));
    }

    /** Number of toasts waiting for a free slot. */
    public int queuedCount() {
        return queue.size();
    }

    /** Past toasts, newest first (max {@value #HISTORY_SIZE}). */
    public List<Notification> history() {
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    /** Clears the history list. */
    public void clearHistory() {
        history.clear();
    }

    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    // ---- standard notifications ----------------------------------------------------------------------------------

    public Optional<Notification> profileLoaded(String profileName) {
        return post(NotificationKind.SUCCESS, Lang.tr("vanta.notification.profile_loaded.title"),
                Lang.tr("vanta.notification.profile_loaded.body", profileName));
    }

    public Optional<Notification> settingsSaved() {
        return post(NotificationKind.SUCCESS, Lang.tr("vanta.notification.settings_saved.title"),
                Lang.tr("vanta.notification.settings_saved.body"));
    }

    public Optional<Notification> resourcePackEnabled(String packName) {
        return post(NotificationKind.INFO, Lang.tr("vanta.notification.resource_pack_enabled.title"),
                Lang.tr("vanta.notification.resource_pack_enabled.body", packName));
    }

    public Optional<Notification> performancePresetApplied(String presetName) {
        return post(NotificationKind.SUCCESS, Lang.tr("vanta.notification.performance_preset_applied.title"),
                Lang.tr("vanta.notification.performance_preset_applied.body", presetName));
    }

    /**
     * The one-click FPS boost finished; {@code restartRequired} when it installed Performance pack mods the game has
     * to restart for.
     */
    public Optional<Notification> boostApplied(boolean restartRequired) {
        return post(NotificationKind.SUCCESS, Lang.tr("vanta.notification.boost_applied.title"),
                Lang.tr(restartRequired ? "vanta.notification.boost_applied.restart"
                        : "vanta.notification.boost_applied.body"), restartRequired ? 8000 : 5000);
    }

    /** Boost FPS was clicked while its Performance pack install is still running; nothing new was started. */
    public Optional<Notification> boostAlreadyRunning() {
        return post(NotificationKind.INFO, Lang.tr("vanta.notification.boost_running.title"),
                Lang.tr("vanta.notification.boost_running.body"));
    }

    public Optional<Notification> hudPresetApplied(String presetName) {
        return post(NotificationKind.SUCCESS, Lang.tr("vanta.notification.hud_preset_applied.title"),
                Lang.tr("vanta.notification.hud_preset_applied.body", presetName));
    }

    public Optional<Notification> keybindConflict(int count) {
        return post(NotificationKind.WARNING, Lang.tr("vanta.notification.keybind_conflict.title"),
                Lang.tr("vanta.notification.keybind_conflict.body", count));
    }

    public Optional<Notification> screenshotSaved(Path file) {
        return post(NotificationKind.SUCCESS, Lang.tr("vanta.notification.screenshot_saved.title"),
                Lang.tr("vanta.notification.screenshot_saved.body", file.getFileName()));
    }

    public Optional<Notification> updateAvailable(String version) {
        return post(NotificationKind.INFO, Lang.tr("vanta.notification.update_available.title"),
                Lang.tr("vanta.notification.update_available.body", version), 0);
    }

    public Optional<Notification> renderDistanceSuggestion(int from, int to) {
        return post(NotificationKind.INFO, Lang.tr("vanta.notification.render_distance_suggestion.title"),
                Lang.tr("vanta.notification.render_distance_suggestion.body", from, to), 8000);
    }

    public Optional<Notification> renderDistanceApplied(int from, int to) {
        return post(NotificationKind.INFO, Lang.tr("vanta.notification.render_distance_applied.title"),
                Lang.tr("vanta.notification.render_distance_applied.body", from, to));
    }

    public Optional<Notification> profileImported(String profileName) {
        return post(NotificationKind.SUCCESS, Lang.tr("vanta.notification.profile_imported.title"),
                Lang.tr("vanta.notification.profile_imported.body", profileName));
    }

    public Optional<Notification> profileExported(Path file) {
        return post(NotificationKind.SUCCESS, Lang.tr("vanta.notification.profile_exported.title"),
                Lang.tr("vanta.notification.profile_exported.body", file.getFileName()));
    }

    public Optional<Notification> importFailed(String reason) {
        return post(NotificationKind.ERROR, Lang.tr("vanta.notification.import_failed.title"), reason, 8000);
    }

    public Optional<Notification> statisticsCleared() {
        return post(NotificationKind.INFO, Lang.tr("vanta.notification.statistics_cleared.title"),
                Lang.tr("vanta.notification.statistics_cleared.body"));
    }

    public Optional<Notification> hudToggled(boolean enabled) {
        return post(NotificationKind.INFO, Lang.tr("vanta.notification.hud_toggled.title"),
                Lang.tr(enabled ? "vanta.notification.hud_toggled.on" : "vanta.notification.hud_toggled.off"), 2000);
    }
}
