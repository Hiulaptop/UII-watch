package com.example.watchtest.uii;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class PayloadsTest {
    private static SampleWindow w(long t, Double hr, Double mean, Double max, Double var, Integer steps) {
        return new SampleWindow(t, hr, mean, max, var, steps);
    }

    @Test public void clientIdsMatchTheServerRuleAndAreUnique() {
        String a = Payloads.newClientId();
        assertTrue(a, a.matches("^[A-Za-z0-9_-]{8,64}$"));
        assertNotEquals(a, Payloads.newClientId());
    }

    @Test public void samplesKeepValidFieldsAndDropOutOfRangeOnes() throws Exception {
        String json = Payloads.samples(List.of(w(1_700_000_000_000L, 72.456, 9.8123, 14.0, 0.3, 12)), 80);
        JSONObject o = new JSONObject(json);
        JSONObject s = o.getJSONArray("samples").getJSONObject(0);
        assertEquals(72.46, s.getDouble("hr"), 0.0001);
        assertEquals(9.81, s.getDouble("accel_mean"), 0.0001);
        assertEquals(12, s.getInt("steps"));
        assertEquals(1_700_000_000_000L, s.getLong("t"));
        assertEquals(80, o.getInt("battery_pct"));

        JSONObject bad = new JSONObject(Payloads.samples(List.of(w(5, 400.0, 9.0, null, null, null)), null)).getJSONArray("samples").getJSONObject(0);
        assertFalse("hr 400 is outside 20..250", bad.has("hr"));
        assertTrue(bad.has("accel_mean"));
    }

    @Test public void windowsWithNothingUsableAreSkippedAndAnEmptyBatchIsNull() {
        assertNull(Payloads.samples(List.of(w(5, null, null, null, null, null)), 50));
        assertNull(Payloads.samples(List.of(w(5, 10.0, Double.NaN, null, null, null)), 50));
        assertNull(Payloads.samples(List.of(w(0, 70.0, null, null, null, null)), 50));
        assertNull(Payloads.samples(new ArrayList<>(), 50));
    }

    @Test public void batteryOutsideZeroToHundredIsOmitted() throws Exception {
        assertFalse(new JSONObject(Payloads.samples(List.of(w(5, 70.0, null, null, null, null)), 101)).has("battery_pct"));
        assertFalse(new JSONObject(Payloads.samples(List.of(w(5, 70.0, null, null, null, null)), -1)).has("battery_pct"));
        assertFalse(new JSONObject(Payloads.samples(List.of(w(5, 70.0, null, null, null, null)), null)).has("battery_pct"));
    }

    @Test public void aBatchNeverExceedsTheServerLimit() throws Exception {
        List<SampleWindow> many = new ArrayList<>();
        for (int i = 1; i <= 800; i++) many.add(w(i, 70.0, null, null, null, null));
        assertEquals(720, new JSONObject(Payloads.samples(many, null)).getJSONArray("samples").length());
    }

    @Test public void alertCarriesTypeTriggerTimeAndOnlyValidMetrics() throws Exception {
        JSONObject o = new JSONObject(Payloads.alert("w-12345678", Payloads.FALL, Payloads.TRIGGER_DEVICE, 1_800_000_000_000L, 118.0, 500.0, 1.5, null));
        assertEquals("w-12345678", o.getString("client_id"));
        assertEquals("fall", o.getString("type"));
        assertEquals("device_event", o.getString("trigger"));
        assertEquals(1_800_000_000_000L, o.getLong("detected_at"));
        assertEquals(118.0, o.getJSONObject("metrics").getDouble("heart_rate"), 0.0001);
        assertFalse("accel_peak 500 is out of range", o.getJSONObject("metrics").has("accel_peak"));
        assertFalse("confidence 1.5 is out of range", o.has("confidence"));
        assertFalse(o.has("snapshot"));
        assertFalse(o.has("lat"));
    }

    @Test public void alertWithoutAnyMetricHasNoMetricsObject() throws Exception {
        JSONObject o = new JSONObject(Payloads.alert("w-12345678", Payloads.SEIZURE, Payloads.TRIGGER_MANUAL, 1L, null, null, null, null));
        assertFalse(o.has("metrics"));
        assertEquals("manual", o.getString("trigger"));
    }

    @Test public void snapshotIsAttachedInTheServerShape() throws Exception {
        Payloads.Snapshot snap = new Payloads.Snapshot(1_000L, 50.0, new float[]{0.1f, 0.2f}, new float[]{0f, 0f}, new float[]{9.81f, 9.8f});
        JSONObject s = new JSONObject(Payloads.alert("w-12345678", Payloads.FALL, Payloads.TRIGGER_DEVICE, 2_000L, null, null, null, snap)).getJSONObject("snapshot");
        assertEquals(50.0, s.getDouble("sample_rate_hz"), 0.0001);
        assertEquals(1_000L, s.getLong("started_at"));
        JSONArray axes = s.getJSONArray("axes");
        assertEquals(2, axes.length());
        assertEquals(3, axes.getJSONArray(0).length());
        assertEquals(9.81, axes.getJSONArray(0).getDouble(2), 0.0001);
    }

    @Test public void snapshotsThatBreakServerLimitsAreLeftOut() throws Exception {
        float[] big = new float[12_001];
        assertFalse(new JSONObject(Payloads.alert("w-12345678", Payloads.FALL, Payloads.TRIGGER_DEVICE, 2L, null, null, null,
                new Payloads.Snapshot(1, 50.0, big, big, big))).has("snapshot"));
        float[] one = new float[1];
        assertFalse(new JSONObject(Payloads.alert("w-12345678", Payloads.FALL, Payloads.TRIGGER_DEVICE, 2L, null, null, null,
                new Payloads.Snapshot(1, 500.0, one, one, one))).has("snapshot"));
        assertFalse(new JSONObject(Payloads.alert("w-12345678", Payloads.FALL, Payloads.TRIGGER_DEVICE, 2L, null, null, null,
                new Payloads.Snapshot(1, 50.0, new float[0], new float[0], new float[0]))).has("snapshot"));
    }
}
