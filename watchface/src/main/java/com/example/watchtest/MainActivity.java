package com.example.watchtest;

import android.app.Activity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;

public final class MainActivity extends Activity implements SensorRepository.Listener {
    private static final int BODY_SENSOR_REQUEST = 42;
    private static final int BACKGROUND = Color.rgb(16, 18, 22);
    private static final int PRIMARY = Color.rgb(242, 245, 249);
    private static final int SECONDARY = Color.rgb(173, 181, 192);
    private final SensorStateStore sensorStates = new SensorStateStore();
    private SensorRepository repository;
    private LinearLayout content;
    private Integer selectedId;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        repository = new AndroidSensorRepository(this);
        showList(repository.discover());
    }

    @Override protected void onStart() {
        super.onStart();
        if (requiresBodySensorPermission() && checkSelfPermission(Manifest.permission.BODY_SENSORS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BODY_SENSORS}, BODY_SENSOR_REQUEST);
        } else {
            repository.start(this);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == BODY_SENSOR_REQUEST) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) repository.start(this);
            else {
                repository.start(this);
                showPermissionMessage();
            }
        }
    }

    @Override protected void onStop() {
        repository.stop();
        super.onStop();
    }

    @Override public void onSensorStateChanged(final SensorInfo info) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                sensorStates.update(info);
                if (selectedId != null && selectedId == info.id) showDetail(info);
                else showList(new ArrayList<>(sensorStates.values()));
            }
        });
    }

    private void showList(Iterable<SensorInfo> sensors) {
        selectedId = null;
        java.util.ArrayList<SensorInfo> discovered = new java.util.ArrayList<>();
        for (SensorInfo info : sensors) discovered.add(info);
        sensorStates.replace(discovered);
        content = base("Sensors");
        if (sensorStates.isEmpty()) {
            content.addView(label("No sensors are available on this device.", 18, PRIMARY));
        } else {
            for (SensorInfo info : sensorStates.values()) addSensorRow(info);
        }
        setContentView(scroll(content));
    }

    private void showDetail(SensorInfo info) {
        selectedId = info.id;
        content = base("Sensor details");
        Button back = button("Back to sensors");
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { showList(new ArrayList<>(sensorStates.values())); }
        });
        content.addView(back);
        content.addView(label(info.name, 22, PRIMARY));
        content.addView(label("Type: " + info.type, 14, SECONDARY));
        content.addView(label("Vendor: " + info.vendor, 14, SECONDARY));
        content.addView(label("Range: " + infoRange(info), 14, SECONDARY));
        content.addView(label("Status: " + statusText(info), 16, PRIMARY));
        content.addView(label(info.valuesText(), 18, PRIMARY));
        setContentView(scroll(content));
    }

    private void addSensorRow(final SensorInfo info) {
        Button row = button(info.name + "\n" + info.type + "\n" + statusText(info) + "\n" + info.valuesText());
        row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        row.setContentDescription(info.name + ", " + statusText(info));
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { showDetail(sensorStates.get(info.id)); }
        });
        content.addView(row);
    }

    private LinearLayout base(String title) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 24, 20, 24);
        root.setBackgroundColor(BACKGROUND);
        root.addView(label(title, 24, PRIMARY));
        return root;
    }

    private ScrollView scroll(View view) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);
        scroll.addView(view, new ViewGroup.LayoutParams(-1, -2));
        return scroll;
    }

    private TextView label(String text, int size, int color) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(size);
        label.setTextColor(color);
        label.setPadding(8, 12, 8, 12);
        return label;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(PRIMARY);
        button.setAllCaps(false);
        button.setPadding(12, 10, 12, 10);
        button.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return button;
    }

    private String statusText(SensorInfo info) {
        switch (info.status) {
            case CURRENT: return "Live: " + info.valuesText();
            case ERROR: return "Error: " + info.message;
            case UNAVAILABLE: return "Unavailable: " + info.message;
            default: return info.message;
        }
    }

    private String infoRange(SensorInfo info) {
        return info.valueCount == 0 ? "Values determined by device" : info.valueCount + " value(s)";
    }

    private boolean requiresBodySensorPermission() {
        for (SensorInfo info : sensorStates.values()) {
            if (info.type.contains("heart_rate")) return true;
        }
        return false;
    }

    private void showPermissionMessage() {
        if (sensorStates.isEmpty()) return;
        content.addView(label("Heart-rate data requires body sensor permission; other sensors remain available.", 14, SECONDARY));
    }
}
