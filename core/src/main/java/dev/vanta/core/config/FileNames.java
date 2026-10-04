package dev.vanta.core.config;

import java.util.Locale;

/**
 * Turns user-provided names into safe file names: lower-case ASCII letters, digits, {@code -} and {@code _} only, no
 * path separators, no leading dots, bounded length. Used for profiles and HUD presets.
 */
public final class FileNames {
    /** Longest file stem produced. */
    public static final int MAX_LENGTH = 48;

    private FileNames() {
    }

    /**
     * @param name     free text (may contain anything)
     * @param fallback used when nothing safe remains (must itself be safe)
     */
    public static String sanitize(String name, String fallback) {
        String source = name == null ? "" : name.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        boolean lastDash = true;
        for (int i = 0; i < source.length() && sb.length() < MAX_LENGTH; i++) {
            char c = source.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                sb.append(c);
                lastDash = false;
            } else if (c == '_' && !lastDash) {
                sb.append('_');
                lastDash = true;
            } else if (!lastDash) {
                sb.append('-');
                lastDash = true;
            }
        }
        while (!sb.isEmpty() && (sb.charAt(sb.length() - 1) == '-' || sb.charAt(sb.length() - 1) == '_')) {
            sb.setLength(sb.length() - 1);
        }
        return sb.isEmpty() ? fallback : sb.toString();
    }

    /** True when {@code stem} is already a safe file stem (as produced by {@link #sanitize}). */
    public static boolean isSafe(String stem) {
        return stem != null && !stem.isEmpty() && stem.length() <= MAX_LENGTH
                && stem.matches("[a-z0-9][a-z0-9_-]*") && !stem.endsWith("-") && !stem.endsWith("_");
    }
}
