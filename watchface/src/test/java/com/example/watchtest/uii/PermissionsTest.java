package com.example.watchtest.uii;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PermissionsTest {
    @Test public void wearOs5AsksForBodySensors() {
        for (int sdk : new int[]{34, 35}) {
            assertEquals("android.permission.BODY_SENSORS", Permissions.heartRate(sdk));
            assertTrue(Permissions.needed(sdk).contains("android.permission.BODY_SENSORS"));
            assertFalse(Permissions.needed(sdk).contains(Permissions.READ_HEART_RATE));
        }
    }

    @Test public void wearOs6AsksForTheHealthPermissionAndNeverForBodySensors() {
        for (int sdk : new int[]{36, 37, 40}) {
            assertEquals(Permissions.READ_HEART_RATE, Permissions.heartRate(sdk));
            assertFalse("a permission that cannot be granted would loop the request screen",
                    Permissions.needed(sdk).contains("android.permission.BODY_SENSORS"));
            assertFalse(Permissions.motionServiceGate(sdk).contains("android.permission.BODY_SENSORS"));
        }
    }

    @Test public void everyLevelAsksForActivityRecognitionAndNotifications() {
        for (int sdk : new int[]{34, 36}) {
            assertTrue(Permissions.needed(sdk).contains("android.permission.ACTIVITY_RECOGNITION"));
            assertTrue(Permissions.needed(sdk).contains("android.permission.POST_NOTIFICATIONS"));
            assertEquals(3, Permissions.needed(sdk).size());
        }
    }

    @Test public void everyPermissionHasAReadableLabel() {
        for (int sdk : new int[]{34, 36}) {
            for (String p : Permissions.needed(sdk)) assertFalse(p, Permissions.label(p).contains("."));
        }
        assertEquals("nhịp tim", Permissions.label(Permissions.READ_HEART_RATE));
        assertEquals("nhịp tim", Permissions.label("android.permission.BODY_SENSORS"));
    }

    @Test public void fromTheBackgroundOnlyActivityRecognitionOpensTheMotionService() {
        for (int sdk : new int[]{34, 36}) {
            assertEquals(java.util.Collections.singletonList("android.permission.ACTIVITY_RECOGNITION"), Permissions.motionServiceGate(sdk, true));
        }
    }

    @Test public void theMotionServiceGateIsHeartRateOrActivityRecognition() {
        assertEquals(2, Permissions.motionServiceGate(34).size());
        assertTrue(Permissions.motionServiceGate(34).contains("android.permission.BODY_SENSORS"));
        assertTrue(Permissions.motionServiceGate(36).contains(Permissions.READ_HEART_RATE));
        assertTrue(Permissions.motionServiceGate(36).contains("android.permission.ACTIVITY_RECOGNITION"));
    }
}
