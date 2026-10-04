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
 * Durability of the held items: one row per hand (off hand with {@code showOffhand}) with the item name, the
 * remaining durability (or a percentage with {@code showPercent}) and a colour-coded bar. Items without durability
 * show their name only; an empty hand shows "Empty".
 */
public final class ItemDurabilityWidget implements HudWidgetRenderer {

    /** Height of one item row (text + bar). */
    public static final int ROW_HEIGHT = 12;

    @Override
    public HudWidgetType type() {
        return HudWidgetType.ITEM_DURABILITY;
    }

    private static int rows(HudWidgetState state, HudData data) {
        boolean offhand = state.propBool("showOffhand") && data.heldItems().size() > 1;
        return offhand ? 2 : 1;
    }

    private static ItemInfo item(HudData data, int index) {
        List<ItemInfo> held = data.heldItems();
        return index < held.size() ? held.get(index) : ItemInfo.EMPTY;
    }

    @Override
    public Size preferredSize(TextMetrics metrics, HudWidgetState state, HudData data, HudPaint paint) {
        int pad = paint.padding();
        int rows = rows(state, data);
        int w = metrics.textWidth(Lang.tr("vanta.hud.label.empty_hand"), FontKind.UI);
        for (int i = 0; i < rows; i++) {
            ItemInfo item = item(data, i);
            if (item.isEmpty()) {
                continue;
            }
            int valueW = item.hasDurability() ? HudPaint.GAP + metrics.textWidth(value(state, item), FontKind.UI) : 0;
            w = Math.max(w, metrics.textWidth(item.name(), FontKind.UI) + valueW);
        }
        return new Size(w + pad * 2, pad * 2 + rows * ROW_HEIGHT - 1);
    }

    @Override
    public void render(Canvas canvas, HudWidgetState state, HudData data, HudPaint paint) {
        paint.panel(canvas, state);
        int pad = paint.padding();
        int availW = Math.max(0, state.width() - pad * 2);
        int rows = rows(state, data);
        int block = rows * ROW_HEIGHT - 1;
        int y = Math.max(pad, (state.height() - block) / 2);
        int right = pad + availW;
        int bottom = state.height() - pad;
        for (int i = 0; i < rows; i++) {
            if (y + HudPaint.LINE_HEIGHT > bottom + 1) {
                break;
            }
            ItemInfo item = item(data, i);
            if (item.isEmpty()) {
                paint.text(canvas, canvas.textClipped(Lang.tr("vanta.hud.label.empty_hand"), availW, FontKind.UI),
                        pad, y, paint.muted(state));
            } else if (!item.hasDurability()) {
                paint.text(canvas, canvas.textClipped(item.name(), availW, FontKind.UI), pad, y, state.textColor());
            } else {
                String value = value(state, item);
                int valueW = canvas.textWidth(value, FontKind.UI);
                String name = canvas.textClipped(item.name(), Math.max(0, availW - valueW - HudPaint.GAP),
                        FontKind.UI);
                paint.text(canvas, name, pad, y, state.textColor());
                double fraction = item.durabilityFraction();
                paint.textRight(canvas, value, right, y, paint.fractionColor(fraction));
                if (y + HudPaint.LINE_HEIGHT + 1 <= bottom) {
                    paint.bar(canvas, pad, y + HudPaint.LINE_HEIGHT, availW, 1, fraction, paint.track(state),
                            paint.fractionColor(fraction));
                }
            }
            y += ROW_HEIGHT;
        }
    }

    private static String value(HudWidgetState state, ItemInfo item) {
        if (state.propBool("showPercent")) {
            return ArmorWidget.percent(item);
        }
        return Integer.toString(item.durability());
    }
}
