package com.example.watchtest.uii;

/** Holds the active detector. Release builds keep the no-op one; only the model (or a debug tool) replaces it. */
public final class DetectorHolder {
    private static final MotionDetector NOOP = (tMs, features, heartRateBpm) -> null;
    private static volatile MotionDetector current = NOOP;

    private DetectorHolder() {}

    public static MotionDetector get() {
        return current;
    }

    /** Passing null restores the no-op detector. */
    public static void set(MotionDetector detector) {
        current = detector != null ? detector : NOOP;
    }
}
