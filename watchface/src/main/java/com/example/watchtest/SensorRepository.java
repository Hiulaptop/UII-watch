package com.example.watchtest;

import java.util.List;

public interface SensorRepository {
    interface Listener {
        void onSensorStateChanged(SensorInfo info);
    }

    List<SensorInfo> discover();
    void start(Listener listener);
    void stop();
}
