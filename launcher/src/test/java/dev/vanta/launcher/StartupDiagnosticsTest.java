package dev.vanta.launcher;

import dev.vanta.launcher.core.log.LauncherLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The start-up log and {@code startup-error.txt}: what a player without a console can send in.
 */
class StartupDiagnosticsTest {

    @TempDir
    Path tmp;

    @AfterEach
    void closeLog() {
        LauncherLog.close();
    }

    @Test
    void beginOpensTheLogBeforeAnythingElseAndRecordsTheEnvironment() throws Exception {
        final Path logs = tmp.resolve("data/logs");
        Files.createDirectories(logs);
        Files.writeString(logs.resolve(StartupDiagnostics.ERROR_FILE), "old failure");
        final StartupDiagnostics diagnostics = new StartupDiagnostics(logs);
        diagnostics.begin();
        for (Handler h : LauncherLog.root().getHandlers()) {
            h.flush();
        }
        final String log = Files.readString(logs.resolve("launcher-0.log"), StandardCharsets.UTF_8);
        assertTrue(log.contains("VANTA Launcher " + LauncherVersion.VERSION + " starting"), log);
        assertTrue(log.contains("java.version = "), log);
        assertTrue(log.contains("javafx.platform (build) = " + LauncherVersion.JAVAFX_PLATFORM), log);
        assertTrue(Files.notExists(logs.resolve(StartupDiagnostics.ERROR_FILE)), "the error file always describes the latest start");
        assertEquals("old failure", Files.readString(logs.resolve("startup-error.previous.txt")));
    }

    @Test
    void writeErrorKeepsMessageEnvironmentAndStackTrace() throws Exception {
        final StartupDiagnostics diagnostics = new StartupDiagnostics(tmp.resolve("logs"));
        final Path file = diagnostics.writeError("The launcher window could not be built", new IllegalStateException("no suitable pipeline found"))
            .orElseThrow();
        assertEquals(tmp.resolve("logs").resolve(StartupDiagnostics.ERROR_FILE), file);
        final String text = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(text.startsWith("VANTA Launcher " + LauncherVersion.VERSION + " could not start"), text);
        assertTrue(text.contains("The launcher window could not be built"));
        assertTrue(text.contains("os.name = "));
        assertTrue(text.contains("java.lang.IllegalStateException: no suitable pipeline found"));
        assertTrue(text.contains("at dev.vanta.launcher.StartupDiagnosticsTest"), "full stack trace");
        assertTrue(text.contains("https://github.com/LennardOwnTest123006/VANTA-Client/issues"));
    }

    @Test
    void anUnwritableDataDirectoryFallsBackToTheTemporaryDirectory() throws Exception {
        final Path blocker = tmp.resolve("not-a-dir");
        Files.writeString(blocker, "file in the way");
        final Path file = new StartupDiagnostics(blocker.resolve("logs")).writeError("x", null).orElseThrow();
        assertEquals(Path.of(System.getProperty("java.io.tmpdir")).resolve("vanta-launcher-" + StartupDiagnostics.ERROR_FILE), file);
        Files.deleteIfExists(file);
    }
}
