package dev.vanta.core.hud.render;

import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.TextMetrics;

/**
 * Draws one {@link HudWidgetType}. Implementations are stateless: everything they show comes from the
 * {@link HudData} snapshot and the {@link HudWidgetState}; everything about their look comes from the
 * {@link HudPaint}.
 * <p>
 * The canvas handed to {@link #render} is already translated to the widget's top-left corner and scaled by the
 * widget and global scale, so renderers draw into {@code (0, 0, state.width(), state.height())} in unscaled GUI
 * pixels and never allocate more than a few short strings per frame.
 */
public interface HudWidgetRenderer {

    /** The widget type this renderer draws. */
    HudWidgetType type();

    /**
     * Content-based size in unscaled GUI pixels (panel padding included). The editor's "Fit to content" and newly
     * added widgets use it; the renderer itself must cope with any size the user chose.
     */
    Size preferredSize(TextMetrics metrics, HudWidgetState state, HudData data, HudPaint paint);

    /** Draws the widget into {@code (0, 0, state.width(), state.height())}. */
    void render(Canvas canvas, HudWidgetState state, HudData data, HudPaint paint);
}
