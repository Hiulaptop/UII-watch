package com.example.watchtest.uii;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import java.util.List;

/**
 * Foreground service that summarises motion. The accelerometer is read in hardware-batched mode, and only a few
 * numbers per window are kept, so the radio and the CPU are not woken for every reading.
 */
public final class MotionService extends Service implements SensorEventListener {
    // ---- Sampling configuration. These three numbers decide most of the app's battery cost; change them only with
    // ---- measurements (docs/validation, tools/measure-battery.ps1) and update the validation record when you do.

    /** Length of one summary row sent to the server. Longer windows mean fewer rows and fewer wake-ups. */
    public static final long WINDOW_MS = 10_000;
    /**
     * Accelerometer sampling period: 20 000 us = 50 Hz. Chosen so the raw burst attached to a fall alert is detailed
     * enough for a person to judge afterwards. A lower rate (for example 25 Hz = 40 000 us) is the first thing to try
     * if the battery budget is missed.
     */
    public static final int SAMPLING_US = 20_000;
    /**
     * How long the sensor hub may hold readings before waking the CPU to deliver them. Larger values save power
     * but delay the summary; 2 s is far below the 10 s window, so the rows are not affected.
     */
    public static final int MAX_LATENCY_US = 2_000_000;
    private static final long TICK_MS = 30_000;

    private final WindowAggregator aggregator = new WindowAggregator(WINDOW_MS);
    /** 2 s window, 1 s hop: short enough to see a few seconds of rhythmic shaking, long enough for a 0.5 Hz bin. */
    private final FeatureWindower windower = new FeatureWindower(2_000, 1_000);
    private SensorManager sensors;
    private HandlerThread thread;
    private Handler handler;
    private boolean running;
    private long bootOffsetMs;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            long now = System.currentTimeMillis();
            publish(aggregator.drain(now), now);
            if (running) handler.postDelayed(this, TICK_MS);
        }
    };

    public static void start(Context c) {
        c.startForegroundService(new Intent(c, MotionService.class));
    }

    public static void stop(Context c) {
        c.stopService(new Intent(c, MotionService.class));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Uii uii = Uii.get(this);
        try {
            startForeground(Notifications.ID_MONITOR, Notifications.monitoring(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH);
        } catch (RuntimeException e) {
            // Permission revoked between the check and now: stop quietly instead of crashing the app.
            uii.state.setMotionStatus("bị từ chối: " + e.getClass().getSimpleName());
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!uii.secrets.isPaired()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!running) {
            running = true;
            bootOffsetMs = System.currentTimeMillis() - SystemClock.elapsedRealtime();
            thread = new HandlerThread("uii-motion");
            thread.start();
            handler = new Handler(thread.getLooper());
            sensors = getSystemService(SensorManager.class);
            Sensor accel = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (accel != null) sensors.registerListener(this, accel, SAMPLING_US, MAX_LATENCY_US, handler);
            handler.postDelayed(tick, TICK_MS);
        }
        UploadScheduler.ensurePeriodic(this);
        return START_STICKY;
    }

    @Override public void onSensorChanged(SensorEvent e) {
        // event.timestamp is nanoseconds since boot; convert to wall-clock time for the server.
        long t = bootOffsetMs + e.timestamp / 1_000_000L;
        float x = e.values[0], y = e.values[1], z = e.values[2];
        Uii uii = Uii.get(this);
        uii.raw.add(t, x, y, z);
        FeatureWindower.Row row = windower.add(t, x, y, z);
        if (row != null) {
            uii.lastFeatures = row.f;
            detect(row, uii);
        }
        publish(aggregator.addAccel(t, Math.sqrt(x * x + y * y + z * z)), System.currentTimeMillis());
    }

    /** A faulty detector must not take the motion service down with it. */
    private void detect(FeatureWindower.Row row, Uii uii) {
        try {
            MotionDetector.Detection d = DetectorHolder.get().onFeatures(
                    row.tMs, row.f, uii.freshHeartRate(System.currentTimeMillis()));
            if (d != null) PassiveAlerts.onDetected(this, d, row.tMs);
        } catch (RuntimeException e) {
            Log.w("UII", "detector failed: " + e);
        }
    }

    private void publish(List<SampleWindow> windows, long now) {
        Uii uii = Uii.get(this);
        if (uii.sampleSink.add(windows, now, battery())) UploadScheduler.kick(this);
    }

    private Integer battery() {
        BatteryManager bm = getSystemService(BatteryManager.class);
        int pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        return pct >= 0 && pct <= 100 ? pct : null;
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    @Override public void onDestroy() {
        running = false;
        if (sensors != null) sensors.unregisterListener(this);
        if (handler != null) handler.removeCallbacksAndMessages(null);
        if (thread != null) thread.quitSafely();
        Uii uii = Uii.get(this);
        if (uii.sampleSink.flush(battery())) UploadScheduler.kick(this);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
