package com.tawkit.service.location;

import com.tawkit.model.prayer.Location;
import com.tawkit.service.city.CityService;
import com.tawkit.service.city.CountryCodes;
import com.tawkit.service.prayer.TimezoneCheck;

import javax.swing.SwingUtilities;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Resolves the real position of the computer, best source first:
 *
 * <ol>
 *   <li><b>Device location service</b> (Windows 10/11 GPS / Wi-Fi positioning
 *       via <code>Windows.Devices.Geolocation</code>): gives the actual
 *       coordinates with a metre-level accuracy, exactly like the Windows
 *       "Location" setting used by Maps or the weather app.</li>
 *   <li><b>Network geolocation</b> ({@link GeoLocationService}, public IP):
 *       city-level approximation, used only when the device position is
 *       unavailable — offline, machine without GPS, or location access
 *       disabled in Windows privacy settings.</li>
 * </ol>
 *
 * <p>The coordinates are then reverse-geocoded to a human name (city,
 * country, ISO code) and the UTC offset is derived from them, so prayer
 * times and the dashboard clock follow the exact spot.
 *
 * <p>Callbacks are delivered on the Swing EDT so callers may update the UI
 * directly; all network/process work happens on a daemon thread.
 */
public final class PreciseLocationService {

    /** Where the coordinates came from. */
    public enum Source {
        /** OS location service (GPS / Wi-Fi positioning) — exact. */
        DEVICE("this computer's location service"),
        /** Public IP lookup — city-level approximation. */
        NETWORK("the network address (approximate)");

        private final String label;

        Source(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /**
     * Result of a lookup.
     *
     * @param location          resolved coordinates + name/country/timezone
     * @param accuracyMeters    device accuracy in metres, {@code NaN} if unknown
     * @param source            device sensor vs network approximation
     * @param permissionBlocked OS refused location access (Windows privacy setting off)
     */
    public record Fix(Location location, double accuracyMeters, Source source,
                      boolean permissionBlocked) {

        /** Human-readable accuracy: "±12 m", "approximate (network)"… */
        public String accuracyText() {
            if (source == Source.NETWORK) {
                return "approximate, network";
            }
            if (Double.isNaN(accuracyMeters)) {
                return "exact position";
            }
            return "±" + Math.round(accuracyMeters) + " m";
        }
    }

    /** Lookup callbacks, both invoked on the EDT. */
    public interface Callback {
        void onLocation(Fix fix);

        void onFailure(String message, boolean permissionBlocked);
    }

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    private static final long POWERSHELL_TIMEOUT_SECONDS = 25;
    private static final String REVERSE_GEOCODE_URL =
            "https://api.bigdatacloud.net/data/reverse-geocode-client";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    private final CityService cities = new CityService();

    /** Looks the location up in the background; never blocks the caller. */
    public void locateAsync(Callback callback) {
        Thread t = new Thread(() -> {
            Fix fix = null;
            boolean blocked = false;
            String failure = null;

            if (isWindows()) {
                DeviceAttempt attempt = deviceFix();
                if (attempt.location() != null) {
                    fix = new Fix(attempt.location(), attempt.accuracyMeters(),
                            Source.DEVICE, false);
                } else {
                    blocked = attempt.accessDenied();
                    failure = attempt.error();
                }
            }

            if (fix == null) {
                try {
                    Location ip = new GeoLocationService().lookup();
                    if (ip != null) {
                        fix = new Fix(ip, Double.NaN, Source.NETWORK, blocked);
                    } else if (failure == null) {
                        failure = "the network lookup returned no location";
                    }
                } catch (Exception e) {
                    if (failure == null) {
                        failure = e.getMessage() == null ? "network lookup failed" : e.getMessage();
                    }
                }
            }

            if (fix != null) {
                enrich(fix.location());
                final Fix result = fix;
                SwingUtilities.invokeLater(() -> callback.onLocation(result));
            } else {
                final String message = failure == null ? "no location source available" : failure;
                final boolean denied = blocked;
                SwingUtilities.invokeLater(() -> callback.onFailure(message, denied));
            }
        }, "tawkit-precise-location");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------- device location

    private record DeviceAttempt(Location location, double accuracyMeters,
                                 boolean accessDenied, String error) {
    }

    /**
     * Windows location service through PowerShell + WinRT. PowerShell 5.1
     * ships with Windows 10/11 and can project the WinRT geolocation API
     * without extra libraries, so the app stays dependency-free.
     */
    private DeviceAttempt deviceFix() {
        Process process = null;
        try {
            String encoded = Base64.getEncoder().encodeToString(
                    WINDOWS_GEOLOCATOR_SCRIPT.getBytes(StandardCharsets.UTF_16LE));
            process = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-WindowStyle", "Hidden",
                    "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded)
                    .redirectErrorStream(true)
                    .start();

            if (!process.waitFor(POWERSHELL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new DeviceAttempt(null, Double.NaN, false,
                        "the location service did not answer in time");
            }
            String output = new String(process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);

            String access = value(output, "ACCESS");
            boolean denied = "Denied".equalsIgnoreCase(access)
                    || "Disabled".equalsIgnoreCase(access)
                    || "NotAllowed".equalsIgnoreCase(access);

            double lat = number(value(output, "LAT"));
            double lon = number(value(output, "LON"));
            if (!Double.isNaN(lat) && !Double.isNaN(lon)) {
                Location loc = new Location("", "", lat, lon, 0);
                return new DeviceAttempt(loc, number(value(output, "ACC")), false, null);
            }

            String error = value(output, "ERR");
            if (error.isBlank() && denied) {
                error = "Windows location access is disabled for desktop apps";
            }
            return new DeviceAttempt(null, Double.NaN, denied,
                    error.isBlank() ? null : error);
        } catch (Exception e) {
            com.tawkit.infrastructure.Log.warn("Windows location lookup failed ("
                    + e.getMessage() + ")");
            return new DeviceAttempt(null, Double.NaN, false, e.getMessage());
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /**
     * WinRT geolocation in Windows PowerShell. Prints key=value lines that
     * {@link #deviceFix()} parses; any failure still prints ACCESS so a
     * denied permission can be reported to the user.
     */
    private static final String WINDOWS_GEOLOCATOR_SCRIPT = """
            $ErrorActionPreference = 'Stop'
            $ProgressPreference = 'SilentlyContinue'
            $access = 'Unknown'
            try {
                Add-Type -AssemblyName System.Runtime.WindowsRuntime
                $asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
                    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
                    $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
                })[0]
                function Await($WinRtTask, $ResultType) {
                    $asTask = $asTaskGeneric.MakeGenericMethod($ResultType)
                    $netTask = $asTask.Invoke($null, @($WinRtTask))
                    $netTask.Wait(-1) | Out-Null
                    $netTask.Result
                }
                $null = [Windows.Devices.Geolocation.Geolocator,Windows.Devices.Geolocation,ContentType=WindowsRuntime]
                $access = Await ([Windows.Devices.Geolocation.Geolocator]::RequestAccessAsync()) ([Windows.Devices.Geolocation.GeolocationAccessStatus])
                Write-Output ('ACCESS=' + $access)
                if ($access -ne 'Allowed') { exit 0 }
                $locator = New-Object Windows.Devices.Geolocation.Geolocator
                $locator.DesiredAccuracyInMeters = 10
                $position = Await ($locator.GetGeopositionAsync()) ([Windows.Devices.Geolocation.Geoposition])
                $culture = [System.Globalization.CultureInfo]::InvariantCulture
                Write-Output ('LAT=' + $position.Coordinate.Point.Position.Latitude.ToString($culture))
                Write-Output ('LON=' + $position.Coordinate.Point.Position.Longitude.ToString($culture))
                Write-Output ('ACC=' + $position.Coordinate.Accuracy.ToString($culture))
            } catch {
                Write-Output ('ACCESS=' + $access)
                Write-Output ('ERR=' + $_.Exception.GetBaseException().Message)
            }
            """;

    // ------------------------------------------------------- enrichment

    /** Fills name/country/ISO code/timezone from coordinates when missing. */
    private void enrich(Location loc) {
        if (loc.getName() == null || loc.getName().isBlank()) {
            Location named = reverseGeocode(loc.getLatitude(), loc.getLongitude());
            if (named != null) {
                loc.setName(named.getName());
                loc.setCountry(named.getCountry());
                loc.setCountryCode(named.getCountryCode());
            } else {
                // Offline but still useful: nearest city from the built-in DB
                CityService.City nearest = cities.nearest(loc.getLatitude(), loc.getLongitude());
                loc.setName(nearest != null ? nearest.name() : "My location");
                loc.setCountry(nearest != null ? nearest.country() : "");
            }
        }
        if (loc.getCountryCode().isBlank() && loc.getCountry() != null) {
            loc.setCountryCode(CountryCodes.fromCountryName(loc.getCountry()));
        }
        // Exact, DST-aware offset for the coordinates (the source may already
        // know it — IP lookups do; a device fix does not).
        if (loc.getTimezone() == 0) {
            loc.setTimezone(resolveTimezone(loc));
        }
    }

    /**
     * UTC offset (hours) for coordinates: timeapi.io first (free, no key),
     * then the OS zone when it is geographically plausible, then the
     * longitude-based heuristic. Keeps prayer times correct across DST.
     */
    private double resolveTimezone(Location loc) {
        try {
            String url = "https://timeapi.io/api/TimeZone/coordinate?latitude="
                    + URLEncoder.encode(String.valueOf(loc.getLatitude()), StandardCharsets.UTF_8)
                    + "&longitude=" + URLEncoder.encode(String.valueOf(loc.getLongitude()),
                            StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(HTTP_TIMEOUT)
                    .header("User-Agent", "TawkitDesktop/1.0")
                    .GET()
                    .build();
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 200) {
                int marker = response.body().indexOf("\"currentUtcOffset\"");
                if (marker >= 0) {
                    double seconds = numberField(response.body(), "seconds", marker);
                    if (!Double.isNaN(seconds)) {
                        return seconds / 3600.0;
                    }
                }
            }
        } catch (Exception e) {
            com.tawkit.infrastructure.Log.warn("Timezone lookup failed ("
                    + e.getMessage() + ")");
        }

        // Same-region fallback: the computer's own zone (DST included)
        double longitudeGuess = loc.getLongitude() / 15.0;
        double systemOffset = TimezoneCheck.systemOffset(java.time.Instant.now());
        if (Math.abs(systemOffset - longitudeGuess) <= 3.0) {
            return systemOffset;
        }
        return TimezoneCheck.resolve(loc, null);
    }

    /** Reads a numeric JSON field starting at/after {@code from}. */
    private static double numberField(String json, String key, int from) {
        int i = json.indexOf("\"" + key + "\"", from);
        if (i < 0) return Double.NaN;
        i = json.indexOf(':', i) + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        int start = i;
        while (i < json.length() && "-0123456789.".indexOf(json.charAt(i)) >= 0) {
            i++;
        }
        try {
            return Double.parseDouble(json.substring(start, i));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** Coordinates → city/country/ISO code (BigDataCloud, free, no key). */
    private Location reverseGeocode(double lat, double lon) {
        try {
            String url = REVERSE_GEOCODE_URL
                    + "?latitude=" + URLEncoder.encode(String.valueOf(lat), StandardCharsets.UTF_8)
                    + "&longitude=" + URLEncoder.encode(String.valueOf(lon), StandardCharsets.UTF_8)
                    + "&localityLanguage=en";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(HTTP_TIMEOUT)
                    .header("User-Agent", "TawkitDesktop/1.0")
                    .GET()
                    .build();
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) return null;
            String json = response.body();

            String name = extractString(json, "city");
            if (name.isBlank()) name = extractString(json, "locality");
            if (name.isBlank()) name = extractString(json, "principalSubdivision");
            if (name.isBlank()) return null;

            Location loc = new Location(name, extractString(json, "countryName"), lat, lon, 0);
            loc.setCountryCode(extractString(json, "countryCode"));
            return loc;
        } catch (Exception e) {
            com.tawkit.infrastructure.Log.warn("Reverse geocoding failed ("
                    + e.getMessage() + ")");
            return null;
        }
    }

    // ------------------------------------------------------------ helpers

    /** Reads "KEY=value" from the PowerShell output. */
    private static String value(String output, String key) {
        for (String line : output.split("\\R")) {
            int eq = line.indexOf('=');
            if (eq > 0 && line.substring(0, eq).trim().equalsIgnoreCase(key)) {
                return line.substring(eq + 1).trim();
            }
        }
        return "";
    }

    private static double number(String value) {
        try {
            return value.isBlank() ? Double.NaN : Double.parseDouble(value.replace(',', '.'));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** Minimal targeted JSON string extraction (reverse-geocode response). */
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
            if (c == '\\' && end + 1 < json.length()) {
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
