package dev.vanta.core.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.bridge.FakeGameBridge;
import dev.vanta.core.bridge.FakeKeybindBridge;
import dev.vanta.core.bridge.FakeOptionsBridge;
import dev.vanta.core.bridge.FakeResourcePackBridge;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.search.SearchHit;
import dev.vanta.core.settings.Setting;
import dev.vanta.core.settings.SettingsSearchIndex;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.time.DefaultZone;
import dev.vanta.core.time.TimeZoneChoice;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link VantaServices#timeZone()}: the zone every VANTA time of day is formatted in. */
class VantaServicesTimeZoneTest {
    @TempDir
    Path dir;

    private VantaServices services() {
        VantaServices services = VantaServices.create(VantaPaths.inGameDirectory(dir), new FakeGameBridge(),
                new FakeOptionsBridge(), new FakeKeybindBridge(), new FakeResourcePackBridge(),
                MutableClock.standard());
        services.load();
        return services;
    }

    @Test
    void byDefaultTheZoneIsThisPcsZoneReadOnEveryCall() {
        VantaServices services = services();
        assertEquals(ZoneOffset.UTC, services.clock().getZone(), "the clock for timestamps stays UTC");
        DefaultZone.with("Europe/Berlin", () -> assertEquals(ZoneId.of("Europe/Berlin"), services.timeZone()));
        DefaultZone.with("America/Chicago", () -> assertEquals(ZoneId.of("America/Chicago"), services.timeZone()));
    }

    @Test
    void theChosenZoneIsUsedAndSurvivesARestart() throws Exception {
        VantaServices services = services();
        services.settings().set(VantaSettings.GENERAL_TIME_ZONE, TimeZoneChoice.EUROPE_BERLIN);
        DefaultZone.with("Asia/Tokyo", () -> assertEquals(ZoneId.of("Europe/Berlin"), services.timeZone()));
        services.saveAll();
        String json = Files.readString(services.paths().settingsFile());
        assertTrue(json.contains("\"general.timeZone\": \"europe_berlin\""), json);

        VantaServices restarted = services();
        assertEquals(TimeZoneChoice.EUROPE_BERLIN, restarted.settings().get(VantaSettings.GENERAL_TIME_ZONE));
        assertEquals(ZoneId.of("Europe/Berlin"), restarted.timeZone());
        restarted.settings().set(VantaSettings.GENERAL_TIME_ZONE, TimeZoneChoice.AUTO);
        DefaultZone.with("Asia/Tokyo", () -> assertEquals(ZoneId.of("Asia/Tokyo"), restarted.timeZone()));
    }

    @Test
    void theSettingIsFoundByTheWordsPlayersSearchFor() {
        SettingsSearchIndex index = new SettingsSearchIndex(VantaSettings.registry());
        for (String query : List.of("time zone", "timezone", "clock", "wrong time", "zeitzone", "utc")) {
            List<SearchHit<Setting<?>>> hits = index.search(query, 5);
            assertTrue(hits.stream().anyMatch(h -> h.item() == VantaSettings.GENERAL_TIME_ZONE),
                    "'" + query + "' finds the time zone setting");
        }
    }
}
