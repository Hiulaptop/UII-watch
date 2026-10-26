package com.example.watchtest.uii;

/** The calls the watch makes to the UII server. */
public interface Api {
    ApiResult pair(String code, String name, String model);

    ApiResult postSamples(String token, String json);

    ApiResult postAlert(String token, String json);

    /** The reduced SOS profile shown on the watch after an alert (see the web app's lib/sos/watch-card.ts). */
    ApiResult getSosCard(String token);

    /** A later position of an alert that was already sent (see {@link Payloads#location}). */
    ApiResult postAlertLocation(String token, String json);

    /** Tells the server this watch is being disconnected, so it leaves the account's device list. */
    ApiResult unpair(String token);
}
