package dev.vanta.launcher.ui.model;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Notifications shown bottom-right. The model only holds the list; the view animates and auto-dismisses.
 * All methods must be called on the UI thread.
 */
public final class ToastModel {

    /** Visual kind of a toast. */
    public enum Kind {
        /** Neutral information. */
        INFO,
        /** Something finished successfully. */
        SUCCESS,
        /** Something needs attention. */
        WARNING,
        /** Something failed. */
        ERROR
    }

    /**
     * One notification.
     *
     * @param id      unique id
     * @param kind    kind
     * @param title   short title
     * @param message optional body (empty allowed)
     * @param ttl     how long the view keeps it visible
     */
    public record Toast(long id, Kind kind, String title, String message, Duration ttl) {
        public Toast {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(title, "title");
            message = message == null ? "" : message;
            ttl = ttl == null ? Duration.ofSeconds(6) : ttl;
        }
    }

    private static final int MAX_VISIBLE = 4;

    private final ObservableList<Toast> toasts = FXCollections.observableArrayList();
    private final AtomicLong ids = new AtomicLong();

    /** @return visible toasts (oldest first) */
    public ObservableList<Toast> toasts() {
        return toasts;
    }

    /**
     * Shows a toast.
     *
     * @param kind    kind
     * @param title   title
     * @param message message
     * @return the toast
     */
    public Toast show(final Kind kind, final String title, final String message) {
        final Duration ttl = kind == Kind.ERROR ? Duration.ofSeconds(12) : Duration.ofSeconds(6);
        final Toast toast = new Toast(ids.incrementAndGet(), kind, title, message, ttl);
        toasts.add(toast);
        while (toasts.size() > MAX_VISIBLE) {
            toasts.remove(0);
        }
        return toast;
    }

    /**
     * @param title   title
     * @param message message
     * @return toast
     */
    public Toast info(final String title, final String message) {
        return show(Kind.INFO, title, message);
    }

    /**
     * @param title   title
     * @param message message
     * @return toast
     */
    public Toast success(final String title, final String message) {
        return show(Kind.SUCCESS, title, message);
    }

    /**
     * @param title   title
     * @param message message
     * @return toast
     */
    public Toast warning(final String title, final String message) {
        return show(Kind.WARNING, title, message);
    }

    /**
     * @param title   title
     * @param message message
     * @return toast
     */
    public Toast error(final String title, final String message) {
        return show(Kind.ERROR, title, message);
    }

    /**
     * Removes a toast.
     *
     * @param id toast id
     */
    public void dismiss(final long id) {
        toasts.removeIf(t -> t.id() == id);
    }

    /** Removes all toasts. */
    public void clear() {
        toasts.clear();
    }
}
