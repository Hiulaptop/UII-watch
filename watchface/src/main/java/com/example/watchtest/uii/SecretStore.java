package com.example.watchtest.uii;

/** Holds the device token issued when the watch was paired. */
public interface SecretStore {
    /** Null when the watch is not paired. */
    String token();

    String deviceId();

    void save(String deviceId, String token);

    void clear();

    default boolean isPaired() {
        return token() != null;
    }
}
