package dev.vanta.core.bridge;

import java.util.Objects;

/**
 * Snapshot of one resource pack known to the game.
 *
 * @param id                pack id (file or folder name, or {@code vanilla})
 * @param title             display title
 * @param description       description text
 * @param compatible        true when the pack targets the running game version
 * @param compatibilityText vanilla compatibility description ("Made for an older version of Minecraft", …)
 * @param enabled           true when the pack is in the selected list
 * @param required          true when the pack cannot be disabled (vanilla, server packs)
 * @param position          index in the selected list (0 = bottom / lowest priority) or {@code -1} when disabled
 * @param source            source label (built-in, world, server, file)
 * @param hasIcon           true when a {@code pack.png} exists
 */
public record PackInfo(String id, String title, String description, boolean compatible, String compatibilityText,
                       boolean enabled, boolean required, int position, String source, boolean hasIcon) {
    public PackInfo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(compatibilityText, "compatibilityText");
        Objects.requireNonNull(source, "source");
    }
}
