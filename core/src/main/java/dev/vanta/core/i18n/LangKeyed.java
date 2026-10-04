package dev.vanta.core.i18n;

/**
 * Implemented by enums and models whose display name comes from the language file.
 * The UI layer calls {@code Lang.tr(value.langKey())}.
 */
public interface LangKeyed {
    /** Translation key of the display name. */
    String langKey();
}
