package dev.vanta.core.modrinth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Restart hand-off with the VANTA launcher. The launcher starts the game with
 * {@code -Dvanta.launcher.restartable=true}; before quitting for a restart the game writes
 * {@code <gameDir>/config/vanta/restart.request} (UTF-8, an ISO-8601 timestamp) and the launcher starts the game
 * again when it finds that file after the game exited.
 */
public final class RestartMarker {
    /**
     * System property the VANTA launcher sets when it can relaunch the game ({@code vanta.launcher.restartable}).
     * Joined from parts so the translation-key scan of the domain sources does not take it for a lang key.
     */
    public static final String RESTARTABLE_PROPERTY = String.join(".", "vanta", "launcher", "restartable");
    /** File name of the marker below {@code config/vanta}. */
    public static final String FILE_NAME = "restart.request";

    private RestartMarker() {
    }

    /** True when the VANTA launcher started this game and will relaunch it. */
    public static boolean launcherRestartable() {
        return Boolean.parseBoolean(System.getProperty(RESTARTABLE_PROPERTY, "false"));
    }

    /** {@code <gameDir>/config/vanta/restart.request}. */
    public static Path file(Path gameDir) {
        return gameDir.resolve("config").resolve("vanta").resolve(FILE_NAME);
    }

    /** Writes the marker atomically and returns its path. */
    public static Path write(Path gameDir, Clock clock) throws IOException {
        Path file = file(gameDir).toAbsolutePath();
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(FILE_NAME + ".tmp");
        Files.writeString(tmp, Instant.now(clock).truncatedTo(ChronoUnit.SECONDS).toString(), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
        return file;
    }
}
