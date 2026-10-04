package com.example.watchtest.uii;

/** One summary window of measured signals (a few seconds), as sent to /api/device/samples. */
public final class SampleWindow {
    public final long tMs;
    public final Double hr;
    public final Double accelMean;
    public final Double accelMax;
    public final Double accelVar;
    public final Integer steps;

    public SampleWindow(long tMs, Double hr, Double accelMean, Double accelMax, Double accelVar, Integer steps) {
        this.tMs = tMs;
        this.hr = hr;
        this.accelMean = accelMean;
        this.accelMax = accelMax;
        this.accelVar = accelVar;
        this.steps = steps;
    }
}
