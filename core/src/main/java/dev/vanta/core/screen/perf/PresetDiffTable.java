package dev.vanta.core.screen.perf;

import dev.vanta.core.i18n.Lang;
import dev.vanta.core.screen.common.SettingFormats;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;

/**
 * Three-column table rendering a {@link PresetDiff}: option name, current value, value after applying. Rows that
 * change get an accent dot and a bright target value; unchanged rows stay muted; unsupported options say so.
 */
public class PresetDiffTable extends UiNode {
    /** Height of one row. */
    public static final int ROW_H = 13;
    /** Height of the header line. */
    public static final int HEADER_H = 14;
    private static final int ARROW_W = 12;

    private PresetDiff diff;

    public PresetDiffTable(PresetDiff diff) {
        this.diff = diff;
    }

    /** Replaces the diff. */
    public PresetDiffTable setDiff(PresetDiff next) {
        this.diff = next;
        return this;
    }

    public PresetDiff diff() {
        return diff;
    }

    @Override
    protected Size measure(UiContext ctx) {
        int rows = diff == null ? 0 : diff.rows().size();
        return new Size(240, HEADER_H + rows * ROW_H);
    }

    private static String format(PresetDiff.Row row, Object value) {
        return SettingFormats.formatVanilla(row.option(), value);
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        if (diff == null) {
            return;
        }
        Theme theme = ctx.theme();
        Rect b = bounds();
        int nameW = Math.round(b.w() * 0.42f);
        int valueW = (b.w() - nameW - ARROW_W) / 2;
        int currentX = b.x() + nameW;
        int arrowX = currentX + valueW;
        int afterX = arrowX + ARROW_W;
        int lh = canvas.lineHeight(FontKind.UI);
        int hy = b.y() + (HEADER_H - lh) / 2;
        canvas.text(Lang.tr("vanta.perf.diff.option"), b.x() + Theme.SPACE_3, hy, theme.textMuted(), FontKind.UI_BOLD,
                false);
        canvas.textRight(Lang.tr("vanta.perf.diff.current"), arrowX - Theme.SPACE_2, hy, theme.textMuted(),
                FontKind.UI_BOLD, false);
        canvas.text(Lang.tr("vanta.perf.diff.after"), afterX, hy, theme.textMuted(), FontKind.UI_BOLD, false);
        canvas.fill(b.x(), b.y() + HEADER_H - 1, b.w(), 1, Colors.withAlpha(theme.borderSubtle(), 0.9f));
        int y = b.y() + HEADER_H;
        for (PresetDiff.Row row : diff.rows()) {
            int ty = y + (ROW_H - lh) / 2;
            if (row.changes()) {
                canvas.fillRounded(b.x(), y, b.w(), ROW_H, Theme.RADIUS_SM, Colors.withAlpha(theme.accent(), 0.08f));
                canvas.circle(b.x() + 3, y + ROW_H / 2, 1, theme.accentHover());
            }
            int nameColor = row.changes() ? theme.textPrimary() : theme.textSecondary();
            canvas.text(canvas.textClipped(Lang.tr(row.option().langKey()), nameW - Theme.SPACE_3 - Theme.SPACE_2,
                    FontKind.UI), b.x() + Theme.SPACE_3 + 4, ty, nameColor, FontKind.UI, false);
            if (!row.supported()) {
                canvas.text(canvas.textClipped(Lang.tr("vanta.perf.diff.unsupported"), valueW * 2 + ARROW_W, FontKind.UI),
                        currentX, ty, theme.textMuted(), FontKind.UI, false);
            } else {
                String current = row.current().map(v -> format(row, v)).orElse(Lang.tr("vanta.common.unknown"));
                canvas.textRight(canvas.textClipped(current, valueW - Theme.SPACE_2, FontKind.UI), arrowX - Theme.SPACE_2,
                        ty, theme.textMuted(), FontKind.UI, false);
                canvas.text("→", arrowX + 2, ty, row.changes() ? theme.accentHover() : Colors.withAlpha(theme.textMuted(),
                        0.6f), FontKind.UI, false);
                canvas.text(canvas.textClipped(format(row, row.target()), valueW, row.changes() ? FontKind.UI_BOLD
                        : FontKind.UI), afterX, ty, row.changes() ? theme.textPrimary() : theme.textMuted(),
                        row.changes() ? FontKind.UI_BOLD : FontKind.UI, false);
            }
            y += ROW_H;
        }
    }
}
