package dev.vanta.core.keybinds;

import dev.vanta.core.bridge.KeyRef;
import dev.vanta.core.bridge.KeyType;
import dev.vanta.core.i18n.Lang;
import java.util.Locale;
import java.util.Map;

/**
 * Turns key references into short human-readable labels ("Right Shift", "Mouse 4", "F3").
 * <p>
 * Vanilla key names ({@code key.keyboard.right.shift}) are converted by rule so the formatter works without
 * Minecraft's translation tables; GLFW key codes are mapped for the common keys.
 */
public final class KeybindFormatter {
    private static final Map<Integer, String> GLFW_NAMES = Map.ofEntries(
            Map.entry(32, "key.keyboard.space"), Map.entry(39, "key.keyboard.apostrophe"),
            Map.entry(44, "key.keyboard.comma"), Map.entry(45, "key.keyboard.minus"),
            Map.entry(46, "key.keyboard.period"), Map.entry(47, "key.keyboard.slash"),
            Map.entry(59, "key.keyboard.semicolon"), Map.entry(61, "key.keyboard.equal"),
            Map.entry(91, "key.keyboard.left.bracket"), Map.entry(92, "key.keyboard.backslash"),
            Map.entry(93, "key.keyboard.right.bracket"), Map.entry(96, "key.keyboard.grave.accent"),
            Map.entry(256, "key.keyboard.escape"), Map.entry(257, "key.keyboard.enter"),
            Map.entry(258, "key.keyboard.tab"), Map.entry(259, "key.keyboard.backspace"),
            Map.entry(260, "key.keyboard.insert"), Map.entry(261, "key.keyboard.delete"),
            Map.entry(262, "key.keyboard.right"), Map.entry(263, "key.keyboard.left"),
            Map.entry(264, "key.keyboard.down"), Map.entry(265, "key.keyboard.up"),
            Map.entry(266, "key.keyboard.page.up"), Map.entry(267, "key.keyboard.page.down"),
            Map.entry(268, "key.keyboard.home"), Map.entry(269, "key.keyboard.end"),
            Map.entry(280, "key.keyboard.caps.lock"), Map.entry(281, "key.keyboard.scroll.lock"),
            Map.entry(282, "key.keyboard.num.lock"), Map.entry(283, "key.keyboard.print.screen"),
            Map.entry(284, "key.keyboard.pause"), Map.entry(340, "key.keyboard.left.shift"),
            Map.entry(341, "key.keyboard.left.control"), Map.entry(342, "key.keyboard.left.alt"),
            Map.entry(343, "key.keyboard.left.win"), Map.entry(344, "key.keyboard.right.shift"),
            Map.entry(345, "key.keyboard.right.control"), Map.entry(346, "key.keyboard.right.alt"),
            Map.entry(347, "key.keyboard.right.win"), Map.entry(348, "key.keyboard.menu"));

    private KeybindFormatter() {
    }

    /** Label for a key reference; unbound → translated "Not bound". */
    public static String format(KeyRef key) {
        if (key == null || key.isUnbound()) {
            return Lang.tr("vanta.keybinds.unbound");
        }
        return format(key.type(), key.code(), key.name());
    }

    /** Label from the raw parts. */
    public static String format(KeyType type, int code, String name) {
        if (type == KeyType.MOUSE) {
            return switch (code) {
                case 0 -> Lang.tr("vanta.keybinds.mouse.left");
                case 1 -> Lang.tr("vanta.keybinds.mouse.right");
                case 2 -> Lang.tr("vanta.keybinds.mouse.middle");
                default -> Lang.tr("vanta.keybinds.mouse.button", code + 1);
            };
        }
        if (type == KeyType.SCANCODE) {
            return Lang.tr("vanta.keybinds.scancode", code);
        }
        String resolved = name;
        if (resolved == null || resolved.isBlank() || KeyRef.UNKNOWN_NAME.equals(resolved)) {
            resolved = glfwKeyName(code);
        }
        return humanize(resolved);
    }

    /** Vanilla key name for a GLFW key code ({@code key.keyboard.a}, {@code key.keyboard.f3}, …). */
    public static String glfwKeyName(int code) {
        if (code >= 'A' && code <= 'Z') {
            return "key.keyboard." + Character.toLowerCase((char) code);
        }
        if (code >= '0' && code <= '9') {
            return "key.keyboard." + (char) code;
        }
        if (code >= 290 && code <= 314) {
            return "key.keyboard.f" + (code - 289);
        }
        if (code >= 320 && code <= 329) {
            return "key.keyboard.keypad." + (code - 320);
        }
        String known = GLFW_NAMES.get(code);
        return known != null ? known : KeyRef.UNKNOWN_NAME;
    }

    /** Converts {@code key.keyboard.right.shift} to {@code Right Shift}, {@code key.keyboard.f3} to {@code F3}. */
    public static String humanize(String vanillaName) {
        if (vanillaName == null || vanillaName.isBlank() || KeyRef.UNKNOWN_NAME.equals(vanillaName)) {
            return Lang.tr("vanta.keybinds.unbound");
        }
        String body = vanillaName;
        if (body.startsWith("key.keyboard.")) {
            body = body.substring("key.keyboard.".length());
        } else if (body.startsWith("key.mouse.")) {
            return Lang.tr("vanta.keybinds.mouse.button", body.substring("key.mouse.".length()));
        }
        String[] parts = body.split("\\.");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            if (part.matches("f\\d{1,2}")) {
                out.append(part.toUpperCase(Locale.ROOT));
            } else if (part.length() == 1) {
                out.append(part.toUpperCase(Locale.ROOT));
            } else if ("win".equals(part)) {
                out.append("Super");
            } else {
                out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return out.toString();
    }
}
