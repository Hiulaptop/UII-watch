package com.example.watchtest.uii;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

public final class Notifications {
    public static final String CHANNEL_FALL = "uii_fall";
    public static final String CHANNEL_MONITOR = "uii_monitor";
    public static final int ID_FALL = 4101;
    public static final int ID_MONITOR = 4102;

    private Notifications() {}

    public static void ensureChannels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel fall = new NotificationChannel(CHANNEL_FALL, "Té ngã", NotificationManager.IMPORTANCE_HIGH);
        fall.setDescription("Báo khi phát hiện té ngã, cho bạn 20 giây để hủy");
        fall.enableVibration(true);
        nm.createNotificationChannel(fall);
        NotificationChannel mon = new NotificationChannel(CHANNEL_MONITOR, "Đang theo dõi", NotificationManager.IMPORTANCE_LOW);
        mon.setDescription("Hiển thị khi UII đang theo dõi chuyển động");
        nm.createNotificationChannel(mon);
    }

    public static Notification monitoring(Context c) {
        ensureChannels(c);
        return new Notification.Builder(c, CHANNEL_MONITOR)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentTitle(c.getString(com.example.watchtest.R.string.monitoring_notification_title))
                .setOngoing(true)
                .build();
    }

    /** The wearer can cancel from the notification itself; no activity has to be launched. */
    public static void showFall(Context c, String outboxItemId) {
        ensureChannels(c);
        Intent cancel = new Intent(c, CancelFallReceiver.class).putExtra(CancelFallReceiver.EXTRA_ID, outboxItemId);
        PendingIntent cancelPi = PendingIntent.getBroadcast(c, ID_FALL, cancel, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        // Tapping the notification body only opens the app. Cancelling a real fall must be a deliberate tap on "Tôi ổn".
        PendingIntent openPi = PendingIntent.getActivity(c, ID_FALL, new Intent(c, com.example.watchtest.ui.UiiActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long seconds = Uii.FALL_CANCEL_WINDOW_MS / 1000;
        Notification n = new Notification.Builder(c, CHANNEL_FALL)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(c.getString(com.example.watchtest.R.string.fall_notification_title))
                .setContentText(c.getString(com.example.watchtest.R.string.fall_notification_text, (int) seconds))
                .setCategory(Notification.CATEGORY_ALARM)
                .setTimeoutAfter(Uii.FALL_CANCEL_WINDOW_MS)
                .setContentIntent(openPi)
                .addAction(new Notification.Action.Builder(null, c.getString(com.example.watchtest.R.string.fall_ok), cancelPi).build())
                .build();
        notifyIfAllowed(c, ID_FALL, n);
    }

    public static void clearFall(Context c) {
        c.getSystemService(NotificationManager.class).cancel(ID_FALL);
    }

    private static void notifyIfAllowed(Context c, int id, Notification n) {
        // Without the notification permission the fall is still reported after the window; the wearer just cannot cancel.
        if (c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            c.getSystemService(NotificationManager.class).notify(id, n);
        }
    }
}
