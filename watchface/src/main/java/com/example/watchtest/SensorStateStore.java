package com.example.watchtest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Keeps independent sensor states so one device failure does not erase other readings. */
public final class SensorStateStore {
    private final Map<Integer, SensorInfo> states = new LinkedHashMap<>();

    public void replace(Collection<SensorInfo> discovered) {
        states.clear();
        for (SensorInfo info : discovered) states.put(info.id, info);
    }

    public void update(SensorInfo info) {
        if (states.containsKey(info.id)) states.put(info.id, info);
    }

    public List<SensorInfo> values() {
        return new ArrayList<>(states.values());
    }

    public boolean isEmpty() {
        return states.isEmpty();
    }

    public SensorInfo get(int id) {
        return states.get(id);
    }
}
