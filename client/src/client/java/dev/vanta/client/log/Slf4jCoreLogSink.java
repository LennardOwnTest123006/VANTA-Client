package dev.vanta.client.log;

import dev.vanta.core.config.CoreLog;
import java.util.Objects;
import java.util.logging.Level;
import org.slf4j.Logger;

/**
 * Routes {@link CoreLog} records from the pure-Java core to the mod's SLF4J logger so that everything VANTA logs
 * ends up in the game log under the {@code VANTA} name.
 */
public final class Slf4jCoreLogSink implements CoreLog.Sink {
    private final Logger logger;

    public Slf4jCoreLogSink(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void log(Level level, String message, Throwable error) {
        int severity = level.intValue();
        if (severity >= Level.SEVERE.intValue()) {
            if (error == null) {
                logger.error(message);
            } else {
                logger.error(message, error);
            }
        } else if (severity >= Level.WARNING.intValue()) {
            if (error == null) {
                logger.warn(message);
            } else {
                logger.warn(message, error);
            }
        } else if (severity >= Level.INFO.intValue()) {
            if (error == null) {
                logger.info(message);
            } else {
                logger.info(message, error);
            }
        } else if (error == null) {
            logger.debug(message);
        } else {
            logger.debug(message, error);
        }
    }
}
