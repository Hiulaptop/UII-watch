package com.example.watchtest.uii;

import android.content.Context;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/** Strong vibration so a warning or a fall prompt is noticed even when the watch is not being looked at. */
public final class Haptics {
    /** off, on, off, on ... in milliseconds. A seizure warning is longer and repeats, to be felt through movement. */
    static final long[] WARNING = {0, 500, 250, 500, 250, 500, 250, 500};
    static final long[] PROMPT = {0, 400, 200, 400};

    private Haptics() {}

    public static void alert(Context c, String type) {
        try {
            VibratorManager vm = c.getSystemService(VibratorManager.class);
            Vibrator v = vm != null ? vm.getDefaultVibrator() : null;
            if (v == null || !v.hasVibrator()) return;
            long[] pattern = Payloads.PRE_SEIZURE.equals(type) ? WARNING : PROMPT;
            v.vibrate(VibrationEffect.createWaveform(pattern, -1));
        } catch (RuntimeException e) {
            // A vibration problem must never stop the alert itself.
        }
    }
}
