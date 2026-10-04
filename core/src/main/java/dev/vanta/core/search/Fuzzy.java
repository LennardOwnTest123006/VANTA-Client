package dev.vanta.core.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tokenisation and edit-distance helpers shared by the search indexes.
 */
public final class Fuzzy {
    private Fuzzy() {
    }

    /** A token with its position in the source string. */
    public record Token(String text, int start, int end) {
    }

    /**
     * Splits text into lower-case alphanumeric tokens, also splitting camelCase ({@code fpsLimit} → {@code fps},
     * {@code limit}) and letter/digit boundaries ({@code 1.21} stays {@code 1}, {@code 21}).
     */
    public static List<Token> tokenize(String text) {
        List<Token> tokens = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return tokens;
        }
        int start = -1;
        int n = text.length();
        for (int i = 0; i <= n; i++) {
            char c = i < n ? text.charAt(i) : ' ';
            boolean alnum = Character.isLetterOrDigit(c);
            boolean boundary = false;
            if (alnum && start >= 0 && i > start) {
                char prev = text.charAt(i - 1);
                boolean camel = Character.isLowerCase(prev) && Character.isUpperCase(c);
                boolean digitSwitch = Character.isDigit(prev) != Character.isDigit(c);
                boundary = camel || digitSwitch;
            }
            if (!alnum || boundary) {
                if (start >= 0 && i > start) {
                    tokens.add(new Token(text.substring(start, i).toLowerCase(Locale.ROOT), start, i));
                }
                start = alnum ? i : -1;
            } else if (start < 0) {
                start = i;
            }
        }
        return tokens;
    }

    /** Plain lower-case token strings. */
    public static List<String> words(String text) {
        List<String> out = new ArrayList<>();
        for (Token token : tokenize(text)) {
            out.add(token.text());
        }
        return out;
    }

    /**
     * Optimal string alignment (restricted Damerau–Levenshtein) distance with early exit once {@code max} is
     * exceeded; returns {@code max + 1} in that case.
     */
    public static int damerauDistance(String a, String b, int max) {
        int la = a.length();
        int lb = b.length();
        if (Math.abs(la - lb) > max) {
            return max + 1;
        }
        if (la == 0) {
            return lb;
        }
        if (lb == 0) {
            return la;
        }
        int[] prev2 = new int[lb + 1];
        int[] prev = new int[lb + 1];
        int[] cur = new int[lb + 1];
        for (int j = 0; j <= lb; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= la; i++) {
            cur[0] = i;
            int rowMin = cur[0];
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= lb; j++) {
                char cb = b.charAt(j - 1);
                int cost = ca == cb ? 0 : 1;
                int value = Math.min(Math.min(prev[j] + 1, cur[j - 1] + 1), prev[j - 1] + cost);
                if (i > 1 && j > 1 && ca == b.charAt(j - 2) && a.charAt(i - 2) == cb) {
                    value = Math.min(value, prev2[j - 2] + 1);
                }
                cur[j] = value;
                rowMin = Math.min(rowMin, value);
            }
            if (rowMin > max) {
                return max + 1;
            }
            int[] tmp = prev2;
            prev2 = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[lb];
    }
}
