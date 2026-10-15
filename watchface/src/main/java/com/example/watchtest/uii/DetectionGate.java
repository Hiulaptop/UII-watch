package com.example.watchtest.uii;

/** Decides whether a detection of a given kind may raise a new passive alert, so one event is not reported twice. */
public final class DetectionGate {
    /** The system can report one fall more than once. */
    public static final long FALL_DEDUPE_MS = 2 * 60_000;
    /** Seizure and warning detections can stay positive for a minute or more; one alert per episode is enough. */
    public static final long EPISODE_DEDUPE_MS = 5 * 60_000;

    private DetectionGate() {}

    public static boolean isKnownType(String type) {
        return Payloads.FALL.equals(type) || Payloads.SEIZURE.equals(type) || Payloads.PRE_SEIZURE.equals(type);
    }

    public static long dedupeMs(String type) {
        return Payloads.FALL.equals(type) ? FALL_DEDUPE_MS : EPISODE_DEDUPE_MS;
    }

    /** @param lastMs when this kind last raised an alert (0 if never) */
    public static boolean allow(String type, long nowMs, long lastMs) {
        return isKnownType(type) && nowMs - lastMs >= dedupeMs(type);
    }
}
