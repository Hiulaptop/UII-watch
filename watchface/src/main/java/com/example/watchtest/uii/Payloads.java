package com.example.watchtest.uii;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.UUID;

/**
 * Builds request bodies that match the server contract (see the web app's lib/device/schemas.ts).
 * Values outside the server's accepted ranges are left out here, so one bad reading cannot get a whole batch rejected.
 */
public final class Payloads {
    public static final int MAX_SAMPLES_PER_REQUEST = 720;
    public static final int MAX_SNAPSHOT_POINTS = 12_000;

    public static final String FALL = "fall";
    public static final String SEIZURE = "seizure";
    public static final String PRE_SEIZURE = "pre_seizure";
    public static final String TRIGGER_DEVICE = "device_event";
    public static final String TRIGGER_MANUAL = "manual";

    private Payloads() {}

    /** Matches the server rule ^[A-Za-z0-9_-]{8,64}$. One id per event, reused on every retry. */
    public static String newClientId() {
        return "w-" + UUID.randomUUID().toString().replace("-", "");
    }

    private static boolean in(Double v, double min, double max) {
        return v != null && !v.isNaN() && !v.isInfinite() && v >= min && v <= max;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** Returns null when no window holds a usable measurement. */
    public static String samples(List<SampleWindow> windows, Integer batteryPct) {
        try {
            JSONArray arr = new JSONArray();
            for (SampleWindow w : windows) {
                if (arr.length() >= MAX_SAMPLES_PER_REQUEST) break;
                if (w.tMs <= 0) continue;
                JSONObject o = new JSONObject();
                int measurements = 0;
                if (in(w.hr, 20, 250)) { o.put("hr", round2(w.hr)); measurements++; }
                if (in(w.accelMean, 0, 400)) { o.put("accel_mean", round2(w.accelMean)); measurements++; }
                if (in(w.accelMax, 0, 400)) { o.put("accel_max", round2(w.accelMax)); measurements++; }
                if (in(w.accelVar, 0, 100_000)) { o.put("accel_var", round2(w.accelVar)); measurements++; }
                if (w.steps != null && w.steps >= 0 && w.steps <= 100_000) { o.put("steps", w.steps.intValue()); measurements++; }
                if (measurements == 0) continue;
                o.put("t", w.tMs);
                arr.put(o);
            }
            if (arr.length() == 0) return null;
            JSONObject body = new JSONObject();
            body.put("samples", arr);
            if (batteryPct != null && batteryPct >= 0 && batteryPct <= 100) body.put("battery_pct", batteryPct.intValue());
            return body.toString();
        } catch (JSONException e) {
            return null;
        }
    }

    /** {@code snapshot} may be null; the alert is worth sending without it. */
    public static String alert(String clientId, String type, String trigger, long detectedAtMs,
                               Double heartRate, Double accelPeak, Double confidence, Snapshot snapshot) {
        try {
            JSONObject o = new JSONObject();
            o.put("client_id", clientId);
            o.put("type", type);
            o.put("trigger", trigger);
            o.put("detected_at", detectedAtMs);
            if (in(confidence, 0, 1)) o.put("confidence", round2(confidence));
            JSONObject metrics = new JSONObject();
            if (in(heartRate, 20, 250)) metrics.put("heart_rate", round2(heartRate));
            if (in(accelPeak, 0, 400)) metrics.put("accel_peak", round2(accelPeak));
            if (metrics.length() > 0) o.put("metrics", metrics);
            if (snapshot != null && snapshot.size() > 0 && snapshot.size() <= MAX_SNAPSHOT_POINTS
                    && snapshot.sampleRateHz >= 1 && snapshot.sampleRateHz <= 200) {
                JSONObject s = new JSONObject();
                s.put("sample_rate_hz", round2(snapshot.sampleRateHz));
                s.put("started_at", snapshot.startedAtMs);
                JSONArray axes = new JSONArray();
                for (int i = 0; i < snapshot.size(); i++) {
                    JSONArray p = new JSONArray();
                    p.put(round2(snapshot.x[i])).put(round2(snapshot.y[i])).put(round2(snapshot.z[i]));
                    axes.put(p);
                }
                s.put("axes", axes);
                o.put("snapshot", s);
            }
            return o.toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A burst of raw motion data (m/s^2) taken around an alert. */
    public static final class Snapshot {
        public final long startedAtMs;
        public final double sampleRateHz;
        public final float[] x;
        public final float[] y;
        public final float[] z;

        public Snapshot(long startedAtMs, double sampleRateHz, float[] x, float[] y, float[] z) {
            this.startedAtMs = startedAtMs;
            this.sampleRateHz = sampleRateHz;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public int size() { return x.length; }
    }
}
