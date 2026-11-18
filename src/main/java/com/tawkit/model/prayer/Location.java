package com.tawkit.model.prayer;

/**
 * Geographic location used for prayer-time calculation.
 */
public class Location {

    private String name;
    private String country;
    private String countryCode = "";  // ISO 3166-1 alpha-2 for the flag widget
    private double latitude;
    private double longitude;
    private double timezone;  // offset from UTC in hours

    public Location() {
    }

    public Location(String name, String country, double latitude, double longitude, double timezone) {
        this.name = name;
        this.country = country;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timezone = timezone;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getTimezone() {
        return timezone;
    }

    public void setTimezone(double timezone) {
        this.timezone = timezone;
    }

    public String getCountryCode() {
        return countryCode == null ? "" : countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode == null ? "" : countryCode.trim().toUpperCase();
    }

    public String getDisplayName() {
        String place = (name == null || name.isBlank()) ? "My location" : name;
        if (country != null && !country.isBlank()) {
            return place + ", " + country;
        }
        return place;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
