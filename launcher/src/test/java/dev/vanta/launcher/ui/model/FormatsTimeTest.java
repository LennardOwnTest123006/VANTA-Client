package dev.vanta.launcher.ui.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.launcher.ui.Messages;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** Times of day in the launcher are 12-hour times with AM/PM. */
class FormatsTimeTest {

    private final Formats formats = new Formats(Messages.english(), ZoneOffset.UTC);

    @Test
    void logTimestampsUseAmPm() {
        assertEquals("2:05:09 PM", formats.time(Instant.parse("2026-10-09T14:05:09Z")));
        assertEquals("12:00:00 AM", formats.time(Instant.parse("2026-10-09T00:00:00Z")));
        assertEquals("12:30:00 PM", formats.time(Instant.parse("2026-10-09T12:30:00Z")));
        assertEquals("9:07:03 AM", formats.time(Instant.parse("2026-10-09T09:07:03Z")));
    }

    @Test
    void dateTimesUseAmPm() {
        String text = formats.dateTime(Instant.parse("2026-10-09T14:05:00Z"));
        assertTrue(text.contains("2:05") && text.contains("PM"), text);
    }
}
