package com.example.watchtest;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AndroidSensorRepository implements SensorRepository, SensorEventListener {
    private final SensorManager manager;
    private final Map<Integer, Sensor> sensors = new LinkedHashMap<>();
    private final Map<Integer, SensorInfo> states = new LinkedHashMap<>();
    private Listener listener;

    public AndroidSensorRepository(Context context) {
        manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    }

    @Override public List<SensorInfo> discover() {
        sensors.clear();
        states.clear();
        if (manager == null) return new ArrayList<>();
        for (Sensor sensor : manager.getSensorList(Sensor.TYPE_ALL)) {
            sensors.put(sensor.getId(), sensor);
            states.put(sensor.getId(), SensorInfo.pending(sensor));
        }
        return new ArrayList<>(states.values());
    }

    @Override public void start(Listener listener) {
        this.listener = listener;
        for (Sensor sensor : sensors.values()) {
            try {
                if (!manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)) {
                    publish(states.get(sensor.getId()).error("Could not register sensor"));
                }
            } catch (SecurityException exception) {
                publish(states.get(sensor.getId()).error("Permission required"));
            } catch (RuntimeException exception) {
                publish(states.get(sensor.getId()).error("Sensor unavailable"));
            }
        }
    }

    @Override public void stop() {
        if (manager != null) manager.unregisterListener(this);
        listener = null;
    }

    @Override public void onSensorChanged(SensorEvent event) {
        SensorInfo state = states.get(event.sensor.getId());
        if (state != null) publish(state.current(event.values));
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            SensorInfo state = states.get(sensor.getId());
            if (state != null) publish(state.unavailable("Sensor data is unreliable"));
        }
    }

    private void publish(SensorInfo state) {
        states.put(state.id, state);
        if (listener != null) listener.onSensorStateChanged(state);
    }
}
