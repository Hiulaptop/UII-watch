package com.example.watchtest.uii;

/**
 * The most recent raw accelerometer readings, kept in a fixed ring so a fall or seizure alert can carry the few
 * seconds around the event. Memory use is constant. Thread-safe.
 */
public final class RawBuffer {
    private final int capacity;
    private final long[] t;
    private final float[] x;
    private final float[] y;
    private final float[] z;
    private int next;
    private int size;

    public RawBuffer(int capacity) {
        this.capacity = capacity;
        t = new long[capacity];
        x = new float[capacity];
        y = new float[capacity];
        z = new float[capacity];
    }

    public synchronized void add(long tMs, float ax, float ay, float az) {
        t[next] = tMs;
        x[next] = ax;
        y[next] = ay;
        z[next] = az;
        next = (next + 1) % capacity;
        if (size < capacity) size++;
    }

    /** Readings from the last {@code windowMs} up to {@code nowMs}, oldest first, or null if there are too few. */
    public synchronized Payloads.Snapshot snapshot(long nowMs, long windowMs, int minPoints) {
        int count = 0;
        for (int i = 0; i < size; i++) {
            long ts = t[(next - 1 - i + capacity) % capacity];
            if (nowMs - ts <= windowMs && ts <= nowMs) count++;
            else break;
        }
        if (count < minPoints) return null;

        float[] ox = new float[count];
        float[] oy = new float[count];
        float[] oz = new float[count];
        int start = (next - count + capacity) % capacity;
        for (int i = 0; i < count; i++) {
            int idx = (start + i) % capacity;
            ox[i] = x[idx];
            oy[i] = y[idx];
            oz[i] = z[idx];
        }
        long first = t[start];
        long last = t[(next - 1 + capacity) % capacity];
        double span = Math.max(1, last - first);
        double rate = count > 1 ? (count - 1) * 1000.0 / span : 1;
        return new Payloads.Snapshot(first, Math.min(200, Math.max(1, rate)), ox, oy, oz);
    }
}
