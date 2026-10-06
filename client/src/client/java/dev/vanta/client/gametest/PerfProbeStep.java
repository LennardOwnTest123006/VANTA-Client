package dev.vanta.client.gametest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.vanta.client.perf.FrameProbe;
import dev.vanta.core.modrinth.PerformancePack;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.settings.VantaSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Frame-cost probe of the client game test: with the player standing still in the test world it measures, over
 * {@value #FRAMES} rendered frames each, the game's frame time and the wall time of VANTA's render hooks in three
 * states (VANTA HUD enabled, VANTA HUD disabled, GUI hidden with F1) and writes a JSON summary next to the
 * screenshots so CI uploads it with them. See {@link FrameProbe} for what is counted.
 * <p>
 * The only assertion is a generous baseline: the VANTA HUD element must average under {@value #MAX_HUD_RENDER_MILLIS}
 * ms per call on the software-rendered CI runner (skipped when fewer than {@value #MIN_FRAMES_FOR_ASSERTION} calls
 * were measured). Everything else is reported, not judged.
 */
final class PerfProbeStep {
    /** Frames measured per state. */
    static final int FRAMES = 300;
    /** Ticks one state may take to reach {@link #FRAMES} (45 s; the software renderer may be slow). */
    static final int MODE_TIMEOUT_TICKS = 900;
    /** Baseline bound for the VANTA HUD element, milliseconds per call. */
    static final double MAX_HUD_RENDER_MILLIS = 4.0;
    static final int MIN_FRAMES_FOR_ASSERTION = 30;
    /** File written next to the screenshots. */
    static final String JSON_FILE = "vanta-perf-probe.json";

    private PerfProbeStep() {
    }

    private record Mode(String name, FrameProbe.Snapshot snapshot, int ticks, long wallMillis) {
        double wallFps() {
            return wallMillis <= 0L ? 0.0 : snapshot.frames() * 1000.0 / wallMillis;
        }
    }

    /**
     * Runs the three states and writes the summary.
     *
     * @return failures of the baseline assertion (empty when it held or could not be judged)
     */
    static List<String> run(ClientGameTestContext context, VantaServices services, Path outputDir) {
        List<String> failures = new ArrayList<>();
        List<Mode> modes = new ArrayList<>();
        try {
            context.runOnClient(client -> FrameProbe.setEnabled(true));
            modes.add(measure(context, "hudEnabled"));
            context.runOnClient(client -> services.settings().set(VantaSettings.HUD_ENABLED, false));
            modes.add(measure(context, "hudDisabled"));
            context.runOnClient(client -> {
                services.settings().set(VantaSettings.HUD_ENABLED, true);
                client.options.hideGui = true;
            });
            modes.add(measure(context, "guiHidden"));
        } finally {
            context.runOnClient(client -> {
                client.options.hideGui = false;
                services.settings().set(VantaSettings.HUD_ENABLED, true);
                FrameProbe.setEnabled(false);
            });
        }

        JsonObject summary = summary(context, modes);
        VantaClientGameTest.step("perf probe json: " + summary);
        Path file = outputDir.resolve(JSON_FILE);
        try {
            Files.createDirectories(outputDir);
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(summary));
            VantaClientGameTest.step("perf probe written to " + file);
        } catch (IOException e) {
            VantaClientGameTest.warn("perf probe could not be written to " + file + ": " + e);
        }

        Mode hudOn = modes.get(0);
        Mode hudOff = modes.get(1);
        Mode hidden = modes.get(2);
        FrameProbe.TimerStats hud = hudOn.snapshot().hud();
        VantaClientGameTest.step(String.format(Locale.ROOT,
                "perf probe summary: game frame time %.2f ms (%.1f fps) with the VANTA HUD, %.2f ms (%.1f fps) "
                        + "without it, %.2f ms (%.1f fps) with the GUI hidden; VANTA HUD element %.3f ms avg, "
                        + "%.3f ms max per call (%d calls), %.1f fills + %.1f gradients + %.1f text runs + %.1f "
                        + "images + %.1f scissor changes + %.1f transform pushes + %.1f text measurements per HUD frame",
                hudOn.snapshot().averageFrameMillis(), hudOn.snapshot().fps(),
                hudOff.snapshot().averageFrameMillis(), hudOff.snapshot().fps(),
                hidden.snapshot().averageFrameMillis(), hidden.snapshot().fps(),
                hud.averageMillis(), hud.maxMillis(), hud.calls(),
                hudOn.snapshot().perHudCall(hudOn.snapshot().fills()),
                hudOn.snapshot().perHudCall(hudOn.snapshot().gradients()),
                hudOn.snapshot().perHudCall(hudOn.snapshot().texts()),
                hudOn.snapshot().perHudCall(hudOn.snapshot().images()),
                hudOn.snapshot().perHudCall(hudOn.snapshot().scissorChanges()),
                hudOn.snapshot().perHudCall(hudOn.snapshot().transformPushes()),
                hudOn.snapshot().perHudCall(hudOn.snapshot().textWidths())));

        if (hud.calls() < MIN_FRAMES_FOR_ASSERTION) {
            VantaClientGameTest.warn("perf probe: only " + hud.calls() + " VANTA HUD element calls were measured, the "
                    + MAX_HUD_RENDER_MILLIS + " ms baseline assertion is skipped");
        } else if (hud.averageMillis() > MAX_HUD_RENDER_MILLIS) {
            String finding = String.format(Locale.ROOT, "PERF BASELINE: the VANTA HUD element averaged %.3f ms per "
                    + "call over %d calls, more than the %.1f ms baseline (max %.3f ms)", hud.averageMillis(),
                    hud.calls(), MAX_HUD_RENDER_MILLIS, hud.maxMillis());
            VantaClientGameTest.warn(finding);
            failures.add(finding);
        } else {
            VantaClientGameTest.step(String.format(Locale.ROOT, "perf probe: VANTA HUD element %.3f ms avg per call "
                    + "is within the %.1f ms baseline", hud.averageMillis(), MAX_HUD_RENDER_MILLIS));
        }
        return failures;
    }

    private static Mode measure(ClientGameTestContext context, String name) {
        // Let the state change settle (HUD toggles re-layout, F1 changes what the Gui renders) before counting.
        context.waitTicks(10);
        context.runOnClient(client -> FrameProbe.reset());
        long start = System.nanoTime();
        int ticks = 0;
        while (ticks < MODE_TIMEOUT_TICKS && context.computeOnClient(client -> FrameProbe.frames()) < FRAMES) {
            context.waitTick();
            ticks++;
        }
        long wall = (System.nanoTime() - start) / 1_000_000L;
        FrameProbe.Snapshot snapshot = context.computeOnClient(client -> FrameProbe.snapshot());
        Mode mode = new Mode(name, snapshot, ticks, wall);
        VantaClientGameTest.step(String.format(Locale.ROOT,
                "perf probe [%s]: %d level frames in %d ticks / %d ms (%.1f fps by wall clock); game frame time avg "
                        + "%.2f ms, p50 %.2f ms, p95 %.2f ms (%.1f fps); VANTA HUD element %.3f ms avg / %.3f ms max "
                        + "over %d calls; custom crosshair %.3f ms avg over %d calls; primitives per HUD frame: %.1f "
                        + "fills, %.1f gradients, %.1f text runs, %.1f images, %.1f scissor changes, %.1f transform "
                        + "pushes, %.1f text measurements",
                name, snapshot.frames(), ticks, wall, mode.wallFps(),
                snapshot.averageFrameMillis(), snapshot.percentileFrameMillis(0.5),
                snapshot.percentileFrameMillis(0.95), snapshot.fps(),
                snapshot.hud().averageMillis(), snapshot.hud().maxMillis(), snapshot.hud().calls(),
                snapshot.crosshair().averageMillis(), snapshot.crosshair().calls(),
                snapshot.perHudCall(snapshot.fills()), snapshot.perHudCall(snapshot.gradients()),
                snapshot.perHudCall(snapshot.texts()), snapshot.perHudCall(snapshot.images()),
                snapshot.perHudCall(snapshot.scissorChanges()), snapshot.perHudCall(snapshot.transformPushes()),
                snapshot.perHudCall(snapshot.textWidths())));
        return mode;
    }

    private static JsonObject summary(ClientGameTestContext context, List<Mode> modes) {
        JsonObject root = new JsonObject();
        root.addProperty("framesPerMode", FRAMES);
        root.addProperty("modeTimeoutTicks", MODE_TIMEOUT_TICKS);
        root.addProperty("java", System.getProperty("java.version"));
        root.addProperty("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        root.add("window", context.computeOnClient(client -> {
            JsonObject window = new JsonObject();
            window.addProperty("width", client.getWindow().getScreenWidth());
            window.addProperty("height", client.getWindow().getScreenHeight());
            window.addProperty("guiScale", client.options.guiScale().get());
            window.addProperty("guiWidth", client.getWindow().getGuiScaledWidth());
            window.addProperty("guiHeight", client.getWindow().getGuiScaledHeight());
            window.addProperty("renderDistance", client.options.renderDistance().get());
            return window;
        }));
        JsonArray loaded = new JsonArray();
        FabricLoader loader = FabricLoader.getInstance();
        for (PerformancePack.Item item : PerformancePack.ITEMS) {
            if (loader.isModLoaded(item.modId())) {
                loaded.add(item.modId());
            }
        }
        root.add("performanceModsLoaded", loaded);
        JsonObject byMode = new JsonObject();
        for (Mode mode : modes) {
            byMode.add(mode.name(), modeJson(mode));
        }
        root.add("modes", byMode);
        if (modes.size() >= 2) {
            JsonObject derived = new JsonObject();
            double on = modes.get(0).snapshot().averageFrameMillis();
            double off = modes.get(1).snapshot().averageFrameMillis();
            derived.addProperty("hudFrameTimeDeltaMs", round(on - off));
            derived.addProperty("hudElementShareOfFrame",
                    on <= 0.0 ? 0.0 : round(modes.get(0).snapshot().hud().averageMillis() / on));
            root.add("derived", derived);
        }
        return root;
    }

    private static JsonObject modeJson(Mode mode) {
        FrameProbe.Snapshot s = mode.snapshot();
        JsonObject json = new JsonObject();
        json.addProperty("frames", s.frames());
        json.addProperty("ticks", mode.ticks());
        json.addProperty("wallMs", mode.wallMillis());
        json.addProperty("wallFps", round(mode.wallFps()));
        JsonObject frame = new JsonObject();
        frame.addProperty("avgMs", round(s.averageFrameMillis()));
        frame.addProperty("p50Ms", round(s.percentileFrameMillis(0.5)));
        frame.addProperty("p95Ms", round(s.percentileFrameMillis(0.95)));
        frame.addProperty("maxMs", round(s.percentileFrameMillis(1.0)));
        frame.addProperty("fps", round(s.fps()));
        json.add("gameFrameTime", frame);
        json.add("hudElement", timer(s.hud()));
        json.add("crosshairElement", timer(s.crosshair()));
        json.add("screenRender", timer(s.screen()));
        JsonObject primitives = new JsonObject();
        primitives.addProperty("fills", s.fills());
        primitives.addProperty("gradients", s.gradients());
        primitives.addProperty("texts", s.texts());
        primitives.addProperty("images", s.images());
        primitives.addProperty("scissorChanges", s.scissorChanges());
        primitives.addProperty("transformPushes", s.transformPushes());
        primitives.addProperty("textWidths", s.textWidths());
        json.add("primitivesTotal", primitives);
        JsonObject perFrame = new JsonObject();
        perFrame.addProperty("fills", round(s.perHudCall(s.fills())));
        perFrame.addProperty("gradients", round(s.perHudCall(s.gradients())));
        perFrame.addProperty("texts", round(s.perHudCall(s.texts())));
        perFrame.addProperty("images", round(s.perHudCall(s.images())));
        perFrame.addProperty("scissorChanges", round(s.perHudCall(s.scissorChanges())));
        perFrame.addProperty("transformPushes", round(s.perHudCall(s.transformPushes())));
        perFrame.addProperty("textWidths", round(s.perHudCall(s.textWidths())));
        json.add("primitivesPerHudFrame", perFrame);
        return json;
    }

    private static JsonObject timer(FrameProbe.TimerStats stats) {
        JsonObject json = new JsonObject();
        json.addProperty("calls", stats.calls());
        json.addProperty("avgMs", round(stats.averageMillis()));
        json.addProperty("maxMs", round(stats.maxMillis()));
        json.addProperty("totalMs", round(stats.totalNanos() / 1_000_000.0));
        return json;
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
