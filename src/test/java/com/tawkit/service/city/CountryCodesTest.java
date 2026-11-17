package com.tawkit.service.city;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The flag must match the location's country — task §5. */
class CountryCodesTest {

    @Test
    void cityDatabaseCountriesResolve() {
        assertEquals("MA", CountryCodes.fromCountryName("Morocco"));
        assertEquals("SA", CountryCodes.fromCountryName("Saudi Arabia"));
        assertEquals("TR", CountryCodes.fromCountryName("Turkey"));
        assertEquals("FR", CountryCodes.fromCountryName("France"));
        assertEquals("US", CountryCodes.fromCountryName("United States"));
        assertEquals("GB", CountryCodes.fromCountryName("United Kingdom"));
        assertEquals("AE", CountryCodes.fromCountryName("United Arab Emirates"));
        assertEquals("CI", CountryCodes.fromCountryName("Ivory Coast"));
    }

    @Test
    void geocodingStyleNamesResolve() {
        assertEquals("MA", CountryCodes.fromCountryName("Kingdom of Morocco"));
        assertEquals("US", CountryCodes.fromCountryName("USA"));
    }

    @Test
    void localeFallbackForUnlistedCountries() {
        // Not in the table but resolvable via JDK locale data
        assertEquals("PT", CountryCodes.fromCountryName("Portugal"));
    }

    @Test
    void blankOrUnknownIsEmptyCode() {
        assertEquals("", CountryCodes.fromCountryName(""));
        assertEquals("", CountryCodes.fromCountryName(null));
        assertEquals("", CountryCodes.fromCountryName("Atlantis"));
    }
}
