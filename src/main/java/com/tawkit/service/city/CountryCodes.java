package com.tawkit.service.city;

import java.util.Locale;
import java.util.Map;

/**
 * Resolves ISO 3166-1 alpha-2 country codes for the flag widget.
 * Covers the built-in city database plus common extras; falls back to
 * a locale-data lookup, then to an empty code (neutral flag badge).
 */
public final class CountryCodes {

    private static final Map<String, String> BY_NAME = Map.ofEntries(
            Map.entry("afghanistan", "AF"), Map.entry("algeria", "DZ"),
            Map.entry("australia", "AU"), Map.entry("austria", "AT"),
            Map.entry("azerbaijan", "AZ"), Map.entry("bahrain", "BH"),
            Map.entry("bangladesh", "BD"), Map.entry("belgium", "BE"),
            Map.entry("brazil", "BR"), Map.entry("brunei", "BN"),
            Map.entry("cameroon", "CM"), Map.entry("canada", "CA"),
            Map.entry("chad", "TD"), Map.entry("china", "CN"),
            Map.entry("denmark", "DK"), Map.entry("egypt", "EG"),
            Map.entry("ethiopia", "ET"), Map.entry("france", "FR"),
            Map.entry("germany", "DE"), Map.entry("ghana", "GH"),
            Map.entry("guinea", "GN"), Map.entry("india", "IN"),
            Map.entry("indonesia", "ID"), Map.entry("iran", "IR"),
            Map.entry("iraq", "IQ"), Map.entry("italy", "IT"),
            Map.entry("ivory coast", "CI"), Map.entry("cote d'ivoire", "CI"),
            Map.entry("japan", "JP"), Map.entry("jordan", "JO"),
            Map.entry("kazakhstan", "KZ"), Map.entry("kenya", "KE"),
            Map.entry("kuwait", "KW"), Map.entry("kyrgyzstan", "KG"),
            Map.entry("lebanon", "LB"), Map.entry("libya", "LY"),
            Map.entry("malaysia", "MY"), Map.entry("maldives", "MV"),
            Map.entry("mali", "ML"), Map.entry("mauritania", "MR"),
            Map.entry("mexico", "MX"), Map.entry("morocco", "MA"),
            Map.entry("netherlands", "NL"), Map.entry("niger", "NE"),
            Map.entry("nigeria", "NG"), Map.entry("norway", "NO"),
            Map.entry("oman", "OM"), Map.entry("pakistan", "PK"),
            Map.entry("palestine", "PS"), Map.entry("poland", "PL"),
            Map.entry("qatar", "QA"), Map.entry("russia", "RU"),
            Map.entry("saudi arabia", "SA"), Map.entry("senegal", "SN"),
            Map.entry("singapore", "SG"), Map.entry("somalia", "SO"),
            Map.entry("spain", "ES"), Map.entry("sri lanka", "LK"),
            Map.entry("sudan", "SD"), Map.entry("sweden", "SE"),
            Map.entry("switzerland", "CH"), Map.entry("syria", "SY"),
            Map.entry("tanzania", "TZ"), Map.entry("thailand", "TH"),
            Map.entry("tunisia", "TN"), Map.entry("turkey", "TR"),
            Map.entry("uganda", "UG"), Map.entry("united arab emirates", "AE"),
            Map.entry("united kingdom", "GB"), Map.entry("united states", "US"),
            Map.entry("usa", "US"), Map.entry("uzbekistan", "UZ"),
            Map.entry("yemen", "YE"),
            // extras (geocoding search may return these)
            Map.entry("portugal", "PT"), Map.entry("ireland", "IE"),
            Map.entry("greece", "GR"), Map.entry("finland", "FI"),
            Map.entry("israel", "IL"), Map.entry("south africa", "ZA"),
            Map.entry("argentina", "AR"), Map.entry("chile", "CL"),
            Map.entry("colombia", "CO"), Map.entry("philippines", "PH"),
            Map.entry("vietnam", "VN"), Map.entry("south korea", "KR"),
            Map.entry("korea", "KR"), Map.entry("mongolia", "MN"),
            Map.entry("nepal", "NP"), Map.entry("cambodia", "KH"),
            Map.entry("myanmar", "MM")
    );

    private CountryCodes() {
    }

    /**
     * @param country country name as shown in the city database ("Morocco")
     *                or in geocoding results ("Kingdom of Morocco")
     * @return two-letter ISO code (upper case) or "" when unknown
     */
    public static String fromCountryName(String country) {
        if (country == null || country.isBlank()) return "";
        String key = country.trim().toLowerCase();
        String direct = BY_NAME.get(key);
        if (direct != null) return direct;
        // Handle prefixes like "Kingdom of Morocco", "Republic of Turkey"
        for (String prefix : new String[]{"kingdom of ", "republic of ", "the ", "state of "}) {
            if (key.startsWith(prefix)) {
                String stripped = key.substring(prefix.length());
                String hit = BY_NAME.get(stripped);
                if (hit != null) return hit;
            }
        }
        // Locale-based fallback for anything not in the table
        for (String iso : Locale.getISOCountries()) {
            Locale l = new Locale("", iso);
            if (l.getDisplayCountry(Locale.ENGLISH).toLowerCase().equals(key)) {
                return iso;
            }
        }
        return "";
    }
}
