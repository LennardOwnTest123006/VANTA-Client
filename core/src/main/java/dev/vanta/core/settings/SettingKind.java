package dev.vanta.core.settings;

/**
 * Editor type of a setting; decides which widget the settings screen renders.
 */
public enum SettingKind {
    /** Toggle. Value type {@link Boolean}. */
    BOOL,
    /** Integer slider with min/max/step. Value type {@link Integer}. */
    INT_RANGE,
    /** Double slider with min/max/step. Value type {@link Double}. */
    DOUBLE_RANGE,
    /** Dropdown over a fixed list of values (Java enum or vanilla enum name). */
    ENUM,
    /** ARGB colour. Value type {@link Integer}, persisted as {@code #AARRGGBB}. */
    COLOR,
    /** Free text. Value type {@link String}. */
    STRING,
    /** Key capture. Value type {@link String} holding a vanilla key name such as {@code key.keyboard.c}. */
    KEY,
    /** Button that triggers an action; nothing is persisted. */
    ACTION
}
