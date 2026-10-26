package com.example.watchtest.uii;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.CancellationSignal;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Reads the wearer's position around an SOS, following {@link LocationPolicy}. One incident at a time: {@link #begin()}
 * when the wearer picks an SOS type or a detection fires, {@link #bestFix()} when the alert is built, {@link #bind} once
 * the alert is in the outbox, {@link #cancel()} when it is cancelled. Nothing here ever delays an alert, and without a
 * location permission every call does nothing.
 */
public final class LocationTracker {
    private final Context app;
    private final Outbox outbox;
    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();

    private final List<ScheduledFuture<?>> pending = new ArrayList<>();
    private int epoch;
    private boolean active;
    private long startMs;
    private LocationFix best;
    // Set by bind(): which alert later fixes belong to, and what the server already has for it.
    private String clientId;
    private long dueMs;
    private LocationFix sent;
    private int updates;

    public LocationTracker(Context app, Outbox outbox) {
        this.app = app.getApplicationContext();
        this.outbox = outbox;
    }

    public boolean hasPermission() {
        return app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** Starts an incident (milestone L0): seeds with the cached position and asks for a fresh one. */
    public synchronized void begin() {
        stop();
        if (!hasPermission()) return;
        active = true;
        startMs = System.currentTimeMillis();
        best = lastKnown();
        final int mine = epoch;
        schedule(0, mine);
        for (long after : LocationPolicy.FOLLOW_UP_AFTER_MS) schedule(after, mine);
    }

    /** The best position known right now, or null. Never waits. */
    public synchronized LocationFix bestFix() {
        return active ? best : null;
    }

    /**
     * Ties the incident to the alert that was just queued, so later fixes are sent as updates to it.
     * @param fixInAlert the fix that went into the alert itself (may be null)
     * @param dueMs when the alert may be sent; updates are held until then
     */
    public synchronized void bind(String alertClientId, long dueMs, LocationFix fixInAlert) {
        if (!active) return;
        this.clientId = alertClientId;
        this.dueMs = dueMs;
        this.sent = fixInAlert;
        // A fix that arrived between building the alert and now.
        if (LocationPolicy.worthSending(best, sent) && LocationPolicy.goodEnough(best)) send(best);
    }

    /** The alert was cancelled or the watch unpaired: stop reading and drop updates not yet sent. */
    public synchronized void cancel() {
        String id = clientId;
        stop();
        if (id != null) {
            for (Outbox.Item i : outbox.list()) {
                if (i.kind == Outbox.Kind.LOCATION && i.json.contains(id)) outbox.remove(i.id);
            }
        }
    }

    private void stop() {
        epoch++;
        active = false;
        clientId = null;
        sent = null;
        updates = 0;
        for (ScheduledFuture<?> f : pending) f.cancel(false);
        pending.clear();
    }

    private void schedule(long afterMs, int mine) {
        long delay = Math.max(0, startMs + afterMs - System.currentTimeMillis());
        pending.add(exec.schedule(() -> readOnce(fix -> onRead(mine, fix)), delay, TimeUnit.MILLISECONDS));
    }

    private synchronized void onRead(int mine, LocationFix fix) {
        if (mine != epoch || fix == null) return;
        if (LocationPolicy.betterForNow(fix, best)) best = fix;
        if (clientId != null && LocationPolicy.worthSending(fix, sent)) send(fix);
    }

    private void noteBest(int mine, LocationFix fix) {
        synchronized (this) {
            if (mine == epoch && LocationPolicy.betterForNow(fix, best)) best = fix;
        }
    }

    private void send(LocationFix fix) {
        if (updates >= LocationPolicy.MAX_UPDATES) return;
        String json = Payloads.location(clientId, fix);
        if (json == null) return;
        outbox.add(Outbox.Kind.LOCATION, json, dueMs);
        sent = fix;
        updates++;
        UploadScheduler.kick(app);
    }

    private LocationFix lastKnown() {
        LocationManager lm = app.getSystemService(LocationManager.class);
        if (lm == null) return null;
        long now = System.currentTimeMillis();
        LocationFix pick = null;
        for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER}) {
            try {
                LocationFix f = fix(lm.getLastKnownLocation(p));
                if (LocationPolicy.isFresh(f, now) && LocationPolicy.betterForNow(f, pick)) pick = f;
            } catch (SecurityException | IllegalArgumentException ignored) {
                // No permission, or the watch has no such provider.
            }
        }
        return pick;
    }

    /**
     * One read: asks GPS and the network provider at once (the network answers fast and coarse, GPS slowly and finely),
     * ends at the first fix that is good enough or after the timeout, and reports the most accurate fix seen.
     */
    private void readOnce(Consumer<LocationFix> done) {
        final int mine;
        synchronized (this) { mine = epoch; }
        LocationManager lm = app.getSystemService(LocationManager.class);
        if (lm == null || !hasPermission()) { done.accept(null); return; }

        final AtomicBoolean finished = new AtomicBoolean(false);
        final CancellationSignal cancel = new CancellationSignal();
        final LocationFix[] top = new LocationFix[1];
        final AtomicInteger waiting = new AtomicInteger(0);
        final Runnable finish = () -> {
            if (!finished.compareAndSet(false, true)) return;
            cancel.cancel();
            LocationFix result;
            synchronized (top) { result = top[0]; }
            done.accept(result);
        };

        int started = 0;
        for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            try {
                if (!lm.isProviderEnabled(provider)) continue;
                waiting.incrementAndGet();
                started++;
                lm.getCurrentLocation(provider, cancel, exec, (Location loc) -> {
                    if (finished.get()) return;
                    LocationFix f = fix(loc);
                    if (f != null && f.isValid()) {
                        synchronized (top) {
                            if (top[0] == null || accuracy(f) < accuracy(top[0])) top[0] = f;
                        }
                        noteBest(mine, f);
                        if (LocationPolicy.goodEnough(f)) { finish.run(); return; }
                    }
                    if (waiting.decrementAndGet() <= 0) finish.run();
                });
            } catch (SecurityException | IllegalArgumentException e) {
                waiting.decrementAndGet();
                started--;
            }
        }
        if (started == 0) { done.accept(null); return; }
        exec.schedule(finish, LocationPolicy.READ_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private static double accuracy(LocationFix f) {
        return f.accuracyM == null ? Double.POSITIVE_INFINITY : f.accuracyM;
    }

    private static LocationFix fix(Location l) {
        if (l == null) return null;
        Double acc = l.hasAccuracy() ? Double.valueOf(l.getAccuracy()) : null;
        return new LocationFix(l.getLatitude(), l.getLongitude(), acc, l.getTime());
    }
}
