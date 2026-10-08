package dev.vanta.launcher.core.ai;

/**
 * Install state of the launcher-managed Local AI, as the launcher sees it (the client has its own, richer runtime
 * states: starting, ready, busy).
 */
public enum LocalAiState {
    /** This build has no usable Local AI manifest, so nothing can be installed. */
    UNAVAILABLE("not available in this build"),
    /** The manifest lists no runtime for this operating system and CPU architecture. */
    UNSUPPORTED_PLATFORM("not available for this system"),
    /** Nothing is installed. */
    NOT_INSTALLED("not installed"),
    /** Files exist but the install is incomplete or a file changed; "Install" completes it. */
    PARTIAL("partially installed"),
    /** Runtime and model are in place and matched their digests when last checked. */
    INSTALLED("installed");

    private final String label;

    LocalAiState(final String label) {
        this.label = label;
    }

    /** @return short English label for the command line */
    public String label() {
        return label;
    }
}
