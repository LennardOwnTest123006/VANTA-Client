package dev.vanta.core.ui;

import dev.vanta.core.ui.widget.Tooltip;

import java.util.Objects;

/**
 * Base class of every VANTA screen. A screen owns a {@link UiContext}, a root {@link UiNode} built by
 * {@link #build(UiContext)}, the popup layer, hover/tooltip tracking and the open/close transition.
 * <p>
 * Lifecycle driven by the host: {@link #attach(UiEnvironment)} once, {@link #init(int, int)} when shown or
 * resized, then {@link #render(Canvas, int, int, float)} every frame and the input methods as events arrive.
 * Coordinates passed in are host GUI pixels; the screen divides them by {@link #effectiveScale()} so the tree
 * works in logical pixels.
 * <p>
 * The scale actually used is the theme's {@link Theme#effectiveScale()} (user UI scale times the large-text
 * bonus), reduced when it would leave fewer than {@value #MIN_LOGICAL_WIDTH}x{@value #MIN_LOGICAL_HEIGHT} logical
 * pixels: Minecraft guarantees that many GUI pixels and every screen is laid out to fit them, so a zoom that
 * shrinks a 320x240 window to 256x192 would push buttons off screen. The clamp never lowers the scale below 1
 * (scales below 1 only enlarge the logical area) and the same value is used for layout, rendering and input.
 */
public abstract class UiScreen {

    /** Duration of the open and close transitions in ms. */
    public static final long TRANSITION_MS = 160L;
    /** Minimum logical width every screen is laid out for (Minecraft's smallest GUI size). */
    public static final int MIN_LOGICAL_WIDTH = 320;
    /** Minimum logical height every screen is laid out for (Minecraft's smallest GUI size). */
    public static final int MIN_LOGICAL_HEIGHT = 240;
    private static final float TRANSITION_SCALE = 0.96f;
    /** Logical pixels the Vanta Lab slide transition moves the screen by. */
    public static final int SLIDE_PX = 14;
    /** Most layout passes one {@link #doLayout()} runs when nodes keep requesting another one. */
    static final int MAX_LAYOUT_PASSES = 4;

    private UiEnvironment env;
    private UiContext ctx;
    private UiNode root;
    private int hostWidth;
    private int hostHeight;
    private int width;
    private int height;
    private boolean initialised;
    private boolean layoutDirty;
    private long openedAt;
    private long closeStartedAt = -1L;
    private boolean closeDelivered;
    private UiNode hoverNode;
    private long hoverSince;
    private long lastFocusChange = -1L;

    // ---------------------------------------------------------------- lifecycle

    /** Supplies the environment; must be called before {@link #init(int, int)}. */
    public final void attach(UiEnvironment environment) {
        this.env = Objects.requireNonNull(environment, "environment");
        if (ctx == null) {
            this.ctx = new UiContext(env, () -> layoutDirty = true);
        } else {
            ctx.setTheme(env.theme());
        }
    }

    /**
     * Builds (first call) or re-lays out the tree for the given host size. Called by the host when the screen
     * is shown and whenever the window is resized.
     */
    public final void init(int hostWidth, int hostHeight) {
        if (ctx == null) {
            throw new IllegalStateException("attach(UiEnvironment) must be called before init");
        }
        this.hostWidth = hostWidth;
        this.hostHeight = hostHeight;
        updateLogicalSize();
        if (!initialised) {
            root = Objects.requireNonNull(build(ctx), "build(ctx) returned null");
            ctx.popups().setBaseScope(() -> root);
            ctx.focus().setScope(() -> root);
            openedAt = ctx.now();
            initialised = true;
            doLayout();
            onInit();
        } else {
            doLayout();
            onResize();
        }
    }

    /** Alias for {@link #init(int, int)} after a window resize. */
    public final void resize(int newHostWidth, int newHostHeight) {
        init(newHostWidth, newHostHeight);
    }

    private void updateLogicalSize() {
        float s = usedScale();
        width = Math.max(1, Math.round(hostWidth / s));
        height = Math.max(1, Math.round(hostHeight / s));
        ctx.setScreenSize(width, height);
    }

    /**
     * The scale every coordinate conversion of this screen uses: the theme's effective scale, lowered (never
     * below 1) so that {@code hostWidth / scale >= MIN_LOGICAL_WIDTH} and {@code hostHeight / scale >=
     * MIN_LOGICAL_HEIGHT}. Before {@link #init} the host size is unknown and the theme scale is returned as is.
     */
    private float usedScale() {
        float s = ctx.theme().effectiveScale();
        if (s <= 1f || hostWidth <= 0 || hostHeight <= 0) {
            return s;
        }
        float allowed = Math.min(hostWidth / (float) MIN_LOGICAL_WIDTH, hostHeight / (float) MIN_LOGICAL_HEIGHT);
        return Math.min(s, Math.max(1f, allowed));
    }

    /**
     * Lays the tree out and re-runs the pass while a node asked for another one from inside its {@code layout()}
     * (grids that only learn their width during the pass, footers that wrap). Bounded so two nodes that keep
     * contradicting each other cannot stall a frame; the flag is cleared afterwards in every case.
     */
    private void doLayout() {
        for (int pass = 0; pass < MAX_LAYOUT_PASSES; pass++) {
            layoutDirty = false;
            root.setBounds(0, 0, width, height);
            root.layout(ctx);
            ctx.popups().layout(ctx);
            if (!layoutDirty) {
                break;
            }
        }
        layoutDirty = false;
        ctx.focus().validate(ctx);
    }

    /** Creates the root node of the screen. Called once from {@link #init(int, int)}. */
    protected abstract UiNode build(UiContext ctx);

    /** Hook after the first layout. */
    protected void onInit() {
    }

    /** Hook after a resize layout. */
    protected void onResize() {
    }

    /** Called by the host when the screen was removed (after the close transition or when replaced). */
    public void onClose() {
    }

    /** Human readable title (for narration / window title). */
    public String title() {
        return "VANTA";
    }

    /** Whether the game should pause while the screen is open (singleplayer). */
    public boolean isPauseScreen() {
        return true;
    }

    /** Whether the host should blur the world behind the screen. */
    public boolean wantsBlur() {
        return theme().wantsBlur();
    }

    /** Whether Escape closes the screen when no node consumed it. */
    public boolean shouldCloseOnEsc() {
        return true;
    }

    // ---------------------------------------------------------------- accessors

    /** The screen context (available after {@link #attach}). */
    public final UiContext context() {
        return ctx;
    }

    /** The root node (available after {@link #init}). */
    public final UiNode root() {
        return root;
    }

    /** Active theme. */
    public final Theme theme() {
        return ctx.theme();
    }

    /** Logical width (host width divided by the effective scale). */
    public final int width() {
        return width;
    }

    /** Logical height. */
    public final int height() {
        return height;
    }

    /**
     * Factor between host pixels and logical pixels: the theme's {@link Theme#effectiveScale()}, clamped so the
     * logical area never falls below {@value #MIN_LOGICAL_WIDTH}x{@value #MIN_LOGICAL_HEIGHT} (see the class
     * comment). Layout, rendering and every input method divide by this same value.
     */
    public final float effectiveScale() {
        return usedScale();
    }

    /** Whether {@link #init} has run. */
    public final boolean isInitialised() {
        return initialised;
    }

    /** Swaps the theme at runtime and re-lays out. */
    public final void setTheme(Theme theme) {
        ctx.setTheme(theme);
        if (initialised) {
            updateLogicalSize();
            doLayout();
        }
    }

    /** Marks the layout dirty; it re-runs before the next frame. */
    public final void invalidateLayout() {
        layoutDirty = true;
    }

    // ---------------------------------------------------------------- transition

    /** Starts the close transition; the host's {@code closeScreen()} fires when it finishes. */
    public final void close() {
        if (closeStartedAt >= 0L) {
            return;
        }
        ctx.popups().closeAll(ctx);
        closeStartedAt = ctx.now();
        onCloseStarted(closeStartedAt);
        if (theme().reducedMotion()) {
            deliverClose();
        }
    }

    /** Hook when {@link #close()} started the close transition (screens forward it to the Vanta Lab effects). */
    protected void onCloseStarted(long now) {
    }

    /**
     * Progress of the Vanta Lab slide transition on top of the built-in fade: 0..1 while opening, 1..0 while
     * closing, 1 when the feature is off (the default). Screens that know the services override it with
     * {@code LabEffects.transitionProgress}. Input is never blocked by it: hit testing ignores the slide.
     */
    protected float slideProgress(long now) {
        return 1f;
    }

    /** Whether the close transition is running or finished. */
    public final boolean isClosing() {
        return closeStartedAt >= 0L;
    }

    /**
     * Eased progress of the current transition: rises 0→1 while opening, falls 1→0 while closing. Always 1 when
     * reduced motion is on and no close is pending.
     */
    public final float transitionProgress() {
        if (closeStartedAt >= 0L) {
            float p = ctx.animator().progress(closeStartedAt, TRANSITION_MS, Easing.IN_CUBIC);
            return 1f - p;
        }
        return ctx.animator().progress(openedAt, TRANSITION_MS, Easing.OUT_CUBIC);
    }

    private void deliverClose() {
        if (!closeDelivered) {
            closeDelivered = true;
            ctx.host().closeScreen();
        }
    }

    // ---------------------------------------------------------------- rendering

    /**
     * Renders the whole screen. {@code mouseX}/{@code mouseY} are host pixels; {@code dt} is the frame delta in
     * ticks as reported by the host.
     */
    public final void render(Canvas canvas, int mouseX, int mouseY, float dt) {
        if (!initialised) {
            throw new IllegalStateException("init(width, height) must be called before render");
        }
        float s = effectiveScale();
        ctx.setMouse(mouseX / s, mouseY / s);
        ctx.setDeltaTicks(dt);
        if (layoutDirty) {
            doLayout();
        }
        ctx.focus().validate(ctx);
        updateHover();
        updateFocusVisibility();

        float progress = transitionProgress();
        float alpha = progress;
        float zoom = TRANSITION_SCALE + (1f - TRANSITION_SCALE) * progress;

        float previousAlpha = canvas.partialAlpha();
        canvas.setPartialAlpha(previousAlpha * alpha);
        renderBackground(canvas, ctx);

        boolean scaled = s != 1f;
        boolean zoomed = zoom < 0.999f;
        float slide = theme().reducedMotion() ? 1f : slideProgress(ctx.now());
        boolean slid = slide < 0.999f;
        if (zoomed) {
            float cx = hostWidth / 2f;
            float cy = hostHeight / 2f;
            canvas.pushTranslate(cx, cy);
            canvas.pushScale(zoom, zoom);
            canvas.pushTranslate(-cx, -cy);
        }
        if (slid) {
            canvas.pushTranslate(0f, (1f - Math.max(0f, slide)) * SLIDE_PX * s);
        }
        if (scaled) {
            canvas.pushScale(s, s);
        }
        root.render(canvas, ctx);
        ctx.popups().render(canvas, ctx);
        renderOverlay(canvas, ctx);
        renderTooltip(canvas);
        if (scaled) {
            canvas.pop();
        }
        if (slid) {
            canvas.pop();
        }
        if (zoomed) {
            canvas.pop();
            canvas.pop();
            canvas.pop();
        }
        canvas.setPartialAlpha(previousAlpha);

        if (closeStartedAt >= 0L && progress <= 0f) {
            deliverClose();
        }
    }

    /**
     * Draws what sits behind the tree: by default a blur request plus the dim layer. Drawn in host pixels.
     */
    protected void renderBackground(Canvas canvas, UiContext context) {
        if (wantsBlur()) {
            canvas.blurBackground();
        }
        canvas.fill(0, 0, canvas.width(), canvas.height(), context.theme().screenDim());
    }

    /** Hook drawn above popups and below tooltips, in logical pixels. */
    protected void renderOverlay(Canvas canvas, UiContext context) {
    }

    private void updateHover() {
        UiNode hit = null;
        if (!isClosing()) {
            if (ctx.popups().isOpen()) {
                hit = ctx.popups().hitTest(ctx.mouseX(), ctx.mouseY());
                if (hit == null && !ctx.popups().top().modal()) {
                    hit = root.hitTest(ctx.mouseX(), ctx.mouseY());
                }
            } else {
                hit = root.hitTest(ctx.mouseX(), ctx.mouseY());
            }
        }
        UiNode capture = ctx.mouseCapture();
        if (capture != null) {
            hit = capture;
        }
        if (hit != hoverNode) {
            hoverNode = hit;
            hoverSince = ctx.now();
        }
        clearHover(root);
        for (PopupLayer.Popup p : ctx.popups().popups()) {
            clearHover(p.node());
        }
        for (UiNode n = hit; n != null; n = n.parent()) {
            n.setHovered(true);
        }
    }

    private static void clearHover(UiNode node) {
        node.setHovered(false);
        for (UiNode c : node.children()) {
            clearHover(c);
        }
    }

    private void updateFocusVisibility() {
        long changed = ctx.focus().changedAt();
        if (changed != lastFocusChange) {
            lastFocusChange = changed;
            UiNode focused = ctx.focus().focused();
            if (focused != null) {
                ScrollIntoView.reveal(ctx, focused);
            }
        }
    }

    private void renderTooltip(Canvas canvas) {
        if (hoverNode == null || isClosing()) {
            return;
        }
        String text = null;
        for (UiNode n = hoverNode; n != null && text == null; n = n.parent()) {
            text = n.tooltipText();
        }
        if (text == null || text.isBlank()) {
            return;
        }
        if (ctx.now() - hoverSince < Tooltip.DELAY_MS) {
            return;
        }
        Tooltip.render(canvas, ctx, text, (int) ctx.mouseX(), (int) ctx.mouseY());
    }

    /** The node currently under the cursor (or the mouse-capturing node). */
    public final UiNode hoveredNode() {
        return hoverNode;
    }

    // ---------------------------------------------------------------- ticks

    /** Game tick (20 Hz). */
    public final void tick() {
        if (!initialised) {
            return;
        }
        root.onTick(ctx);
        ctx.popups().tick(ctx);
        onTick();
    }

    /** Tick hook for subclasses. */
    protected void onTick() {
    }

    // ---------------------------------------------------------------- input

    /** Mouse press in host pixels. */
    public boolean mouseDown(double x, double y, int button) {
        if (!initialised || isClosing()) {
            return isClosing();
        }
        float s = effectiveScale();
        double lx = x / s;
        double ly = y / s;
        ctx.setMouse(lx, ly);
        if (ctx.popups().isOpen() && ctx.popups().mouseDown(ctx, lx, ly, button)) {
            return true;
        }
        boolean consumed = root.mouseDown(ctx, lx, ly, button);
        if (!consumed && button == Keys.MOUSE_LEFT) {
            ctx.focus().clear(ctx);
        }
        return consumed || onMouseDown(lx, ly, button);
    }

    /** Mouse release in host pixels. */
    public boolean mouseUp(double x, double y, int button) {
        if (!initialised || isClosing()) {
            return false;
        }
        float s = effectiveScale();
        double lx = x / s;
        double ly = y / s;
        ctx.setMouse(lx, ly);
        UiNode capture = ctx.mouseCapture();
        if (capture != null) {
            boolean consumed = capture.mouseUp(ctx, lx, ly, button);
            ctx.releaseMouse();
            return consumed;
        }
        if (ctx.popups().isOpen() && ctx.popups().mouseUp(ctx, lx, ly, button)) {
            return true;
        }
        return root.mouseUp(ctx, lx, ly, button);
    }

    /** Mouse drag in host pixels. */
    public boolean mouseDrag(double x, double y, int button, double dx, double dy) {
        if (!initialised || isClosing()) {
            return false;
        }
        float s = effectiveScale();
        double lx = x / s;
        double ly = y / s;
        ctx.setMouse(lx, ly);
        UiNode capture = ctx.mouseCapture();
        if (capture != null) {
            return capture.mouseDrag(ctx, lx, ly, button, dx / s, dy / s);
        }
        if (ctx.popups().isOpen() && ctx.popups().mouseDrag(ctx, lx, ly, button, dx / s, dy / s)) {
            return true;
        }
        return root.mouseDrag(ctx, lx, ly, button, dx / s, dy / s);
    }

    /** Mouse wheel in host pixels. */
    public boolean mouseScroll(double x, double y, double scrollX, double scrollY) {
        if (!initialised || isClosing()) {
            return false;
        }
        float s = effectiveScale();
        double lx = x / s;
        double ly = y / s;
        ctx.setMouse(lx, ly);
        if (ctx.popups().isOpen() && ctx.popups().mouseScroll(ctx, lx, ly, scrollX, scrollY)) {
            return true;
        }
        return root.mouseScroll(ctx, lx, ly, scrollX, scrollY);
    }

    /** Key press (GLFW code, scancode, modifier bits). */
    public boolean keyDown(int key, int scancode, int mods) {
        if (!initialised || isClosing()) {
            return false;
        }
        for (UiNode n = ctx.focus().focused(); n != null; n = n.parent()) {
            if (n.keyDown(ctx, key, scancode, mods)) {
                return true;
            }
        }
        if (ctx.popups().keyDown(ctx, key, scancode, mods)) {
            return true;
        }
        switch (key) {
            case Keys.TAB -> {
                if (Keys.hasShift(mods)) {
                    ctx.focus().focusPrev(ctx);
                } else {
                    ctx.focus().focusNext(ctx);
                }
                return true;
            }
            case Keys.DOWN, Keys.RIGHT -> {
                if (ctx.focus().focused() != null) {
                    ctx.focus().focusNext(ctx);
                    return true;
                }
            }
            case Keys.UP, Keys.LEFT -> {
                if (ctx.focus().focused() != null) {
                    ctx.focus().focusPrev(ctx);
                    return true;
                }
            }
            case Keys.ESCAPE -> {
                if (shouldCloseOnEsc()) {
                    close();
                    return true;
                }
            }
            default -> {
            }
        }
        if (ctx.popups().isOpen()) {
            // Screen shortcuts (Ctrl+F, Ctrl+K, editor keys) act on the tree behind the popup; while one is open
            // they would move focus out of the popup's scope, where validate() drops it on the next frame.
            return false;
        }
        return onKeyDown(key, scancode, mods);
    }

    /** Key release. */
    public boolean keyUp(int key, int scancode, int mods) {
        if (!initialised || isClosing()) {
            return false;
        }
        for (UiNode n = ctx.focus().focused(); n != null; n = n.parent()) {
            if (n.keyUp(ctx, key, scancode, mods)) {
                return true;
            }
        }
        return false;
    }

    /** Character typed. */
    public boolean charTyped(int codePoint, int mods) {
        if (!initialised || isClosing()) {
            return false;
        }
        for (UiNode n = ctx.focus().focused(); n != null; n = n.parent()) {
            if (n.charTyped(ctx, codePoint, mods)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Screen-level key hook after nodes and focus navigation declined the key. Not called while a popup is open:
     * the popup (dropdown, palette, dialog) owns the keyboard until it closes.
     */
    protected boolean onKeyDown(int key, int scancode, int mods) {
        return false;
    }

    /** Screen-level mouse hook after nodes declined the press (logical coordinates). */
    protected boolean onMouseDown(double x, double y, int button) {
        return false;
    }
}
