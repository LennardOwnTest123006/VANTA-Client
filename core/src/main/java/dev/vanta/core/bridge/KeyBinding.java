package dev.vanta.core.bridge;

import java.util.Objects;

/**
 * Snapshot of one key mapping (vanilla, another mod's or VANTA's own).
 *
 * @param id           mapping name / translation key, e.g. {@code key.forward} or {@code key.vanta.open_menu}
 * @param displayName  translated name
 * @param categoryId   category key, e.g. {@code key.categories.movement}
 * @param categoryName translated category name
 * @param boundKeyName vanilla key name of the current binding ({@code key.keyboard.w})
 * @param boundKeyCode GLFW code / scancode / mouse button of the current binding, {@code -1} when unbound
 * @param boundKeyType input source of the current binding
 * @param isDefault    true when the binding equals its default
 * @param isVanilla    true for Minecraft's own mappings
 */
public record KeyBinding(String id, String displayName, String categoryId, String categoryName, String boundKeyName,
                         int boundKeyCode, KeyType boundKeyType, boolean isDefault, boolean isVanilla) {
    public KeyBinding {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(categoryId, "categoryId");
        Objects.requireNonNull(categoryName, "categoryName");
        Objects.requireNonNull(boundKeyName, "boundKeyName");
        Objects.requireNonNull(boundKeyType, "boundKeyType");
    }

    /** The current binding as a {@link KeyRef}. */
    public KeyRef boundKey() {
        return new KeyRef(boundKeyType, boundKeyCode, boundKeyName);
    }

    /** True when nothing is bound. */
    public boolean isUnbound() {
        return boundKey().isUnbound();
    }

    /** Copy with a different binding. */
    public KeyBinding withKey(KeyRef key, boolean nowDefault) {
        return new KeyBinding(id, displayName, categoryId, categoryName, key.name(), key.code(), key.type(),
                nowDefault, isVanilla);
    }
}
