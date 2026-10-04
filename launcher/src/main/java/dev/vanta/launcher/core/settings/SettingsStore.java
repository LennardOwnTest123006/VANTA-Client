package dev.vanta.launcher.core.settings;

import com.google.gson.JsonParseException;
import dev.vanta.launcher.core.util.Json;
import dev.vanta.launcher.core.util.SystemMemory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads and saves {@link LauncherSettings}. Reads are tolerant: a missing or corrupt file yields defaults (a corrupt
 * file is kept as {@code settings.json.corrupt} for inspection); writes are atomic.
 */
public final class SettingsStore {

    private static final Logger LOG = Logger.getLogger("VANTA.Settings");

    private final Path file;
    private final long totalRamMb;

    /**
     * @param file settings file
     */
    public SettingsStore(final Path file) {
        this(file, SystemMemory.totalMemoryMb().orElse(0L));
    }

    /**
     * @param file       settings file
     * @param totalRamMb physical memory used for the heap default
     */
    public SettingsStore(final Path file, final long totalRamMb) {
        this.file = Objects.requireNonNull(file, "file");
        this.totalRamMb = totalRamMb;
    }

    /** @return the settings file */
    public Path file() {
        return file;
    }

    /** @return defaults for this machine */
    public LauncherSettings defaults() {
        return LauncherSettings.defaults(totalRamMb);
    }

    /**
     * Loads settings, falling back to defaults.
     *
     * @return settings
     */
    public LauncherSettings load() {
        if (!Files.isRegularFile(file)) {
            return defaults();
        }
        try {
            final String text = Files.readString(file, StandardCharsets.UTF_8);
            return migrate(Json.parse(text, LauncherSettings.class));
        } catch (IOException | JsonParseException | IllegalArgumentException e) {
            LOG.log(Level.WARNING, "settings.json is unreadable, using defaults: {0}", e.toString());
            try {
                Files.move(file, file.resolveSibling(file.getFileName() + ".corrupt"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveFailure) {
                LOG.log(Level.FINE, "Could not preserve corrupt settings file", moveFailure);
            }
            return defaults();
        }
    }

    /**
     * Saves settings atomically.
     *
     * @param settings settings
     * @throws IOException on failure
     */
    public void save(final LauncherSettings settings) throws IOException {
        Json.write(file, settings);
    }

    private LauncherSettings migrate(final LauncherSettings loaded) {
        // Schema version 1 is the first version; future migrations go here.
        if (loaded.schemaVersion() > LauncherSettings.SCHEMA_VERSION) {
            LOG.log(Level.WARNING, "settings.json has schema {0}, newer than supported {1}; unknown fields are ignored",
                new Object[] {loaded.schemaVersion(), LauncherSettings.SCHEMA_VERSION});
        }
        return loaded;
    }
}
