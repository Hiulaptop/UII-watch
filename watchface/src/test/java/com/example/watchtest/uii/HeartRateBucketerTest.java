package com.example.watchtest.uii;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class HeartRateBucketerTest {
    private static List<long[]> times(long... t) {
        List<long[]> out = new ArrayList<>();
        for (long v : t) out.add(new long[]{v});
        return out;
    }

    @Test public void readingsInTheSameWindowAreAveragedIntoOneRow() {
        List<SampleWindow> w = HeartRateBucketer.bucket(times(10_000, 11_000, 19_999), List.of(70.0, 80.0, 90.0), 10_000);
        assertEquals(1, w.size());
        assertEquals(80.0, w.get(0).hr, 1e-9);
        assertNull(w.get(0).accelMean);
    }

    @Test public void aOneHertzStreamBecomesOneRowPerWindow() {
        List<long[]> t = new ArrayList<>();
        List<Double> v = new ArrayList<>();
        for (int i = 0; i < 600; i++) { t.add(new long[]{1_000_000L + i * 1_000L}); v.add(70.0 + (i % 5)); }
        assertEquals("ten minutes at 1 Hz -> 60 windows of 10 s, not 600 rows", 60, HeartRateBucketer.bucket(t, v, 10_000).size());
    }

    @Test public void rowsAreStampedOneMillisecondAfterTheWindowStartSoTheyNeverCollideWithMotionRows() {
        SampleWindow w = HeartRateBucketer.bucket(times(20_000), List.of(70.0), 10_000).get(0);
        assertEquals(20_001L, w.tMs);
        assertTrue(w.tMs % 10_000 != 0);
    }

    @Test public void windowsComeOutInTimeOrderEvenIfReadingsArriveOutOfOrder() {
        List<SampleWindow> w = HeartRateBucketer.bucket(times(35_000, 5_000, 25_000), List.of(3.0, 1.0, 2.0), 10_000);
        assertEquals(3, w.size());
        assertEquals(1.0, w.get(0).hr, 1e-9);
        assertEquals(2.0, w.get(1).hr, 1e-9);
        assertEquals(3.0, w.get(2).hr, 1e-9);
    }

    @Test public void badReadingsAreIgnoredAndEmptyInputGivesNothing() {
        assertEquals(1, HeartRateBucketer.bucket(times(1_000, 2_000), List.of(Double.NaN, 72.0), 10_000).size());
        assertEquals(72.0, HeartRateBucketer.bucket(times(1_000, 2_000), List.of(Double.NaN, 72.0), 10_000).get(0).hr, 1e-9);
        assertTrue(HeartRateBucketer.bucket(new ArrayList<>(), new ArrayList<>(), 10_000).isEmpty());
        assertTrue(HeartRateBucketer.bucket(times(1_000), List.of(Double.POSITIVE_INFINITY), 10_000).isEmpty());
    }

    @Test public void mismatchedListLengthsAreHandledWithoutThrowing() {
        assertEquals(1, HeartRateBucketer.bucket(times(1_000, 2_000, 3_000), List.of(60.0), 10_000).size());
    }
}
