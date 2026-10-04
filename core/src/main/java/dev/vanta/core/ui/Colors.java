package dev.vanta.core.ui;

import java.util.Locale;

/**
 * Helpers for packed ARGB colors ({@code 0xAARRGGBB}), the color representation used throughout the UI kit
 * and by Minecraft's {@code GuiGraphics}.
 */
public final class Colors {

    /** Fully transparent black. */
    public static final int TRANSPARENT = 0x00000000;
    /** Opaque white. */
    public static final int WHITE = 0xFFFFFFFF;
    /** Opaque black. */
    public static final int BLACK = 0xFF000000;

    private Colors() {
    }

    /** Packs channel values (each clamped to 0..255). */
    public static int argb(int a, int r, int g, int b) {
        return (clamp255(a) << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
    }

    /** Packs an opaque color. */
    public static int rgb(int r, int g, int b) {
        return argb(255, r, g, b);
    }

    /** Alpha channel 0..255. */
    public static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    /** Red channel 0..255. */
    public static int red(int argb) {
        return (argb >>> 16) & 0xFF;
    }

    /** Green channel 0..255. */
    public static int green(int argb) {
        return (argb >>> 8) & 0xFF;
    }

    /** Blue channel 0..255. */
    public static int blue(int argb) {
        return argb & 0xFF;
    }

    /** Alpha as a fraction 0..1. */
    public static float alphaF(int argb) {
        return alpha(argb) / 255f;
    }

    /** Replaces the alpha channel with {@code alpha} given as a fraction 0..1. */
    public static int withAlpha(int argb, float alpha) {
        return withAlpha255(argb, Math.round(clamp01(alpha) * 255f));
    }

    /** Replaces the alpha channel with a 0..255 value. */
    public static int withAlpha255(int argb, int alpha) {
        return (clamp255(alpha) << 24) | (argb & 0x00FFFFFF);
    }

    /** Multiplies the existing alpha by {@code factor} (0..1). */
    public static int multiplyAlpha(int argb, float factor) {
        return withAlpha255(argb, Math.round(alpha(argb) * clamp01(factor)));
    }

    /** Forces the color opaque. */
    public static int opaque(int argb) {
        return argb | 0xFF000000;
    }

    /** Linear interpolation of all four channels; {@code t} is clamped to 0..1. */
    public static int lerp(int from, int to, float t) {
        float f = clamp01(t);
        int a = Math.round(alpha(from) + (alpha(to) - alpha(from)) * f);
        int r = Math.round(red(from) + (red(to) - red(from)) * f);
        int g = Math.round(green(from) + (green(to) - green(from)) * f);
        int b = Math.round(blue(from) + (blue(to) - blue(from)) * f);
        return argb(a, r, g, b);
    }

    /** Moves the RGB channels towards white by {@code amount} (0..1); alpha is kept. */
    public static int lighten(int argb, float amount) {
        float f = clamp01(amount);
        return argb(alpha(argb),
                Math.round(red(argb) + (255 - red(argb)) * f),
                Math.round(green(argb) + (255 - green(argb)) * f),
                Math.round(blue(argb) + (255 - blue(argb)) * f));
    }

    /** Moves the RGB channels towards black by {@code amount} (0..1); alpha is kept. */
    public static int darken(int argb, float amount) {
        float f = 1f - clamp01(amount);
        return argb(alpha(argb), Math.round(red(argb) * f), Math.round(green(argb) * f), Math.round(blue(argb) * f));
    }

    /** Multiplies RGB by a factor; alpha is kept. Used for subtle hover tints. */
    public static int scaleRgb(int argb, float factor) {
        float f = Math.max(0f, factor);
        return argb(alpha(argb), Math.round(red(argb) * f), Math.round(green(argb) * f), Math.round(blue(argb) * f));
    }

    /**
     * The color Minecraft uses for a text drop shadow: every channel quartered, alpha kept.
     */
    public static int shadow(int argb) {
        return (argb & 0xFF000000) | ((argb & 0x00FCFCFC) >> 2);
    }

    /** Source-over compositing of {@code fg} onto an opaque or translucent {@code bg}. */
    public static int over(int bg, int fg) {
        float fa = alphaF(fg);
        float ba = alphaF(bg);
        float outA = fa + ba * (1f - fa);
        if (outA <= 0f) {
            return TRANSPARENT;
        }
        int r = Math.round((red(fg) * fa + red(bg) * ba * (1f - fa)) / outA);
        int g = Math.round((green(fg) * fa + green(bg) * ba * (1f - fa)) / outA);
        int b = Math.round((blue(fg) * fa + blue(bg) * ba * (1f - fa)) / outA);
        return argb(Math.round(outA * 255f), r, g, b);
    }

    /** Relative luminance (0..1) of the RGB channels, ignoring alpha (WCAG formula). */
    public static double luminance(int argb) {
        return 0.2126 * linear(red(argb)) + 0.7152 * linear(green(argb)) + 0.0722 * linear(blue(argb));
    }

    /** Whether text on this background should be light. */
    public static boolean isDark(int argb) {
        return luminance(argb) < 0.35;
    }

    /** WCAG contrast ratio between two opaque colors (1..21). */
    public static double contrastRatio(int a, int b) {
        double la = luminance(a) + 0.05;
        double lb = luminance(b) + 0.05;
        return la > lb ? la / lb : lb / la;
    }

    /**
     * Parses {@code #RRGGBB}, {@code #AARRGGBB}, {@code #RGB} (shorthand) with an optional {@code #} or
     * {@code 0x} prefix. Six-digit values are opaque.
     *
     * @throws IllegalArgumentException for anything else
     */
    public static int fromHex(String hex) {
        if (hex == null) {
            throw new IllegalArgumentException("hex color is null");
        }
        String s = hex.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        } else if (s.startsWith("0x") || s.startsWith("0X")) {
            s = s.substring(2);
        }
        if (!s.matches("[0-9a-fA-F]+")) {
            throw new IllegalArgumentException("Invalid hex color: " + hex);
        }
        return switch (s.length()) {
            case 3 -> {
                int r = Integer.parseInt(s.substring(0, 1).repeat(2), 16);
                int g = Integer.parseInt(s.substring(1, 2).repeat(2), 16);
                int b = Integer.parseInt(s.substring(2, 3).repeat(2), 16);
                yield rgb(r, g, b);
            }
            case 6 -> 0xFF000000 | Integer.parseInt(s, 16);
            case 8 -> (int) Long.parseLong(s, 16);
            default -> throw new IllegalArgumentException("Invalid hex color length: " + hex);
        };
    }

    /**
     * Parses a CSS-style color: {@code #RRGGBB}, {@code #RGB} or {@code #RRGGBBAA} (alpha last, as written in
     * {@code shared/design/tokens.json}). Six-digit values are opaque.
     *
     * @throws IllegalArgumentException for anything else
     */
    public static int fromCssHex(String hex) {
        int parsed = fromHex(hex);
        String digits = hex == null ? "" : hex.trim();
        if (digits.startsWith("#")) {
            digits = digits.substring(1);
        } else if (digits.startsWith("0x") || digits.startsWith("0X")) {
            digits = digits.substring(2);
        }
        if (digits.length() != 8) {
            return parsed;
        }
        // parsed is AARRGGBB of the digit string RRGGBBAA: rotate the last byte to the front.
        int rgb = (parsed >>> 8) & 0x00FFFFFF;
        int a = parsed & 0xFF;
        return (a << 24) | rgb;
    }

    /** Lenient variant of {@link #fromHex(String)} returning {@code fallback} on invalid input. */
    public static int fromHexOr(String hex, int fallback) {
        try {
            return fromHex(hex);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /** Whether {@link #fromHex(String)} would accept the string. */
    public static boolean isValidHex(String hex) {
        try {
            fromHex(hex);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** {@code #RRGGBB} for opaque colors, {@code #AARRGGBB} otherwise (upper case). */
    public static String toHex(int argb) {
        if (alpha(argb) == 255) {
            return String.format(Locale.ROOT, "#%06X", argb & 0x00FFFFFF);
        }
        return String.format(Locale.ROOT, "#%08X", argb);
    }

    /** Always eight digits: {@code #AARRGGBB}. */
    public static String toHexArgb(int argb) {
        return String.format(Locale.ROOT, "#%08X", argb);
    }

    private static double linear(int channel) {
        double c = channel / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static int clamp255(int v) {
        return v < 0 ? 0 : Math.min(255, v);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(1f, v);
    }
}
