package com.example.watchtest.uii;

/**
 * The only thing the rest of the app knows about detection. The real model replaces the implementation behind
 * {@link DetectorHolder}; everything after a {@link Detection} (cooldown prompt, alert, upload) does not change.
 */
public interface MotionDetector {
    /**
     * Called about once per second with the latest short-window features (see {@link MotionFeatures#NAMES}).
     *
     * @param heartRateBpm the latest heart rate if it is recent, else null
     * @return a detection, or null when nothing is wrong. Must be cheap and must not throw.
     */
    Detection onFeatures(long tMs, float[] features, Double heartRateBpm);

    final class Detection {
        /** {@link Payloads#FALL}, {@link Payloads#SEIZURE} or {@link Payloads#PRE_SEIZURE}. */
        public final String type;
        /** 0..1 */
        public final double confidence;

        public Detection(String type, double confidence) {
            this.type = type;
            this.confidence = confidence;
        }
    }
}
