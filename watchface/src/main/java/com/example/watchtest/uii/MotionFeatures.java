package com.example.watchtest.uii;

/**
 * Features of one window of raw accelerometer readings (m/s^2). Pure Java so it runs in JVM unit tests.
 * The 2-8 Hz band is where rhythmic convulsive shaking sits; walking is mostly 1-2.5 Hz.
 */
public final class MotionFeatures {
    public static final String[] NAMES = {
            "mag_mean", "mag_std", "mag_max", "jerk_rms", "zero_cross_per_s", "dominant_hz", "band_2_8_ratio"};
    public static final int COUNT = NAMES.length;

    private static final double F_MIN = 0.5, F_MAX = 12.0, F_STEP = 0.25, BAND_LO = 2.0, BAND_HI = 8.0;

    private MotionFeatures() {}

    public static float[] compute(float[] x, float[] y, float[] z, double fs) {
        int n = x.length;
        if (n < 8 || y.length != n || z.length != n || !(fs > 0)) {
            throw new IllegalArgumentException("need >= 8 samples on three equal axes and a positive rate");
        }
        double[] m = new double[n];
        double sum = 0, max = 0;
        for (int i = 0; i < n; i++) {
            m[i] = Math.sqrt((double) x[i] * x[i] + (double) y[i] * y[i] + (double) z[i] * z[i]);
            sum += m[i];
            max = i == 0 ? m[0] : Math.max(max, m[i]);
        }
        double mean = sum / n;

        double var = 0, jerkSq = 0;
        int crossings = 0;
        for (int i = 0; i < n; i++) {
            double d = m[i] - mean;
            var += d * d;
            if (i > 0) {
                double j = (m[i] - m[i - 1]) * fs;
                jerkSq += j * j;
                if ((m[i] - mean) * (m[i - 1] - mean) < 0) crossings++;
            }
        }
        double std = Math.sqrt(var / n);

        double total = 0, band = 0, bestPower = 0, bestHz = 0;
        for (double f = F_MIN; f <= F_MAX; f += F_STEP) {
            double w = 2 * Math.PI * f / fs, re = 0, im = 0;
            for (int i = 0; i < n; i++) {
                double d = m[i] - mean;
                re += d * Math.cos(w * i);
                im -= d * Math.sin(w * i);
            }
            double p = (re * re + im * im) / ((double) n * n);
            total += p;
            if (f >= BAND_LO && f <= BAND_HI) band += p;
            if (p > bestPower) { bestPower = p; bestHz = f; }
        }
        boolean still = total < 1e-9;

        float[] out = new float[COUNT];
        out[0] = (float) mean;
        out[1] = (float) std;
        out[2] = (float) max;
        out[3] = (float) Math.sqrt(jerkSq / (n - 1));
        out[4] = (float) (crossings / (n / fs));
        out[5] = still ? 0f : (float) bestHz;
        out[6] = still ? 0f : (float) (band / total);
        return out;
    }
}
