package dev.vanta.core.crosshair;

import dev.vanta.core.settings.Setting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Colour names the Vanta Nexus assistant understands for the crosshair ({@code crosshair.set} with
 * {@code "color": "red"}), mapped to opaque ARGB values, plus {@code #RRGGBB} / {@code #AARRGGBB} hex. The values are
 * the CSS colours of the same name, except {@code green}, which is the bright CSS {@code lime} (a dark green crosshair
 * is hard to see).
 */
public final class CrosshairColors {
    private static final Map<String, Integer> NAMED;

    static {
        Map<String, Integer> named = new LinkedHashMap<>();
        named.put("white", 0xFFFFFFFF);
        named.put("red", 0xFFFF0000);
        named.put("green", 0xFF00FF00);
        named.put("blue", 0xFF0000FF);
        named.put("yellow", 0xFFFFFF00);
        named.put("cyan", 0xFF00FFFF);
        named.put("magenta", 0xFFFF00FF);
        named.put("black", 0xFF000000);
        named.put("orange", 0xFFFFA500);
        named.put("purple", 0xFF800080);
        named.put("pink", 0xFFFFC0CB);
        named.put("gray", 0xFF808080);
        NAMED = Collections.unmodifiableMap(named);
    }

    private CrosshairColors() {
    }

    /** The colour names in a fixed order. */
    public static List<String> names() {
        return new ArrayList<>(NAMED.keySet());
    }

    /**
     * Parses a colour name (case-insensitive; "grey" is "gray") or a hex colour. Hex colours are made opaque, like the
     * crosshair customizer's colour field.
     *
     * @return the opaque ARGB value, empty when the text is neither
     */
    public static Optional<Integer> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String name = text.trim().toLowerCase(Locale.ROOT);
        if (name.equals("grey")) {
            name = "gray";
        }
        Integer named = NAMED.get(name);
        if (named != null) {
            return Optional.of(named);
        }
        return Setting.parseColor(name).map(argb -> 0xFF000000 | argb);
    }

    /** The name of an opaque colour when it is one of {@link #names()}, otherwise {@code #RRGGBB}. */
    public static String describe(int argb) {
        int opaque = 0xFF000000 | argb;
        for (Map.Entry<String, Integer> e : NAMED.entrySet()) {
            if (e.getValue() == opaque) {
                return e.getKey();
            }
        }
        return String.format(Locale.ROOT, "#%06X", argb & 0xFFFFFF);
    }
}
