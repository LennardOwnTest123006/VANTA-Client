package dev.vanta.launcher.core.net;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cooperative cancellation shared between the UI and long running operations.
 */
public final class CancellationToken {

    /** A token that can never be cancelled. */
    public static final CancellationToken NONE = new CancellationToken(true);

    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final boolean immutable;

    /** Creates a cancellable token. */
    public CancellationToken() {
        this(false);
    }

    private CancellationToken(final boolean immutable) {
        this.immutable = immutable;
    }

    /** Requests cancellation. Idempotent; ignored for {@link #NONE}. */
    public void cancel() {
        if (!immutable) {
            cancelled.set(true);
        }
    }

    /** @return whether cancellation was requested */
    public boolean isCancelled() {
        return cancelled.get();
    }

    /**
     * Throws when cancelled.
     *
     * @throws CancellationException when cancelled
     */
    public void throwIfCancelled() {
        if (isCancelled()) {
            throw new CancellationException("Operation cancelled");
        }
    }
}
