package com.example.watchtest.uii;

import android.os.SystemClock;
import android.util.Log;

import com.example.watchtest.BuildConfig;

import androidx.health.services.client.PassiveListenerService;
import androidx.health.services.client.data.DataPointContainer;
import androidx.health.services.client.data.DataType;
import androidx.health.services.client.data.HealthEvent;
import androidx.health.services.client.data.SampleDataPoint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Called by Health Services even when the app is not open: batched heart-rate readings, and the system's own
 * fall detection. The watch does not run its own fall algorithm; it relays the operating system's event.
 */
public final class HealthPassiveService extends PassiveListenerService {
    private static final String TAG = "UII";

    @Override public void onCreate() {
        super.onCreate();
        if (BuildConfig.DEBUG) Log.i(TAG, "HealthPassiveService created");
    }

    @Override public void onNewDataPointsReceived(DataPointContainer dataPoints) {
        if (BuildConfig.DEBUG) Log.i(TAG, "data points received: " + dataPoints.getDataTypes());
        Uii uii = Uii.get(this);
        if (!uii.secrets.isPaired()) return;

        List<SampleDataPoint<Double>> hrs = dataPoints.getData(DataType.HEART_RATE_BPM);
        if (hrs == null || hrs.isEmpty()) return;

        Instant boot = Instant.ofEpochMilli(System.currentTimeMillis() - SystemClock.elapsedRealtime());
        List<long[]> times = new ArrayList<>(hrs.size());
        List<Double> values = new ArrayList<>(hrs.size());
        for (SampleDataPoint<Double> p : hrs) {
            long t = p.getTimeInstant(boot).toEpochMilli();
            double bpm = p.getValue();
            uii.noteHeartRate(t, bpm);
            times.add(new long[]{t});
            values.add(bpm);
        }
        List<SampleWindow> windows = HeartRateBucketer.bucket(times, values, MotionService.WINDOW_MS);
        if (uii.sampleSink.add(windows, System.currentTimeMillis(), null)) UploadScheduler.kick(this);
    }

    @Override public void onHealthEventReceived(HealthEvent event) {
        if (BuildConfig.DEBUG) Log.i(TAG, "health event received: " + event.getType());
        if (event.getType().equals(HealthEvent.Type.FALL_DETECTED)) {
            String queued = FallAlerts.onFallDetected(this, event.getEventTime().toEpochMilli());
            if (BuildConfig.DEBUG) Log.i(TAG, "fall handled, queued=" + (queued != null));
        }
    }

    @Override public void onPermissionLost() {
        Uii.get(this).state.setPermissionLost(true);
    }
}
