package com.example.watchtest.uii;

import android.content.Context;

/**
 * A passive alert (the watch noticed something, the wearer did not press anything). It is queued at once but held
 * back for the cancel window, so it survives the app being killed and a false alarm can be dismissed.
 */
public final class PassiveAlerts {
    private PassiveAlerts() {}

    /** A detection from the motion detector. */
    public static String onDetected(Context c, MotionDetector.Detection d, long eventTimeMs) {
        return report(c, d.type, Payloads.TRIGGER_ALGORITHM, d.confidence, eventTimeMs);
    }

    /**
     * @return the outbox id of the held-back alert, or null if nothing was queued (not paired, unknown type, or the
     *         same kind was reported a moment ago).
     */
    static String report(Context c, String type, String trigger, Double confidence, long eventTimeMs) {
        Uii uii = Uii.get(c);
        if (!uii.secrets.isPaired()) return null;

        long now = System.currentTimeMillis();
        if (!DetectionGate.allow(type, now, uii.state.lastDetectedMs(type))) return null;
        uii.state.setLastDetectedMs(type, now);

        long detectedAt = Math.min(eventTimeMs > 0 ? eventTimeMs : now, now);
        String json = Payloads.alert(
                Payloads.newClientId(), type, trigger, detectedAt,
                uii.freshHeartRate(now), null, confidence, uii.raw.snapshot(now, 10_000, 50));
        long due = now + Uii.FALL_CANCEL_WINDOW_MS;
        Outbox.Item item = uii.outbox.add(Outbox.Kind.ALERT, json, due);
        // Wear OS does not honour full-screen intents, so the "are you fine?" screen can only take over when the app
        // is already in front. Then it is the only thing shown: a notification on top would cover its button with the
        // system alert chip. Otherwise the notification is what the wearer sees (and the prompt posts it if it is left).
        if (com.example.watchtest.ui.UiVisibility.visible) {
            c.startActivity(com.example.watchtest.ui.FallPromptActivity.intent(c, item.id, due, type));
        } else {
            Notifications.showFall(c, item.id, type);
        }
        Haptics.alert(c, type);
        uii.flushAt(due);                 // precise, while this process is alive
        UploadScheduler.kickAt(c, due);   // fallback if the process is killed before then
        return item.id;
    }
}
