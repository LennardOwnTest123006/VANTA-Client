package dev.vanta.core.time;

import dev.vanta.core.i18n.Lang;
import dev.vanta.core.i18n.LangKeyed;
import dev.vanta.core.i18n.LiveLabel;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;

/**
 * The values of the {@code general.timeZone} setting: the time zone every VANTA time of day is shown in (the HUD clock,
 * statistics, profiles, the timer). {@link #AUTO}, the default, is this PC's time zone; the others are a curated list
 * of IANA zones, one or more for every common UTC offset and the big regions, ordered from west to east by their
 * standard offset (after {@link #UTC}).
 * <p>
 * Only the formatting zone changes: timestamps stay UTC epoch milliseconds. A named zone carries its daylight saving
 * rules, so {@link #EUROPE_BERLIN} shows {@code UTC+2} in summer and {@code UTC+1} in winter without any change by the
 * player.
 */
public enum TimeZoneChoice implements LangKeyed, LiveLabel {
    /** This PC's time zone ({@link ZoneId#systemDefault()}, read every time it is used). */
    AUTO(null),
    UTC("UTC"),
    PACIFIC_HONOLULU("Pacific/Honolulu"),
    AMERICA_ANCHORAGE("America/Anchorage"),
    AMERICA_LOS_ANGELES("America/Los_Angeles"),
    AMERICA_DENVER("America/Denver"),
    AMERICA_PHOENIX("America/Phoenix"),
    AMERICA_CHICAGO("America/Chicago"),
    AMERICA_MEXICO_CITY("America/Mexico_City"),
    AMERICA_NEW_YORK("America/New_York"),
    AMERICA_BOGOTA("America/Bogota"),
    AMERICA_HALIFAX("America/Halifax"),
    AMERICA_SANTIAGO("America/Santiago"),
    AMERICA_ST_JOHNS("America/St_Johns"),
    AMERICA_SAO_PAULO("America/Sao_Paulo"),
    AMERICA_BUENOS_AIRES("America/Argentina/Buenos_Aires"),
    AMERICA_NORONHA("America/Noronha"),
    ATLANTIC_AZORES("Atlantic/Azores"),
    EUROPE_LONDON("Europe/London"),
    AFRICA_LAGOS("Africa/Lagos"),
    EUROPE_BERLIN("Europe/Berlin"),
    EUROPE_PARIS("Europe/Paris"),
    EUROPE_MADRID("Europe/Madrid"),
    EUROPE_ROME("Europe/Rome"),
    EUROPE_VIENNA("Europe/Vienna"),
    EUROPE_ZURICH("Europe/Zurich"),
    EUROPE_WARSAW("Europe/Warsaw"),
    AFRICA_JOHANNESBURG("Africa/Johannesburg"),
    AFRICA_CAIRO("Africa/Cairo"),
    EUROPE_ATHENS("Europe/Athens"),
    EUROPE_KYIV("Europe/Kyiv"),
    EUROPE_ISTANBUL("Europe/Istanbul"),
    EUROPE_MOSCOW("Europe/Moscow"),
    ASIA_RIYADH("Asia/Riyadh"),
    ASIA_TEHRAN("Asia/Tehran"),
    ASIA_DUBAI("Asia/Dubai"),
    ASIA_KABUL("Asia/Kabul"),
    ASIA_KARACHI("Asia/Karachi"),
    ASIA_KOLKATA("Asia/Kolkata"),
    ASIA_KATHMANDU("Asia/Kathmandu"),
    ASIA_DHAKA("Asia/Dhaka"),
    ASIA_YANGON("Asia/Yangon"),
    ASIA_BANGKOK("Asia/Bangkok"),
    ASIA_JAKARTA("Asia/Jakarta"),
    ASIA_SHANGHAI("Asia/Shanghai"),
    ASIA_SINGAPORE("Asia/Singapore"),
    AUSTRALIA_PERTH("Australia/Perth"),
    ASIA_SEOUL("Asia/Seoul"),
    ASIA_TOKYO("Asia/Tokyo"),
    AUSTRALIA_ADELAIDE("Australia/Adelaide"),
    AUSTRALIA_BRISBANE("Australia/Brisbane"),
    AUSTRALIA_SYDNEY("Australia/Sydney"),
    PACIFIC_NOUMEA("Pacific/Noumea"),
    PACIFIC_AUCKLAND("Pacific/Auckland"),
    PACIFIC_TONGATAPU("Pacific/Tongatapu");

    /** {@code Berlin (UTC+2)}: the place name and the offset right now. */
    private static final String LABEL_KEY = "vanta.time.zone.label";
    /** {@code Automatic (this PC, UTC+2)}. */
    private static final String AUTO_LABEL_KEY = "vanta.time.zone.auto_label";

    private final String zoneId;
    /** Resolved once: a region id keeps its daylight saving rules, so caching it is correct all year. */
    private final ZoneId zone;

    TimeZoneChoice(String zoneId) {
        this.zoneId = zoneId;
        this.zone = zoneId == null ? null : ZoneId.of(zoneId);
    }

    /**
     * The zone to format times of day in. {@link #AUTO} reads {@link ZoneId#systemDefault()} on every call, so a
     * changed JVM default zone is followed at once; daylight saving time is part of every zone's rules and is followed
     * by all of them.
     */
    public ZoneId zone() {
        return zone == null ? ZoneId.systemDefault() : zone;
    }

    /** The IANA id of a fixed zone ({@code Europe/Berlin}); empty for {@link #AUTO}. */
    public Optional<String> zoneId() {
        return Optional.ofNullable(zoneId);
    }

    /** True for {@link #AUTO}. */
    public boolean isAutomatic() {
        return zone == null;
    }

    /** Translation key of the plain place name ({@code Berlin}, {@code Automatic (this PC)}). */
    @Override
    public String langKey() {
        return "vanta.time.zone." + name().toLowerCase(Locale.ROOT);
    }

    /**
     * The dropdown label at {@code at}: {@code Berlin (UTC+2)}, {@code Automatic (this PC, UTC+2)} or plain
     * {@code UTC}. The offset includes daylight saving time.
     */
    public String label(Instant at) {
        if (this == AUTO) {
            return Lang.tr(AUTO_LABEL_KEY, TimeOfDay.offset(zone(), at));
        }
        if (this == UTC) {
            return Lang.tr(langKey());
        }
        return Lang.tr(LABEL_KEY, Lang.tr(langKey()), TimeOfDay.offset(zone, at));
    }

    /** {@link #label(Instant)} for the current moment. */
    @Override
    public String liveLabel() {
        return label(Instant.now());
    }
}
