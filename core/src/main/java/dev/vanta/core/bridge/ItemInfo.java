package dev.vanta.core.bridge;

import java.util.Objects;

/**
 * Snapshot of an item stack relevant to HUD widgets (armor, held items, durability).
 *
 * @param name          display name
 * @param durability    remaining durability, or 0 when the item has none
 * @param maxDurability maximum durability, or 0 when the item is not damageable
 */
public record ItemInfo(String name, int durability, int maxDurability) {
    /** The "nothing in this slot" marker. */
    public static final ItemInfo EMPTY = new ItemInfo("", 0, 0);

    public ItemInfo {
        Objects.requireNonNull(name, "name");
        durability = Math.max(0, durability);
        maxDurability = Math.max(0, maxDurability);
    }

    /** True when the slot is empty. */
    public boolean isEmpty() {
        return name.isEmpty() && maxDurability == 0;
    }

    /** True when the item has a durability bar. */
    public boolean hasDurability() {
        return maxDurability > 0;
    }

    /** Remaining durability as a fraction in [0, 1]; 1 for items without durability. */
    public double durabilityFraction() {
        if (maxDurability <= 0) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, durability / (double) maxDurability));
    }
}
