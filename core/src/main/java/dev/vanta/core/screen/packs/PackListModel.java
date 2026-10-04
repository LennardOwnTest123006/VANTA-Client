package dev.vanta.core.screen.packs;

import dev.vanta.core.bridge.PackInfo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The two columns of the resource pack screen derived from the bridge's pack list: disabled packs sorted by title
 * on the left, enabled packs on the right with the highest priority (largest {@link PackInfo#position()}) at the top,
 * both narrowed by the search filter.
 *
 * @param available disabled packs matching the filter, by title
 * @param selected  enabled packs matching the filter, top priority first
 * @param total     number of packs before filtering
 */
public record PackListModel(List<PackInfo> available, List<PackInfo> selected, int total) {

    public PackListModel {
        available = List.copyOf(available);
        selected = List.copyOf(selected);
    }

    /** Builds the model. A blank filter keeps everything. */
    public static PackListModel from(List<PackInfo> packs, String filter) {
        Objects.requireNonNull(packs, "packs");
        List<PackInfo> available = new ArrayList<>();
        List<PackInfo> selected = new ArrayList<>();
        for (PackInfo pack : packs) {
            if (!matches(pack, filter)) {
                continue;
            }
            if (pack.enabled()) {
                selected.add(pack);
            } else {
                available.add(pack);
            }
        }
        available.sort(Comparator.comparing((PackInfo p) -> p.title().toLowerCase(Locale.ROOT)).thenComparing(PackInfo::id));
        selected.sort(Comparator.comparingInt(PackInfo::position).reversed().thenComparing(PackInfo::id));
        return new PackListModel(available, selected, packs.size());
    }

    /** Case-insensitive match against id, title and description. */
    public static boolean matches(PackInfo pack, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        String q = filter.trim().toLowerCase(Locale.ROOT);
        return pack.title().toLowerCase(Locale.ROOT).contains(q)
                || pack.description().toLowerCase(Locale.ROOT).contains(q)
                || pack.id().toLowerCase(Locale.ROOT).contains(q);
    }

    /** Finds a pack in either column. */
    public Optional<PackInfo> find(String id) {
        for (PackInfo p : selected) {
            if (p.id().equals(id)) {
                return Optional.of(p);
            }
        }
        for (PackInfo p : available) {
            if (p.id().equals(id)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    /** True when the pack is not the top-most enabled pack. */
    public boolean canMoveUp(PackInfo pack) {
        int index = selected.indexOf(pack);
        return index > 0;
    }

    /** True when the pack is enabled and not the bottom-most one. */
    public boolean canMoveDown(PackInfo pack) {
        int index = selected.indexOf(pack);
        return index >= 0 && index < selected.size() - 1;
    }

    /** True when the filter hid every pack. */
    public boolean isFilteredEmpty() {
        return total > 0 && available.isEmpty() && selected.isEmpty();
    }
}
