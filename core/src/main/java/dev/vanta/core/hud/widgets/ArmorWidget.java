package dev.vanta.core.hud.widgets;

import dev.vanta.core.bridge.ItemInfo;
import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.hud.render.HudData;
import dev.vanta.core.hud.render.HudPaint;
import dev.vanta.core.hud.render.HudWidgetRenderer;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.TextMetrics;

import java.util.List;

/**
 * Armor points plus one durability row per worn piece ({@code showDurability}). The vertical layout lists the
 * pieces with a bar under each name; the horizontal layout puts four bars side by side. Empty slots are skipped and
 * a player without armor sees "No armor".
 */
public final class ArmorWidget implements HudWidgetRenderer {

    /** Height of a piece row in the vertical layout (text + bar). */
    public static final int ROW_HEIGHT = 12;
    private static final int COLUMN_GAP = 3;
    private static final int BAR_HEIGHT = 2;

    @Override
    public HudWidgetType type() {
        return HudWidgetType.ARMOR;
    }

    private static int wornCount(HudData data) {
        int n = 0;
        for (ItemInfo piece : data.armorPieces()) {
            if (!piece.isEmpty()) {
                n++;
            }
        }
        return n;
    }

    private static boolean horizontal(HudWidgetState state) {
        return "horizontal".equals(state.prop("layout"));
    }

    @Override
    public Size preferredSize(TextMetrics metrics, HudWidgetState state, HudData data, HudPaint paint) {
        int pad = paint.padding();
        int headerW = metrics.textWidth(Lang.tr("vanta.hud.label.armor"), FontKind.UI) + HudPaint.GAP
                + metrics.textWidth(Integer.toString(data.armorValue()), FontKind.UI_BOLD);
        int w = headerW;
        int h = HudPaint.LINE_HEIGHT;
        int worn = wornCount(data);
        if (state.propBool("showDurability")) {
            if (worn == 0) {
                w = Math.max(w, metrics.textWidth(Lang.tr("vanta.hud.label.no_armor"), FontKind.UI));
                h += 1 + HudPaint.LINE_HEIGHT;
            } else if (horizontal(state)) {
                w = Math.max(w, 4 * 14 + 3 * COLUMN_GAP);
                h += 2 + HudPaint.LINE_HEIGHT + 1 + BAR_HEIGHT;
            } else {
                for (ItemInfo piece : data.armorPieces()) {
                    if (!piece.isEmpty()) {
                        w = Math.max(w, metrics.textWidth(piece.name(), FontKind.UI) + HudPaint.GAP
                                + metrics.textWidth(percent(piece), FontKind.UI));
                    }
                }
                h += 2 + worn * ROW_HEIGHT;
            }
        }
        return new Size(w + pad * 2, h + pad * 2);
    }

    @Override
    public void render(Canvas canvas, HudWidgetState state, HudData data, HudPaint paint) {
        paint.panel(canvas, state);
        int pad = paint.padding();
        int availW = Math.max(0, state.width() - pad * 2);
        int x = pad;
        int y = pad;
        int right = pad + availW;
        boolean durability = state.propBool("showDurability");
        if (!durability) {
            y = paint.centeredLineY(state.height());
        }
        // Header: label + armor points.
        String label = Lang.tr("vanta.hud.label.armor");
        paint.text(canvas, label, x, y, state.accentColor());
        paint.value(canvas, Integer.toString(data.armorValue()), x + canvas.textWidth(label, FontKind.UI)
                + HudPaint.GAP, y, state.textColor());
        if (!durability) {
            return;
        }
        y += HudPaint.LINE_HEIGHT + 2;
        List<ItemInfo> pieces = data.armorPieces();
        if (wornCount(data) == 0) {
            paint.text(canvas, canvas.textClipped(Lang.tr("vanta.hud.label.no_armor"), availW, FontKind.UI), x, y,
                    paint.muted(state));
            return;
        }
        if (horizontal(state)) {
            int columns = 4;
            int colW = Math.max(1, (availW - COLUMN_GAP * (columns - 1)) / columns);
            int barY = y + HudPaint.LINE_HEIGHT + 1;
            if (barY + BAR_HEIGHT > state.height() - pad) {
                barY = y;
            }
            for (int i = 0; i < Math.min(columns, pieces.size()); i++) {
                ItemInfo piece = pieces.get(i);
                int cx = x + i * (colW + COLUMN_GAP);
                if (piece.isEmpty()) {
                    paint.bar(canvas, cx, barY, colW, BAR_HEIGHT, 0.0, paint.track(state), paint.track(state));
                    continue;
                }
                double fraction = piece.durabilityFraction();
                if (barY != y && colW >= 14) {
                    String text = canvas.textClipped(percent(piece), colW, FontKind.UI);
                    paint.text(canvas, text, cx, y, paint.muted(state));
                }
                paint.bar(canvas, cx, barY, colW, BAR_HEIGHT, fraction, paint.track(state),
                        paint.fractionColor(fraction));
            }
            return;
        }
        int bottom = state.height() - pad;
        for (ItemInfo piece : pieces) {
            if (piece.isEmpty()) {
                continue;
            }
            if (y + HudPaint.LINE_HEIGHT > bottom) {
                break;
            }
            String value = percent(piece);
            int valueW = canvas.textWidth(value, FontKind.UI);
            String name = canvas.textClipped(piece.name(), Math.max(0, availW - valueW - HudPaint.GAP), FontKind.UI);
            paint.text(canvas, name, x, y, state.textColor());
            paint.textRight(canvas, value, right, y, paint.muted(state));
            double fraction = piece.durabilityFraction();
            if (y + HudPaint.LINE_HEIGHT + 1 < bottom) {
                paint.bar(canvas, x, y + HudPaint.LINE_HEIGHT, availW, 1, fraction, paint.track(state),
                        paint.fractionColor(fraction));
            }
            y += ROW_HEIGHT;
        }
    }

    /** Remaining durability as a percentage string. */
    static String percent(ItemInfo item) {
        return Lang.tr("vanta.common.percent", (int) Math.round(item.durabilityFraction() * 100.0));
    }
}
