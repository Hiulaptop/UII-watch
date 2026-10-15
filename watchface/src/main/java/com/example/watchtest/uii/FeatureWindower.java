package com.example.watchtest.uii;

import java.util.ArrayDeque;

/** Slides a window over the raw stream and emits one feature row per hop. Not thread-safe: use one thread. */
public final class FeatureWindower {
    public static final class Row {
        public final long tMs;
        public final float[] f;

        Row(long tMs, float[] f) {
            this.tMs = tMs;
            this.f = f;
        }
    }

    private static final class S {
        final long t;
        final float x, y, z;

        S(long t, float x, float y, float z) {
            this.t = t;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private final long windowMs, hopMs;
    private final ArrayDeque<S> buf = new ArrayDeque<>();
    private long nextEmitMs = Long.MIN_VALUE;

    public FeatureWindower(long windowMs, long hopMs) {
        if (windowMs <= 0 || hopMs <= 0) throw new IllegalArgumentException("window and hop must be positive");
        this.windowMs = windowMs;
        this.hopMs = hopMs;
    }

    /** Returns a row when a hop has elapsed and the window is at least 90% full, else null. */
    public Row add(long tMs, float x, float y, float z) {
        buf.addLast(new S(tMs, x, y, z));
        while (!buf.isEmpty() && tMs - buf.peekFirst().t > windowMs) buf.removeFirst();
        long span = tMs - buf.peekFirst().t;
        if (span < windowMs * 0.9) return null;
        if (nextEmitMs != Long.MIN_VALUE && tMs < nextEmitMs) return null;

        int n = buf.size();
        if (n < 8) return null;
        nextEmitMs = tMs + hopMs;
        float[] xs = new float[n], ys = new float[n], zs = new float[n];
        int i = 0;
        for (S s : buf) {
            xs[i] = s.x;
            ys[i] = s.y;
            zs[i] = s.z;
            i++;
        }
        double fs = (n - 1) * 1000.0 / span;
        return new Row(tMs, MotionFeatures.compute(xs, ys, zs, fs));
    }
}
