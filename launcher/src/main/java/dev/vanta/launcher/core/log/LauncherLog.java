package dev.vanta.launcher.core.log;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * {@code java.util.logging} setup for the launcher: a rotating {@code logs/launcher-N.log} (5 files × 2 MiB)
 * and a redacting formatter so no access token ever lands on disk.
 */
public final class LauncherLog {

    /** Root logger name of the launcher. */
    public static final String ROOT = "VANTA";

    private static final int LIMIT_BYTES = 2 * 1024 * 1024;
    private static final int FILE_COUNT = 5;
    private static Handler fileHandler;

    private LauncherLog() {
    }

    /**
     * Configures the file handler. Safe to call multiple times; later calls replace the handler.
     *
     * @param logsDir directory for {@code launcher-N.log}
     * @throws IOException when the directory or file cannot be created
     */
    public static synchronized void init(final Path logsDir) throws IOException {
        Files.createDirectories(logsDir);
        final Logger root = Logger.getLogger(ROOT);
        if (fileHandler != null) {
            root.removeHandler(fileHandler);
            fileHandler.close();
        }
        final FileHandler handler = new FileHandler(logsDir.resolve("launcher-%g.log").toString(), LIMIT_BYTES, FILE_COUNT, true);
        handler.setFormatter(new RedactingFormatter());
        handler.setLevel(Level.ALL);
        handler.setEncoding("UTF-8");
        root.addHandler(handler);
        root.setLevel(Level.FINE);
        root.setUseParentHandlers(true);
        fileHandler = handler;
    }

    /**
     * Removes and closes the file handler so the current log file is released. Windows cannot delete or move an
     * open log file, so {@code LauncherServices.close()} calls this before the data directory may be cleaned up.
     */
    public static synchronized void close() {
        if (fileHandler != null) {
            Logger.getLogger(ROOT).removeHandler(fileHandler);
            fileHandler.close();
            fileHandler = null;
        }
    }

    /**
     * @param name logger suffix
     * @return logger {@code VANTA.<name>}
     */
    public static Logger get(final String name) {
        return Logger.getLogger(ROOT + "." + name);
    }

    /** @return the launcher root logger */
    public static Logger root() {
        return Logger.getLogger(ROOT);
    }

    /**
     * Registers a secret so it is masked in every log line.
     *
     * @param secret secret value
     */
    public static void redact(final String secret) {
        Redactor.global().register(secret);
    }

    /**
     * Formatter: {@code 2026-10-04T12:00:00.123Z LEVEL [logger] message} with redaction applied to message and
     * stack traces.
     */
    public static final class RedactingFormatter extends Formatter {

        private static final DateTimeFormatter TIME = DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("UTC"));

        @Override
        public String format(final LogRecord record) {
            final StringBuilder sb = new StringBuilder(160);
            sb.append(TIME.format(Instant.ofEpochMilli(record.getMillis())))
                .append(' ').append(record.getLevel().getName())
                .append(" [").append(record.getLoggerName()).append("] ")
                .append(formatMessage(record)).append(System.lineSeparator());
            if (record.getThrown() != null) {
                final StringWriter sw = new StringWriter();
                try (PrintWriter pw = new PrintWriter(sw)) {
                    record.getThrown().printStackTrace(pw);
                }
                sb.append(sw);
            }
            return Redactor.global().apply(sb.toString());
        }
    }
}
