package dev.vanta.core.perf;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Reads heap usage from the JVM.
 */
public final class MemorySampler {

    /** One reading, in bytes. */
    public record MemorySample(long used, long allocated, long max) {
        /** Used fraction of the maximum heap (0–1). */
        public double usedFraction() {
            return max > 0 ? Math.max(0.0, Math.min(1.0, used / (double) max)) : 0;
        }

        /** Used fraction of the currently allocated heap (0–1). */
        public double allocatedFraction() {
            return allocated > 0 ? Math.max(0.0, Math.min(1.0, used / (double) allocated)) : 0;
        }

        /** Mebibytes. */
        public static long toMiB(long bytes) {
            return bytes >> 20;
        }
    }

    private final Supplier<MemorySample> source;

    /** Sampler reading the running JVM. */
    public MemorySampler() {
        this(() -> {
            Runtime rt = Runtime.getRuntime();
            long total = rt.totalMemory();
            return new MemorySample(total - rt.freeMemory(), total, rt.maxMemory());
        });
    }

    /** Sampler with a custom source (tests). */
    public MemorySampler(Supplier<MemorySample> source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    /** Current reading. */
    public MemorySample sample() {
        return source.get();
    }
}
