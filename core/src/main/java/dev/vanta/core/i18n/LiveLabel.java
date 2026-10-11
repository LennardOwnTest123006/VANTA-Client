package dev.vanta.core.i18n;

/**
 * Implemented by options whose display text depends on the moment it is shown, for example a time zone labelled with
 * its current UTC offset ({@code Berlin (UTC+2)} in summer, {@code Berlin (UTC+1)} in winter). The settings rows ask
 * for {@link #liveLabel()} every time they draw; {@link LangKeyed#langKey()} stays the stable translation key of the
 * plain name.
 */
public interface LiveLabel {
    /** The translated display text for right now. */
    String liveLabel();
}
