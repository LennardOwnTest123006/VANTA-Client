package dev.vanta.launcher.core.java;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Probes a Java executable by running {@code java -XshowSettings:properties -version} with a timeout. This is the
 * only place where the launcher executes a program it did not start itself as the game, and it only ever runs
 * candidates found in well-known Java locations or runtimes it installed and verified.
 */
public final class ProcessJavaProbe implements JavaProbe {

    private static final Logger LOG = Logger.getLogger("VANTA.Java");

    private final Duration timeout;

    /** Probe with a 15 second timeout. */
    public ProcessJavaProbe() {
        this(Duration.ofSeconds(15));
    }

    /**
     * @param timeout maximum time to wait for the process
     */
    public ProcessJavaProbe(final Duration timeout) {
        this.timeout = timeout;
    }

    @Override
    public Optional<JavaInstall> probe(final Path javaExecutable) {
        if (!Files.isRegularFile(javaExecutable) || !Files.isExecutable(javaExecutable)) {
            return Optional.empty();
        }
        final ProcessBuilder builder = new ProcessBuilder(javaExecutable.toString(), "-XshowSettings:properties", "-version")
            .redirectErrorStream(true);
        builder.environment().remove("JAVA_TOOL_OPTIONS");
        builder.environment().remove("_JAVA_OPTIONS");
        builder.environment().remove("JDK_JAVA_OPTIONS");
        try {
            final Process process = builder.start();
            final byte[] output;
            try (InputStream in = process.getInputStream()) {
                output = in.readNBytes(256 * 1024);
            }
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                LOG.log(Level.FINE, "Java probe timed out: {0}", javaExecutable);
                return Optional.empty();
            }
            return JavaVersionParser.fromProbeOutput(javaExecutable, new String(output, StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOG.log(Level.FINE, "Java probe failed for " + javaExecutable, e);
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
