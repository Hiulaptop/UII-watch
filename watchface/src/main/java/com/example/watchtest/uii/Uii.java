package com.example.watchtest.uii;

import android.content.Context;

import com.example.watchtest.BuildConfig;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Process-wide wiring. Everything here is created once and shared by the activities, services and workers. */
public final class Uii {
    /** Time given to the wearer to say "I'm fine" before a detected fall is reported. */
    public static final long FALL_CANCEL_WINDOW_MS = BuildConfig.FALL_CANCEL_WINDOW_MS;
    /** How long a heart-rate reading stays "current" when attached to an alert. */
    public static final long HR_FRESH_MS = 2 * 60_000;

    private static volatile Uii instance;

    public final Api api;
    public final SecretStore secrets;
    public final FileOutbox outbox;
    public final Uploader uploader;
    public final SampleSink sampleSink;
    public final RawBuffer raw = new RawBuffer(600);
    public final UiiState state;

    private volatile double lastHr = Double.NaN;
    private volatile long lastHrMs;
    /** Latest short-window motion features (see {@link MotionFeatures#NAMES}), or null before the first window. */
    public volatile float[] lastFeatures;

    private final Context app;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private final java.util.concurrent.Executor background = Executors.newSingleThreadExecutor();

    private Uii(Context c) {
        app = c.getApplicationContext();
        if (BuildConfig.DEBUG) ApiClient.errorLog = m -> android.util.Log.w("UII", m);
        api = new ApiClient(BuildConfig.API_BASE_URL, BuildConfig.DEBUG);
        secrets = new KeystoreSecretStore(app);
        outbox = new FileOutbox(new File(app.getFilesDir(), "outbox"));
        uploader = new Uploader(outbox, api, secrets);
        sampleSink = new SampleSink(outbox);
        state = new UiiState(app);
        uploader.setDropListener(item -> state.addRejected(item.id));
    }

    public static Uii get(Context c) {
        Uii i = instance;
        if (i == null) {
            synchronized (Uii.class) {
                i = instance;
                if (i == null) instance = i = new Uii(c);
            }
        }
        return i;
    }

    /**
     * Sends what is due at exactly {@code dueMs} from a timer inside the running process. WorkManager's delayed work
     * can run 10-20 seconds late, which is too long for an emergency, so this is the fast path and WorkManager is
     * only the fallback for when the process is killed in the meantime.
     */
    public void flushAt(long dueMs) {
        long delay = Math.max(0, dueMs - System.currentTimeMillis());
        timer.schedule(() -> {
            Uploader.Result r = flushNow();
            if (r.state == Uploader.State.RETRY_LATER) UploadScheduler.kickUrgent(app);
        }, delay, TimeUnit.MILLISECONDS);
    }

    /** Sends what is due and records the outcome for the home screen. */
    public Uploader.Result flushNow() {
        Uploader.Result r = uploader.flush(System.currentTimeMillis());
        state.setLastUploadMs(System.currentTimeMillis());
        state.setLastUploadState(r.state.name() + " sent=" + r.sent + " dropped=" + r.dropped + " left=" + r.remaining);
        // The uploader has already dropped the token; remember why, so the wearer is told instead of seeing a silent reset.
        if (r.state == Uploader.State.UNAUTHORIZED) state.setPairingRevoked(true);
        return r;
    }

    /** The card is re-fetched at most this often unless it is missing, so a profile edit reaches the watch the same day. */
    private static final long SOS_CARD_STALE_MS = 6 * 60 * 60_000L;
    /** While there is no card (no profile yet, or the server was unreachable) look again after this long. */
    private static final long SOS_CARD_RETRY_MS = 10 * 60_000L;

    /** Fetches the SOS card in the background and caches it, so it can be shown with no network after an alert. */
    public void refreshSosCardAsync() {
        if (!secrets.isPaired()) return;
        long now = System.currentTimeMillis();
        long maxAge = state.sosCardJson().isEmpty() ? SOS_CARD_RETRY_MS : SOS_CARD_STALE_MS;
        if (now - state.sosCardFetchedMs() < maxAge) return;
        state.setSosCardFetchedMs(now);   // also throttles retries while the server is unreachable
        // Not the alert timer: a slow request here must never delay sending an alert.
        background.execute(() -> {
            String token = secrets.token();
            if (token == null) return;
            ApiResult r = api.getSosCard(token);
            if (r.outcome != ApiResult.Outcome.OK) return;
            try {
                SosCard card = SosCard.fromResponse(r.body);
                state.setSosCardJson(card == null ? "" : card.toJson());
            } catch (IllegalArgumentException e) {
                // An unreadable answer must not wipe a good cached card.
            }
        });
    }

    /** The cached SOS card, or null when the wearer has none (or it has not been fetched yet). */
    public SosCard sosCard() {
        return SosCard.fromJson(state.sosCardJson());
    }

    public void noteHeartRate(long tMs, double bpm) {
        if (tMs >= lastHrMs) {
            lastHr = bpm;
            lastHrMs = tMs;
        }
    }

    /** The latest heart rate if it is recent enough to describe "now", else null. */
    public Double freshHeartRate(long nowMs) {
        return !Double.isNaN(lastHr) && nowMs - lastHrMs <= HR_FRESH_MS ? Double.valueOf(lastHr) : null;
    }
}
