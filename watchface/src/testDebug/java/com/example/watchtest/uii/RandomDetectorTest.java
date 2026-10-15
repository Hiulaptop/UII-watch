package com.example.watchtest.uii;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Random;

public class RandomDetectorTest {
    private static final float[] F = new float[MotionFeatures.COUNT];

    @Test public void probabilityZeroNeverFires() {
        RandomDetector d = new RandomDetector(new Random(1), 0.0);
        for (int i = 0; i < 1_000; i++) assertNull(d.onFeatures(i, F, null));
    }

    @Test public void probabilityOneAlwaysFiresWithAValidKindAndConfidence() {
        RandomDetector d = new RandomDetector(new Random(1), 1.0);
        for (int i = 0; i < 1_000; i++) {
            MotionDetector.Detection x = d.onFeatures(i, F, null);
            assertNotNull(x);
            assertTrue(DetectionGate.isKnownType(x.type));
            assertTrue(x.confidence >= 0.5 && x.confidence <= 1.0);
        }
    }

    @Test public void theMixRoughlyFollowsHalfWarningThirtyPercentFallTwentyPercentSeizure() {
        RandomDetector d = new RandomDetector(new Random(42), 1.0);
        int pre = 0, fall = 0, seizure = 0, n = 10_000;
        for (int i = 0; i < n; i++) {
            String t = d.onFeatures(i, F, null).type;
            if (Payloads.PRE_SEIZURE.equals(t)) pre++;
            else if (Payloads.FALL.equals(t)) fall++;
            else seizure++;
        }
        assertEquals(0.5, pre / (double) n, 0.03);
        assertEquals(0.3, fall / (double) n, 0.03);
        assertEquals(0.2, seizure / (double) n, 0.03);
    }
}
