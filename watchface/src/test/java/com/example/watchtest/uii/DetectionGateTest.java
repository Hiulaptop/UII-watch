package com.example.watchtest.uii;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;

public class DetectionGateTest {
    @After public void restoreDetector() {
        DetectorHolder.set(null);
    }

    @Test public void aFirstDetectionOfAnyKnownKindIsAllowed() {
        assertTrue(DetectionGate.allow(Payloads.FALL, 1_000_000L, 0));
        assertTrue(DetectionGate.allow(Payloads.SEIZURE, 1_000_000L, 0));
        assertTrue(DetectionGate.allow(Payloads.PRE_SEIZURE, 1_000_000L, 0));
    }

    @Test public void aFallIsNotReportedTwiceWithinTwoMinutes() {
        long last = 1_000_000L;
        assertFalse(DetectionGate.allow(Payloads.FALL, last + 119_000, last));
        assertTrue(DetectionGate.allow(Payloads.FALL, last + 120_000, last));
    }

    @Test public void aSeizureEpisodeIsReportedOncePerFiveMinutes() {
        long last = 1_000_000L;
        assertFalse(DetectionGate.allow(Payloads.SEIZURE, last + 299_000, last));
        assertTrue(DetectionGate.allow(Payloads.SEIZURE, last + 300_000, last));
        assertFalse(DetectionGate.allow(Payloads.PRE_SEIZURE, last + 299_000, last));
    }

    @Test public void anUnknownKindIsNeverAllowed() {
        assertFalse(DetectionGate.allow("tremor", 1_000_000L, 0));
        assertFalse(DetectionGate.allow(null, 1_000_000L, 0));
    }

    @Test public void theDetectorDefaultsToNothingDetectedAndCanBeReplacedAndRestored() {
        assertNull(DetectorHolder.get().onFeatures(1L, new float[MotionFeatures.COUNT], null));

        MotionDetector always = (t, f, hr) -> new MotionDetector.Detection(Payloads.FALL, 0.7);
        DetectorHolder.set(always);
        assertSame(always, DetectorHolder.get());
        assertEquals(Payloads.FALL, DetectorHolder.get().onFeatures(1L, null, null).type);

        DetectorHolder.set(null);
        assertNull(DetectorHolder.get().onFeatures(1L, null, null));
    }

    @Test public void anAlgorithmAlertCarriesItsTriggerAndConfidence() throws Exception {
        String json = Payloads.alert("w-12345678", Payloads.PRE_SEIZURE, Payloads.TRIGGER_ALGORITHM, 1L, 88.0, null, 0.8, null);
        JSONObject o = new JSONObject(json);
        assertEquals("algorithm", o.getString("trigger"));
        assertEquals("pre_seizure", o.getString("type"));
        assertEquals(0.8, o.getDouble("confidence"), 1e-9);
        assertEquals(88.0, o.getJSONObject("metrics").getDouble("heart_rate"), 1e-9);
    }
}
