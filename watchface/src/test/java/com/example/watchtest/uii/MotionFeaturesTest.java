package com.example.watchtest.uii;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MotionFeaturesTest {
    private static final double FS = 50.0;
    private static final int MEAN = 0, STD = 1, JERK = 3, ZCR = 4, DOM = 5, BAND = 6;

    /** 2 s of gravity on z plus a sine of the given frequency and amplitude on z. */
    private static float[][] sine(double hz, double amp) {
        int n = 100;
        float[] x = new float[n], y = new float[n], z = new float[n];
        for (int i = 0; i < n; i++) z[i] = (float) (9.81 + amp * Math.sin(2 * Math.PI * hz * i / FS));
        return new float[][]{x, y, z};
    }

    @Test public void aStillWristHasNoVariationAndNoDominantFrequency() {
        float[][] s = sine(1.0, 0.0);
        float[] f = MotionFeatures.compute(s[0], s[1], s[2], FS);
        assertEquals(9.81, f[MEAN], 1e-3);
        assertEquals(0.0, f[STD], 1e-3);
        assertEquals(0.0, f[DOM], 1e-6);
        assertEquals(0.0, f[BAND], 1e-6);
    }

    @Test public void aFiveHertzShakeIsFoundInTheConvulsionBand() {
        float[][] s = sine(5.0, 3.0);
        float[] f = MotionFeatures.compute(s[0], s[1], s[2], FS);
        assertEquals(5.0, f[DOM], 0.26);
        assertTrue("most power in 2-8 Hz, was " + f[BAND], f[BAND] > 0.8);
        assertTrue(f[STD] > 1.5);
        assertTrue(f[JERK] > 20);
        assertTrue(f[ZCR] > 8);
    }

    @Test public void aSlowOneHertzSwingIsOutsideTheConvulsionBand() {
        float[][] s = sine(1.0, 3.0);
        float[] f = MotionFeatures.compute(s[0], s[1], s[2], FS);
        assertEquals(1.0, f[DOM], 0.26);
        assertTrue("was " + f[BAND], f[BAND] < 0.3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void tooFewSamplesAreRejected() {
        MotionFeatures.compute(new float[3], new float[3], new float[3], FS);
    }
}
