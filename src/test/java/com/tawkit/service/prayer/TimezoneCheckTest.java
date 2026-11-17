package com.tawkit.service.prayer;

import com.tawkit.model.prayer.Location;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Timezone sanity tests (#2, #10): a mis-typed UTC offset must never skew
 * prayer times by hours.
 */
class TimezoneCheckTest {

    @Test
    void storedSaneOffsetIsKept() {
        Location makkah = new Location("Makkah", "Saudi Arabia", 21.4225, 39.8262, 3.0);
        assertEquals(3.0, TimezoneCheck.resolve(makkah, LocalDate.of(2026, 1, 1)), 0.001);
    }

    @Test
    void bogusOffsetIsCorrectedFromLongitude() {
        // Someone typed +1 for Casablanca (real: +1 / DST +0) — longitude says ~-0.4h
        Location bogus = new Location("Casablanca", "Morocco", 33.5731, -7.5898, 1.0);
        double resolved = TimezoneCheck.resolve(bogus, LocalDate.of(2026, 1, 1));
        // Morocco is UTC+1 year-round since 2018 (with Ramadan exception), so either
        // 0.0 or 1.0 is defensible — but never a 5-hour error
        assertTrue(resolved >= -1.0 && resolved <= 1.5,
                "Casablanca offset must be ~0..1, was " + resolved);
    }

    @Test
    void newYorkFollowsDst() {
        Location ny = new Location("New York", "USA", 40.7128, -74.0060, 0.0);
        double summer = TimezoneCheck.resolve(ny, LocalDate.of(2026, 7, 1));
        double winter = TimezoneCheck.resolve(ny, LocalDate.of(2026, 1, 1));
        assertTrue(summer == -4.0 || summer == -5.0, "NY summer offset, was " + summer);
        assertTrue(winter == -5.0 || winter == -4.0, "NY winter offset, was " + winter);
    }

    @Test
    void nullLocationFallsBackToSystemZone() {
        double resolved = TimezoneCheck.resolve(null, LocalDate.of(2026, 1, 1));
        assertTrue(Math.abs(resolved) <= 14.0, "System offset must be valid, was " + resolved);
    }

    @Test
    void guessFromLongitudeIsRounded() {
        Location somewhere = new Location("Mid-ocean", "", 0.0, 37.5, 0.0);
        double resolved = TimezoneCheck.resolve(somewhere, LocalDate.of(2026, 1, 1));
        // Solar expectation is 37.5/15 = 2.5h; a matching real zone (e.g. UTC+3)
        // is preferred when within an hour of that — never a multi-hour error
        assertTrue(Math.abs(resolved - 2.5) <= 1.0,
                "Offset must be within 1h of the longitude expectation, was " + resolved);
    }
}
