package com.example.watchtest.uii;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayList;
import java.util.List;

public class SignalsTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    // ---- WindowAggregator ----
    @Test public void aWindowClosesOnlyWhenALaterReadingArrives() {
        WindowAggregator a = new WindowAggregator(10_000);
        assertTrue(a.addAccel(1_000, 9.0).isEmpty());
        assertTrue(a.addAccel(9_999, 11.0).isEmpty());
        List<SampleWindow> closed = a.addAccel(10_000, 9.8);
        assertEquals(1, closed.size());
        SampleWindow w = closed.get(0);
        assertEquals(0L, w.tMs);
        assertEquals(10.0, w.accelMean, 1e-9);
        assertEquals(11.0, w.accelMax, 1e-9);
        assertEquals(2.0, w.accelVar, 1e-9); // sample variance of {9, 11}
        assertNull("no heart rate was read", w.hr);
    }

    @Test public void varianceMatchesTheTextbookValue() {
        WindowAggregator a = new WindowAggregator(60_000);
        double[] v = {2, 4, 4, 4, 5, 5, 7, 9};
        for (int i = 0; i < v.length; i++) a.addAccel(i * 100L, v[i]);
        SampleWindow w = a.drain(60_000).get(0);
        assertEquals(5.0, w.accelMean, 1e-9);
        assertEquals(9.0, w.accelMax, 1e-9);
        assertEquals(32.0 / 7.0, w.accelVar, 1e-9); // sample variance = sum of squares / (n - 1)
    }

    @Test public void heartRateIsAveragedPerWindow() {
        WindowAggregator a = new WindowAggregator(10_000);
        a.addHeartRate(1_000, 70);
        a.addHeartRate(5_000, 80);
        SampleWindow w = a.drain(10_000).get(0);
        assertEquals(75.0, w.hr, 1e-9);
        assertNull(w.accelMean);
    }

    @Test public void aSingleAccelReadingHasZeroVarianceNotNaN() {
        WindowAggregator a = new WindowAggregator(10_000);
        a.addAccel(1_000, 9.81);
        SampleWindow w = a.drain(10_000).get(0);
        assertEquals(0.0, w.accelVar, 0.0);
    }

    @Test public void emptyWindowsAndBadReadingsProduceNothing() {
        WindowAggregator a = new WindowAggregator(10_000);
        a.addAccel(1_000, Double.NaN);
        a.addHeartRate(2_000, Double.POSITIVE_INFINITY);
        assertTrue(a.drain(100_000).isEmpty());
        assertTrue("a gap of many empty windows emits none", a.addAccel(500_000, 9.8).isEmpty());
        assertEquals(1, a.drain(510_000).size());
    }

    @Test public void drainFlushesAQuietPeriodAndEachWindowIsEmittedOnce() {
        WindowAggregator a = new WindowAggregator(10_000);
        a.addAccel(1_000, 9.0);
        assertTrue(a.drain(5_000).isEmpty());
        assertEquals(1, a.drain(10_000).size());
        assertTrue(a.drain(20_000).isEmpty());
    }

    @Test public void windowsAreAlignedToTheClockEvenForOddTimestamps() {
        WindowAggregator a = new WindowAggregator(10_000);
        a.addAccel(1_700_000_003_456L, 9.0);
        assertEquals(1_700_000_000_000L, a.drain(1_700_000_020_000L).get(0).tMs);
    }

    // ---- RawBuffer ----
    @Test public void snapshotReturnsTheLastSecondsOldestFirst() {
        RawBuffer b = new RawBuffer(100);
        for (int i = 0; i < 50; i++) b.add(1_000L + i * 20, i, 0, 9.8f); // 50 Hz
        Payloads.Snapshot s = b.snapshot(1_980L, 10_000, 10);
        assertNotNull(s);
        assertEquals(50, s.size());
        assertEquals(1_000L, s.startedAtMs);
        assertEquals(0f, s.x[0], 0f);
        assertEquals(49f, s.x[49], 0f);
        assertEquals(50.0, s.sampleRateHz, 1.0);
    }

    @Test public void theRingKeepsOnlyTheNewestWhenItWrapsAround() {
        RawBuffer b = new RawBuffer(10);
        for (int i = 0; i < 25; i++) b.add(i * 10L, i, 0, 0);
        Payloads.Snapshot s = b.snapshot(240L, 10_000, 1);
        assertEquals(10, s.size());
        assertEquals(15f, s.x[0], 0f);
        assertEquals(24f, s.x[9], 0f);
    }

    @Test public void tooFewOrTooOldReadingsGiveNoSnapshot() {
        RawBuffer b = new RawBuffer(100);
        assertNull(b.snapshot(1_000, 10_000, 1));
        for (int i = 0; i < 5; i++) b.add(i * 20L, 0, 0, 0);
        assertNull("only 5 points, 10 required", b.snapshot(100, 10_000, 10));
        assertNull("everything is older than the window", b.snapshot(60_000, 5_000, 1));
    }

    @Test public void onlyReadingsInsideTheWindowAreIncluded() {
        RawBuffer b = new RawBuffer(100);
        for (int i = 0; i < 20; i++) b.add(i * 1_000L, i, 0, 0); // one per second
        Payloads.Snapshot s = b.snapshot(19_000L, 5_000, 1);
        assertEquals(6, s.size()); // t = 14..19 s
        assertEquals(14f, s.x[0], 0f);
    }

    // ---- SampleSink ----
    @Test public void aBatchIsWrittenOnceEnoughWindowsHaveGathered() throws Exception {
        FileOutbox box = new FileOutbox(tmp.newFolder());
        SampleSink sink = new SampleSink(box);
        List<SampleWindow> ws = new ArrayList<>();
        for (int i = 1; i < SampleSink.BATCH_SIZE; i++) ws.add(new SampleWindow(i * 10_000L, 70.0, null, null, null, null));
        assertEquals(false, sink.add(ws, 1_000, 80));
        assertEquals(0, box.size());
        assertEquals(true, sink.add(List.of(new SampleWindow(999_999L, 71.0, null, null, null, null)), 2_000, 80));
        assertEquals(1, box.size());
        assertEquals(Outbox.Kind.SAMPLES, box.list().get(0).kind);
    }

    @Test public void anOldPartialBatchIsWrittenAnywayAfterFiveMinutes() throws Exception {
        FileOutbox box = new FileOutbox(tmp.newFolder());
        SampleSink sink = new SampleSink(box);
        sink.add(List.of(new SampleWindow(10_000L, 70.0, null, null, null, null)), 0, null);
        assertEquals(0, box.size());
        assertEquals(true, sink.add(new ArrayList<>(), SampleSink.MAX_AGE_MS, null));
        assertEquals(1, box.size());
    }

    @Test public void unusableWindowsNeverReachTheOutbox() throws Exception {
        FileOutbox box = new FileOutbox(tmp.newFolder());
        SampleSink sink = new SampleSink(box);
        sink.add(List.of(new SampleWindow(10_000L, 999.0, null, null, null, null)), 0, null);
        assertEquals(false, sink.flush(null));
        assertEquals(0, box.size());
    }
}
