package dev.vanta.core.waypoints;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

/**
 * Waypoint categories are free text. These are the suggestions the add/edit dialog offers and the rules that keep
 * stored categories tidy. A built-in suggestion typed in any letter case is stored in its canonical spelling.
 */
public final class WaypointCategories {
    public static final String HOME = "Home";
    public static final String BASE = "Base";
    public static final String FARM = "Farm";
    public static final String PORTAL = "Portal";
    public static final String RESOURCE = "Resource";
    public static final String OTHER = "Other";

    /** Built-in suggestions in display order. */
    public static final List<String> SUGGESTIONS = List.of(HOME, BASE, FARM, PORTAL, RESOURCE, OTHER);
    /** Category used when none (or nothing printable) was given. */
    public static final String DEFAULT = OTHER;
    /** Longest category kept after {@link #sanitize(String)}. */
    public static final int MAX_LENGTH = 24;

    private WaypointCategories() {
    }

    /**
     * Cleans a category the way {@link Waypoint#sanitizeName(String)} cleans names (control characters removed,
     * whitespace collapsed, at most {@value #MAX_LENGTH} characters) and maps built-in suggestions to their
     * canonical spelling. {@link #DEFAULT} when nothing remains.
     */
    public static String sanitize(String raw) {
        String cleaned = Waypoint.collapse(raw, MAX_LENGTH);
        if (cleaned.isEmpty()) {
            return DEFAULT;
        }
        for (String suggestion : SUGGESTIONS) {
            if (suggestion.equalsIgnoreCase(cleaned)) {
                return suggestion;
            }
        }
        return cleaned;
    }

    /** True when {@code category} is one of the built-in suggestions (case-insensitive). */
    public static boolean isBuiltIn(String category) {
        if (category == null) {
            return false;
        }
        for (String suggestion : SUGGESTIONS) {
            if (suggestion.equalsIgnoreCase(category.strip())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The built-in suggestions followed by every other category in use (alphabetically, first spelling wins,
     * compared case-insensitively).
     */
    public static List<String> suggestions(Collection<Waypoint> existing) {
        TreeMap<String, String> custom = new TreeMap<>();
        for (Waypoint waypoint : existing) {
            String category = waypoint.category();
            if (!isBuiltIn(category)) {
                custom.putIfAbsent(category.toLowerCase(Locale.ROOT), category);
            }
        }
        List<String> out = new ArrayList<>(SUGGESTIONS);
        out.addAll(custom.values());
        return List.copyOf(out);
    }
}
