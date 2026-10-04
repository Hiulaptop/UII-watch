package com.example.watchtest.uii;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** "Tôi ổn": removes the held-back fall alert from the outbox so it is never sent. */
public final class CancelFallReceiver extends BroadcastReceiver {
    public static final String EXTRA_ID = "outbox_id";

    @Override public void onReceive(Context context, Intent intent) {
        String id = intent.getStringExtra(EXTRA_ID);
        if (id != null) FallAlerts.cancel(context, id);
    }
}
