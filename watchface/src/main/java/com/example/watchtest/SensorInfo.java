package com.example.watchtest;

import android.hardware.Sensor;

import java.util.Locale;

/** Immutable sensor metadata and latest value state used by the UI. */
public final class SensorInfo {
    public enum Status { PENDING, CURRENT, UNAVAILABLE, ERROR }

    public final int id;
    public final String name;
    public final String type;
    public final String vendor;
    public final int valueCount;
    public final float[] values;
    public final Status status;
    public final String message;

    private SensorInfo(int id, String name, String type, String vendor, int valueCount,
                       float[] values, Status status, String message) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.vendor = vendor;
        this.valueCount = valueCount;
        this.values = values == null ? null : values.clone();
        this.status = status;
        this.message = message;
    }

    public static SensorInfo pending(Sensor sensor) {
        return new SensorInfo(sensor.getId(), sensor.getName(), typeName(sensor), sensor.getVendor(),
                0,
                null, Status.PENDING, "Waiting for reading");
    }

    public static SensorInfo fromValues(int id, String name, String type, String vendor, float[] values) {
        return new SensorInfo(id, name, type, vendor, values.length, values, Status.CURRENT, null);
    }

    public static SensorInfo pending(int id, String name, String type, String vendor) {
        return new SensorInfo(id, name, type, vendor, 0, null, Status.PENDING, "Waiting for reading");
    }

    public SensorInfo current(float[] values) {
        return new SensorInfo(id, name, type, vendor, values.length, values, Status.CURRENT, null);
    }

    public SensorInfo unavailable(String message) {
        return new SensorInfo(id, name, type, vendor, valueCount, null, Status.UNAVAILABLE, message);
    }

    public SensorInfo error(String message) {
        return new SensorInfo(id, name, type, vendor, valueCount, null, Status.ERROR, message);
    }

    public String valuesText() {
        if (values == null) return message;
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) output.append("  ");
            output.append(String.format(Locale.US, "[%d] %.4f", i + 1, values[i]));
        }
        return output.toString();
    }

    private static String typeName(Sensor sensor) {
        String stringType = sensor.getStringType();
        return stringType == null ? "Type " + sensor.getType() : stringType;
    }
}
