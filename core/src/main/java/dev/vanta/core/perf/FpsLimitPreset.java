package dev.vanta.core.perf;

import dev.vanta.core.i18n.LangKeyed;
import java.util.Locale;

/**
 * Quick frame-rate limit choices. Each maps to the vanilla {@code framerateLimit} option (and {@code enableVsync} for
 * {@link #VSYNC}); 260 is vanilla's "unlimited" value.
 */
public enum FpsLimitPreset implements LangKeyed {
    /** Vertical sync on, limit unlimited (the monitor refresh rate caps the frame rate). */
    VSYNC(260, true),
    FPS_60(60, false),
    FPS_120(120, false),
    FPS_144(140, false),
    FPS_240(240, false),
    UNLIMITED(260, false);

    /** Vanilla value meaning "unlimited". */
    public static final int VANILLA_UNLIMITED = 260;

    private final int framerateLimit;
    private final boolean vsync;

    FpsLimitPreset(int framerateLimit, boolean vsync) {
        this.framerateLimit = framerateLimit;
        this.vsync = vsync;
    }

    /** Value for {@code VanillaOption.FRAMERATE_LIMIT} (vanilla steps are multiples of 10, so 144 → 140). */
    public int framerateLimit() {
        return framerateLimit;
    }

    /** Value for {@code VanillaOption.VSYNC}. */
    public boolean vsync() {
        return vsync;
    }

    /** True when the limit is "unlimited". */
    public boolean isUnlimited() {
        return framerateLimit >= VANILLA_UNLIMITED;
    }

    @Override
    public String langKey() {
        return "vanta.perf.fps_limit." + name().toLowerCase(Locale.ROOT);
    }

    /** Target frame rate for advisors: the limit, or 0 when unlimited/vsync. */
    public int targetFps() {
        return isUnlimited() ? 0 : framerateLimit;
    }
}
