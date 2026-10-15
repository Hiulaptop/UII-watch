package com.example.watchtest.uii;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class FeatureWindowerTest {
    @Test public void aFiveHertzShakeProducesOneRowPerSecondOnceTheFirstWindowIsFull() {
        FeatureWindower w = new FeatureWindower(2_000, 1_000);
        List<FeatureWindower.Row> rows = new ArrayList<>();
        for (int i = 0; i < 300; i++) {            // 6 s at 50 Hz
            long t = 1_000_000L + i * 20L;
            float z = (float) (9.81 + 3 * Math.sin(2 * Math.PI * 5 * i / 50.0));
            FeatureWindower.Row r = w.add(t, 0f, 0f, z);
            if (r != null) rows.add(r);
        }
        assertTrue("rows: " + rows.size(), rows.size() >= 4 && rows.size() <= 6);
        for (FeatureWindower.Row r : rows) assertEquals(5.0, r.f[5], 0.26);
    }

    @Test public void nothingIsEmittedBeforeTheWindowIsFull() {
        FeatureWindower w = new FeatureWindower(2_000, 1_000);
        for (int i = 0; i < 40; i++) assertNull(w.add(i * 20L, 0f, 0f, 9.81f));
    }
}
