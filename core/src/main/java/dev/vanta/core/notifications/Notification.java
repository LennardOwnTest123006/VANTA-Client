package dev.vanta.core.notifications;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * One toast.
 *
 * @param id         unique, increasing
 * @param kind       severity
 * @param title      short title (already translated)
 * @param body       one or two lines of detail, may be empty
 * @param createdAt  epoch millis when it became visible (0 while queued)
 * @param durationMs how long it stays visible once shown; 0 = until dismissed
 * @param progress   0–1 for progress toasts
 * @param iconId     icon id (defaults to the kind's icon)
 */
public record Notification(long id, NotificationKind kind, String title, String body, long createdAt, long durationMs,
                           OptionalDouble progress, String iconId) {
    public Notification {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(title, "title");
        body = body == null ? "" : body;
        durationMs = Math.max(0, durationMs);
        progress = progress == null ? OptionalDouble.empty() : progress;
        if (progress.isPresent()) {
            progress = OptionalDouble.of(Math.max(0.0, Math.min(1.0, progress.getAsDouble())));
        }
        iconId = iconId == null || iconId.isBlank() ? kind.iconId() : iconId;
    }

    /** True for toasts that stay until dismissed or completed. */
    public boolean isSticky() {
        return durationMs == 0;
    }

    /** Epoch millis when the toast should disappear, or {@link Long#MAX_VALUE} for sticky ones. */
    public long expiresAt() {
        return isSticky() || createdAt == 0 ? Long.MAX_VALUE : createdAt + durationMs;
    }

    /** True once the display time has elapsed. */
    public boolean isExpired(long nowMs) {
        return nowMs >= expiresAt();
    }

    /** Fraction of the display time already elapsed (0–1; 0 for sticky). */
    public double elapsedFraction(long nowMs) {
        if (isSticky() || createdAt == 0) {
            return 0;
        }
        return Math.max(0.0, Math.min(1.0, (nowMs - createdAt) / (double) durationMs));
    }

    /** True when this is a progress toast that has reached 100 %. */
    public boolean isComplete() {
        return progress.isPresent() && progress.getAsDouble() >= 1.0;
    }

    Notification shownAt(long now) {
        return new Notification(id, kind, title, body, now, durationMs, progress, iconId);
    }

    Notification withProgress(double value) {
        return new Notification(id, kind, title, body, createdAt, durationMs, OptionalDouble.of(value), iconId);
    }

    Notification withDuration(long ms) {
        return new Notification(id, kind, title, body, createdAt, ms, progress, iconId);
    }

    Notification withBody(String newBody) {
        return new Notification(id, kind, title, newBody, createdAt, durationMs, progress, iconId);
    }
}
