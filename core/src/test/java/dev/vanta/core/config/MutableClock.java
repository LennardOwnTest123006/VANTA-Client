package dev.vanta.core.config;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Test clock that only moves when told to. Shared by every core test and by the Java2D preview tooling.
 */
public final class MutableClock extends Clock {
    private long millis;
    private final ZoneId zone;

    public MutableClock(long startMillis) {
        this(startMillis, ZoneOffset.UTC);
    }

    public MutableClock(long startMillis, ZoneId zone) {
        this.millis = startMillis;
        this.zone = zone;
    }

    /** Clock starting at 2026-10-04T12:00:00Z. */
    public static MutableClock standard() {
        return new MutableClock(1_791_115_200_000L);
    }

    public void advance(long deltaMillis) {
        millis += deltaMillis;
    }

    public void set(long newMillis) {
        millis = newMillis;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new MutableClock(millis, newZone);
    }

    @Override
    public Instant instant() {
        return Instant.ofEpochMilli(millis);
    }

    @Override
    public long millis() {
        return millis;
    }
}
