package dev.vanta.launcher.core.ai;

import java.io.IOException;
import java.util.Objects;

/**
 * The Local AI cannot be installed on this launcher: the build carries no usable manifest
 * ({@link LocalAiState#UNAVAILABLE}) or the manifest lists no runtime for this platform
 * ({@link LocalAiState#UNSUPPORTED_PLATFORM}).
 */
public final class LocalAiUnavailableException extends IOException {

    private static final long serialVersionUID = 1L;

    private final LocalAiState state;

    /**
     * @param state   {@link LocalAiState#UNAVAILABLE} or {@link LocalAiState#UNSUPPORTED_PLATFORM}
     * @param message explanation
     */
    public LocalAiUnavailableException(final LocalAiState state, final String message) {
        super(message);
        this.state = Objects.requireNonNull(state, "state");
    }

    /** @return why the Local AI is unavailable */
    public LocalAiState state() {
        return state;
    }
}
