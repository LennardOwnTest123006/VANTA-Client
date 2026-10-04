package dev.vanta.launcher.ui.prefs;

import com.google.gson.JsonParseException;
import dev.vanta.launcher.core.util.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads and saves {@link UiPreferences}. Tolerant reads (defaults on any problem), atomic writes.
 */
public final class UiPreferencesStore {

    /** File name inside the data directory. */
    public static final String FILE_NAME = "ui-preferences.json";

    private static final Logger LOG = Logger.getLogger("VANTA.UI");

    private final Path file;

    /**
     * @param file preferences file
     */
    public UiPreferencesStore(final Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    /**
     * @param dataDir launcher data directory
     * @return store for {@code <dataDir>/ui-preferences.json}
     */
    public static UiPreferencesStore in(final Path dataDir) {
        return new UiPreferencesStore(dataDir.resolve(FILE_NAME));
    }

    /** @return the file */
    public Path file() {
        return file;
    }

    /**
     * @return stored preferences or defaults
     */
    public UiPreferences load() {
        if (!Files.isRegularFile(file)) {
            return UiPreferences.defaults();
        }
        try {
            return Json.parse(Files.readString(file, StandardCharsets.UTF_8), UiPreferences.class);
        } catch (IOException | JsonParseException | IllegalArgumentException e) {
            LOG.log(Level.FINE, "ui-preferences.json unreadable, using defaults: {0}", e.toString());
            return UiPreferences.defaults();
        }
    }

    /**
     * Saves preferences atomically. Failures are logged, never thrown: preferences are not worth an error dialog.
     *
     * @param prefs preferences
     * @return whether the write succeeded
     */
    public boolean save(final UiPreferences prefs) {
        try {
            Json.write(file, prefs);
            return true;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not save ui-preferences.json: {0}", e.toString());
            return false;
        }
    }
}
