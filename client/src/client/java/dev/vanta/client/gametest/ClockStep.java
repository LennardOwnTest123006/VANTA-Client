package dev.vanta.client.gametest;

import dev.vanta.core.hud.render.HudData;
import dev.vanta.core.hud.render.HudRenderer;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.time.TimeZoneChoice;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * The HUD clock shows the player's local time (from client 1.6.0 on; 1.5.0 formatted it in UTC, so a player in Germany
 * saw 6:00 AM at 8:00 AM). Runs in the test world with the Clock widget enabled and reads the clock text the real HUD
 * renderer captured on its last client tick:
 * <ol>
 *   <li>with the default setting ({@code general.timeZone = auto}) the text is this PC's local time: it is logged next
 *       to {@link LocalTime#now()} and must equal it (allowing for a minute that turns over during the read), and the
 *       captured instant must be the real wall clock;</li>
 *   <li>because CI runners use UTC, which would hide the old bug, the JVM default zone is then switched to
 *       Asia/Kolkata (UTC+5:30) for two ticks: Automatic must follow it on the next tick;</li>
 *   <li>a chosen zone (Berlin) wins over the PC's zone.</li>
 * </ol>
 * The JDK's own {@code h:mm a} formatter is the reference, so the check does not trust VANTA's formatter. The setting
 * and the default zone are restored afterwards.
 */
final class ClockStep {
    private static final DateTimeFormatter REFERENCE = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    /** A zone whose offset differs from UTC and from every whole-hour zone. */
    private static final String OTHER_ZONE = "Asia/Kolkata";

    private ClockStep() {
    }

    /** One read of the HUD snapshot on the client thread. */
    private record Reading(String text, long capturedAt, ZoneId zone, long wallMillis, LocalTime localNow) {
    }

    static void run(ClientGameTestContext context, VantaServices services, HudRenderer hud) {
        TimeZoneChoice saved = context.computeOnClient(client -> services.settings()
                .get(VantaSettings.GENERAL_TIME_ZONE));
        TimeZone savedDefault = TimeZone.getDefault();
        try {
            context.runOnClient(client -> services.settings().set(VantaSettings.GENERAL_TIME_ZONE,
                    TimeZoneChoice.AUTO));
            LocalTime before = LocalTime.now();
            context.waitTicks(2);
            Reading auto = read(context, hud);
            ZoneId pcZone = ZoneId.systemDefault();
            VantaClientGameTest.step("clock: HUD clock " + auto.text() + " in " + auto.zone() + " (setting auto), "
                    + "LocalTime.now() " + auto.localNow() + " in " + pcZone);
            VantaClientGameTest.check(auto.zone().equals(pcZone), "With the default time zone setting the HUD clock "
                    + "uses " + auto.zone() + " instead of this PC's zone " + pcZone);
            VantaClientGameTest.check(Math.abs(auto.wallMillis() - auto.capturedAt()) < 5_000L,
                    "The HUD clock instant " + Instant.ofEpochMilli(auto.capturedAt()) + " is not the wall clock "
                            + Instant.ofEpochMilli(auto.wallMillis()));
            Set<String> local = Set.of(REFERENCE.format(before), REFERENCE.format(auto.localNow()));
            VantaClientGameTest.check(local.contains(auto.text()), "The HUD clock shows " + auto.text()
                    + " but the local time is " + local);
            VantaClientGameTest.check(hour(auto.text()) == twelveHour(auto.localNow().getHour())
                            || hour(auto.text()) == twelveHour(before.getHour()),
                    "The HUD clock hour of " + auto.text() + " is not the local hour of " + auto.localNow());

            context.runOnClient(client -> TimeZone.setDefault(TimeZone.getTimeZone(OTHER_ZONE)));
            context.waitTicks(2);
            Reading moved = read(context, hud);
            String expectedMoved = reference(moved.capturedAt(), ZoneId.of(OTHER_ZONE));
            VantaClientGameTest.step("clock: with the PC zone " + OTHER_ZONE + " the HUD clock shows " + moved.text()
                    + " (expected " + expectedMoved + ")");
            VantaClientGameTest.check(moved.text().equals(expectedMoved), "Automatic did not follow the default zone "
                    + OTHER_ZONE + ": the HUD clock shows " + moved.text() + ", expected " + expectedMoved);
            context.runOnClient(client -> TimeZone.setDefault(savedDefault));

            context.runOnClient(client -> services.settings().set(VantaSettings.GENERAL_TIME_ZONE,
                    TimeZoneChoice.EUROPE_BERLIN));
            context.waitTicks(2);
            Reading berlin = read(context, hud);
            String expectedBerlin = reference(berlin.capturedAt(), ZoneId.of("Europe/Berlin"));
            VantaClientGameTest.step("clock: with the setting Berlin the HUD clock shows " + berlin.text()
                    + " (expected " + expectedBerlin + ")");
            VantaClientGameTest.check(berlin.text().equals(expectedBerlin), "The chosen zone Europe/Berlin is not "
                    + "used: the HUD clock shows " + berlin.text() + ", expected " + expectedBerlin);
        } finally {
            context.runOnClient(client -> {
                TimeZone.setDefault(savedDefault);
                services.settings().set(VantaSettings.GENERAL_TIME_ZONE, saved);
            });
        }
    }

    private static Reading read(ClientGameTestContext context, HudRenderer hud) {
        return context.computeOnClient(client -> {
            HudData data = hud.data();
            return new Reading(data.clock12(false), data.epochMillis(), data.zone(), System.currentTimeMillis(),
                    LocalTime.now());
        });
    }

    private static String reference(long epochMillis, ZoneId zone) {
        return REFERENCE.format(Instant.ofEpochMilli(epochMillis).atZone(zone));
    }

    /** The hour of a {@code h:mm AM} text. */
    private static int hour(String text) {
        int colon = text.indexOf(':');
        return colon > 0 ? Integer.parseInt(text.substring(0, colon)) : -1;
    }

    private static int twelveHour(int hour) {
        int h = hour % 12;
        return h == 0 ? 12 : h;
    }
}
