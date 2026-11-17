package com.tawkit.service.prayer;

import com.tawkit.model.prayer.Location;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Timezone sanity utilities (change #2). A manually-typed UTC offset that
 * disagrees with the longitude by hours produces prayer times that are off
 * by exactly that error — the classic "times are wrong by 1h" bug.
 *
 * <p>Strategy:
 * <ol>
 *   <li>Guess the offset from longitude (≈ lon/15).</li>
 *   <li>Prefer a real system zone whose standard offset matches the guess —
 *       this also handles DST automatically.</li>
 *   <li>Otherwise round the longitude guess to whole/half/quarter hours.</li>
 * </ol>
 */
public final class TimezoneCheck {

    private TimezoneCheck() {
    }

    /**
     * Best-effort UTC offset (hours) for a location. Honours DST on the given
     * date when a matching system zone is found.
     */
    public static double resolve(Location location, LocalDate date) {
        if (location == null) {
            return systemOffset(Instant.now());
        }
        double lon = location.getLongitude();
        double guess = Math.round(lon / 15.0 * 4.0) / 4.0; // quarter-hour steps

        ZoneId zone = zoneMatching(lon, location.getLatitude());
        if (zone != null) {
            return ZoneOffset.from(zone.getRules().getOffset(
                    date == null ? Instant.now()
                            : date.atStartOfDay(zone).toInstant())).getTotalSeconds() / 3600.0;
        }
        // A stored timezone that is "close" to the longitude guess is kept as-is
        double stored = location.getTimezone();
        if (stored != 0.0 && Math.abs(stored - lon / 15.0) <= 1.25) {
            return stored;
        }
        return guess;
    }

    /** Current system default offset in hours. */
    public static double systemOffset(Instant instant) {
        return ZoneOffset.from(ZoneId.systemDefault().getRules().getOffset(instant))
                .getTotalSeconds() / 3600.0;
    }

    /**
     * Finds a JVM-known zone whose <em>standard</em> (non-DST) offset matches
     * the longitude within tolerance and that is geographically plausible.
     * Returns {@code null} when nothing matches convincingly.
     */
    private static ZoneId zoneMatching(double longitude, double latitude) {
        double expected = longitude / 15.0;
        ZoneId best = null;
        double bestScore = Double.MAX_VALUE;
        for (String id : ZoneId.getAvailableZoneIds()) {
            if (id.startsWith("Etc/") || id.equals("GMT")) continue;
            ZoneId zone = ZoneId.of(id);
            ZoneOffset std = zone.getRules().getTransitions().isEmpty()
                    ? ZoneOffset.from(zone.getRules().getOffset(Instant.now()))
                    : zone.getRules().getTransitions().get(0).getOffsetBefore();
            double diff = std.getTotalSeconds() / 3600.0 - expected;
            if (Math.abs(diff) > 1.0) continue; // offset must be plausible
            // Geographic plausibility: zone centroid distance, cheap heuristic
            double score = Math.abs(diff) + centroidDistance(id, latitude, longitude) / 4000.0;
            if (score < bestScore) {
                bestScore = score;
                best = zone;
            }
        }
        return bestScore <= 3.0 ? best : null;
    }

    /** Rough centroid coordinates for common zone regions (degrees). */
    private static double centroidDistance(String zoneId, double lat, double lon) {
        double[] c = centroid(zoneId);
        if (c == null) return 2000; // unknown → neutral-ish penalty
        double dLat = c[0] - lat, dLon = c[1] - lon;
        // Longitude distance wrapped to [-180, 180]
        if (dLon > 180) dLon -= 360;
        if (dLon < -180) dLon += 360;
        return Math.sqrt(dLat * dLat + dLon * dLon);
    }

    private static double[] centroid(String zoneId) {
        String region = zoneId.contains("/") ? zoneId.substring(zoneId.indexOf('/') + 1) : zoneId;
        return switch (region) {
            case "Morocco", "Casablanca", "El_Aaiun" -> new double[]{32, -6};
            case "Algiers", "Tunis" -> new double[]{35, 3};
            case "Cairo", "Tripoli" -> new double[]{28, 20};
            case "Riyadh", "Qatar", "Kuwait", "Bahrain", "Aden", "Muscat" -> new double[]{23, 46};
            case "Dubai", "Abu_Dhabi" -> new double[]{24.2, 54.5};
            case "Istanbul", "Athens", "Bucharest", "Sofia", "Belgrade", "Baghdad" -> new double[]{38, 38};
            case "Paris", "Madrid", "Rome", "Berlin", "Amsterdam", "Brussels", "Vienna",
                 "Zurich", "Prague", "Warsaw", "Budapest", "Stockholm", "Oslo",
                 "Copenhagen", "Monaco", "Luxembourg" -> new double[]{48, 8};
            case "London", "Dublin", "Lisbon" -> new double[]{52, -3};
            case "New_York", "Toronto", "Montreal", "Detroit", "Indiana" -> new double[]{42, -78};
            case "Chicago", "Winnipeg", "Houston", "Mexico_City" -> new double[]{32, -95};
            case "Denver", "Phoenix", "Edmonton", "Boise", "Calgary" -> new double[]{40, -110};
            case "Los_Angeles", "Vancouver", "Tijuana", "Las_Vegas" -> new double[]{36, -118};
            case "Sao_Paulo", "Rio_Branco", "Montevideo", "Buenos_Aires", "Santiago" -> new double[]{-25, -58};
            case "Dakar", "Abidjan", "Accra", "Lagos" -> new double[]{9, -2};
            case "Nairobi", "Addis_Ababa", "Kampala", "Dar_es_Salaam" -> new double[]{-2, 36};
            case "Johannesburg", "Cape_Town", "Harare", "Maputo" -> new double[]{-26, 27};
            case "Karachi", "Lahore", "Islamabad" -> new double[]{30, 70};
            case "Kolkata", "Calcutta", "Dhaka", "Colombo" -> new double[]{21, 80};
            case "Jakarta", "Kuala_Lumpur", "Singapore", "Bangkok", "Ho_Chi_Minh" -> new double[]{6, 105};
            case "Manila", "Shanghai", "Hong_Kong", "Taipei", "Macau" -> new double[]{25, 118};
            case "Tokyo", "Seoul", "Pyongyang" -> new double[]{37, 133};
            case "Kabul", "Tashkent", "Dushanbe", "Baku", "Tbilisi", "Yerevan" -> new double[]{38, 62};
            case "Tehran" -> new double[]{33, 54};
            case "Moscow", "Kiev", "Minsk", "Riga", "Vilnius", "Tallinn", "Helsinki" -> new double[]{55, 35};
            case "Sydney", "Melbourne", "Canberra", "Hobart" -> new double[]{-35, 148};
            case "Perth" -> new double[]{-31, 121};
            case "Auckland", "Fiji" -> new double[]{-38, 176};
            case "Tokelau", "Apia", "Tongatapu" -> new double[]{-15, -172};
            case "Honolulu", "Tahiti" -> new double[]{20, -157};
            case "Anchorage", "Juneau" -> new double[]{60, -149};
            case "Halifax", "St_Johns" -> new double[]{46, -62};
            default -> null;
        };
    }
}
