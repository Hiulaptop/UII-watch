package com.example.watchtest.uii;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** HTTP client for the UII device API. No third-party dependencies, so it can be tested on the JVM. */
public final class ApiClient implements Api {
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 20_000;
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;

    /** Optional sink for network error descriptions (set by the app in debug builds). */
    public static volatile java.util.function.Consumer<String> errorLog;

    private final String base;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    /** Plain http is accepted only when {@code allowCleartext} is set (debug builds talking to a local server). */
    public ApiClient(String baseUrl, boolean allowCleartext) {
        this(baseUrl, allowCleartext, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
    }

    ApiClient(String baseUrl, boolean allowCleartext, int connectTimeoutMs, int readTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        String b = baseUrl.trim().replaceAll("/+$", "");
        boolean https = b.startsWith("https://");
        boolean http = b.startsWith("http://");
        if (!https && !(http && allowCleartext)) {
            throw new IllegalArgumentException("Server URL must use https");
        }
        this.base = b;
    }

    @Override public ApiResult pair(String code, String name, String model) {
        try {
            JSONObject o = new JSONObject();
            o.put("code", code);
            if (name != null) o.put("name", name);
            if (model != null) o.put("model", model);
            o.put("platform", "wearos");
            return post("/api/device/pair", null, o.toString());
        } catch (JSONException e) {
            return new ApiResult(ApiResult.Outcome.REJECTED, -1, "");
        }
    }

    @Override public ApiResult postSamples(String token, String json) {
        return post("/api/device/samples", token, json);
    }

    @Override public ApiResult postAlert(String token, String json) {
        return post("/api/device/alerts", token, json);
    }

    private ApiResult post(String path, String token, String json) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(base + path).openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(connectTimeoutMs);
            c.setReadTimeout(readTimeoutMs);
            c.setInstanceFollowRedirects(false);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("Accept", "application/json");
            if (token != null) c.setRequestProperty("Authorization", "Bearer " + token);

            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(body.length);
            try (OutputStream os = c.getOutputStream()) {
                os.write(body);
            }
            int status = c.getResponseCode();
            InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
            return new ApiResult(ApiResult.classify(status), status, read(in));
        } catch (IOException e) {
            // Includes timeouts and a missing connection: nothing reached the server, so it is safe to retry.
            java.util.function.Consumer<String> log = errorLog;
            // Only the error type and message are logged, never the request or response body.
            if (log != null) log.accept(path + " failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return new ApiResult(ApiResult.Outcome.RETRY, -1, "");
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) return "";
        try (InputStream is = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0 && out.size() < MAX_RESPONSE_BYTES) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8);
        }
    }
}
