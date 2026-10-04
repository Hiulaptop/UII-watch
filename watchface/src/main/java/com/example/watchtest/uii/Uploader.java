package com.example.watchtest.uii;

import java.util.ArrayList;
import java.util.List;

/** Sends what is waiting in the outbox. Alerts always go before signal batches. */
public final class Uploader {
    public enum State {
        /** Not paired, so nothing can be sent. */
        NOT_PAIRED,
        /** Everything that was due has been handled. */
        DONE,
        /** A request failed for a reason that may pass; try again later. */
        RETRY_LATER,
        /** The server no longer accepts this watch's token. The token has been cleared. */
        UNAUTHORIZED
    }

    public static final class Result {
        public final State state;
        public final int sent;
        public final int dropped;
        public final int remaining;
        /** Earliest time a held-back item (a fall waiting out its cancel window) becomes due; 0 if none. */
        public final long nextDueMs;

        Result(State state, int sent, int dropped, int remaining, long nextDueMs) {
            this.state = state;
            this.sent = sent;
            this.dropped = dropped;
            this.remaining = remaining;
            this.nextDueMs = nextDueMs;
        }
    }

    /** Told about an item the server will never accept, which is removed from the queue so it cannot block the rest. */
    public interface DropListener { void onDropped(Outbox.Item item); }

    private final Outbox outbox;
    private final Api api;
    private final SecretStore store;
    private volatile DropListener dropListener;

    public void setDropListener(DropListener l) { this.dropListener = l; }

    public Uploader(Outbox outbox, Api api, SecretStore store) {
        this.outbox = outbox;
        this.api = api;
        this.store = store;
    }

    public synchronized Result flush(long nowMs) {
        String token = store.token();
        if (token == null) return new Result(State.NOT_PAIRED, 0, 0, outbox.size(), 0);

        List<Outbox.Item> ordered = new ArrayList<>();
        List<Outbox.Item> all = outbox.list();
        for (Outbox.Item i : all) if (i.kind == Outbox.Kind.ALERT) ordered.add(i);
        for (Outbox.Item i : all) if (i.kind == Outbox.Kind.SAMPLES) ordered.add(i);

        int sent = 0;
        int dropped = 0;
        long nextDue = 0;
        for (Outbox.Item item : ordered) {
            if (item.notBeforeMs > nowMs) {
                nextDue = nextDue == 0 ? item.notBeforeMs : Math.min(nextDue, item.notBeforeMs);
                continue;
            }
            ApiResult r = item.kind == Outbox.Kind.ALERT ? api.postAlert(token, item.json) : api.postSamples(token, item.json);
            switch (r.outcome) {
                case OK:
                    outbox.remove(item.id);
                    sent++;
                    break;
                case REJECTED:
                    // Retrying a payload the server refuses would only block everything behind it.
                    outbox.remove(item.id);
                    dropped++;
                    DropListener l = dropListener;
                    if (l != null) l.onDropped(item);
                    break;
                case UNAUTHORIZED:
                    store.clear();
                    return new Result(State.UNAUTHORIZED, sent, dropped, outbox.size(), nextDue);
                case RETRY:
                default:
                    return new Result(State.RETRY_LATER, sent, dropped, outbox.size(), nextDue);
            }
        }
        return new Result(State.DONE, sent, dropped, outbox.size(), nextDue);
    }
}
