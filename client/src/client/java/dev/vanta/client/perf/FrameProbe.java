package dev.vanta.client.perf;

import java.util.Arrays;
import net.minecraft.client.Minecraft;

/**
 * Opt-in frame-cost probe for the client game test ({@code -Dvanta.gametest.perfProbe=true} or
 * {@link #setEnabled(boolean)} from the test). It accumulates, on the render thread only:
 * <ul>
 *   <li>the game's own frame time ({@link Minecraft#getFrameTimeNs()}) once per rendered level frame, sampled from the
 *       {@code GameRendererMixin} so frames are counted even while the GUI is hidden and no HUD element runs;</li>
 *   <li>the wall time of the VANTA HUD element, the custom crosshair element and VANTA screen rendering;</li>
 *   <li>how many {@code GuiGraphicsCanvas} primitives were issued (fills, native gradients, both vertical and the
 *       rotated horizontal ones, text runs, images, scissor changes, transform pushes) and how many text widths were
 *       measured.</li>
 * </ul>
 * Disabled (the default in normal play) every hook is one static boolean check; no allocation happens.
 */
public final class FrameProbe {
    /** JVM property that enables the probe from the start. */
    public static final String PROPERTY = "vanta.gametest.perfProbe";
    private static final int MAX_SAMPLES = 4096;

    private static boolean enabled = Boolean.getBoolean(PROPERTY);

    private static final long[] frameSamples = new long[MAX_SAMPLES];
    private static int frames;
    private static long frameNanos;

    private static final Timer hud = new Timer();
    private static final Timer crosshair = new Timer();
    private static final Timer screen = new Timer();

    private static long fills;
    private static long gradients;
    private static long texts;
    private static long images;
    private static long scissorChanges;
    private static long transformPushes;
    private static long textWidths;

    private FrameProbe() {
    }

    /** Whether the probe is collecting. */
    public static boolean isEnabled() {
        return enabled;
    }

    /** Turns collecting on or off (render thread). */
    public static void setEnabled(boolean on) {
        enabled = on;
    }

    /** Clears every counter (render thread). */
    public static void reset() {
        frames = 0;
        frameNanos = 0L;
        hud.reset();
        crosshair.reset();
        screen.reset();
        fills = 0L;
        gradients = 0L;
        texts = 0L;
        images = 0L;
        scissorChanges = 0L;
        transformPushes = 0L;
        textWidths = 0L;
    }

    /** Rendered level frames seen since the last reset. */
    public static int frames() {
        return frames;
    }

    /** Slowest single VANTA HUD element call since the last reset, in nanoseconds (render thread, no allocation). */
    public static long hudMaxNanos() {
        return hud.max;
    }

    /** Slowest single custom crosshair draw since the last reset, in nanoseconds (render thread, no allocation). */
    public static long crosshairMaxNanos() {
        return crosshair.max;
    }

    // ---- hooks -------------------------------------------------------------------------------------------------

    /** One rendered level frame; records the duration of the previous frame as the game measured it. */
    public static void onLevelFrame() {
        if (!enabled) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        long nanos = minecraft.getFrameTimeNs();
        if (frames < MAX_SAMPLES) {
            frameSamples[frames] = nanos;
        }
        frames++;
        frameNanos += nanos;
    }

    /** Wall time of one {@code VantaHudElement.render} call. */
    public static void recordHud(long nanos) {
        if (enabled) {
            hud.add(nanos);
        }
    }

    /** Wall time of one custom crosshair draw. */
    public static void recordCrosshair(long nanos) {
        if (enabled) {
            crosshair.add(nanos);
        }
    }

    /** Wall time of one {@code VantaScreen.render} call. */
    public static void recordScreen(long nanos) {
        if (enabled) {
            screen.add(nanos);
        }
    }

    public static void countFill() {
        if (enabled) {
            fills++;
        }
    }

    public static void countGradient() {
        if (enabled) {
            gradients++;
        }
    }

    public static void countText() {
        if (enabled) {
            texts++;
        }
    }

    public static void countImage() {
        if (enabled) {
            images++;
        }
    }

    public static void countScissorChange() {
        if (enabled) {
            scissorChanges++;
        }
    }

    public static void countTransformPush() {
        if (enabled) {
            transformPushes++;
        }
    }

    public static void countTextWidth() {
        if (enabled) {
            textWidths++;
        }
    }

    // ---- results -----------------------------------------------------------------------------------------------

    /** Copies the current counters (render thread). */
    public static Snapshot snapshot() {
        int kept = Math.min(frames, MAX_SAMPLES);
        long[] sorted = Arrays.copyOf(frameSamples, kept);
        Arrays.sort(sorted);
        return new Snapshot(frames, frameNanos, sorted, hud.stats(), crosshair.stats(), screen.stats(), fills,
                gradients, texts, images, scissorChanges, transformPushes, textWidths);
    }

    /** Accumulated wall time of one render hook. */
    public record TimerStats(int calls, long totalNanos, long maxNanos) {
        /** Average per call in milliseconds (0 without calls). */
        public double averageMillis() {
            return calls == 0 ? 0.0 : totalNanos / 1_000_000.0 / calls;
        }

        /** Slowest call in milliseconds. */
        public double maxMillis() {
            return maxNanos / 1_000_000.0;
        }
    }

    /**
     * Immutable copy of the counters.
     *
     * @param frames          rendered level frames
     * @param frameNanos      sum of the game's frame times over those frames
     * @param sortedSamples   the individual frame times (ascending, at most {@value #MAX_SAMPLES})
     */
    public record Snapshot(int frames, long frameNanos, long[] sortedSamples, TimerStats hud, TimerStats crosshair,
                           TimerStats screen, long fills, long gradients, long texts, long images,
                           long scissorChanges, long transformPushes, long textWidths) {
        /** Average game frame time in milliseconds. */
        public double averageFrameMillis() {
            return frames == 0 ? 0.0 : frameNanos / 1_000_000.0 / frames;
        }

        /** Frames per second implied by the average frame time. */
        public double fps() {
            double avg = averageFrameMillis();
            return avg <= 0.0 ? 0.0 : 1000.0 / avg;
        }

        /** Frame time percentile (0..1) in milliseconds, from the kept samples. */
        public double percentileFrameMillis(double p) {
            if (sortedSamples.length == 0) {
                return 0.0;
            }
            int index = (int) Math.min(sortedSamples.length - 1, Math.max(0, Math.round(p * (sortedSamples.length - 1))));
            return sortedSamples[index] / 1_000_000.0;
        }

        /** A primitive count divided by the HUD element calls (per-frame average while the HUD element runs). */
        public double perHudCall(long count) {
            return hud.calls() == 0 ? 0.0 : (double) count / hud.calls();
        }
    }

    private static final class Timer {
        private int calls;
        private long total;
        private long max;

        void add(long nanos) {
            calls++;
            total += nanos;
            if (nanos > max) {
                max = nanos;
            }
        }

        void reset() {
            calls = 0;
            total = 0L;
            max = 0L;
        }

        TimerStats stats() {
            return new TimerStats(calls, total, max);
        }
    }
}
