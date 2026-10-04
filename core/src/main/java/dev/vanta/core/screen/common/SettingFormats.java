package dev.vanta.core.screen.common;

import dev.vanta.core.bridge.VanillaOption;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.perf.FpsLimitPreset;
import dev.vanta.core.settings.Setting;
import dev.vanta.core.settings.SettingKind;
import java.util.Locale;
import java.util.Optional;

/**
 * Human-readable value labels for numeric settings ("12 chunks", "Unlimited", "85 %", "3.0x"), shared by the settings
 * rows, the Performance Center diff table and search results.
 */
public final class SettingFormats {
    private SettingFormats() {
    }

    /**
     * Translated text of a key with no arguments, with {@code %%} collapsed to {@code %} (language files escape
     * literal percent signs the {@link String#format} way, which {@link Lang#tr(String)} leaves untouched).
     */
    public static String text(String key) {
        return Lang.format(Lang.tr(key));
    }

    /** Formats a value of {@code setting} for display. Enum and boolean values use their translations. */
    public static String format(Setting<?> setting, Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Boolean b) {
            return Lang.tr(b ? "vanta.common.on" : "vanta.common.off");
        }
        if (setting.kind() == SettingKind.ENUM) {
            return optionLabel(setting, value);
        }
        if (setting.kind() == SettingKind.COLOR && value instanceof Integer argb) {
            return Setting.formatColor(argb);
        }
        if (!(value instanceof Number number)) {
            return String.valueOf(value);
        }
        Optional<VanillaOption> vanilla = setting.vanillaBinding();
        if (vanilla.isPresent()) {
            return formatVanilla(vanilla.get(), number);
        }
        return formatVanta(setting, number);
    }

    /** Translated label of an enum option. */
    @SuppressWarnings("unchecked")
    public static String optionLabel(Setting<?> setting, Object option) {
        return Lang.tr(((Setting<Object>) setting).optionLangKey(option));
    }

    /** Formats a vanilla option value (used without a setting by the preset diff table). */
    public static String formatVanilla(VanillaOption option, Object value) {
        if (value instanceof Boolean b) {
            return Lang.tr(b ? "vanta.common.on" : "vanta.common.off");
        }
        if (value instanceof String s) {
            return Lang.tr(option.enumValueLangKey(s));
        }
        if (!(value instanceof Number number)) {
            return String.valueOf(value);
        }
        switch (option) {
            case FRAMERATE_LIMIT -> {
                int fps = number.intValue();
                return fps >= FpsLimitPreset.VANILLA_UNLIMITED ? Lang.tr(FpsLimitPreset.UNLIMITED.langKey())
                        : Lang.tr("vanta.common.fps", fps);
            }
            case RENDER_DISTANCE, SIMULATION_DISTANCE -> {
                return Lang.tr("vanta.common.chunks", number.intValue());
            }
            case GUI_SCALE -> {
                return number.intValue() == 0 ? Lang.tr("vanta.common.auto") : Integer.toString(number.intValue());
            }
            case BIOME_BLEND, MIPMAP_LEVELS, MENU_BLUR -> {
                return number.intValue() == 0 ? Lang.tr("vanta.common.off") : Integer.toString(number.intValue());
            }
            case FOV -> {
                return Integer.toString(number.intValue());
            }
            case ENTITY_DISTANCE_SCALING -> {
                return percent(number.doubleValue());
            }
            case MOUSE_WHEEL_SENSITIVITY, PANORAMA_SPEED -> {
                return String.format(Locale.ROOT, "%.2f", number.doubleValue());
            }
            default -> {
                if (option.kind() == VanillaOption.Kind.DOUBLE && option.min() >= 0 && option.max() <= 1.0) {
                    return percent(number.doubleValue());
                }
                if (option.kind() == VanillaOption.Kind.INT) {
                    return Integer.toString(number.intValue());
                }
                return String.format(Locale.ROOT, "%.2f", number.doubleValue());
            }
        }
    }

    private static String formatVanta(Setting<?> setting, Number number) {
        String id = setting.id();
        if (id.endsWith("Ms")) {
            return Lang.tr("vanta.common.seconds", String.format(Locale.ROOT, "%.1f", number.doubleValue() / 1000.0));
        }
        if (setting.kind() == SettingKind.INT_RANGE) {
            return Integer.toString(number.intValue());
        }
        if (id.startsWith("zoom.")) {
            return Lang.tr("vanta.zoom.indicator", String.format(Locale.ROOT, "%.1f", number.doubleValue()));
        }
        String lower = id.toLowerCase(Locale.ROOT);
        if (lower.contains("scale") || lower.contains("opacity") || (setting.min() >= 0 && setting.max() <= 1.0)) {
            return percent(number.doubleValue());
        }
        return String.format(Locale.ROOT, "%.2f", number.doubleValue());
    }

    /** {@code 0.85 → "85 %"}. */
    public static String percent(double fraction) {
        return Lang.tr("vanta.common.percent", Math.round(fraction * 100.0));
    }

    /** Width in pixels to reserve for the value label of a slider setting. */
    public static int valueLabelWidth(Setting<?> setting) {
        Optional<VanillaOption> vanilla = setting.vanillaBinding();
        if (vanilla.isPresent()) {
            return switch (vanilla.get()) {
                case FRAMERATE_LIMIT -> 48;
                case RENDER_DISTANCE, SIMULATION_DISTANCE -> 50;
                default -> 34;
            };
        }
        return setting.id().endsWith("Ms") ? 34 : 32;
    }
}
