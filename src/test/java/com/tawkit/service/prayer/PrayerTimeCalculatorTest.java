package com.tawkit.service.prayer;

import com.tawkit.model.prayer.AsrMethod;
import com.tawkit.model.prayer.CalculationMethod;
import com.tawkit.model.prayer.PrayerType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Astronomy engine tests (#10) against published reference values.
 * Reference: Aladhan API (PrayTimes-compatible), Makkah 21.4225 N / 39.8262 E,
 * UTC+3, Muslim World League, standard Asr, 2026-01-01:
 * Fajr 05:39, Sunrise 06:59, Dhuhr 12:24, Asr 15:29, Maghrib 17:50, Isha 19:05.
 */
class PrayerTimeCalculatorTest {

    private static final LocalDate DATE = LocalDate.of(2026, 1, 1);
    private static final double MAKKAH_LAT = 21.4225;
    private static final double MAKKAH_LON = 39.8262;
    private static final double MAKKAH_TZ = 3.0;

    private static Map<PrayerType, LocalTime> computeMakkah() {
        return new PrayerTimeCalculator().compute(DATE, MAKKAH_LAT, MAKKAH_LON, MAKKAH_TZ,
                CalculationMethod.MUSLIM_WORLD_LEAGUE, AsrMethod.STANDARD);
    }

    /** Allowance covers the reference's own rounding and the 1-min Dhuhr safety. */
    private static final int TOLERANCE_MINUTES = 2;

    private static void assertClose(LocalTime actual, String expected, String label) {
        assertNotNull(actual, label + " should be computable at Makkah");
        LocalTime expectedTime = LocalTime.parse(expected);
        long diff = Math.abs(java.time.Duration.between(expectedTime, actual).toSeconds());
        assertTrue(diff <= TOLERANCE_MINUTES * 60L,
                label + " expected ~" + expected + " but was " + actual);
    }

    @Test
    void makkahMatchesPublishedReferenceTimes() {
        Map<PrayerType, LocalTime> times = computeMakkah();
        assertClose(times.get(PrayerType.FAJR), "05:39", "Fajr");
        assertClose(times.get(PrayerType.SHURUQ), "06:59", "Sunrise");
        assertClose(times.get(PrayerType.DHUHR), "12:24", "Dhuhr");
        assertClose(times.get(PrayerType.ASR), "15:29", "Asr");
        assertClose(times.get(PrayerType.MAGHRIB), "17:50", "Maghrib");
        assertClose(times.get(PrayerType.ISHA), "19:05", "Isha");
    }

    @Test
    void chronologicalOrderHolds() {
        Map<PrayerType, LocalTime> times = computeMakkah();
        assertTrue(times.get(PrayerType.FAJR).isBefore(times.get(PrayerType.SHURUQ)));
        assertTrue(times.get(PrayerType.SHURUQ).isBefore(times.get(PrayerType.DHUHR)));
        assertTrue(times.get(PrayerType.DHUHR).isBefore(times.get(PrayerType.ASR)));
        assertTrue(times.get(PrayerType.ASR).isBefore(times.get(PrayerType.MAGHRIB)));
        assertTrue(times.get(PrayerType.MAGHRIB).isBefore(times.get(PrayerType.ISHA)));
    }

    @Test
    void timesShiftWithLongitude() {
        // 15° east = one hour later solar events
        Map<PrayerType, LocalTime> west = computeMakkah();
        Map<PrayerType, LocalTime> east = new PrayerTimeCalculator().compute(
                DATE, MAKKAH_LAT, MAKKAH_LON + 15.0, MAKKAH_TZ,
                CalculationMethod.MUSLIM_WORLD_LEAGUE, AsrMethod.STANDARD);
        long shift = java.time.Duration.between(
                west.get(PrayerType.MAGHRIB), east.get(PrayerType.MAGHRIB)).toMinutes();
        assertTrue(Math.abs(shift + 60) <= 3, "Maghrib should be ~1h earlier; shift was " + shift);
    }

    @Test
    void hanafiAsrIsLaterThanStandard() {
        Map<PrayerType, LocalTime> standard = computeMakkah();
        Map<PrayerType, LocalTime> hanafi = new PrayerTimeCalculator().compute(
                DATE, MAKKAH_LAT, MAKKAH_LON, MAKKAH_TZ,
                CalculationMethod.MUSLIM_WORLD_LEAGUE, AsrMethod.HANAFI);
        assertTrue(hanafi.get(PrayerType.ASR).isAfter(standard.get(PrayerType.ASR)),
                "Hanafi Asr (shadow x2) must be later");
        assertEquals(standard.get(PrayerType.FAJR), hanafi.get(PrayerType.FAJR),
                "Asr method must not affect Fajr");
    }

    @Test
    void decemberSolsticeDaylightIsShorterThanJune() {
        LocalTime winterSunrise = new PrayerTimeCalculator().compute(
                LocalDate.of(2026, 12, 21), MAKKAH_LAT, MAKKAH_LON, MAKKAH_TZ,
                CalculationMethod.MUSLIM_WORLD_LEAGUE, AsrMethod.STANDARD).get(PrayerType.SHURUQ);
        LocalTime winterSunset = new PrayerTimeCalculator().compute(
                LocalDate.of(2026, 12, 21), MAKKAH_LAT, MAKKAH_LON, MAKKAH_TZ,
                CalculationMethod.MUSLIM_WORLD_LEAGUE, AsrMethod.STANDARD).get(PrayerType.MAGHRIB);
        LocalTime summerSunrise = new PrayerTimeCalculator().compute(
                LocalDate.of(2026, 6, 21), MAKKAH_LAT, MAKKAH_LON, MAKKAH_TZ,
                CalculationMethod.MUSLIM_WORLD_LEAGUE, AsrMethod.STANDARD).get(PrayerType.SHURUQ);
        LocalTime summerSunset = new PrayerTimeCalculator().compute(
                LocalDate.of(2026, 6, 21), MAKKAH_LAT, MAKKAH_LON, MAKKAH_TZ,
                CalculationMethod.MUSLIM_WORLD_LEAGUE, AsrMethod.STANDARD).get(PrayerType.MAGHRIB);

        double winterDay = java.time.Duration.between(winterSunrise, winterSunset).toMinutes();
        double summerDay = java.time.Duration.between(summerSunrise, summerSunset).toMinutes();
        // Makkah is in the northern hemisphere
        assertTrue(summerDay > winterDay,
                "Northern hemisphere June day must be longer (" + summerDay + " vs " + winterDay + ")");
    }
}
