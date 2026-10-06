package dev.vanta.core.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class CpuSamplerTest {

    @Test
    void readingIsReusedForTheCacheWindow() {
        AtomicLong now = new AtomicLong(1_000L);
        AtomicInteger reads = new AtomicInteger();
        CpuSampler sampler = new CpuSampler(() -> OptionalDouble.of(reads.incrementAndGet() / 10.0), now::get);

        assertEquals(OptionalDouble.of(0.1), sampler.sample(), "first call reads");
        for (int tick = 1; tick < 5; tick++) {
            now.addAndGet(50L);
            assertEquals(OptionalDouble.of(0.1), sampler.sample(), "20 Hz ticks inside the window reuse it");
        }
        assertEquals(1, reads.get());
        now.addAndGet(50L); // 250 ms after the first read
        assertEquals(OptionalDouble.of(0.2), sampler.sample(), "the window has elapsed");
        assertEquals(2, reads.get());
    }

    @Test
    void clockGoingBackwardsForcesAFreshReading() {
        AtomicLong now = new AtomicLong(5_000L);
        AtomicInteger reads = new AtomicInteger();
        CpuSampler sampler = new CpuSampler(() -> OptionalDouble.of(reads.incrementAndGet()), now::get);
        sampler.sample();
        now.set(100L);
        sampler.sample();
        assertEquals(2, reads.get());
    }

    @Test
    void emptyReadingsAreCachedToo() {
        AtomicLong now = new AtomicLong();
        AtomicInteger reads = new AtomicInteger();
        CpuSampler sampler = new CpuSampler(() -> {
            reads.incrementAndGet();
            return OptionalDouble.empty();
        }, now::get);
        assertTrue(sampler.sample().isEmpty());
        now.addAndGet(100L);
        assertTrue(sampler.sample().isEmpty());
        assertEquals(1, reads.get(), "an unavailable platform is not probed every tick");
    }

    @Test
    void platformSamplerNeverThrows() {
        CpuSampler sampler = new CpuSampler();
        OptionalDouble load = sampler.sample();
        assertTrue(load.isEmpty() || (load.getAsDouble() >= 0.0 && load.getAsDouble() <= 1.0));
    }
}
