package com.example.watchtest.uii;

import android.content.Context;
import android.content.pm.PackageManager;

/** Starts and stops everything that measures and reports: the motion service, Health Services and the upload schedule. */
public final class Monitoring {
    private Monitoring() {}

    /**
     * Android refuses a "health" foreground service unless one of these is granted, and the refusal would crash the
     * service. So the service is only started once the wearer has granted a health permission.
     */
    public static boolean canRunMotionService(Context c, boolean fromBackground) {
        for (String p : Permissions.motionServiceGate(android.os.Build.VERSION.SDK_INT, fromBackground)) {
            if (c.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED) return true;
        }
        return false;
    }

    public static void start(Context c) {
        start(c, false);
    }

    /** @param fromBackground true when nothing of the app is on screen (boot, app update). */
    public static void start(Context c, boolean fromBackground) {
        Uii uii = Uii.get(c);
        if (!uii.secrets.isPaired()) return;
        Notifications.ensureChannels(c);
        PassiveRegistration.register(c);
        if (canRunMotionService(c, fromBackground)) {
            try {
                MotionService.start(c);
                uii.state.setMotionStatus("bật");
            } catch (RuntimeException e) {
                uii.state.setMotionStatus("lỗi " + e.getClass().getSimpleName());
            }
        } else {
            // Heart rate and falls can still arrive through Health Services; motion waits for the permission.
            uii.state.setMotionStatus("chờ cấp quyền");
        }
        UploadScheduler.ensurePeriodic(c);
        UploadScheduler.kick(c);
    }

    public static void stop(Context c) {
        MotionService.stop(c);
        PassiveRegistration.unregister(c);
        UploadScheduler.cancelAll(c);
    }
}
