package dev.vanta.core.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonPrimitive;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.screen.common.SettingFormats;
import dev.vanta.core.settings.SettingCategory;
import dev.vanta.core.settings.VantaSettings;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TimeZoneChoiceTest {
    private static final Instant SUMMER = Instant.parse("2026-07-15T06:00:00Z");
    private static final Instant WINTER = Instant.parse("2026-01-15T06:00:00Z");

    @Test
    void theSettingDefaultsToThisPcsZoneAndIsStoredAsAuto() {
        assertEquals(TimeZoneChoice.AUTO, VantaSettings.GENERAL_TIME_ZONE.defaultValue());
        assertEquals("general.timeZone", VantaSettings.GENERAL_TIME_ZONE.id());
        assertEquals(SettingCategory.GENERAL, VantaSettings.GENERAL_TIME_ZONE.category());
        assertTrue(VantaSettings.GENERAL_TIME_ZONE.isPersisted());
        assertFalse(VantaSettings.GENERAL_TIME_ZONE.needsRestart(), "a new choice shows on the next tick");
        assertEquals(new JsonPrimitive("auto"), VantaSettings.GENERAL_TIME_ZONE.encode(TimeZoneChoice.AUTO));
        assertEquals(new JsonPrimitive("europe_berlin"),
                VantaSettings.GENERAL_TIME_ZONE.encode(TimeZoneChoice.EUROPE_BERLIN));
        assertEquals(Optional.of(TimeZoneChoice.EUROPE_BERLIN),
                VantaSettings.GENERAL_TIME_ZONE.decode(new JsonPrimitive("europe_berlin")));
        assertEquals(Optional.empty(), VantaSettings.GENERAL_TIME_ZONE.decode(new JsonPrimitive("Mars/Olympus")),
                "an unknown value falls back to the default when loaded");
        assertEquals(List.of(TimeZoneChoice.values()), VantaSettings.GENERAL_TIME_ZONE.options());
    }

    @Test
    void autoFollowsTheDefaultZoneOnEveryCall() {
        DefaultZone.with("Europe/Berlin", () -> {
            assertEquals(ZoneId.of("Europe/Berlin"), TimeZoneChoice.AUTO.zone());
            assertEquals("8:00 AM", TimeOfDay.format(SUMMER.toEpochMilli(), TimeZoneChoice.AUTO.zone()));
            assertEquals("7:00 AM", TimeOfDay.format(WINTER.toEpochMilli(), TimeZoneChoice.AUTO.zone()));
        });
        DefaultZone.with("Asia/Tokyo", () -> {
            assertEquals(ZoneId.of("Asia/Tokyo"), TimeZoneChoice.AUTO.zone(), "not cached from the earlier call");
            assertEquals("3:00 PM", TimeOfDay.format(SUMMER.toEpochMilli(), TimeZoneChoice.AUTO.zone()));
        });
        assertTrue(TimeZoneChoice.AUTO.isAutomatic());
        assertEquals(Optional.empty(), TimeZoneChoice.AUTO.zoneId());
    }

    @Test
    void aFixedChoiceIgnoresTheDefaultZoneAndKeepsItsDaylightSavingRules() {
        DefaultZone.with("America/Los_Angeles", () -> {
            assertEquals(ZoneId.of("Europe/Berlin"), TimeZoneChoice.EUROPE_BERLIN.zone());
            assertEquals("8:00 AM", TimeOfDay.format(SUMMER.toEpochMilli(), TimeZoneChoice.EUROPE_BERLIN.zone()));
            assertEquals("7:00 AM", TimeOfDay.format(WINTER.toEpochMilli(), TimeZoneChoice.EUROPE_BERLIN.zone()));
        });
        assertEquals(Optional.of("Europe/Berlin"), TimeZoneChoice.EUROPE_BERLIN.zoneId());
        assertFalse(TimeZoneChoice.EUROPE_BERLIN.isAutomatic());
        assertEquals("6:00 AM", TimeOfDay.format(SUMMER.toEpochMilli(), TimeZoneChoice.UTC.zone()));
    }

    @Test
    void labelsShowTheCityAndTheOffsetOfThatMoment() {
        assertEquals("Berlin (UTC+2)", TimeZoneChoice.EUROPE_BERLIN.label(SUMMER));
        assertEquals("Berlin (UTC+1)", TimeZoneChoice.EUROPE_BERLIN.label(WINTER));
        assertEquals("London (UTC+0)", TimeZoneChoice.EUROPE_LONDON.label(WINTER));
        assertEquals("New York (UTC-4)", TimeZoneChoice.AMERICA_NEW_YORK.label(SUMMER));
        assertEquals("Kolkata (UTC+5:30)", TimeZoneChoice.ASIA_KOLKATA.label(SUMMER));
        assertEquals("St. John's (UTC-2:30)", TimeZoneChoice.AMERICA_ST_JOHNS.label(SUMMER));
        assertEquals("Sydney (UTC+11)", TimeZoneChoice.AUSTRALIA_SYDNEY.label(WINTER));
        assertEquals("São Paulo (UTC-3)", TimeZoneChoice.AMERICA_SAO_PAULO.label(SUMMER));
        assertEquals("UTC", TimeZoneChoice.UTC.label(SUMMER));
        DefaultZone.with("Europe/Berlin", () -> {
            assertEquals("Automatic (this PC, UTC+2)", TimeZoneChoice.AUTO.label(SUMMER));
            assertEquals("Automatic (this PC, UTC+1)", TimeZoneChoice.AUTO.label(WINTER));
        });
        assertEquals("Automatic (this PC)", Lang.tr(TimeZoneChoice.AUTO.langKey()), "the plain name");
    }

    @Test
    void theSettingsRowsShowTheLiveLabel() {
        Instant now = Instant.now();
        for (TimeZoneChoice choice : List.of(TimeZoneChoice.EUROPE_BERLIN, TimeZoneChoice.ASIA_TOKYO)) {
            String label = SettingFormats.optionLabel(VantaSettings.GENERAL_TIME_ZONE, choice);
            assertEquals(choice.label(now), label);
            assertEquals(choice.label(now), SettingFormats.format(VantaSettings.GENERAL_TIME_ZONE, choice));
        }
        assertEquals("Tokyo (UTC+9)", SettingFormats.optionLabel(VantaSettings.GENERAL_TIME_ZONE,
                TimeZoneChoice.ASIA_TOKYO), "Japan has no daylight saving time");
    }

    @Test
    void everyChoiceIsARealDistinctZoneWithAName() {
        Set<ZoneId> zones = new HashSet<>();
        for (TimeZoneChoice choice : TimeZoneChoice.values()) {
            assertTrue(Lang.has(choice.langKey()), "name of " + choice);
            if (choice == TimeZoneChoice.AUTO) {
                continue;
            }
            ZoneId zone = choice.zone();
            assertEquals(ZoneId.of(choice.zoneId().orElseThrow()), zone);
            assertTrue(zones.add(zone), "listed twice: " + zone);
            assertFalse(choice.label(SUMMER).contains("%"), choice.label(SUMMER));
        }
        assertEquals(TimeZoneChoice.UTC, TimeZoneChoice.values()[1], "UTC comes right after Automatic");
    }

    @Test
    void theListRunsFromWestToEastByStandardOffset() {
        TimeZoneChoice[] all = TimeZoneChoice.values();
        int previous = Integer.MIN_VALUE;
        for (int i = 2; i < all.length; i++) {
            int standard = all[i].zone().getRules().getStandardOffset(WINTER).getTotalSeconds();
            assertTrue(standard >= previous, all[i] + " is listed after a zone further east");
            previous = standard;
        }
    }

    @Test
    void everyCommonOffsetAndTheBigRegionsAreCovered() {
        Set<Integer> standardMinutes = new HashSet<>();
        for (TimeZoneChoice choice : TimeZoneChoice.values()) {
            if (choice != TimeZoneChoice.AUTO) {
                standardMinutes.add(choice.zone().getRules().getStandardOffset(WINTER).getTotalSeconds() / 60);
            }
        }
        for (int hours = -10; hours <= 13; hours++) {
            assertTrue(standardMinutes.contains(hours * 60), "no zone with standard offset UTC" + hours);
        }
        for (int minutes : new int[] {-210, 210, 270, 330, 345, 390, 570}) {
            assertTrue(standardMinutes.contains(minutes), "no zone with standard offset " + minutes + " min");
        }
        for (String id : List.of("Europe/Berlin", "Europe/London", "Europe/Paris", "Europe/Moscow", "Europe/Istanbul",
                "America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles", "America/Sao_Paulo",
                "America/Mexico_City", "Asia/Dubai", "Asia/Kolkata", "Asia/Shanghai", "Asia/Tokyo", "Asia/Seoul",
                "Asia/Singapore", "Australia/Sydney", "Pacific/Auckland", "Africa/Cairo", "Africa/Johannesburg",
                "UTC")) {
            boolean listed = false;
            for (TimeZoneChoice choice : TimeZoneChoice.values()) {
                listed |= choice.zoneId().map(id::equals).orElse(false);
            }
            assertTrue(listed, id + " is not offered");
        }
        assertEquals(ZoneOffset.UTC, TimeZoneChoice.UTC.zone().getRules().getOffset(SUMMER));
    }
}
