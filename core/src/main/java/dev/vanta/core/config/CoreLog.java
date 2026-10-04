package dev.vanta.core.config;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tiny logging facade for the pure-Java core.
 * <p>
 * {@code core} must not depend on SLF4J, so it logs through a pluggable {@link Sink}. The default sink forwards to
 * {@code java.util.logging}; the Fabric client installs a sink that forwards to its SLF4J logger named {@code VANTA}.
 */
public final class CoreLog {

    /** Receives log records. Implementations must be thread-safe. */
    public interface Sink {
        /**
         * Emits one record.
         *
         * @param level   severity
         * @param message already formatted message
         * @param error   optional throwable, may be {@code null}
         */
        void log(Level level, String message, Throwable error);
    }

    private static final Sink JUL_SINK = (level, message, error) -> {
        Logger logger = Logger.getLogger("VANTA");
        if (error == null) {
            logger.log(level, message);
        } else {
            logger.log(level, message, error);
        }
    };

    private static volatile Sink sink = JUL_SINK;

    private CoreLog() {
    }

    /** Installs the sink used by every subsequent log call. */
    public static void install(Sink newSink) {
        sink = Objects.requireNonNull(newSink, "sink");
    }

    /** Restores the default {@code java.util.logging} sink. */
    public static void reset() {
        sink = JUL_SINK;
    }

    /** Logs at INFO level. Placeholders {@code {}} are replaced by the arguments in order. */
    public static void info(String message, Object... args) {
        sink.log(Level.INFO, format(message, args), null);
    }

    /** Logs at WARNING level. */
    public static void warn(String message, Object... args) {
        sink.log(Level.WARNING, format(message, args), null);
    }

    /** Logs at WARNING level with a cause. */
    public static void warn(Throwable error, String message, Object... args) {
        sink.log(Level.WARNING, format(message, args), error);
    }

    /** Logs at SEVERE level with a cause. */
    public static void error(Throwable error, String message, Object... args) {
        sink.log(Level.SEVERE, format(message, args), error);
    }

    /** Logs at FINE level (debug). */
    public static void debug(String message, Object... args) {
        sink.log(Level.FINE, format(message, args), null);
    }

    /** Replaces each {@code {}} in {@code message} with the next argument; surplus arguments are appended. */
    static String format(String message, Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }
        StringBuilder out = new StringBuilder(message.length() + 32);
        int argIndex = 0;
        int i = 0;
        while (i < message.length()) {
            if (argIndex < args.length && message.startsWith("{}", i)) {
                out.append(args[argIndex++]);
                i += 2;
            } else {
                out.append(message.charAt(i));
                i++;
            }
        }
        while (argIndex < args.length) {
            out.append(' ').append(args[argIndex++]);
        }
        return out.toString();
    }
}
