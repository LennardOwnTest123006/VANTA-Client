package dev.vanta.launcher.ui.model;

import dev.vanta.launcher.core.log.LauncherLog;
import dev.vanta.launcher.core.log.Redactor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Feeds the launcher's {@code java.util.logging} output ({@code VANTA.*} loggers) into a {@link LogBuffer} with
 * secrets redacted, so the Logs page shows the same lines that land in {@code logs/launcher-0.log}.
 */
public final class JulLogBridge extends Handler {

    private final LogBuffer buffer;
    private Logger attachedTo;

    /**
     * @param buffer target buffer
     */
    public JulLogBridge(final LogBuffer buffer) {
        this.buffer = Objects.requireNonNull(buffer, "buffer");
        setLevel(Level.ALL);
    }

    /**
     * Attaches the bridge to the launcher root logger.
     *
     * @return this
     */
    public JulLogBridge attach() {
        return attach(LauncherLog.root());
    }

    /**
     * Attaches the bridge to a logger.
     *
     * @param logger logger
     * @return this
     */
    public synchronized JulLogBridge attach(final Logger logger) {
        detach();
        logger.addHandler(this);
        if (logger.getLevel() == null || logger.getLevel().intValue() > Level.FINE.intValue()) {
            logger.setLevel(Level.FINE);
        }
        attachedTo = logger;
        return this;
    }

    /** Detaches from the logger. */
    public synchronized void detach() {
        if (attachedTo != null) {
            attachedTo.removeHandler(this);
            attachedTo = null;
        }
    }

    @Override
    public void publish(final LogRecord record) {
        if (record == null) {
            return;
        }
        buffer.append(LogLevel.fromJul(record.getLevel()), format(record));
    }

    /**
     * Formats a record as {@code LEVEL [Name] message} (+ stack trace) with redaction applied.
     *
     * @param record record
     * @return text
     */
    public static String format(final LogRecord record) {
        final StringBuilder sb = new StringBuilder(128);
        sb.append(record.getLevel().getName()).append(" [").append(shortName(record.getLoggerName())).append("] ");
        String message = record.getMessage() == null ? "" : record.getMessage();
        final Object[] params = record.getParameters();
        if (params != null && params.length > 0) {
            try {
                message = java.text.MessageFormat.format(message, params);
            } catch (IllegalArgumentException ignored) {
                // keep the raw message
            }
        }
        sb.append(message);
        if (record.getThrown() != null) {
            final StringWriter sw = new StringWriter();
            try (PrintWriter pw = new PrintWriter(sw)) {
                record.getThrown().printStackTrace(pw);
            }
            sb.append(System.lineSeparator()).append(sw.toString().stripTrailing());
        }
        return Redactor.global().apply(sb.toString());
    }

    private static String shortName(final String loggerName) {
        if (loggerName == null) {
            return "Launcher";
        }
        final int dot = loggerName.lastIndexOf('.');
        return dot >= 0 && dot < loggerName.length() - 1 ? loggerName.substring(dot + 1) : loggerName;
    }

    @Override
    public void flush() {
        // nothing buffered
    }

    @Override
    public void close() {
        detach();
    }
}
