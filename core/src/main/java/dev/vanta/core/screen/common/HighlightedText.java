package dev.vanta.core.screen.common;

import dev.vanta.core.search.MatchRange;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.CanvasText;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws a string with search-match ranges emphasised: matched characters use the accent colour and get a 1 px
 * underline; the rest keeps the base colour. Text that does not fit is ellipsized first (ranges beyond the cut are
 * dropped). Shared by the search overlay, the settings filter and the keybind search.
 */
public final class HighlightedText {
    private HighlightedText() {
    }

    /**
     * Draws {@code text} at {@code (x, y)} clipped to {@code maxWidth}.
     *
     * @return the width actually drawn
     */
    public static int draw(Canvas canvas, String text, List<MatchRange> ranges, int x, int y, int maxWidth,
                           FontKind font, int baseColor, int matchColor) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        String shown = CanvasText.ellipsize(text, Math.max(0, maxWidth), font, canvas);
        int visibleChars = shown.endsWith(CanvasText.ELLIPSIS) && !text.endsWith(CanvasText.ELLIPSIS)
                ? shown.length() - 1 : shown.length();
        List<MatchRange> clipped = clip(ranges, visibleChars);
        int cursor = x;
        int index = 0;
        int lh = canvas.lineHeight(font);
        for (MatchRange range : clipped) {
            if (range.start() > index) {
                String plain = shown.substring(index, range.start());
                canvas.text(plain, cursor, y, baseColor, font, false);
                cursor += canvas.textWidth(plain, font);
            }
            String hit = shown.substring(range.start(), range.end());
            int w = canvas.textWidth(hit, font);
            canvas.text(hit, cursor, y, matchColor, font, false);
            canvas.fill(cursor, y + lh - 1, Math.max(1, w), 1, Colors.withAlpha(matchColor, 0.75f));
            cursor += w;
            index = range.end();
        }
        if (index < shown.length()) {
            String tail = shown.substring(index);
            canvas.text(tail, cursor, y, baseColor, font, false);
            cursor += canvas.textWidth(tail, font);
        }
        return cursor - x;
    }

    /** Sorted, merged ranges limited to the first {@code length} characters. */
    public static List<MatchRange> clip(List<MatchRange> ranges, int length) {
        List<MatchRange> out = new ArrayList<>();
        if (ranges == null) {
            return out;
        }
        List<MatchRange> sorted = new ArrayList<>(ranges);
        sorted.sort((a, b) -> Integer.compare(a.start(), b.start()));
        for (MatchRange r : sorted) {
            int start = Math.max(0, Math.min(length, r.start()));
            int end = Math.max(start, Math.min(length, r.end()));
            if (end <= start) {
                continue;
            }
            MatchRange cut = new MatchRange(start, end);
            if (!out.isEmpty() && out.get(out.size() - 1).overlaps(cut)) {
                out.set(out.size() - 1, out.get(out.size() - 1).union(cut));
            } else {
                out.add(cut);
            }
        }
        return out;
    }
}
