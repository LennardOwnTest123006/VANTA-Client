package dev.vanta.core.ui.layout;

/** Cross-axis alignment of children inside {@link Column} and {@link Row}. */
public enum Align {
    /** Align to the top (Row) or left (Column). */
    START,
    /** Center on the cross axis. */
    CENTER,
    /** Align to the bottom (Row) or right (Column). */
    END,
    /** Fill the cross axis. */
    STRETCH
}
