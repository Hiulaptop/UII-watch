package com.example.watchtest.uii;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.Random;

/**
 * Debug builds only. Drives the black-box detector from adb:
 *
 *   fire one detection now:   adb shell am broadcast -n net.studev.uii/com.example.watchtest.uii.DebugDetectReceiver
 *                               -a net.studev.uii.DEBUG_DETECT --es type seizure
 *   random detector on:       ... -a net.studev.uii.DEBUG_DETECT --ef p 0.002
 *   back to the no-op one:    ... -a net.studev.uii.DEBUG_DETECT --ez off true
 *
 * {@code type} is fall, seizure or pre_seizure. Add {@code --ez force true} to skip the duplicate guard.
 */
public final class DebugDetectReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!"net.studev.uii.DEBUG_DETECT".equals(intent.getAction())) return;
        Uii uii = Uii.get(context);

        if (intent.getBooleanExtra("off", false)) {
            DetectorHolder.set(null);
            Log.i("UII", "debug detector: off");
            setResultData("off");
            return;
        }
        if (intent.hasExtra("p")) {
            double p = intent.getFloatExtra("p", 0f);
            DetectorHolder.set(new RandomDetector(new Random(), p));
            Log.i("UII", "debug detector: random p=" + p);
            setResultData("random p=" + p);
            return;
        }
        String type = intent.getStringExtra("type");
        if (!DetectionGate.isKnownType(type)) {
            setResultData("unknown type (use fall, seizure or pre_seizure)");
            return;
        }
        if (intent.getBooleanExtra("force", false)) uii.state.clearLastDetected();
        String queued = PassiveAlerts.onDetected(context, new MotionDetector.Detection(type, 0.9), System.currentTimeMillis());
        Log.i("UII", "debug detect " + type + ": queued=" + (queued != null));
        setResultData(queued == null ? "not-queued" : "queued");
    }
}
