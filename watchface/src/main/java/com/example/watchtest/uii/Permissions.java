package com.example.watchtest.uii;

import android.Manifest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Which runtime permissions the app needs on a given Android level. Pure (the level is passed in) so it can be tested.
 *
 * Heart rate is guarded by BODY_SENSORS up to Wear OS 5 (API 35). Wear OS 6 (API 36) replaced it with the health
 * permission READ_HEART_RATE, and BODY_SENSORS can no longer be granted there, so asking for it would be denied at
 * once and send the permission screen into a request loop.
 */
public final class Permissions {
    public static final String READ_HEART_RATE = "android.permission.health.READ_HEART_RATE";
    /** First Android level where heart rate is a health permission. */
    public static final int HEALTH_PERMISSIONS_SDK = 36;

    private Permissions() {}

    public static String heartRate(int sdkInt) {
        return sdkInt >= HEALTH_PERMISSIONS_SDK ? READ_HEART_RATE : Manifest.permission.BODY_SENSORS;
    }

    /** Short Vietnamese name shown on the status screen. */
    public static String label(String permission) {
        switch (permission) {
            case Manifest.permission.BODY_SENSORS:
            case READ_HEART_RATE:
                return "nhịp tim";
            case Manifest.permission.ACTIVITY_RECOGNITION:
                return "vận động";
            case Manifest.permission.POST_NOTIFICATIONS:
                return "thông báo";
            default:
                return permission;
        }
    }

    /** Everything the app asks the wearer for. */
    public static List<String> needed(int sdkInt) {
        return Collections.unmodifiableList(Arrays.asList(
                heartRate(sdkInt),
                Manifest.permission.ACTIVITY_RECOGNITION,
                Manifest.permission.POST_NOTIFICATIONS));
    }

    /**
     * A foreground service of type "health" may only start once one of these is granted; starting it earlier throws
     * a SecurityException that cannot be caught after the fact.
     */
    public static List<String> motionServiceGate(int sdkInt) {
        return motionServiceGate(sdkInt, false);
    }

    /**
     * From the background (boot, app update) only ACTIVITY_RECOGNITION counts: the heart-rate permissions are
     * "while in use" permissions, and Android refuses to start a health service on them while the app is not in
     * front (Android's foreground-service documentation lists BOOT_COMPLETED as such a case).
     */
    public static List<String> motionServiceGate(int sdkInt, boolean fromBackground) {
        if (fromBackground) return Collections.singletonList(Manifest.permission.ACTIVITY_RECOGNITION);
        return Collections.unmodifiableList(Arrays.asList(heartRate(sdkInt), Manifest.permission.ACTIVITY_RECOGNITION));
    }
}
