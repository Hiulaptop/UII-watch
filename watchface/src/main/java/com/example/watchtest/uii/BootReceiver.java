package com.example.watchtest.uii;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Resumes monitoring after the watch restarts or the app is updated. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String a = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            Monitoring.start(context, true);
        }
    }
}
