package com.example.watchtest.uii;

import java.util.List;

/** Requests waiting to be sent. Persisted, so an alert survives a lost connection or a killed process. */
public interface Outbox {
    enum Kind { ALERT, SAMPLES }

    final class Item {
        public final String id;
        public final Kind kind;
        public final String json;
        public final long createdAtMs;
        /** Not sent before this time. Used for the cancel window after a detected fall. */
        public final long notBeforeMs;

        public Item(String id, Kind kind, String json, long createdAtMs, long notBeforeMs) {
            this.id = id;
            this.kind = kind;
            this.json = json;
            this.createdAtMs = createdAtMs;
            this.notBeforeMs = notBeforeMs;
        }
    }

    Item add(Kind kind, String json, long notBeforeMs);

    /** Oldest first. */
    List<Item> list();

    /** Removing an item also cancels it if it has not been sent yet. */
    boolean remove(String id);

    int size();
}
