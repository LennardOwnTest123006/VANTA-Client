package dev.vanta.core.crosshair.render;

import dev.vanta.core.bridge.KeyStates;
import dev.vanta.core.crosshair.CrosshairGeometry;
import dev.vanta.core.crosshair.CrosshairStyle;
import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.Canvas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Draws the custom crosshair in the centre of the screen.
 * <p>
 * The client replaces the vanilla crosshair HUD element: when {@link #isCustomEnabled()} is true it calls
 * {@link #render(Canvas, int, int, float, boolean)} with that flag instead of the vanilla element, otherwise it lets
 * vanilla draw. The custom crosshair is enabled while the HUD is on and the layout contains an enabled crosshair
 * widget (the "Use vanilla crosshair" toggle of the crosshair screen flips that widget).
 * <p>
 * Dynamic styles spread their arms while the player moves or attacks; the expansion eases over
 * {@value #SMOOTHING_MS} ms so it never pops. The inputs are sampled once per client tick by {@link #tick()} rather
 * than once per frame, and the geometry of the active style is built once per expansion step and replayed until the
 * style or the screen centre changes. The client reports the camera perspective with {@link #setThirdPerson(boolean)}
 * so {@link CrosshairStyle#hideOnThirdPerson()} can be honoured.
 */
public final class CrosshairRenderer {

    /** Extra gap in pixels while moving. */
    public static final int MOVE_EXPANSION = 2;
    /** Extra gap in pixels while attacking or using an item. */
    public static final int ACTION_EXPANSION = 1;
    /** Time constant of the expansion easing in milliseconds. */
    public static final long SMOOTHING_MS = 90L;
    /** Largest expansion a dynamic style reaches (moving and acting at once). */
    public static final int MAX_EXPANSION = MOVE_EXPANSION + ACTION_EXPANSION;

    private final VantaServices services;
    private float expansion;
    private long lastFrameMillis = -1L;
    private boolean thirdPerson;
    private KeyStates keys = KeyStates.NONE;
    private boolean keysSampled;

    // Geometry cache: one primitive list per expansion step for the style and centre last rendered.
    private CrosshairStyle cachedStyle;
    private int cachedCx;
    private int cachedCy;
    private final List<List<CrosshairGeometry.Primitive>> cachedPrimitives =
            new ArrayList<>(Collections.nCopies(MAX_EXPANSION + 1, null));

    public CrosshairRenderer(VantaServices services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    /** True when the custom crosshair replaces the vanilla one (HUD enabled and crosshair widget enabled). */
    public boolean isCustomEnabled() {
        if (!services.settings().get(VantaSettings.HUD_ENABLED)) {
            return false;
        }
        for (HudWidgetState widget : services.hud().layout().widgets()) {
            if (widget.type() == HudWidgetType.CROSSHAIR) {
                return widget.enabled();
            }
        }
        return false;
    }

    /** The active style. */
    public CrosshairStyle style() {
        return services.crosshair().style();
    }

    /** Camera perspective reported by the client (third person hides the crosshair when the style says so). */
    public void setThirdPerson(boolean thirdPerson) {
        this.thirdPerson = thirdPerson;
    }

    public boolean isThirdPerson() {
        return thirdPerson;
    }

    /** Current eased expansion in pixels (0 for static styles). */
    public int currentExpansion() {
        return Math.round(expansion);
    }

    /**
     * Called once per client tick: samples the movement and mouse inputs a dynamic style reacts to. Static styles
     * never read the inputs. The first render samples on demand when no tick happened yet.
     */
    public void tick() {
        keys = style().dynamic() ? services.game().keyStates() : KeyStates.NONE;
        keysSampled = true;
    }

    /** The inputs sampled by the last {@link #tick()}. */
    public KeyStates keys() {
        return keys;
    }

    /**
     * Draws the crosshair centred on the screen, evaluating {@link #isCustomEnabled()} itself. Does nothing when the
     * custom crosshair is disabled, when the game is not in a world, or when the style hides it in third person.
     */
    public void render(Canvas canvas, int screenWidth, int screenHeight, float deltaTicks) {
        render(canvas, screenWidth, screenHeight, deltaTicks, isCustomEnabled());
    }

    /**
     * Draws the crosshair centred on the screen for a caller that already evaluated {@link #isCustomEnabled()} this
     * frame (the client HUD element does, to decide between vanilla and VANTA), so the layout is not scanned twice.
     */
    public void render(Canvas canvas, int screenWidth, int screenHeight, float deltaTicks, boolean customEnabled) {
        if (!customEnabled || !services.game().isInWorld()) {
            return;
        }
        CrosshairStyle style = style();
        if (thirdPerson && style.hideOnThirdPerson()) {
            return;
        }
        if (!keysSampled) {
            tick();
        }
        updateExpansion(style);
        draw(canvas, primitives(style, screenWidth / 2, screenHeight / 2, currentExpansion()));
    }

    private void updateExpansion(CrosshairStyle style) {
        int target = targetExpansion(style, keys);
        long now = services.clock().millis();
        if (lastFrameMillis < 0L || now < lastFrameMillis) {
            expansion = target;
        } else {
            float k = Math.min(1f, (now - lastFrameMillis) / (float) SMOOTHING_MS);
            expansion += (target - expansion) * k;
            if (Math.abs(target - expansion) < 0.02f) {
                expansion = target;
            }
        }
        lastFrameMillis = now;
    }

    /**
     * Geometry for the style centred at {@code (cx, cy)} with the given expansion, built once per expansion step
     * and reused until the style or the centre changes.
     */
    List<CrosshairGeometry.Primitive> primitives(CrosshairStyle style, int cx, int cy, int expansion) {
        if (cachedStyle == null || cx != cachedCx || cy != cachedCy || !style.equals(cachedStyle)) {
            cachedStyle = style;
            cachedCx = cx;
            cachedCy = cy;
            Collections.fill(cachedPrimitives, null);
        }
        if (expansion < 0 || expansion > MAX_EXPANSION) {
            return CrosshairGeometry.build(style, cx, cy, expansion);
        }
        List<CrosshairGeometry.Primitive> primitives = cachedPrimitives.get(expansion);
        if (primitives == null) {
            primitives = CrosshairGeometry.build(style, cx, cy, expansion);
            cachedPrimitives.set(expansion, primitives);
        }
        return primitives;
    }

    /** Expansion a dynamic style aims for given the pressed inputs; 0 for static styles. */
    public static int targetExpansion(CrosshairStyle style, KeyStates keys) {
        if (!style.dynamic() || keys == null) {
            return 0;
        }
        int expansion = 0;
        if (keys.forward() || keys.back() || keys.left() || keys.right() || keys.jump()) {
            expansion += MOVE_EXPANSION;
        }
        if (keys.attack() || keys.use()) {
            expansion += ACTION_EXPANSION;
        }
        return expansion;
    }

    // ---- static painter ---------------------------------------------------------------------------------------------

    /** Draws a style centred at {@code (cx, cy)} with the given dynamic expansion. */
    public static void draw(Canvas canvas, CrosshairStyle style, int cx, int cy, int expansion) {
        draw(canvas, CrosshairGeometry.build(style, cx, cy, expansion));
    }

    /** Draws geometry primitives in order (outlines come first in the list). */
    public static void draw(Canvas canvas, List<CrosshairGeometry.Primitive> primitives) {
        for (CrosshairGeometry.Primitive primitive : primitives) {
            if (primitive instanceof CrosshairGeometry.Rect r) {
                canvas.fill(r.x(), r.y(), r.width(), r.height(), r.argb());
            } else if (primitive instanceof CrosshairGeometry.Ring ring) {
                ring(canvas, ring.cx(), ring.cy(), ring.radius(), ring.thickness(), ring.argb());
            }
        }
    }

    /**
     * Fills a pixel ring: the disc of {@code radius} minus the disc of {@code radius - thickness}, using the same
     * scanline rule as {@link Canvas#circle} so rings and discs line up.
     */
    public static void ring(Canvas canvas, int cx, int cy, int radius, int thickness, int argb) {
        if (radius < 0 || thickness <= 0) {
            return;
        }
        int inner = radius - thickness;
        if (inner < 0) {
            canvas.circle(cx, cy, radius, argb);
            return;
        }
        for (int dy = -radius; dy <= radius; dy++) {
            int outerHalf = halfWidth(radius, dy);
            int y = cy + dy;
            if (Math.abs(dy) > inner) {
                canvas.fill(cx - outerHalf, y, 2 * outerHalf + 1, 1, argb);
                continue;
            }
            int innerHalf = halfWidth(inner, dy);
            int span = outerHalf - innerHalf;
            if (span <= 0) {
                continue;
            }
            canvas.fill(cx - outerHalf, y, span, 1, argb);
            canvas.fill(cx + innerHalf + 1, y, span, 1, argb);
        }
    }

    private static int halfWidth(int radius, int dy) {
        return (int) Math.floor(Math.sqrt((double) radius * radius - (double) dy * dy + 0.25));
    }
}
