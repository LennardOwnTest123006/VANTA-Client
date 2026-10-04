package dev.vanta.core.hud.widgets;

import dev.vanta.core.bridge.KeyStates;
import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.hud.render.HudData;
import dev.vanta.core.hud.render.HudPaint;
import dev.vanta.core.hud.render.HudWidgetRenderer;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.TextMetrics;

/**
 * Keystroke display: W / A S D, the mouse buttons ({@code showMouse}, with click rates when {@code showCps}), the
 * space bar ({@code showSpace}) and the sneak key. Pressed keys fill with the widget accent colour. The states come
 * from the game's own key mappings; VANTA never presses keys.
 */
public final class KeystrokesWidget implements HudWidgetRenderer {

    /** Side of a letter key. */
    public static final int KEY = 18;
    /** Gap between keys. */
    public static final int GAP = 2;
    /** Height of the mouse row without / with click rates. */
    private static final int MOUSE_H = 16;
    private static final int MOUSE_CPS_H = 20;
    /** Height of the space and sneak bars. */
    private static final int BAR_H = 10;
    private static final int INNER_PAD = 2;

    @Override
    public HudWidgetType type() {
        return HudWidgetType.KEYSTROKES;
    }

    private static int blockWidth() {
        return KEY * 3 + GAP * 2;
    }

    private static int blockHeight(HudWidgetState state) {
        int h = KEY * 2 + GAP;
        if (state.propBool("showMouse")) {
            h += GAP + (state.propBool("showCps") ? MOUSE_CPS_H : MOUSE_H);
        }
        if (state.propBool("showSpace")) {
            h += GAP + BAR_H;
        }
        h += GAP + BAR_H;
        return h;
    }

    @Override
    public Size preferredSize(TextMetrics metrics, HudWidgetState state, HudData data, HudPaint paint) {
        return new Size(blockWidth() + INNER_PAD * 2, blockHeight(state) + INNER_PAD * 2);
    }

    @Override
    public void render(Canvas canvas, HudWidgetState state, HudData data, HudPaint paint) {
        paint.panel(canvas, state);
        KeyStates keys = data.keys();
        int blockW = blockWidth();
        int blockH = blockHeight(state);
        int x0 = Math.max(INNER_PAD, (state.width() - blockW) / 2);
        int y = Math.max(INNER_PAD, (state.height() - blockH) / 2);
        // Row 1: W
        key(canvas, state, paint, x0 + KEY + GAP, y, KEY, KEY, "W", null, keys.forward());
        y += KEY + GAP;
        // Row 2: A S D
        key(canvas, state, paint, x0, y, KEY, KEY, "A", null, keys.left());
        key(canvas, state, paint, x0 + KEY + GAP, y, KEY, KEY, "S", null, keys.back());
        key(canvas, state, paint, x0 + 2 * (KEY + GAP), y, KEY, KEY, "D", null, keys.right());
        y += KEY + GAP;
        // Row 3: mouse buttons
        if (state.propBool("showMouse")) {
            boolean cps = state.propBool("showCps");
            int h = cps ? MOUSE_CPS_H : MOUSE_H;
            int half = (blockW - GAP) / 2;
            key(canvas, state, paint, x0, y, half, h, Lang.tr("vanta.hud.label.lmb"),
                    cps ? Integer.toString(data.cpsLeft()) : null, keys.attack());
            key(canvas, state, paint, x0 + half + GAP, y, blockW - half - GAP, h, Lang.tr("vanta.hud.label.rmb"),
                    cps ? Integer.toString(data.cpsRight()) : null, keys.use());
            y += h + GAP;
        }
        // Row 4: space bar
        if (state.propBool("showSpace")) {
            key(canvas, state, paint, x0, y, blockW, BAR_H, Lang.tr("vanta.hud.label.space"), null, keys.jump());
            y += BAR_H + GAP;
        }
        // Row 5: sneak
        key(canvas, state, paint, x0, y, blockW, BAR_H, Lang.tr("vanta.hud.label.shift"), null, keys.sneak());
    }

    private static void key(Canvas canvas, HudWidgetState state, HudPaint paint, int x, int y, int w, int h,
                            String label, String sub, boolean pressed) {
        int fill = pressed ? state.accentColor() : Colors.withAlpha(state.textColor(), 0.12f);
        canvas.fillRounded(x, y, w, h, 2, fill);
        if (!pressed) {
            canvas.strokeRounded(x, y, w, h, 2, Colors.withAlpha(state.textColor(), 0.10f));
        }
        int text = pressed ? Colors.WHITE : state.textColor();
        FontKind font = label.length() == 1 ? FontKind.UI_BOLD : FontKind.UI;
        int lh = HudPaint.LINE_HEIGHT;
        if (sub == null) {
            int ty = y + (h - lh) / 2 + (h <= BAR_H ? 0 : 1);
            if (h <= BAR_H) {
                ty = y + (h - lh) / 2 + 1;
            }
            canvas.text(label, x + (w - canvas.textWidth(label, font)) / 2, ty, text, font, paint.textShadow());
        } else {
            int block = lh * 2 - 1;
            int ty = y + (h - block) / 2 + 1;
            canvas.text(label, x + (w - canvas.textWidth(label, font)) / 2, ty, text, font, paint.textShadow());
            int subColor = pressed ? Colors.withAlpha(Colors.WHITE, 0.8f) : paint.muted(state);
            canvas.text(sub, x + (w - canvas.textWidth(sub, FontKind.UI)) / 2, ty + lh - 1, subColor, FontKind.UI,
                    paint.textShadow());
        }
    }
}
