package com.example.watchtest.uii;

import android.content.Context;

/** The system's own fall event, and the manual alert path. Passive alerts of every kind go through {@link PassiveAlerts}. */
public final class FallAlerts {
    private FallAlerts() {}

    /** @return the outbox id of the held-back alert, or null if nothing was queued (not paired, or a duplicate). */
    public static String onFallDetected(Context c, long eventTimeMs) {
        return PassiveAlerts.report(c, Payloads.FALL, Payloads.TRIGGER_DEVICE, null, eventTimeMs);
    }

    public static void cancel(Context c, String outboxItemId) {
        Uii uii = Uii.get(c);
        boolean removed = uii.outbox.remove(outboxItemId);
        Notifications.clearFall(c);
        // Allow a new detection to be reported straight away after the wearer said they were fine.
        if (removed) uii.state.clearLastDetected();
    }

    /** A manual alert (the wearer pressed a button): sent without delay. @return the outbox id, to follow its delivery. */
    public static String sendManual(Context c, String type) {
        Uii uii = Uii.get(c);
        long now = System.currentTimeMillis();
        String json = Payloads.alert(
                Payloads.newClientId(), type, Payloads.TRIGGER_MANUAL, now,
                uii.freshHeartRate(now), null, null, uii.raw.snapshot(now, 10_000, 50));
        Outbox.Item item = uii.outbox.add(Outbox.Kind.ALERT, json, 0);
        UploadScheduler.kickUrgent(c);
        return item.id;
    }
}
