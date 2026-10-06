package dev.vanta.core.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Text layout helpers shared by widgets: word wrapping and ellipsis clipping on top of {@link TextMetrics}.
 */
public final class CanvasText {

    /** The single-character ellipsis used when clipping. */
    public static final String ELLIPSIS = "…";

    private CanvasText() {
    }

    /**
     * Wraps text into lines no wider than {@code maxWidth}. Explicit {@code \n} always breaks; words longer than
     * the width are split by character. Never returns an empty list (a blank line for empty input).
     */
    public static List<String> wrap(String text, int maxWidth, FontKind font, TextMetrics metrics) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }
        for (String paragraph : text.split("\n", -1)) {
            wrapParagraph(paragraph, maxWidth, font, metrics, lines);
        }
        return lines;
    }

    private static void wrapParagraph(String paragraph, int maxWidth, FontKind font, TextMetrics metrics,
                                      List<String> out) {
        if (paragraph.isEmpty()) {
            out.add("");
            return;
        }
        StringBuilder line = new StringBuilder();
        for (String word : paragraph.split(" ", -1)) {
            if (line.isEmpty()) {
                appendWord(word, maxWidth, font, metrics, line, out);
                continue;
            }
            String candidate = line + " " + word;
            if (metrics.textWidth(candidate, font) <= maxWidth) {
                line.append(' ').append(word);
            } else {
                out.add(line.toString());
                line.setLength(0);
                appendWord(word, maxWidth, font, metrics, line, out);
            }
        }
        out.add(line.toString());
    }

    /** Appends a word to the current line, splitting it over several lines when it is wider than the box. */
    private static void appendWord(String word, int maxWidth, FontKind font, TextMetrics metrics,
                                   StringBuilder line, List<String> out) {
        if (metrics.textWidth(word, font) <= maxWidth || maxWidth <= 0) {
            line.append(word);
            return;
        }
        StringBuilder chunk = new StringBuilder();
        for (int i = 0; i < word.length(); ) {
            int cp = word.codePointAt(i);
            String next = chunk.toString() + new String(Character.toChars(cp));
            if (!chunk.isEmpty() && metrics.textWidth(next, font) > maxWidth) {
                out.add(chunk.toString());
                chunk.setLength(0);
            }
            chunk.appendCodePoint(cp);
            i += Character.charCount(cp);
        }
        line.append(chunk);
    }

    /**
     * Returns the text itself when it fits, otherwise the longest prefix (trailing whitespace stripped) plus an
     * ellipsis that fits.
     * <p>
     * The prefix is found by binary search over the number of leading code points kept, so clipping costs
     * {@code O(log n)} measurements instead of one per removed character. The stripped prefixes grow with that count,
     * so with monotone metrics (every glyph has a non-negative advance, as in the game and the previews) "prefix plus
     * ellipsis fits" holds up to some count and fails beyond it, and the search returns exactly what a linear scan
     * from the end would.
     */
    public static String ellipsize(String text, int maxWidth, FontKind font, TextMetrics metrics) {
        if (text == null) {
            return "";
        }
        if (metrics.textWidth(text, font) <= maxWidth) {
            return text;
        }
        int ellipsisWidth = metrics.textWidth(ELLIPSIS, font);
        if (ellipsisWidth > maxWidth) {
            return "";
        }
        // Zero code points always fit (the ellipsis alone was just checked); the whole text did not.
        int lo = 0;
        int hi = text.codePointCount(0, text.length()) - 1;
        String best = "";
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            String prefix = text.substring(0, text.offsetByCodePoints(0, mid)).stripTrailing();
            if (metrics.textWidth(prefix, font) + ellipsisWidth <= maxWidth) {
                lo = mid;
                best = prefix;
            } else {
                hi = mid - 1;
            }
        }
        return best + ELLIPSIS;
    }

    /** Height in pixels of {@code lines} lines of the font. */
    public static int blockHeight(int lines, FontKind font, TextMetrics metrics) {
        return Math.max(0, lines) * metrics.lineHeight(font);
    }

    /** Width of the widest line. */
    public static int maxLineWidth(List<String> lines, FontKind font, TextMetrics metrics) {
        int max = 0;
        for (String line : lines) {
            max = Math.max(max, metrics.textWidth(line, font));
        }
        return max;
    }
}
