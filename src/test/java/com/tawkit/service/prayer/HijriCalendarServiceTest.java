package com.tawkit.service.prayer;

import com.tawkit.model.common.HijriDate;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hijri calendar tests (#10). External reference: Aladhan API —
 * 2026-01-01 is 12 Rajab 1447.
 */
class HijriCalendarServiceTest {

    @Test
    void knownConversion2026_01_01() {
        HijriDate h = new HijriCalendarService().toHijri(LocalDate.of(2026, 1, 1));
        assertEquals(12, h.getDay());
        assertEquals(7, h.getMonth());      // Rajab
        assertEquals(1447, h.getYear());
        assertEquals("Rajab", h.getMonthName());
    }

    @Test
    void ramadan2025StartsMarchFirst() {
        // Widely published: 1 Ramadan 1446 AH = 2025-03-01 (±1 day for tabular)
        HijriDate h = new HijriCalendarService().toHijri(LocalDate.of(2025, 3, 1));
        assertTrue(h.getMonth() == 9 && Math.abs(h.getDay() - 1) <= 1,
                "2025-03-01 should be ~1 Ramadan 1446, was " + h.getDay()
                        + " month " + h.getMonth());
    }

    @Test
    void offsetShiftsDate() {
        HijriCalendarService svc = new HijriCalendarService();
        HijriDate base = svc.toHijri(LocalDate.of(2026, 1, 1));
        svc.setOffsetDays(1);
        HijriDate shifted = svc.toHijri(LocalDate.of(2026, 1, 1));
        // With +1 day offset the Hijri day advances by one (or rolls into the
        // next month on the 29th/30th)
        if (shifted.getMonth() == base.getMonth()) {
            assertEquals(base.getDay() + 1, shifted.getDay());
        } else {
            assertEquals(base.getMonth() + 1, shifted.getMonth());
            assertTrue(shifted.getDay() <= 2);
        }
    }

    @Test
    void daysUntilRamadanIsPositiveAndBounded() {
        HijriCalendarService svc = new HijriCalendarService();
        long days = svc.daysUntilRamadan(LocalDate.of(2026, 11, 17));
        assertTrue(days >= 0 && days <= 395,
                "Days until next Ramadan should be 0..354ish, was " + days);
    }

    @Test
    void upcomingEventsAreWithinAYear() {
        HijriCalendarService svc = new HijriCalendarService();
        for (var event : HijriCalendarService.EVENTS) {
            long days = svc.daysUntil(LocalDate.of(2026, 11, 17), event.month(), event.day());
            assertTrue(days >= 0 && days <= 366,
                    event.name() + " should recur within a year, got " + days);
        }
    }

    @Test
    void eventMessagesAppearWithinWindow() {
        HijriCalendarService svc = new HijriCalendarService();
        var messages = svc.upcomingEventMessages(LocalDate.of(2026, 11, 17), 365);
        assertTrue(messages.size() >= 2, "Within a year several events must be listed");
        for (String message : messages) {
            assertTrue(message.contains("in ") || message.contains("today"));
        }
    }
}
