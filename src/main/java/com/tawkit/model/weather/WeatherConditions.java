package com.tawkit.model.weather;

/**
 * Real weather conditions mapped from Open-Meteo's WMO weather codes.
 * The widget draws an icon from this enum — a sunny icon is only ever
 * shown for genuinely clear skies.
 */
public enum WeatherConditions {

    SUN, MOON, PARTLY, CLOUD, FOG, RAIN, SNOW, THUNDER;

    /**
     * Maps a WMO code (Open-Meteo) to the icon kind. {@code isDay} decides
     * sun vs moon for clear/mostly-clear skies.
     */
    public static WeatherConditions from(int wmoCode, boolean isDay) {
        return switch (wmoCode) {
            case 0 -> isDay ? SUN : MOON;              // clear sky
            case 1 -> isDay ? PARTLY : MOON;           // mainly clear
            case 2 -> PARTLY;                          // partly cloudy
            case 3 -> CLOUD;                           // overcast
            case 45, 48 -> FOG;                        // fog / depositing rime fog
            case 51, 53, 55, 56, 57 -> RAIN;           // drizzle
            case 61, 63, 65, 66, 67 -> RAIN;           // rain / freezing rain
            case 71, 73, 75, 77, 85, 86 -> SNOW;       // snow / snow showers
            case 80, 81, 82 -> RAIN;                   // rain showers
            case 95, 96, 99 -> THUNDER;                // thunderstorm
            default -> CLOUD;
        };
    }

    /** Human-readable condition text (used by the menu tooltip). */
    public static String describe(int wmoCode) {
        return switch (wmoCode) {
            case 0 -> "Clear sky";
            case 1, 2 -> "Partly cloudy";
            case 3 -> "Overcast";
            case 45, 48 -> "Fog";
            case 51, 53, 55, 56, 57 -> "Drizzle";
            case 61, 63, 65, 66, 67 -> "Rain";
            case 71, 73, 75, 77, 85, 86 -> "Snow";
            case 80, 81, 82 -> "Rain showers";
            case 95, 96, 99 -> "Thunderstorm";
            default -> "Cloudy";
        };
    }
}
