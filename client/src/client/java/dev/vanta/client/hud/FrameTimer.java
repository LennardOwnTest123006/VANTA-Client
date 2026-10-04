package dev.vanta.client.hud;

import dev.vanta.core.perf.PerformanceCenter;
import java.util.Objects;
import net.minecraft.client.Minecraft;

/**
 * Feeds the game's measured frame time into the {@link PerformanceCenter} once per frame.
 * <p>
 * {@code Minecraft.getFrameTimeNs()} is the duration of the last frame as measured by the game loop. It is sampled
 * from the HUD element while a world is rendered and from {@link dev.vanta.client.screen.VantaScreen} when no world is
 * loaded, so exactly one sample per frame reaches the tracker and no render event hooks are needed.
 */
public final class FrameTimer {
    private final PerformanceCenter performance;

    public FrameTimer(PerformanceCenter performance) {
        this.performance = Objects.requireNonNull(performance, "performance");
    }

    /** Called by the HUD element every frame the HUD is drawn (in-world). */
    public void onHudFrame(Minecraft minecraft) {
        feed(minecraft);
    }

    /** Called by VANTA screens every frame; only counts when no world is rendered (the HUD feeds otherwise). */
    public void onScreenFrame(Minecraft minecraft) {
        if (minecraft.level == null) {
            feed(minecraft);
        }
    }

    /** Last frame duration in milliseconds as measured by the game. */
    public static double lastFrameMillis(Minecraft minecraft) {
        return minecraft == null ? 0.0 : minecraft.getFrameTimeNs() / 1_000_000.0;
    }

    private void feed(Minecraft minecraft) {
        double millis = lastFrameMillis(minecraft);
        if (millis > 0.0) {
            performance.onFrame(millis);
        }
    }
}
