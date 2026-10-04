package dev.vanta.launcher.ui.model;

import dev.vanta.launcher.ui.testutil.FakeBackend;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogBufferTest {

    @Test
    void ringBufferKeepsTheNewestLines() {
        final LogBuffer buffer = new LogBuffer(3, FakeBackend.CLOCK);
        final List<LogLine> seen = new ArrayList<>();
        buffer.addListener(seen::add);
        for (int i = 1; i <= 5; i++) {
            buffer.append("line " + i);
        }
        assertEquals(3, buffer.size());
        assertEquals(List.of("line 3", "line 4", "line 5"), buffer.snapshot().stream().map(LogLine::text).toList());
        assertEquals(5, seen.size());
        assertEquals(5, seen.get(4).sequence());
        assertEquals(List.of("line 4", "line 5"), buffer.tail(2).stream().map(LogLine::text).toList());
        buffer.clear();
        assertEquals(0, buffer.size());
        assertTrue(buffer.tail(10).isEmpty());
    }

    @Test
    void levelsAreGuessedFromGameOutput() {
        assertEquals(LogLevel.INFO, LogLevel.parse("[12:00:01] [main/INFO]: Loading Minecraft 1.21.11"));
        assertEquals(LogLevel.WARN, LogLevel.parse("[12:00:05] [Render thread/WARN]: Shader not found"));
        assertEquals(LogLevel.ERROR, LogLevel.parse("[12:00:05] [Render thread/ERROR]: Boom"));
        assertEquals(LogLevel.ERROR, LogLevel.parse("[12:00:05] [Render thread/FATAL]: Boom"));
        assertEquals(LogLevel.DEBUG, LogLevel.parse("[12:00:05] [Worker-1/DEBUG] (FabricLoader) scanning"));
        assertEquals(LogLevel.ERROR, LogLevel.parse("java.lang.IllegalStateException: no"));
        assertEquals(LogLevel.ERROR, LogLevel.parse("\tat net.minecraft.client.main.Main.main(Main.java:200)"));
        assertEquals(LogLevel.ERROR, LogLevel.parse("Caused by: java.io.IOException"));
        assertEquals(LogLevel.WARN, LogLevel.parse("WARNING: A terminally deprecated method in java.lang.System has been called"));
        assertEquals(LogLevel.INFO, LogLevel.parse("FAKE_GAME_START"));
        assertEquals(LogLevel.INFO, LogLevel.parse(""));
        assertEquals(LogLevel.ERROR, LogLevel.fromJul(Level.SEVERE));
        assertEquals(LogLevel.WARN, LogLevel.fromJul(Level.WARNING));
        assertEquals(LogLevel.INFO, LogLevel.fromJul(Level.INFO));
        assertEquals(LogLevel.DEBUG, LogLevel.fromJul(Level.FINE));
    }

    @Test
    void lineMatchingIsCaseInsensitive() {
        final LogBuffer buffer = new LogBuffer(10, FakeBackend.CLOCK);
        final LogLine line = buffer.append("Downloaded Fabric API");
        assertTrue(line.matches("fabric"));
        assertTrue(line.matches(""));
        assertFalse(line.matches("sodium"));
    }

    @Test
    void julBridgeForwardsRedactedRecords() {
        final LogBuffer buffer = new LogBuffer(10, FakeBackend.CLOCK);
        final Logger logger = Logger.getLogger("VANTA.Test." + System.nanoTime());
        logger.setUseParentHandlers(false);
        final JulLogBridge bridge = new JulLogBridge(buffer).attach(logger);
        dev.vanta.launcher.core.log.Redactor.global().register("super-secret-token");
        logger.log(Level.INFO, "Signed in as {0}", "Nova");
        logger.log(Level.WARNING, "token=super-secret-token");
        logger.log(Level.FINE, "fine detail");
        logger.log(Level.SEVERE, "failed", new IllegalStateException("boom"));
        assertEquals(4, buffer.size());
        final List<LogLine> lines = buffer.snapshot();
        assertTrue(lines.get(0).text().startsWith("INFO ["), lines.get(0).text());
        assertTrue(lines.get(0).text().endsWith("Signed in as Nova"));
        assertEquals(LogLevel.WARN, lines.get(1).level());
        assertFalse(lines.get(1).text().contains("super-secret-token"));
        assertTrue(lines.get(1).text().contains("[redacted]"));
        assertEquals(LogLevel.DEBUG, lines.get(2).level());
        assertEquals(LogLevel.ERROR, lines.get(3).level());
        assertTrue(lines.get(3).text().contains("IllegalStateException: boom"));
        bridge.detach();
        logger.info("after detach");
        assertEquals(4, buffer.size());
        final LogRecord record = new LogRecord(Level.INFO, "plain");
        record.setLoggerName(null);
        assertEquals("INFO [Launcher] plain", JulLogBridge.format(record));
    }
}
