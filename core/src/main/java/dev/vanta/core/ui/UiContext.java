package dev.vanta.core.ui;

import java.util.Objects;
import java.util.function.Function;

/**
 * Per-screen mutable state shared by every node during layout, rendering and input: theme, clock, animator,
 * text metrics, focus, popups, mouse position and the host services.
 */
public final class UiContext {

    private final UiHost host;
    private final Clock clock;
    private final Animator animator;
    private final TextMetrics metrics;
    private final Function<String, String> lang;
    private final FocusManager focus;
    private final PopupLayer popups;
    private final Runnable layoutRequest;
    private Theme theme;
    private double mouseX;
    private double mouseY;
    private float deltaTicks;
    private int screenWidth;
    private int screenHeight;
    private UiNode mouseCapture;

    /**
     * Creates a context.
     *
     * @param env           host environment
     * @param layoutRequest callback that marks the owning screen's layout dirty
     */
    public UiContext(UiEnvironment env, Runnable layoutRequest) {
        Objects.requireNonNull(env, "env");
        this.host = env.host();
        this.theme = env.theme();
        this.clock = env.clock();
        this.animator = new Animator(clock, theme.reducedMotion());
        this.metrics = env.metrics();
        this.lang = env.lang();
        this.focus = new FocusManager();
        this.popups = new PopupLayer();
        this.layoutRequest = layoutRequest == null ? () -> { } : layoutRequest;
    }

    /** Host services. */
    public UiHost host() {
        return host;
    }

    /** Active theme. */
    public Theme theme() {
        return theme;
    }

    /** Swaps the theme (keeps the animator's reduced-motion flag in sync) and requests layout. */
    public void setTheme(Theme newTheme) {
        this.theme = Objects.requireNonNull(newTheme, "theme");
        animator.setReducedMotion(newTheme.reducedMotion());
        requestLayout();
    }

    /** Time source. */
    public Clock clock() {
        return clock;
    }

    /** Current time in ms. */
    public long now() {
        return clock.nowMillis();
    }

    /** Animation factory. */
    public Animator animator() {
        return animator;
    }

    /** Text measuring. */
    public TextMetrics metrics() {
        return metrics;
    }

    /** Width of a string in the given font. */
    public int textWidth(String text, FontKind font) {
        return metrics.textWidth(text, font);
    }

    /** Line height of the given font. */
    public int lineHeight(FontKind font) {
        return metrics.lineHeight(font);
    }

    /** Translates a key; unknown keys are returned as-is. */
    public String tr(String key) {
        String value = lang.apply(key);
        return value == null ? key : value;
    }

    /** Translates and formats with {@link String#format}. */
    public String tr(String key, Object... args) {
        String pattern = tr(key);
        try {
            return String.format(pattern, args);
        } catch (RuntimeException e) {
            return pattern;
        }
    }

    /** Focus state. */
    public FocusManager focus() {
        return focus;
    }

    /** Popup stack (dropdowns, dialogs). */
    public PopupLayer popups() {
        return popups;
    }

    /** Mouse x in logical GUI pixels. */
    public double mouseX() {
        return mouseX;
    }

    /** Mouse y in logical GUI pixels. */
    public double mouseY() {
        return mouseY;
    }

    /** Updates the mouse position (called by the screen). */
    public void setMouse(double x, double y) {
        this.mouseX = x;
        this.mouseY = y;
    }

    /** Frame delta in ticks as reported by the host (informational). */
    public float deltaTicks() {
        return deltaTicks;
    }

    /** Updates the frame delta. */
    public void setDeltaTicks(float dt) {
        this.deltaTicks = dt;
    }

    /** Logical screen width. */
    public int screenWidth() {
        return screenWidth;
    }

    /** Logical screen height. */
    public int screenHeight() {
        return screenHeight;
    }

    /** Updates the logical screen size. */
    public void setScreenSize(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
    }

    /** Logical screen bounds. */
    public Rect screenBounds() {
        return new Rect(0, 0, screenWidth, screenHeight);
    }

    /** Node currently receiving drag/up events regardless of position, or {@code null}. */
    public UiNode mouseCapture() {
        return mouseCapture;
    }

    /** Routes subsequent drag/up events to {@code node} until released. */
    public void captureMouse(UiNode node) {
        this.mouseCapture = node;
    }

    /** Ends mouse capture. */
    public void releaseMouse() {
        this.mouseCapture = null;
    }

    /** Marks layout dirty; the screen re-runs layout before the next render. */
    public void requestLayout() {
        layoutRequest.run();
    }

    /** Plays the click sound through the host. */
    public void playClick() {
        host.playClick();
    }
}
