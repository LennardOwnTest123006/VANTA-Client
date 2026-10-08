package dev.vanta.core.ai;

import dev.vanta.core.i18n.LangKeyed;
import java.util.Locale;

/**
 * Where the Local AI stands, from "this computer cannot run it" to "answering a question".
 */
public enum LocalAiStatus implements LangKeyed {
    /** llama.cpp publishes no CPU build for this os/arch. */
    UNSUPPORTED_PLATFORM,
    /** Nothing installed yet. */
    NOT_INSTALLED,
    /** Some files exist but the install is incomplete or fails verification. */
    PARTIAL,
    /** Runtime and model verified; the server is not running. */
    INSTALLED,
    /** The server process is starting and loading the model. */
    STARTING,
    /** The server answers {@code /health}; ready for a question. */
    READY,
    /** A question is being answered. */
    BUSY,
    /** The install, the manifest or the server failed; the reason is reported next to the status. */
    FAILED;

    /** Lower-case id used in lang keys. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String langKey() {
        return "vanta.localai.status." + id();
    }

    /** True when runtime and model are on disk and verified (whether or not the server runs). */
    public boolean isInstalled() {
        return this == INSTALLED || this == STARTING || this == READY || this == BUSY;
    }

    /** True while the server process is alive. */
    public boolean isRunning() {
        return this == STARTING || this == READY || this == BUSY;
    }
}
