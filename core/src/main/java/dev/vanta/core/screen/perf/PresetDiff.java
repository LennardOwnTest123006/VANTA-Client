package dev.vanta.core.screen.perf;

import dev.vanta.core.bridge.OptionsBridge;
import dev.vanta.core.bridge.VanillaOption;
import dev.vanta.core.perf.PerformancePreset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What applying a {@link PerformancePreset} would change: one row per vanilla option the preset writes, with the
 * current value read from the {@link OptionsBridge} and the value the preset sets. Options the running game does
 * not expose are listed as unsupported so the table stays honest about what will happen.
 *
 * @param preset the preset
 * @param rows   rows in the preset's option order
 */
public record PresetDiff(PerformancePreset preset, List<Row> rows) {

    /**
     * One option of the diff.
     *
     * @param option    the vanilla option
     * @param current   current value, empty when the game does not expose the option
     * @param target    value the preset applies (normalised)
     * @param supported whether the option exists in the running game
     * @param changes   whether applying the preset changes the value
     */
    public record Row(VanillaOption option, Optional<Object> current, Object target, boolean supported,
                      boolean changes) {
        public Row {
            Objects.requireNonNull(option, "option");
            Objects.requireNonNull(current, "current");
            Objects.requireNonNull(target, "target");
        }
    }

    public PresetDiff {
        Objects.requireNonNull(preset, "preset");
        rows = List.copyOf(rows);
    }

    /** Computes the diff for the current options. */
    public static PresetDiff compute(PerformancePreset preset, OptionsBridge options) {
        Objects.requireNonNull(preset, "preset");
        Objects.requireNonNull(options, "options");
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<VanillaOption, Object> entry : preset.optionValues().entrySet()) {
            VanillaOption option = entry.getKey();
            Object target = option.normalize(entry.getValue()).orElse(entry.getValue());
            boolean supported = options.supports(option);
            Optional<Object> current = supported ? options.get(option) : Optional.empty();
            boolean changes = supported && (current.isEmpty() || !valuesEqual(current.get(), target));
            rows.add(new Row(option, current, target, supported, changes));
        }
        return new PresetDiff(preset, rows);
    }

    /** Number of options whose value changes. */
    public int changeCount() {
        int n = 0;
        for (Row row : rows) {
            if (row.changes()) {
                n++;
            }
        }
        return n;
    }

    /** True when the current options already match the preset. */
    public boolean isNoop() {
        return changeCount() == 0;
    }

    /** Rows that change, in order. */
    public List<Row> changedRows() {
        List<Row> out = new ArrayList<>();
        for (Row row : rows) {
            if (row.changes()) {
                out.add(row);
            }
        }
        return out;
    }

    static boolean valuesEqual(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) {
            return Math.abs(x.doubleValue() - y.doubleValue()) < 1e-6;
        }
        if (a instanceof String x && b instanceof String y) {
            return x.equalsIgnoreCase(y);
        }
        return Objects.equals(a, b);
    }
}
