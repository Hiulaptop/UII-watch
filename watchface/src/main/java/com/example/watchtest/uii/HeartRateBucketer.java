package com.example.watchtest.uii;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Averages heart-rate readings into fixed windows. Health Services can deliver readings as often as once a
 * second, but the server only needs a number every few seconds.
 */
public final class HeartRateBucketer {
    private HeartRateBucketer() {}

    /**
     * Windows are stamped 1 ms after the window start. The motion windows use the exact window start, so a heart-rate
     * row and a motion row for the same window never share the server's (device, time) key and cannot overwrite each other.
     */
    public static List<SampleWindow> bucket(List<long[]> timesMs, List<Double> bpm, long windowMs) {
        Map<Long, double[]> sums = new TreeMap<>(); // window start -> {sum, count}
        int n = Math.min(timesMs.size(), bpm.size());
        for (int i = 0; i < n; i++) {
            double v = bpm.get(i);
            if (Double.isNaN(v) || Double.isInfinite(v)) continue;
            long start = Math.floorDiv(timesMs.get(i)[0], windowMs) * windowMs;
            double[] s = sums.computeIfAbsent(start, k -> new double[2]);
            s[0] += v;
            s[1] += 1;
        }
        List<SampleWindow> out = new ArrayList<>(sums.size());
        for (Map.Entry<Long, double[]> e : sums.entrySet()) {
            out.add(new SampleWindow(e.getKey() + 1, e.getValue()[0] / e.getValue()[1], null, null, null, null));
        }
        return out;
    }
}
