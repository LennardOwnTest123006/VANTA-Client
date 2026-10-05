package dev.vanta.core.screen;

/**
 * Public links used by the About screen and the "open website" action. The repository URL is the one declared in the
 * mod metadata; the website URL is optional configuration supplied by the host (never a made-up address).
 */
public final class VantaLinks {
    /** Source repository (from {@code fabric.mod.json}). */
    public static final String REPOSITORY = "https://github.com/LennardOwnTest123006/VANTA-Client";
    /** Issue tracker. */
    public static final String ISSUES = REPOSITORY + "/issues";
    /** Documentation folder in the repository. */
    public static final String DOCUMENTATION = REPOSITORY + "/tree/HEAD/docs";

    private VantaLinks() {
    }
}
