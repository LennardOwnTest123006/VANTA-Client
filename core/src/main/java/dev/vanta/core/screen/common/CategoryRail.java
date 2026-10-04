package dev.vanta.core.screen.common;

import dev.vanta.core.i18n.Lang;
import dev.vanta.core.settings.SettingCategory;
import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.VantaShell;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The settings category rail: one {@link VantaShell.RailItem} per {@link SettingCategory}, in display order, with the
 * category's icon and translated name. Rail ids are the category ids so {@link #category(String)} maps a selection
 * back to the enum.
 */
public final class CategoryRail {
    private CategoryRail() {
    }

    /** Rail items for every category. */
    public static List<VantaShell.RailItem> items() {
        List<VantaShell.RailItem> items = new ArrayList<>();
        for (SettingCategory category : SettingCategory.values()) {
            items.add(item(category));
        }
        return items;
    }

    /** The rail item of one category. */
    public static VantaShell.RailItem item(SettingCategory category) {
        return new VantaShell.RailItem(category.id(), icon(category), Lang.tr(category.langKey()));
    }

    /** Icon of a category (from its domain icon id). */
    public static Icons icon(SettingCategory category) {
        return IconIds.resolve(category.iconId(), Icons.GEAR);
    }

    /** Category for a rail id. */
    public static Optional<SettingCategory> category(String railId) {
        if (railId == null) {
            return Optional.empty();
        }
        for (SettingCategory category : SettingCategory.values()) {
            if (category.id().equals(railId)) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }
}
