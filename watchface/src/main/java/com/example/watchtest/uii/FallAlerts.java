package com.example.watchtest.uii;

import android.content.Context;

/** A fall is queued immediately but held back for the cancel window, so it survives the app being killed. */
public final class FallAlerts {
    private static final long DEDUPE_MS = 2 * 60_000;

    private FallAlerts() {}

    /** @return the outbox id of the held-back alert, or null if nothing was queued (not paired, or a duplicate). */
    public static String onFallDetected(Context c, long eventTimeMs) {
        Uii uii = Uii.get(c);
        if (!uii.secrets.isPaired()) return null;

        long now = System.currentTimeMillis();
        // The system can report one fall more than once; do not warn the family twice.
        if (now - uii.state.lastFallMs() < DEDUPE_MS) return null;
        uii.state.setLastFallMs(now);

        long detectedAt = Math.min(eventTimeMs > 0 ? eventTimeMs : now, now);
        String json = Payloads.alert(
                Payloads.newClientId(), Payloads.FALL, Payloads.TRIGGER_DEVICE, detectedAt,
                uii.freshHeartRate(now), null, null, uii.raw.snapshot(now, 10_000, 50));
        long due = now + Uii.FALL_CANCEL_WINDOW_MS;
        Outbox.Item item = uii.outbox.add(Outbox.Kind.ALERT, json, due);
        // Wear OS does not honour full-screen intents, so the "are you fine?" screen can only take over when the app
        // is already in front. Then it is the only thing shown: a notification on top would cover its button with the
        // system alert chip. Otherwise the notification is what the wearer sees (and the prompt posts it if it is left).
        if (com.example.watchtest.ui.UiVisibility.visible) {
            c.startActivity(com.example.watchtest.ui.FallPromptActivity.intent(c, item.id, due));
        } else {
            Notifications.showFall(c, item.id);
        }
        uii.flushAt(due);                 // precise, while this process is alive
        UploadScheduler.kickAt(c, due);   // fallback if the process is killed before then
        return item.id;
    }

    public static void cancel(Context c, String outboxItemId) {
        Uii uii = Uii.get(c);
        boolean removed = uii.outbox.remove(outboxItemId);
        Notifications.clearFall(c);
        // Allow a new fall to be reported straight away after the wearer said they were fine.
        if (removed) uii.state.setLastFallMs(0);
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
