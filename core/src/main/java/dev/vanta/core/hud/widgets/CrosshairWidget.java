package dev.vanta.core.hud.widgets;

import dev.vanta.core.crosshair.CrosshairGeometry;
import dev.vanta.core.crosshair.render.CrosshairRenderer;
import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.hud.render.HudData;
import dev.vanta.core.hud.render.HudPaint;
import dev.vanta.core.hud.render.HudWidgetRenderer;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.TextMetrics;

/**
 * The crosshair widget: draws the active {@link dev.vanta.core.crosshair.CrosshairStyle} centred in its rectangle
 * through {@link CrosshairRenderer#draw}. In-game the crosshair is drawn by {@code CrosshairRenderer} in the
 * vanilla crosshair slot, so {@code HudRenderer} skips this widget; the HUD editor includes it for the preview.
 * The crosshair never draws a panel.
 */
public final class CrosshairWidget implements HudWidgetRenderer {

    @Override
    public HudWidgetType type() {
        return HudWidgetType.CROSSHAIR;
    }

    @Override
    public Size preferredSize(TextMetrics metrics, HudWidgetState state, HudData data, HudPaint paint) {
        CrosshairGeometry.Rect bounds = CrosshairGeometry.bounds(CrosshairGeometry.build(paint.crosshair(), 0, 0));
        int side = Math.max(bounds.width(), bounds.height()) + 2;
        side = Math.max(side, HudWidgetType.CROSSHAIR.defaultWidth());
        return new Size(side, side);
    }

    @Override
    public void render(Canvas canvas, HudWidgetState state, HudData data, HudPaint paint) {
        CrosshairRenderer.draw(canvas, paint.crosshair(), state.width() / 2, state.height() / 2, 0);
    }
}
