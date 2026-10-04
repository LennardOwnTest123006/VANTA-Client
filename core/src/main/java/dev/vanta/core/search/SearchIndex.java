package dev.vanta.core.search;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Small in-memory full-text index with prefix and fuzzy matching.
 * <p>
 * Every entry has a title, keywords and a description, weighted in that order. A query is tokenised; every query
 * token must match at least one field of an entry (AND semantics). Per token the best match in each field counts:
 * exact token &gt; prefix &gt; substring &gt; fuzzy (optimal string alignment distance ≤ 1, only for words of five
 * or more characters so short words never fuzz). An entry whose title starts with the whole query gets a bonus.
 * <p>
 * The index is rebuilt rarely (on language change) and searched on every keystroke; 500 entries search in well
 * under a millisecond.
 *
 * @param <T> item type
 */
public final class SearchIndex<T> {
    /** Minimum word length for fuzzy matching. */
    public static final int FUZZY_MIN_LENGTH = 5;

    private static final double[] EXACT = {10.0, 6.0, 3.0};
    private static final double[] PREFIX = {7.0, 4.0, 2.0};
    private static final double[] CONTAINS = {4.0, 3.0, 1.0};
    private static final double[] FUZZY = {5.0, 3.0, 1.0};
    private static final double TITLE_START_BONUS = 3.0;

    private record Indexed<T>(T item, String title, String titleLower, List<Fuzzy.Token> titleTokens,
                              List<String> keywordTokens, List<String> descriptionTokens) {
    }

    private final List<Indexed<T>> entries = new ArrayList<>();

    /** Adds an entry. {@code keywords} and {@code description} may be empty. */
    public void add(T item, String title, Collection<String> keywords, String description) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(title, "title");
        List<String> keywordTokens = new ArrayList<>();
        for (String keyword : keywords) {
            keywordTokens.addAll(Fuzzy.words(keyword));
        }
        entries.add(new Indexed<>(item, title, title.toLowerCase(Locale.ROOT), Fuzzy.tokenize(title),
                keywordTokens, Fuzzy.words(description == null ? "" : description)));
    }

    /** Removes every entry. */
    public void clear() {
        entries.clear();
    }

    /** Number of entries. */
    public int size() {
        return entries.size();
    }

    /** All indexed items in insertion order. */
    public List<T> items() {
        List<T> out = new ArrayList<>(entries.size());
        for (Indexed<T> e : entries) {
            out.add(e.item());
        }
        return out;
    }

    /** Searches with no limit. */
    public List<SearchHit<T>> search(String query) {
        return search(query, Integer.MAX_VALUE);
    }

    /**
     * Searches and returns the top {@code limit} hits ordered by score, then shorter title, then title.
     * A blank query returns no hits.
     */
    public List<SearchHit<T>> search(String query, int limit) {
        List<String> queryTokens = Fuzzy.words(query);
        if (queryTokens.isEmpty() || limit <= 0) {
            return List.of();
        }
        String queryLower = query.trim().toLowerCase(Locale.ROOT);
        List<SearchHit<T>> hits = new ArrayList<>();
        for (Indexed<T> entry : entries) {
            double total = 0;
            Set<SearchField> fields = EnumSet.noneOf(SearchField.class);
            List<MatchRange> ranges = new ArrayList<>();
            boolean all = true;
            for (String q : queryTokens) {
                double best = 0;
                double titleScore = scoreTitle(entry, q, ranges);
                if (titleScore > 0) {
                    fields.add(SearchField.TITLE);
                    best = Math.max(best, titleScore);
                }
                double keywordScore = scoreTokens(entry.keywordTokens(), q, 1);
                if (keywordScore > 0) {
                    fields.add(SearchField.KEYWORDS);
                    best = Math.max(best, keywordScore);
                }
                double descriptionScore = scoreTokens(entry.descriptionTokens(), q, 2);
                if (descriptionScore > 0) {
                    fields.add(SearchField.DESCRIPTION);
                    best = Math.max(best, descriptionScore);
                }
                if (best == 0) {
                    all = false;
                    break;
                }
                total += best;
            }
            if (!all) {
                continue;
            }
            if (entry.titleLower().startsWith(queryLower)) {
                total += TITLE_START_BONUS;
            }
            hits.add(new SearchHit<>(entry.item(), total, merge(ranges), Collections.unmodifiableSet(fields)));
        }
        hits.sort(Comparator.<SearchHit<T>>comparingDouble(SearchHit::score).reversed()
                .thenComparingInt(h -> titleOf(h).length())
                .thenComparing(this::titleOf));
        return hits.size() > limit ? List.copyOf(hits.subList(0, limit)) : Collections.unmodifiableList(hits);
    }

    private String titleOf(SearchHit<T> hit) {
        for (Indexed<T> e : entries) {
            if (e.item() == hit.item()) {
                return e.title();
            }
        }
        return "";
    }

    private static <T> double scoreTitle(Indexed<T> entry, String q, List<MatchRange> ranges) {
        double best = 0;
        for (Fuzzy.Token token : entry.titleTokens()) {
            String t = token.text();
            if (t.equals(q)) {
                ranges.add(new MatchRange(token.start(), token.end()));
                best = Math.max(best, EXACT[0]);
            } else if (q.length() >= 2 && t.startsWith(q)) {
                ranges.add(new MatchRange(token.start(), token.start() + q.length()));
                best = Math.max(best, PREFIX[0]);
            } else if (q.length() >= 3 && t.contains(q)) {
                int at = t.indexOf(q);
                ranges.add(new MatchRange(token.start() + at, token.start() + at + q.length()));
                best = Math.max(best, CONTAINS[0]);
            } else if (q.length() >= FUZZY_MIN_LENGTH && t.length() >= FUZZY_MIN_LENGTH
                    && Fuzzy.damerauDistance(t, q, 1) <= 1) {
                ranges.add(new MatchRange(token.start(), token.end()));
                best = Math.max(best, FUZZY[0]);
            }
        }
        return best;
    }

    private static double scoreTokens(List<String> tokens, String q, int weightIndex) {
        double best = 0;
        for (String t : tokens) {
            if (t.equals(q)) {
                return EXACT[weightIndex];
            }
            if (q.length() >= 2 && t.startsWith(q)) {
                best = Math.max(best, PREFIX[weightIndex]);
            } else if (q.length() >= 3 && t.contains(q)) {
                best = Math.max(best, CONTAINS[weightIndex]);
            } else if (q.length() >= FUZZY_MIN_LENGTH && t.length() >= FUZZY_MIN_LENGTH
                    && Fuzzy.damerauDistance(t, q, 1) <= 1) {
                best = Math.max(best, FUZZY[weightIndex]);
            }
        }
        return best;
    }

    /** Sorts and merges overlapping ranges. */
    static List<MatchRange> merge(List<MatchRange> ranges) {
        if (ranges.isEmpty()) {
            return List.of();
        }
        List<MatchRange> sorted = new ArrayList<>(ranges);
        sorted.sort(Comparator.comparingInt(MatchRange::start).thenComparingInt(MatchRange::end));
        List<MatchRange> out = new ArrayList<>();
        MatchRange current = sorted.get(0);
        for (int i = 1; i < sorted.size(); i++) {
            MatchRange next = sorted.get(i);
            if (current.overlaps(next)) {
                current = current.union(next);
            } else {
                out.add(current);
                current = next;
            }
        }
        out.add(current);
        return Collections.unmodifiableList(out);
    }
}
