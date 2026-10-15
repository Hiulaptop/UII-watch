package com.example.watchtest.uii;

import java.util.Random;

/**
 * Debug builds only: stands in for the model so the whole alert path (prompt, cancel window, upload, web alert) can
 * be exercised end to end. It lives in the debug source set so it cannot be part of a release package.
 */
public final class RandomDetector implements MotionDetector {
    private final Random rng;
    private final double perRowProbability;

    /** @param perRowProbability chance per feature row (about one per second); 1/600 is roughly one per 10 minutes */
    public RandomDetector(Random rng, double perRowProbability) {
        this.rng = rng;
        this.perRowProbability = perRowProbability;
    }

    @Override public Detection onFeatures(long tMs, float[] features, Double heartRateBpm) {
        if (rng.nextDouble() >= perRowProbability) return null;
        double r = rng.nextDouble();
        String type = r < 0.5 ? Payloads.PRE_SEIZURE : r < 0.8 ? Payloads.FALL : Payloads.SEIZURE;
        return new Detection(type, 0.5 + 0.5 * rng.nextDouble());
    }
}
