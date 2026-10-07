package dev.vanta.core.perf;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Smart Boost's starting guess: a pure, table-tested rule that maps the machine (threads, Java heap, the GPU renderer
 * string) to the preset the first measurement starts from and the highest preset a measurement may raise it to.
 * <p>
 * It is only a starting point; the measurements decide. Rules:
 * <ul>
 *   <li>a software renderer (llvmpipe, softpipe, SwiftShader, Microsoft Basic Render, GDI Generic) starts and stays
 *       at {@link PerformancePreset#BOOST};</li>
 *   <li>an integrated GPU (Intel HD / UHD / Iris / Arc without a model number, AMD "Radeon(TM) Graphics", Vega or the
 *       680M-style APUs) starts at LOW with 4 threads or fewer, else BALANCED, and may reach HIGH;</li>
 *   <li>a discrete GPU (GeForce, RTX, GTX, Quadro, Radeon RX / Pro, Arc A/B series) starts at HIGH with 8 threads
 *       and a heap of 3.5 GiB or more, else BALANCED; ULTRA is reachable only with that machine;</li>
 *   <li>an unknown renderer starts at BALANCED and may reach HIGH;</li>
 *   <li>a heap under 2.5 GiB caps at BALANCED, under 3.5 GiB at HIGH (big render distances need memory).</li>
 * </ul>
 */
public final class HardwareTier {
    /** One GiB. */
    public static final long GIB = 1024L * 1024L * 1024L;
    /** Heap below this caps the result at BALANCED. */
    public static final long SMALL_HEAP = (long) (2.5 * GIB);
    /** Heap below this caps the result at HIGH. */
    public static final long MEDIUM_HEAP = (long) (3.5 * GIB);

    private static final Pattern ARC_DISCRETE = Pattern.compile("\\barc\\b.*\\b[ab]\\d{3}");
    private static final Pattern APU_MODEL = Pattern.compile("\\b\\d{3,4}m\\b");

    /** Coarse GPU class read from the renderer string. */
    public enum GpuClass {
        SOFTWARE, INTEGRATED, DISCRETE, UNKNOWN
    }

    /**
     * The guess.
     *
     * @param start       preset the first window measures
     * @param cap         highest preset a measurement may step up to
     * @param gpu         GPU class the guess is based on
     * @param allowStepUp false when the hardware never gets more than the start (software rendering)
     */
    public record Guess(PerformancePreset start, PerformancePreset cap, GpuClass gpu, boolean allowStepUp) {
        public Guess {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(cap, "cap");
            Objects.requireNonNull(gpu, "gpu");
            if (start.ordinal() > cap.ordinal()) {
                start = cap;
            }
        }
    }

    private HardwareTier() {
    }

    /** GPU class of a {@code GL_RENDERER} string. */
    public static GpuClass classifyGpu(Optional<String> renderer) {
        if (renderer == null || renderer.isEmpty() || renderer.get().isBlank()) {
            return GpuClass.UNKNOWN;
        }
        String r = renderer.get().toLowerCase(Locale.ROOT);
        if (r.contains("llvmpipe") || r.contains("softpipe") || r.contains("swiftshader")
                || r.contains("basic render") || r.contains("gdi generic")) {
            return GpuClass.SOFTWARE;
        }
        if (r.contains("geforce") || r.contains("rtx") || r.contains("gtx") || r.contains("quadro")
                || r.contains("radeon rx") || r.contains("radeon pro") || r.contains("firepro")
                || r.contains("radeon r9") || ARC_DISCRETE.matcher(r).find()) {
            return GpuClass.DISCRETE;
        }
        if (r.contains("intel") || r.contains("iris") || r.contains("uhd graphics") || r.contains("hd graphics")) {
            return GpuClass.INTEGRATED;
        }
        if (r.contains("radeon") && (r.contains("radeon(tm) graphics") || r.contains("radeon graphics")
                || r.contains("vega") || APU_MODEL.matcher(r).find())) {
            return GpuClass.INTEGRATED;
        }
        if (r.contains("nvidia")) {
            return GpuClass.DISCRETE;
        }
        return GpuClass.UNKNOWN;
    }

    /** The starting guess for this machine. */
    public static Guess classify(SystemInfo system, Optional<String> renderer) {
        Objects.requireNonNull(system, "system");
        GpuClass gpu = classifyGpu(renderer);
        int threads = system.cpuCount();
        long heap = system.maxHeapBytes();
        boolean strong = threads >= 8 && heap >= MEDIUM_HEAP;
        PerformancePreset start;
        PerformancePreset cap;
        switch (gpu) {
            case SOFTWARE -> {
                return new Guess(PerformancePreset.BOOST, PerformancePreset.BOOST, gpu, false);
            }
            case INTEGRATED -> {
                start = threads <= 4 ? PerformancePreset.LOW : PerformancePreset.BALANCED;
                cap = PerformancePreset.HIGH;
            }
            case DISCRETE -> {
                start = strong ? PerformancePreset.HIGH : PerformancePreset.BALANCED;
                cap = strong ? PerformancePreset.ULTRA : PerformancePreset.HIGH;
            }
            default -> {
                start = PerformancePreset.BALANCED;
                cap = PerformancePreset.HIGH;
            }
        }
        if (heap > 0 && heap < SMALL_HEAP) {
            cap = min(cap, PerformancePreset.BALANCED);
        } else if (heap > 0 && heap < MEDIUM_HEAP) {
            cap = min(cap, PerformancePreset.HIGH);
        }
        return new Guess(min(start, cap), cap, gpu, true);
    }

    private static PerformancePreset min(PerformancePreset a, PerformancePreset b) {
        return a.ordinal() <= b.ordinal() ? a : b;
    }
}
