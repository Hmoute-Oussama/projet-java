package com.tawkit.service.location;

import com.tawkit.model.prayer.Location;
import com.tawkit.service.prayer.TimezoneCheck;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * First-launch convenience (change #7): guesses the screen's location from
 * its public IP address using ipwho.is (free, HTTPS, no API key), so a new
 * install shows usable prayer times before anyone touches the city picker.
 *
 * <p>Best-effort only: any failure simply leaves the location unset and the
 * city-picker flow remains available in Settings.
 */
public class GeoLocationService {

    private static final String ENDPOINT = "https://ipwho.is/";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    /** Looks up the location asynchronously and delivers it to the consumer. */
    public void lookupAsync(Consumer<Location> onSuccess, Consumer<String> onFailure) {
        Thread t = new Thread(() -> {
            try {
                Location loc = lookup();
                if (loc != null) onSuccess.accept(loc);
                else onFailure.accept("IP location unavailable");
            } catch (Exception e) {
                onFailure.accept(e.getMessage() == null ? "IP lookup failed" : e.getMessage());
            }
        }, "tawkit-geolocate");
        t.setDaemon(true);
        t.start();
    }

    /** Blocking lookup; {@code null} when it fails (offline, blocked, etc.). */
    public Location lookup() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "TawkitDesktop/1.0")
                .GET()
                .build();
        HttpResponse<String> response =
                http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) return null;
        return parse(response.body());
    }

    /** Minimal targeted parsing of the ipwho.is response. */
    static Location parse(String json) {
        if (json.contains("\"success\":false")) return null;
        double lat = extractNumber(json, "latitude");
        double lon = extractNumber(json, "longitude");
        if (lat == 0 && lon == 0) return null;
        String city = extractString(json, "city");
        String country = extractString(json, "country");
        if (city.isBlank()) return null;
        double tz = timezoneOffset(json);
        if (tz == 0) {
            tz = TimezoneCheck.resolve(new Location(city, country, lat, lon, 0.0), null);
        }
        Location loc = new Location(city, country, lat, lon, tz);
        loc.setCountryCode(extractString(json, "country_code")); // real flag, not a guess
        return loc;
    }

    /**
     * ipwho.is returns {@code "timezone":{"id":"Africa/Casablanca",
     * "offset":3600,…}} — the current offset in seconds. This is exact and
     * DST-aware, unlike the old longitude heuristic.
     */
    private static double timezoneOffset(String json) {
        int tz = json.indexOf("\"timezone\"");
        if (tz < 0) return 0;
        int offset = json.indexOf("\"offset\"", tz);
        if (offset >= 0) {
            return extractNumber(json.substring(offset), "offset") / 3600.0;
        }
        String id = extractString(json, "id");
        if (!id.isBlank()) {
            try {
                return java.time.ZoneOffset.from(
                        java.time.ZoneId.of(id).getRules()
                                .getOffset(java.time.Instant.now()))
                        .getTotalSeconds() / 3600.0;
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    // ------------------------------------------------------------ JSON utils

    private static double extractNumber(String json, String key) {
        int i = json.indexOf("\"" + key + "\"");
        if (i < 0) return 0;
        i = json.indexOf(':', i) + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++; // ipwho.is pretty-prints with a space after ':'
        }
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
        i = json.indexOf('"', json.indexOf(':', i)) ;
        if (i < 0) return "";
        int end = json.indexOf('"', i + 1);
        return end < 0 ? "" : json.substring(i + 1, end);
    }
}
