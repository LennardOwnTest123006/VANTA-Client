package dev.vanta.core.perf;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/**
 * Process CPU load via {@code com.sun.management.OperatingSystemMXBean} when available. The first call after start-up
 * and platforms without the extension return empty; callers show "n/a".
 */
public final class CpuSampler {
    private final Supplier<OptionalDouble> source;

    /** Sampler reading the running JVM. */
    public CpuSampler() {
        this(CpuSampler::readProcessCpuLoad);
    }

    /** Sampler with a custom source (tests). */
    public CpuSampler(Supplier<OptionalDouble> source) {
        this.source = source;
    }

    /** Process CPU load in [0, 1], or empty when unknown. */
    public OptionalDouble sample() {
        return source.get();
    }

    static OptionalDouble readProcessCpuLoad() {
        try {
            OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
            if (bean instanceof com.sun.management.OperatingSystemMXBean sun) {
                double load = sun.getProcessCpuLoad();
                if (load >= 0 && !Double.isNaN(load)) {
                    return OptionalDouble.of(Math.min(1.0, load));
                }
            }
        } catch (RuntimeException | LinkageError e) {
            // extension not available on this JVM
        }
        return OptionalDouble.empty();
    }
}
