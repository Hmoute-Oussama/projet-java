package com.tawkit.model.weather;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The widget must never show a sunny icon when the real condition is
 * rain/snow/thunder — task §2.
 */
class WeatherConditionsTest {

    @Test
    void clearSkyIsSunByDayAndMoonAtNight() {
        assertEquals(WeatherConditions.SUN, WeatherConditions.from(0, true));
        assertEquals(WeatherConditions.MOON, WeatherConditions.from(0, false));
        assertEquals(WeatherConditions.PARTLY, WeatherConditions.from(1, true));
        assertEquals(WeatherConditions.MOON, WeatherConditions.from(1, false));
    }

    @Test
    void rainNeverMapsToSun() {
        int[] rainCodes = {51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82};
        for (int code : rainCodes) {
            assertEquals(WeatherConditions.RAIN, WeatherConditions.from(code, true),
                    "WMO " + code + " must be RAIN");
        }
    }

    @Test
    void snowThunderFogCloudMapCorrectly() {
        for (int code : new int[]{71, 73, 75, 77, 85, 86}) {
            assertEquals(WeatherConditions.SNOW, WeatherConditions.from(code, true));
        }
        for (int code : new int[]{95, 96, 99}) {
            assertEquals(WeatherConditions.THUNDER, WeatherConditions.from(code, true));
        }
        for (int code : new int[]{45, 48}) {
            assertEquals(WeatherConditions.FOG, WeatherConditions.from(code, true));
        }
        assertEquals(WeatherConditions.CLOUD, WeatherConditions.from(3, true));
        assertEquals(WeatherConditions.PARTLY, WeatherConditions.from(2, true));
    }

    @Test
    void unknownCodeFallsBackToCloud() {
        assertEquals(WeatherConditions.CLOUD, WeatherConditions.from(42, true));
    }
}
