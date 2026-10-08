package dev.vanta.core.ai;

import dev.vanta.core.i18n.LangKeyed;
import java.util.Locale;

/**
 * The steps of a Local AI install in order, as shown by the setup screen and the launcher. The installer reports the
 * first five; {@link LocalAiService} adds the last two when it starts the server for the first time.
 */
public enum InstallStep implements LangKeyed {
    /** "Checking Local AI": platform, existing files, manifest. */
    CHECKING,
    /** "Downloading Local AI runtime". */
    DOWNLOADING_RUNTIME,
    /** "Downloading Local AI model". */
    DOWNLOADING_MODEL,
    /** "Verifying files": full SHA-256 of what was downloaded. */
    VERIFYING,
    /** "Installing": extracting the archive and writing {@code installed.json}. */
    INSTALLING,
    /** "Initializing Local AI": the server starts and loads the model. */
    INITIALIZING,
    /** "Vanta Nexus ready". */
    READY;

    /** Lower-case id used in lang keys. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String langKey() {
        return "vanta.localai.step." + id();
    }

    /** 1-based position for "step 2 of 7" labels. */
    public int number() {
        return ordinal() + 1;
    }

    /** Number of steps. */
    public static int count() {
        return values().length;
    }
}
