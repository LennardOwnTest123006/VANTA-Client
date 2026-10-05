package dev.vanta.launcher.core.launch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * "Restart game" from inside the game: when the launcher starts the game itself it passes
 * {@code -D}{@value #PROPERTY}{@code =true}; the VANTA Client then offers a "Restart game" button that writes
 * {@code <gameDir>/config/vanta/restart.request} and closes the game. After the game exited the launcher deletes the
 * file and starts the game again ({@link #consume(Path)}). Without the launcher (Minecraft Launcher profile) the
 * property is absent and the client does not offer the button.
 */
public final class RestartRequest {

    /** System property the game receives when the launcher can restart it. */
    public static final String PROPERTY = "vanta.launcher.restartable";
    /** File name of the marker below {@code config/vanta/}. */
    public static final String FILE_NAME = "restart.request";

    private RestartRequest() {
    }

    /**
     * @param gameDir the game directory (the VANTA instance)
     * @return {@code <gameDir>/config/vanta/restart.request}
     */
    public static Path markerFile(final Path gameDir) {
        return gameDir.resolve("config").resolve("vanta").resolve(FILE_NAME);
    }

    /**
     * Deletes the marker when it exists.
     *
     * @param gameDir the game directory
     * @return whether a restart was requested (the marker existed and was deleted)
     * @throws IOException when the marker exists but cannot be deleted (no restart then: it would repeat forever)
     */
    public static boolean consume(final Path gameDir) throws IOException {
        final Path marker = markerFile(gameDir);
        if (!Files.exists(marker)) {
            return false;
        }
        Files.delete(marker);
        return true;
    }
}
