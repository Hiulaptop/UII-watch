package com.example.watchtest.uii;

/** One measured position of the wearer. */
public final class LocationFix {
    public final double lat;
    public final double lng;
    /** Radius in metres within which the position is expected to lie, or null when the provider did not say. */
    public final Double accuracyM;
    /** When the position was measured (wall clock, epoch ms). */
    public final long timeMs;

    public LocationFix(double lat, double lng, Double accuracyM, long timeMs) {
        this.lat = lat;
        this.lng = lng;
        this.accuracyM = accuracyM;
        this.timeMs = timeMs;
    }

    /** False for a fix the server would refuse: out of range, not a number, or without a time. */
    public boolean isValid() {
        return !Double.isNaN(lat) && !Double.isNaN(lng) && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180 && timeMs > 0;
    }
}
