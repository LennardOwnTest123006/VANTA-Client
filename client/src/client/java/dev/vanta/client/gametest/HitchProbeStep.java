package dev.vanta.client.gametest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vanta.client.perf.FrameProbe;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.settings.VantaSettings;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * Hitch probe of the client game test: short freezes rather than averages. In the test world, with no screen open
 * and the player slowly turning on the spot (so chunk culling and sorting keep working), it measures at least
 * {@value #FRAMES} rendered frames with the VANTA HUD on and again with it off, and records per state:
 * <ul>
 *   <li>the game's frame times: max, p99, p99.9 and the number of frames over 50 ms and over 100 ms;</li>
 *   <li>the slowest single call of the VANTA HUD element and of the custom crosshair element (their own wall time,
 *       measured around the element's code, not the software renderer's frame);</li>
 *   <li>the garbage collections in that window (count and accumulated time per collector) and the heap/GC JVM
 *       arguments the game runs with.</li>
 * </ul>
 * The numbers are added as {@code "hitches"} to {@code vanta-perf-probe.json} (next to {@link PerfProbeStep}'s
 * results) and logged.
 * <p>
 * The one assertion: no single VANTA HUD element or crosshair call may take more than
 * {@value #MAX_ELEMENT_FRAME_MILLIS} ms. When the slowest call falls into a tick in which the JVM ran a garbage
 * collection, the collection time of that tick is taken off first (the pause stops every thread, so the element's
 * wall time then includes it, which says nothing about VANTA's code); what is left is still judged, so a collection
 * in that tick cannot hide a slow element. With the HUD on, an element that was never called at all is a finding too
 * (the probe could not measure anything). The frame-time hitch counts are reported, not judged: the CI runner renders
 * in software in lock-step with the test thread and does not stand for a player's PC.
 */
final class HitchProbeStep {
    /** Frames measured per state. */
    static final int FRAMES = 600;
    /** Ticks one state may take to reach {@link #FRAMES}. */
    static final int MODE_TIMEOUT_TICKS = 2400;
    /** Bound for a single VANTA element call, milliseconds. */
    static final double MAX_ELEMENT_FRAME_MILLIS = 8.0;
    /** Element calls needed before the bound is judged. */
    static final int MIN_CALLS_FOR_ASSERTION = 60;
    /** Degrees the player turns per tick (a full turn in 12 s of game time). */
    static final float TURN_DEGREES_PER_TICK = 1.5f;
    /** File written next to the screenshots (shared with {@link PerfProbeStep}). */
    static final String JSON_FILE = PerfProbeStep.JSON_FILE;
    private static final long NANOS_PER_MS = 1_000_000L;

    private HitchProbeStep() {
    }

    /** GC counters summed or per collector at one moment. */
    private record GcSample(Map<String, long[]> byCollector) {
        static GcSample now() {
            Map<String, long[]> out = new LinkedHashMap<>();
            for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
                out.put(bean.getName(), new long[] {Math.max(0L, bean.getCollectionCount()),
                        Math.max(0L, bean.getCollectionTime())});
            }
            return new GcSample(out);
        }

        long totalCount() {
            long sum = 0L;
            for (long[] v : byCollector.values()) {
                sum += v[0];
            }
            return sum;
        }

        long totalTimeMillis() {
            long sum = 0L;
            for (long[] v : byCollector.values()) {
                sum += v[1];
            }
            return sum;
        }
    }

    /** One element's slowest call, whether a GC ran in the same tick and how long that tick's collections took. */
    private static final class Worst {
        long nanos;
        boolean duringGc;
        long gcMillisInTick;
        int tick = -1;

        void update(long maxNanos, boolean gcThisTick, long gcMillisThisTick, int currentTick) {
            if (maxNanos > nanos) {
                nanos = maxNanos;
                duringGc = gcThisTick;
                gcMillisInTick = gcThisTick ? Math.max(0L, gcMillisThisTick) : 0L;
                tick = currentTick;
            }
        }

        double millis() {
            return nanos / (double) NANOS_PER_MS;
        }

        /** The slowest call minus the collection time of its tick (a GC pause explains at most its own length). */
        double millisWithoutGc() {
            return Math.max(0.0, millis() - gcMillisInTick);
        }
    }

    private record Mode(String name, FrameProbe.Snapshot snapshot, int ticks, int gcTicks, long wallMillis, Worst hud,
                        Worst crosshair, GcSample gcBefore, GcSample gcAfter) {
        long[] frames() {
            return snapshot.sortedSamples();
        }
    }

    /**
     * Runs both states, merges the results into the probe JSON and returns the findings of the element bound.
     */
    static List<String> run(ClientGameTestContext context, VantaServices services, Path outputDir) {
        List<String> failures = new ArrayList<>();
        List<Mode> modes = new ArrayList<>();
        float startYaw = context.computeOnClient(client -> client.player == null ? 0f : client.player.getYRot());
        boolean hudWasEnabled = context.computeOnClient(client -> services.settings().get(VantaSettings.HUD_ENABLED));
        try {
            context.runOnClient(client -> {
                services.settings().set(VantaSettings.HUD_ENABLED, true);
                FrameProbe.setEnabled(true);
            });
            modes.add(measure(context, "hudOn"));
            context.runOnClient(client -> services.settings().set(VantaSettings.HUD_ENABLED, false));
            modes.add(measure(context, "hudOff"));
        } finally {
            context.runOnClient(client -> {
                services.settings().set(VantaSettings.HUD_ENABLED, hudWasEnabled);
                FrameProbe.setEnabled(false);
                if (client.player != null) {
                    client.player.setYRot(startYaw);
                }
            });
        }

        JsonObject hitches = new JsonObject();
        hitches.addProperty("framesPerMode", FRAMES);
        hitches.addProperty("maxElementFrameMs", MAX_ELEMENT_FRAME_MILLIS);
        hitches.addProperty("turnDegreesPerTick", TURN_DEGREES_PER_TICK);
        hitches.add("jvm", jvmJson());
        JsonObject byMode = new JsonObject();
        for (Mode mode : modes) {
            byMode.add(mode.name(), modeJson(mode));
        }
        hitches.add("modes", byMode);
        VantaClientGameTest.step("hitch probe json: " + hitches);
        write(outputDir, hitches);

        for (Mode mode : modes) {
            judge(mode, "VANTA HUD element", mode.hud(), mode.snapshot().hud().calls(), failures);
            judge(mode, "custom crosshair element", mode.crosshair(), mode.snapshot().crosshair().calls(), failures);
        }
        Mode hudOn = modes.get(0);
        if (hudOn.snapshot().frames() >= FRAMES && hudOn.snapshot().hud().calls() == 0) {
            // Frames were rendered with the HUD on but the element never ran: the bound was not judged at all.
            String finding = "HITCH PROBE: " + hudOn.snapshot().frames() + " frames were rendered with the VANTA HUD on, "
                    + "but the VANTA HUD element was never called, so its frame time could not be measured";
            VantaClientGameTest.warn(finding);
            failures.add(finding);
        }
        return failures;
    }

    private static Mode measure(ClientGameTestContext context, String name) {
        context.waitTicks(10);
        context.runOnClient(client -> FrameProbe.reset());
        GcSample before = GcSample.now();
        long lastGcCount = before.totalCount();
        long lastGcMillis = before.totalTimeMillis();
        int gcTicks = 0;
        Worst hud = new Worst();
        Worst crosshair = new Worst();
        long start = System.nanoTime();
        int ticks = 0;
        while (ticks < MODE_TIMEOUT_TICKS && context.computeOnClient(client -> FrameProbe.frames()) < FRAMES) {
            context.runOnClient(client -> {
                if (client.player != null) {
                    client.player.setYRot(client.player.getYRot() + TURN_DEGREES_PER_TICK);
                }
            });
            context.waitTick();
            ticks++;
            GcSample gc = GcSample.now();
            long gcCount = gc.totalCount();
            long gcMillis = gc.totalTimeMillis();
            boolean gcThisTick = gcCount != lastGcCount;
            long gcMillisThisTick = gcMillis - lastGcMillis;
            lastGcCount = gcCount;
            lastGcMillis = gcMillis;
            if (gcThisTick) {
                gcTicks++;
            }
            long hudMax = context.computeOnClient(client -> FrameProbe.hudMaxNanos());
            long crosshairMax = context.computeOnClient(client -> FrameProbe.crosshairMaxNanos());
            hud.update(hudMax, gcThisTick, gcMillisThisTick, ticks);
            crosshair.update(crosshairMax, gcThisTick, gcMillisThisTick, ticks);
        }
        long wall = (System.nanoTime() - start) / NANOS_PER_MS;
        FrameProbe.Snapshot snapshot = context.computeOnClient(client -> FrameProbe.snapshot());
        GcSample after = GcSample.now();
        Mode mode = new Mode(name, snapshot, ticks, gcTicks, wall, hud, crosshair, before, after);
        long[] frames = mode.frames();
        VantaClientGameTest.step(String.format(Locale.ROOT,
                "hitch probe [%s]: %d frames in %d ticks / %d ms; frame time max %.2f ms, p99 %.2f ms, p99.9 %.2f ms, "
                        + "%d frames > 50 ms, %d frames > 100 ms; slowest VANTA HUD element call %.3f ms%s over %d "
                        + "calls, slowest crosshair call %.3f ms%s over %d calls; %d GC(s) taking %d ms in this window "
                        + "(in %d of the ticks)",
                name, snapshot.frames(), ticks, wall, maxMillis(frames), percentileMillis(frames, 99.0),
                percentileMillis(frames, 99.9), countOver(frames, 50), countOver(frames, 100),
                hud.millis(), hud.duringGc ? " (GC in that tick)" : "", snapshot.hud().calls(),
                crosshair.millis(), crosshair.duringGc ? " (GC in that tick)" : "", snapshot.crosshair().calls(),
                after.totalCount() - before.totalCount(), gcTimeDelta(before, after), gcTicks));
        if (snapshot.frames() < FRAMES) {
            VantaClientGameTest.warn("hitch probe [" + name + "]: only " + snapshot.frames() + " frames within "
                    + MODE_TIMEOUT_TICKS + " ticks; the numbers cover fewer than " + FRAMES + " frames");
        }
        return mode;
    }

    private static void judge(Mode mode, String element, Worst worst, int calls, List<String> failures) {
        if (calls < MIN_CALLS_FOR_ASSERTION) {
            if (calls > 0) {
                VantaClientGameTest.warn(String.format(Locale.ROOT, "hitch probe [%s]: only %d %s calls, the %.1f ms "
                        + "bound is not judged", mode.name(), calls, element, MAX_ELEMENT_FRAME_MILLIS));
            }
            return;
        }
        if (worst.millis() <= MAX_ELEMENT_FRAME_MILLIS) {
            VantaClientGameTest.step(String.format(Locale.ROOT, "hitch probe [%s]: slowest %s call %.3f ms is within "
                    + "the %.1f ms bound (%d calls)", mode.name(), element, worst.millis(), MAX_ELEMENT_FRAME_MILLIS,
                    calls));
            return;
        }
        if (worst.duringGc && worst.millisWithoutGc() <= MAX_ELEMENT_FRAME_MILLIS) {
            VantaClientGameTest.warn(String.format(Locale.ROOT, "hitch probe [%s]: slowest %s call %.3f ms exceeds "
                    + "the %.1f ms bound, but garbage collections took %d ms in that tick (%d); %.3f ms without them "
                    + "is within the bound", mode.name(), element, worst.millis(), MAX_ELEMENT_FRAME_MILLIS,
                    worst.gcMillisInTick, worst.tick, worst.millisWithoutGc()));
            return;
        }
        String where = worst.duringGc
                ? String.format(Locale.ROOT, "%.3f ms even without the %d ms of garbage collection in that tick",
                        worst.millisWithoutGc(), worst.gcMillisInTick)
                : "no garbage collection in that tick";
        String finding = String.format(Locale.ROOT, "HITCH: the slowest %s call took %.3f ms in the %s state (tick "
                + "%d, %s), more than the %.1f ms bound for a single frame",
                element, worst.millis(), mode.name(), worst.tick, where, MAX_ELEMENT_FRAME_MILLIS);
        VantaClientGameTest.warn(finding);
        failures.add(finding);
    }

    // ---- statistics --------------------------------------------------------------------------------------------

    private static double maxMillis(long[] sorted) {
        return sorted.length == 0 ? 0.0 : sorted[sorted.length - 1] / (double) NANOS_PER_MS;
    }

    /** Nearest-rank percentile of ascending samples, in milliseconds. */
    static double percentileMillis(long[] sorted, double percent) {
        if (sorted.length == 0) {
            return 0.0;
        }
        int rank = (int) Math.ceil(percent / 100.0 * sorted.length);
        int index = Math.max(0, Math.min(sorted.length - 1, rank - 1));
        return sorted[index] / (double) NANOS_PER_MS;
    }

    static int countOver(long[] samples, long millis) {
        long limit = millis * NANOS_PER_MS;
        int count = 0;
        for (long sample : samples) {
            if (sample > limit) {
                count++;
            }
        }
        return count;
    }

    private static long gcTimeDelta(GcSample before, GcSample after) {
        long sum = 0L;
        for (Map.Entry<String, long[]> entry : after.byCollector().entrySet()) {
            long[] old = before.byCollector().get(entry.getKey());
            sum += entry.getValue()[1] - (old == null ? 0L : old[1]);
        }
        return sum;
    }

    // ---- JSON --------------------------------------------------------------------------------------------------

    private static JsonObject modeJson(Mode mode) {
        long[] frames = mode.frames();
        JsonObject json = new JsonObject();
        json.addProperty("frames", mode.snapshot().frames());
        json.addProperty("framesKept", frames.length);
        json.addProperty("ticks", mode.ticks());
        json.addProperty("ticksWithGc", mode.gcTicks());
        json.addProperty("wallMs", mode.wallMillis());
        JsonObject frame = new JsonObject();
        frame.addProperty("avgMs", round(mode.snapshot().averageFrameMillis()));
        frame.addProperty("p99Ms", round(percentileMillis(frames, 99.0)));
        frame.addProperty("p999Ms", round(percentileMillis(frames, 99.9)));
        frame.addProperty("maxMs", round(maxMillis(frames)));
        frame.addProperty("over50Ms", countOver(frames, 50));
        frame.addProperty("over100Ms", countOver(frames, 100));
        json.add("gameFrameTime", frame);
        json.add("hudElement", elementJson(mode.snapshot().hud(), mode.hud()));
        json.add("crosshairElement", elementJson(mode.snapshot().crosshair(), mode.crosshair()));
        JsonObject gc = new JsonObject();
        gc.addProperty("count", mode.gcAfter().totalCount() - mode.gcBefore().totalCount());
        gc.addProperty("timeMs", gcTimeDelta(mode.gcBefore(), mode.gcAfter()));
        JsonObject collectors = new JsonObject();
        for (Map.Entry<String, long[]> entry : mode.gcAfter().byCollector().entrySet()) {
            long[] old = mode.gcBefore().byCollector().getOrDefault(entry.getKey(), new long[2]);
            JsonObject one = new JsonObject();
            one.addProperty("count", entry.getValue()[0] - old[0]);
            one.addProperty("timeMs", entry.getValue()[1] - old[1]);
            collectors.add(entry.getKey(), one);
        }
        gc.add("byCollector", collectors);
        json.add("gc", gc);
        return json;
    }

    private static JsonObject elementJson(FrameProbe.TimerStats stats, Worst worst) {
        JsonObject json = new JsonObject();
        json.addProperty("calls", stats.calls());
        json.addProperty("avgMs", round(stats.averageMillis()));
        json.addProperty("maxMs", round(stats.maxMillis()));
        json.addProperty("maxDuringGcTick", worst.duringGc);
        json.addProperty("gcMsInMaxTick", worst.gcMillisInTick);
        json.addProperty("maxTick", worst.tick);
        return json;
    }

    private static JsonObject jvmJson() {
        JsonObject json = new JsonObject();
        JsonArray args = new JsonArray();
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (arg.startsWith("-Xm") || arg.startsWith("-XX:")) {
                args.add(arg);
            }
        }
        json.add("heapAndGcArgs", args);
        JsonArray collectors = new JsonArray();
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            collectors.add(bean.getName());
        }
        json.add("collectors", collectors);
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        json.addProperty("heapMaxMb", heap.getMax() < 0 ? -1 : heap.getMax() / (1024 * 1024));
        json.addProperty("heapCommittedMb", heap.getCommitted() / (1024 * 1024));
        json.addProperty("availableProcessors", Runtime.getRuntime().availableProcessors());
        return json;
    }

    /** Adds {@code "hitches"} to the probe file {@link PerfProbeStep} wrote (or starts a new one). */
    private static void write(Path outputDir, JsonObject hitches) {
        Path file = outputDir.resolve(JSON_FILE);
        JsonObject root = new JsonObject();
        try {
            if (Files.isRegularFile(file)) {
                JsonElement existing = JsonParser.parseString(Files.readString(file));
                if (existing.isJsonObject()) {
                    root = existing.getAsJsonObject();
                }
            }
        } catch (IOException | RuntimeException e) {
            VantaClientGameTest.warn("hitch probe: could not read " + file + ", writing a new file: " + e);
        }
        root.add("hitches", hitches);
        try {
            Files.createDirectories(outputDir);
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(root));
            VantaClientGameTest.step("hitch probe written to " + file);
        } catch (IOException e) {
            VantaClientGameTest.warn("hitch probe could not be written to " + file + ": " + e);
        }
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
