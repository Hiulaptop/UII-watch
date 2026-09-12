package com.example.watchtest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;

public class SensorStateStoreTest {
    @Test public void replaceSupportsEmptyInventory() {
        SensorStateStore store = new SensorStateStore();
        store.replace(Arrays.<SensorInfo>asList());
        assertEquals(0, store.values().size());
    }

    @Test public void pendingStateDoesNotInventValues() {
        SensorInfo pending = SensorInfo.pending(1, "Accelerometer", "accelerometer", "test");
        assertEquals(SensorInfo.Status.PENDING, pending.status);
        assertEquals("Waiting for reading", pending.valuesText());
    }

    @Test public void updatePreservesIndependentSensorStates() {
        SensorStateStore store = new SensorStateStore();
        SensorInfo first = SensorInfo.fromValues(1, "Accelerometer", "accelerometer", "test", new float[]{1, 2, 3});
        SensorInfo second = SensorInfo.fromValues(2, "Gyroscope", "gyroscope", "test", new float[]{4, 5, 6});
        store.replace(Arrays.asList(first, second));

        store.update(first.unavailable("Sensor unavailable"));

        assertEquals(SensorInfo.Status.UNAVAILABLE, store.get(1).status);
        assertEquals(SensorInfo.Status.CURRENT, store.get(2).status);
    }

    @Test public void multiAxisValuesAreRetained() {
        SensorInfo info = SensorInfo.fromValues(1, "Accelerometer", "accelerometer", "test", new float[]{1, 2, 3});
        assertEquals(3, info.valueCount);
        org.junit.Assert.assertTrue(info.valuesText().contains("[3] 3.0000"));
    }

    @Test public void unknownSensorUpdateIsIgnored() {
        SensorStateStore store = new SensorStateStore();
        SensorInfo unknown = SensorInfo.fromValues(99, "Unknown", "unknown", "test", new float[]{0});
        store.update(unknown);
        assertNull(store.get(99));
    }
}
