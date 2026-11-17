package com.tawkit.service.location;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * City search via the Open-Meteo geocoding API (free, no API key) —
 * replaces hardcoded city lists for the search flow. Returns real
 * coordinates, country and ISO code for every result, so the widget's
 * flag and coordinates always match the selected place.
 */
public class GeocodingService {

    private static final String ENDPOINT = "https://geocoding-api.open-meteo.com/v1/search";

    /** One search result. */
    public record GeoPlace(String name, String admin1, String country,
                           String countryCode, double latitude, double longitude,
                           String timezone) {
        /** "Rabat — Rabat-Sal-Kénitra, Morocco" */
        public String displayLabel() {
            StringBuilder sb = new StringBuilder(name);
            if (admin1 != null && !admin1.isBlank() && !admin1.equals(name)) {
                sb.append(" — ").append(admin1);
            }
            if (country != null && !country.isBlank()) {
                sb.append(", ").append(country);
            }
            return sb.toString();
        }
    }

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    /** Blocking search; empty list on failure or no results. */
    public List<GeoPlace> search(String query) throws IOException, InterruptedException {
        if (query == null || query.isBlank()) return List.of();
        String url = ENDPOINT + "?name=" + URLEncoder.encode(query.trim(), StandardCharsets.UTF_8)
                + "&count=8&language=en&format=json";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "TawkitDesktop/1.0")
                .GET()
                .build();
        HttpResponse<String> response =
                http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) return List.of();
        return parse(response.body());
    }

    /** Asynchronous search; results delivered on the calling consumer. */
    public void searchAsync(String query, java.util.function.Consumer<List<GeoPlace>> onResults,
                            java.util.function.Consumer<String> onFailure) {
        Thread t = new Thread(() -> {
            try {
                onResults.accept(search(query));
            } catch (Exception e) {
                onFailure.accept(e.getMessage() == null ? "search failed" : e.getMessage());
            }
        }, "tawkit-geocode");
        t.setDaemon(true);
        t.start();
    }

    /** Minimal targeted parsing of the geocoding "results" array. */
    static List<GeoPlace> parse(String json) {
        List<GeoPlace> places = new ArrayList<>();
        String array = extractArray(json, "results");
        if (array == null) return places;
        for (String obj : objectsIn(array)) {
            String name = extractString(obj, "name");
            if (name.isBlank()) continue;
            double lat = extractNumber(obj, "latitude");
            double lon = extractNumber(obj, "longitude");
            places.add(new GeoPlace(
                    name,
                    extractString(obj, "admin1"),
                    extractString(obj, "country"),
                    extractString(obj, "country_code").toUpperCase(),
                    lat, lon,
                    extractString(obj, "timezone")));
        }
        return places;
    }

    // ------------------------------------------------------------ JSON utils

    private static String extractArray(String json, String key) {
        int i = json.indexOf("\"" + key + "\"");
        if (i < 0) return null;
        int start = json.indexOf('[', i);
        if (start < 0) return null;
        int depth = 0;
        for (int j = start; j < json.length(); j++) {
            char c = json.charAt(j);
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) return json.substring(start + 1, j);
            }
        }
        return null;
    }

    /** Splits an array body into top-level {...} object strings. */
    private static List<String> objectsIn(String arrayBody) {
        List<String> out = new ArrayList<>();
        int depth = 0, start = -1;
        for (int i = 0; i < arrayBody.length(); i++) {
            char c = arrayBody.charAt(i);
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    out.add(arrayBody.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return out;
    }

    private static double extractNumber(String json, String key) {
        int i = json.indexOf("\"" + key + "\"");
        if (i < 0) return 0;
        i = json.indexOf(':', i) + 1;
        StringBuilder sb = new StringBuilder();
        while (i < json.length() && (Character.isDigit(json.charAt(i))
                || json.charAt(i) == '-' || json.charAt(i) == '.')) {
            sb.append(json.charAt(i++));
        }
        try {
            return Double.parseDouble(sb.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String extractString(String json, String key) {
        int i = json.indexOf("\"" + key + "\"");
        if (i < 0) return "";
        int colon = json.indexOf(':', i);
        if (colon < 0) return "";
        int open = json.indexOf('"', colon);
        if (open < 0) return "";
        int end = open + 1;
        StringBuilder sb = new StringBuilder();
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == '\\' && end + 1 < json.length()) { // escaped char
                sb.append(json.charAt(end + 1));
                end += 2;
                continue;
            }
            if (c == '"') break;
            sb.append(c);
            end++;
        }
        return sb.toString();
    }
}
