package com.tawkit.service.location;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** City search parsing (task §7) — real geocoding response shape. */
class GeocodingServiceTest {

    private static final String SAMPLE_JSON = """
            {"results":[
              {"id":123,"name":"Rabat","latitude":34.0181,"longitude":-6.8362,
               "elevation":75,"feature_code":"PPLC","country_code":"MA",
               "timezone":"Africa/Casablanca","population":577827,
               "country":"Morocco","admin1":"Rabat-Sal-Kenitra"},
              {"id":456,"name":"Rabat","latitude":42.8497,"longitude":-71.9781,
               "country_code":"US","timezone":"America/New_York",
               "country":"United States","admin1":"Massachusetts"}
            ],"generationtime_ms":1.2}
            """;

    @Test
    void parsesAllResultsWithCountryCodes() {
        List<GeocodingService.GeoPlace> places = GeocodingService.parse(SAMPLE_JSON);
        assertEquals(2, places.size());

        GeocodingService.GeoPlace first = places.get(0);
        assertEquals("Rabat", first.name());
        assertEquals("MA", first.countryCode());
        assertEquals("Morocco", first.country());
        assertEquals("Africa/Casablanca", first.timezone());
        assertEquals(34.0181, first.latitude(), 0.0001);
        assertEquals(-6.8362, first.longitude(), 0.0001);
    }

    @Test
    void displayLabelIncludesCountry() {
        GeocodingService.GeoPlace place = GeocodingService.parse(SAMPLE_JSON).get(0);
        assertTrue(place.displayLabel().contains("Rabat"));
        assertTrue(place.displayLabel().contains("Morocco"));
    }

    @Test
    void emptyOrMissingResultsYieldEmptyList() {
        assertTrue(GeocodingService.parse("{\"generationtime_ms\":0.5}").isEmpty());
        assertTrue(GeocodingService.parse("").isEmpty());
    }

    @Test
    void escapedCityNamesParse() {
        String json = "{\"results\":[{\"name\":\"Xi'an\",\"country_code\":\"CN\","
                + "\"country\":\"China\",\"latitude\":34.2583,\"longitude\":108.9286}]}";
        List<GeocodingService.GeoPlace> places = GeocodingService.parse(json);
        assertEquals(1, places.size());
        assertEquals("Xi'an", places.get(0).name());
    }
}
