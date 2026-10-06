package dev.vanta.core.perf;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Process CPU load via {@code com.sun.management.OperatingSystemMXBean} when available. The first call after start-up
 * and platforms without the extension return empty; callers show "n/a".
 * <p>
 * The bean is resolved once, and a reading is reused for {@value #CACHE_MILLIS} ms: the JVM's own figure is an
 * average over the interval since the previous query anyway, so asking twenty times a second (once per HUD tick) only
 * costs a native call per tick without making the number any fresher.
 */
public final class CpuSampler {
    /** How long one reading is reused. */
    public static final long CACHE_MILLIS = 250L;

    private final Supplier<OptionalDouble> source;
    private final LongSupplier clock;
    private long lastReadMillis = Long.MIN_VALUE;
    private OptionalDouble lastReading = OptionalDouble.empty();

    /** Sampler reading the running JVM. */
    public CpuSampler() {
        this(new MxBeanSource(), System::currentTimeMillis);
    }

    /** Sampler with a custom source and the system clock (tests). */
    public CpuSampler(Supplier<OptionalDouble> source) {
        this(source, System::currentTimeMillis);
    }

    /** Sampler with a custom source and clock (tests). */
    public CpuSampler(Supplier<OptionalDouble> source, LongSupplier clock) {
        this.source = Objects.requireNonNull(source, "source");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Process CPU load in [0, 1], or empty when unknown; re-read at most every {@value #CACHE_MILLIS} ms. */
    public OptionalDouble sample() {
        long now = clock.getAsLong();
        if (lastReadMillis != Long.MIN_VALUE && now >= lastReadMillis && now - lastReadMillis < CACHE_MILLIS) {
            return lastReading;
        }
        lastReading = source.get();
        lastReadMillis = now;
        return lastReading;
    }

    /** Resolves the platform bean once and reads it on every call. */
    private static final class MxBeanSource implements Supplier<OptionalDouble> {
        private final com.sun.management.OperatingSystemMXBean bean;

        MxBeanSource() {
            com.sun.management.OperatingSystemMXBean resolved = null;
            try {
                OperatingSystemMXBean platform = ManagementFactory.getOperatingSystemMXBean();
                if (platform instanceof com.sun.management.OperatingSystemMXBean sun) {
                    resolved = sun;
                }
            } catch (RuntimeException | LinkageError e) {
                // extension not available on this JVM
            }
            this.bean = resolved;
        }

        @Override
        public OptionalDouble get() {
            if (bean == null) {
                return OptionalDouble.empty();
            }
            try {
                double load = bean.getProcessCpuLoad();
                if (load >= 0 && !Double.isNaN(load)) {
                    return OptionalDouble.of(Math.min(1.0, load));
                }
            } catch (RuntimeException | LinkageError e) {
                // extension not available on this JVM
            }
            return OptionalDouble.empty();
        }
    }
}
