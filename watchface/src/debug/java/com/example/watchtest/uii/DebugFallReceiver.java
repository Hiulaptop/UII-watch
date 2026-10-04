package com.example.watchtest.uii;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Debug builds only. Runs the same code a system fall event runs, so alert timing can be measured without anyone
 * falling:
 *
 *   adb shell am broadcast -n net.studev.uii/com.example.watchtest.uii.DebugFallReceiver -a net.studev.uii.DEBUG_FALL
 *
 * Add `--ez force true` to skip the two-minute duplicate guard between repeated tests.
 * This class lives in the debug source set and is not part of a release package.
 */
public final class DebugFallReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!"net.studev.uii.DEBUG_FALL".equals(intent.getAction())) return;
        Uii uii = Uii.get(context);
        if (intent.getBooleanExtra("force", false)) uii.state.setLastFallMs(0);
        String queued = FallAlerts.onFallDetected(context, System.currentTimeMillis());
        Log.i("UII", "debug fall: queued=" + (queued != null) + " at " + System.currentTimeMillis());
        setResultData(queued == null ? "not-queued" : "queued");
    }
}
