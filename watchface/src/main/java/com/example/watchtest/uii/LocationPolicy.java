package com.example.watchtest.uii;

/**
 * When the watch reads its position and which fix to trust. GPS is the most expensive sensor on the watch, so it is read
 * only around an SOS, at fixed milestones, one request at a time; never continuously. Pure, so it is tested on the JVM.
 *
 * Milestones, counted from the moment the incident starts (the wearer picks an SOS type, or a detection fires):
 * L0 right away, L1 when the alert is built (uses what is known then, never waits), L2 after 2 min, L3 after 5 min.
 */
public final class LocationPolicy {
    /** Follow-up reads after the first one, as delays from the start of the incident. */
    public static final long[] FOLLOW_UP_AFTER_MS = {2 * 60_000L, 5 * 60_000L};
    /** One read gives up after this long and uses the best fix it saw. */
    public static final long READ_TIMEOUT_MS = 20_000;
    /** A fix this accurate (metres) ends a read at once. */
    public static final double GOOD_ACCURACY_M = 50;
    /** The platform's cached position is used as a first guess only if it is younger than this. */
    public static final long LAST_KNOWN_MAX_AGE_MS = 10 * 60_000L;
    /** More updates than this per incident are not sent; the server keeps six fixes per alert in all. */
    public static final int MAX_UPDATES = 5;
    /** Two fixes closer in time than this are compared by accuracy; a clearly newer one wins regardless. */
    private static final long SAME_MOMENT_MS = 60_000;

    private LocationPolicy() {}

    public static boolean goodEnough(LocationFix f) {
        return f != null && f.accuracyM != null && f.accuracyM <= GOOD_ACCURACY_M;
    }

    public static boolean isFresh(LocationFix f, long nowMs) {
        return f != null && nowMs - f.timeMs <= LAST_KNOWN_MAX_AGE_MS && f.timeMs <= nowMs + 60_000;
    }

    /** Which fix to use as "where the wearer is now": a much newer one wins, otherwise the more accurate one. */
    public static boolean betterForNow(LocationFix candidate, LocationFix current) {
        if (candidate == null || !candidate.isValid()) return false;
        if (current == null) return true;
        if (candidate.timeMs - current.timeMs > SAME_MOMENT_MS) return true;
        if (current.timeMs - candidate.timeMs > SAME_MOMENT_MS) return false;
        return accuracyOf(candidate) < accuracyOf(current);
    }

    /** The server's rule for replacing an alert's current position: newer, or the same moment and more accurate. */
    public static boolean worthSending(LocationFix candidate, LocationFix alreadySent) {
        if (candidate == null || !candidate.isValid()) return false;
        if (alreadySent == null) return true;
        if (candidate.timeMs != alreadySent.timeMs) return candidate.timeMs > alreadySent.timeMs;
        return accuracyOf(candidate) < accuracyOf(alreadySent);
    }

    private static double accuracyOf(LocationFix f) {
        return f.accuracyM == null ? Double.POSITIVE_INFINITY : f.accuracyM;
    }
}
