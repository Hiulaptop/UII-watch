package com.example.watchtest.uii;

import java.util.ArrayList;
import java.util.List;

/** Collects summary windows and writes them to the outbox in batches. Thread-safe. */
public final class SampleSink {
    public static final int BATCH_SIZE = 30;
    public static final long MAX_AGE_MS = 5 * 60_000L;

    private final Outbox outbox;
    private final List<SampleWindow> pending = new ArrayList<>();
    private long oldestMs;

    public SampleSink(Outbox outbox) {
        this.outbox = outbox;
    }

    /** Returns true when a batch was written to the outbox, so the caller knows to schedule an upload. */
    public synchronized boolean add(List<SampleWindow> windows, long nowMs, Integer batteryPct) {
        if (windows.isEmpty()) return maybeFlush(nowMs, batteryPct);
        if (pending.isEmpty()) oldestMs = nowMs;
        pending.addAll(windows);
        return maybeFlush(nowMs, batteryPct);
    }

    /** Writes whatever is pending, for example when monitoring stops. */
    public synchronized boolean flush(Integer batteryPct) {
        if (pending.isEmpty()) return false;
        String json = Payloads.samples(pending, batteryPct);
        pending.clear();
        if (json == null) return false;
        outbox.add(Outbox.Kind.SAMPLES, json, 0);
        return true;
    }

    private boolean maybeFlush(long nowMs, Integer batteryPct) {
        if (pending.isEmpty()) return false;
        if (pending.size() >= BATCH_SIZE || nowMs - oldestMs >= MAX_AGE_MS) return flush(batteryPct);
        return false;
    }
}
