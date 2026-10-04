package com.example.watchtest.uii;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a stream of raw readings into one summary per fixed time window, so the watch sends a few numbers every
 * few seconds instead of every raw sample. Not thread-safe: call it from a single thread.
 */
public final class WindowAggregator {
    private final long windowMs;

    private long bucket = Long.MIN_VALUE;
    private long accelCount;
    private double accelMean;
    private double accelM2;
    private double accelMax;
    private double hrSum;
    private int hrCount;

    public WindowAggregator(long windowMs) {
        if (windowMs <= 0) throw new IllegalArgumentException("windowMs must be positive");
        this.windowMs = windowMs;
    }

    /** Returns the windows that closed because this reading belongs to a later one (usually none, sometimes one). */
    public List<SampleWindow> addAccel(long tMs, double magnitude) {
        List<SampleWindow> closed = advance(tMs);
        if (Double.isNaN(magnitude) || Double.isInfinite(magnitude)) return closed;
        accelCount++;
        // Welford's online algorithm: variance without keeping every sample.
        double delta = magnitude - accelMean;
        accelMean += delta / accelCount;
        accelM2 += delta * (magnitude - accelMean);
        accelMax = accelCount == 1 ? magnitude : Math.max(accelMax, magnitude);
        return closed;
    }

    public List<SampleWindow> addHeartRate(long tMs, double bpm) {
        List<SampleWindow> closed = advance(tMs);
        if (Double.isNaN(bpm) || Double.isInfinite(bpm)) return closed;
        hrSum += bpm;
        hrCount++;
        return closed;
    }

    /** Closes the open window if {@code nowMs} has moved past it. Call it periodically so quiet periods still flush. */
    public List<SampleWindow> drain(long nowMs) {
        return advance(nowMs);
    }

    private List<SampleWindow> advance(long tMs) {
        List<SampleWindow> closed = new ArrayList<>(1);
        long b = Math.floorDiv(tMs, windowMs) * windowMs;
        if (bucket == Long.MIN_VALUE) {
            bucket = b;
        } else if (b > bucket) {
            SampleWindow w = emit();
            if (w != null) closed.add(w);
            bucket = b;
        }
        return closed;
    }

    private SampleWindow emit() {
        SampleWindow w = null;
        if (accelCount > 0 || hrCount > 0) {
            // Explicit Double locals: a ternary mixing double and null would unbox the null and throw.
            Double hr = null;
            Double mean = null;
            Double max = null;
            Double variance = null;
            if (hrCount > 0) hr = hrSum / hrCount;
            if (accelCount > 0) {
                mean = accelMean;
                max = accelMax;
                variance = accelCount > 1 ? accelM2 / (accelCount - 1) : 0.0;
            }
            w = new SampleWindow(bucket, hr, mean, max, variance, null);
        }
        accelCount = 0;
        accelMean = 0;
        accelM2 = 0;
        accelMax = 0;
        hrSum = 0;
        hrCount = 0;
        return w;
    }
}
