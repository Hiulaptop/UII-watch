package com.example.watchtest.uii;

/** What to do next with a request, decided from the HTTP status. */
public final class ApiResult {
    public enum Outcome {
        /** Accepted by the server: remove it from the queue. */
        OK,
        /** Network trouble, throttling or a server error: keep it and try again later. */
        RETRY,
        /** The device token is no longer valid: the watch must be paired again. */
        UNAUTHORIZED,
        /** The server will never accept this payload: drop it so it cannot block the queue. */
        REJECTED
    }

    public final Outcome outcome;
    public final int status;
    public final String body;

    public ApiResult(Outcome outcome, int status, String body) {
        this.outcome = outcome;
        this.status = status;
        this.body = body == null ? "" : body;
    }

    public static Outcome classify(int status) {
        if (status >= 200 && status < 300) return Outcome.OK;
        if (status == 401) return Outcome.UNAUTHORIZED;
        if (status == 408 || status == 429 || status >= 500) return Outcome.RETRY;
        return Outcome.REJECTED;
    }
}
