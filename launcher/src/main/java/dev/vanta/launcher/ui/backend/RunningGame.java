package dev.vanta.launcher.ui.backend;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * A started game as seen by the UI: exit code future, liveness and the log file. Production wraps
 * {@link dev.vanta.launcher.core.launch.GameProcess}; tests use a controllable fake.
 */
public interface RunningGame {

    /** @return completes with the process exit code */
    CompletableFuture<Integer> exitCode();

    /** @return whether the process is still running */
    boolean isAlive();

    /** @return process id */
    long pid();

    /** @return the game log file written by the launcher */
    Path logFile();

    /** Asks the game to exit. */
    void stop();

    /** Kills the game. */
    void kill();
}
