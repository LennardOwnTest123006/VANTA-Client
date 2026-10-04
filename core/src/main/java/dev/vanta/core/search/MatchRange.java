package dev.vanta.core.search;

/**
 * Half-open character range {@code [start, end)} inside an entry title to highlight.
 */
public record MatchRange(int start, int end) {
    public MatchRange {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Invalid range " + start + ".." + end);
        }
    }

    /** Length in characters. */
    public int length() {
        return end - start;
    }

    /** True when the ranges touch or overlap. */
    public boolean overlaps(MatchRange other) {
        return start <= other.end && other.start <= end;
    }

    /** Smallest range covering both. */
    public MatchRange union(MatchRange other) {
        return new MatchRange(Math.min(start, other.start), Math.max(end, other.end));
    }
}
