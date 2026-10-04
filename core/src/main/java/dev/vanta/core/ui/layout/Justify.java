package dev.vanta.core.ui.layout;

/** Main-axis distribution of children inside {@link Column} and {@link Row} when no child is flexible. */
public enum Justify {
    /** Pack children at the start. */
    START,
    /** Center the group. */
    CENTER,
    /** Pack children at the end. */
    END,
    /** Distribute the free space between children. */
    SPACE_BETWEEN
}
