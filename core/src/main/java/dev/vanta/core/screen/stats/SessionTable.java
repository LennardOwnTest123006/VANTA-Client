package dev.vanta.core.screen.stats;

import dev.vanta.core.i18n.Lang;
import dev.vanta.core.stats.SessionRecord;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Read-only table of recent sessions, newest first: date, duration, average fps and the worlds / servers of the
 * session (or a muted note when names are not recorded). Rows alternate in tint; the header is a muted caption row.
 */
public final class SessionTable extends UiNode {
    /** Height of one row. */
    public static final int ROW_H = 14;
    private static final int PAD_X = Theme.SPACE_4;
    private static final float[] COLUMNS = {0.30f, 0.17f, 0.15f, 0.38f};

    private final List<SessionRecord> sessions;
    private final ZoneId zone;
    private final boolean namesRecorded;

    /**
     * @param sessions      newest first
     * @param zone          time zone for dates
     * @param namesRecorded false when both world and server names are turned off in the privacy settings
     */
    public SessionTable(List<SessionRecord> sessions, ZoneId zone, boolean namesRecorded) {
        this.sessions = List.copyOf(Objects.requireNonNull(sessions, "sessions"));
        this.zone = Objects.requireNonNull(zone, "zone");
        this.namesRecorded = namesRecorded;
    }

    public List<SessionRecord> sessions() {
        return sessions;
    }

    /** Text of the "where" column for a session. */
    public String whereText(SessionRecord session) {
        List<String> names = new ArrayList<>(session.worlds());
        names.addAll(session.servers());
        if (names.isEmpty()) {
            return namesRecorded ? "—" : Lang.tr("vanta.stats.names_hidden");
        }
        return String.join(", ", names);
    }

    @Override
    protected Size measure(UiContext ctx) {
        return new Size(240, ROW_H * (sessions.size() + 1) + 4);
    }

    private int[] columnX(Rect b) {
        int[] xs = new int[COLUMNS.length + 1];
        int inner = b.w() - PAD_X * 2;
        int x = b.x() + PAD_X;
        for (int i = 0; i < COLUMNS.length; i++) {
            xs[i] = x;
            x += Math.round(inner * COLUMNS[i]);
        }
        xs[COLUMNS.length] = b.right() - PAD_X;
        return xs;
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        Theme theme = ctx.theme();
        Rect b = bounds();
        canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_LG, theme.panelBackground());
        canvas.strokeRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_LG, theme.borderSubtle());
        int[] xs = columnX(b);
        int lh = canvas.lineHeight(FontKind.UI);
        int y = b.y() + 2;
        String[] headers = {Lang.tr("vanta.stats.table.date"), Lang.tr("vanta.stats.table.duration"),
                Lang.tr("vanta.stats.table.avg_fps"), Lang.tr("vanta.stats.table.where")};
        for (int i = 0; i < headers.length; i++) {
            canvas.text(canvas.textClipped(headers[i], xs[i + 1] - xs[i] - Theme.SPACE_2, FontKind.UI_BOLD), xs[i],
                    y + (ROW_H - lh) / 2, theme.textMuted(), FontKind.UI_BOLD, false);
        }
        y += ROW_H;
        canvas.fill(b.x() + 1, y, b.w() - 2, 1, Colors.withAlpha(theme.borderSubtle(), 0.9f));
        for (int row = 0; row < sessions.size(); row++) {
            SessionRecord s = sessions.get(row);
            if (row % 2 == 1) {
                canvas.fill(b.x() + 1, y, b.w() - 2, ROW_H, Colors.withAlpha(theme.surface2(), 0.5f));
            }
            int ty = y + (ROW_H - lh) / 2;
            cell(canvas, theme, xs, 0, ty, StatFormats.dateTime(s.startedAt(), zone), theme.textPrimary());
            cell(canvas, theme, xs, 1, ty, StatFormats.duration(s.playtimeMs()), theme.textSecondary());
            cell(canvas, theme, xs, 2, ty, StatFormats.fps(s.averageFps()), theme.textSecondary());
            String where = whereText(s);
            boolean muted = where.equals(Lang.tr("vanta.stats.names_hidden")) || where.equals("—");
            cell(canvas, theme, xs, 3, ty, where, muted ? theme.textMuted() : theme.textSecondary());
            y += ROW_H;
        }
    }

    private static void cell(Canvas canvas, Theme theme, int[] xs, int column, int y, String text, int color) {
        int w = xs[column + 1] - xs[column] - Theme.SPACE_2;
        canvas.text(canvas.textClipped(text, Math.max(0, w), FontKind.UI), xs[column], y, color, FontKind.UI, false);
    }
}
