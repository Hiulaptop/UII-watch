package com.example.watchtest.uii;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class ApiClientTest {
    private TinyHttpServer server;

    @Before public void start() throws IOException { server = new TinyHttpServer(); }

    @After public void stop() { server.close(); }

    private ApiClient client() { return new ApiClient(server.baseUrl(), true); }

    @Test public void pairSendsCodeAndPlatformWithoutAuthorization() throws Exception {
        server.status = 201;
        server.response = "{\"device_id\":\"d1\",\"token\":\"uii_x\"}";
        ApiResult r = client().pair("12345678", "Watch", "SM-L310");
        assertEquals(ApiResult.Outcome.OK, r.outcome);
        assertEquals("/api/device/pair", server.path.get());
        assertNull(server.authorization.get());
        assertEquals("application/json", server.contentType.get());
        JSONObject sent = new JSONObject(server.body.get());
        assertEquals("12345678", sent.getString("code"));
        assertEquals("Watch", sent.getString("name"));
        assertEquals("wearos", sent.getString("platform"));
        assertEquals("uii_x", new JSONObject(r.body).getString("token"));
    }

    @Test public void samplesAndAlertsUseBearerTokenAndTheirOwnPaths() {
        client().postSamples("tok-1", "{\"samples\":[]}");
        assertEquals("/api/device/samples", server.path.get());
        assertEquals("Bearer tok-1", server.authorization.get());
        client().postAlert("tok-2", "{\"a\":1}");
        assertEquals("/api/device/alerts", server.path.get());
        assertEquals("Bearer tok-2", server.authorization.get());
        assertEquals("{\"a\":1}", server.body.get());
    }

    @Test public void statusCodesMapToTheRightFollowUp() {
        int[][] cases = {{200, 0}, {201, 0}, {401, 2}, {429, 1}, {408, 1}, {500, 1}, {503, 1}, {400, 3}, {413, 3}, {422, 3}, {409, 3}, {404, 3}};
        ApiResult.Outcome[] by = {ApiResult.Outcome.OK, ApiResult.Outcome.RETRY, ApiResult.Outcome.UNAUTHORIZED, ApiResult.Outcome.REJECTED};
        for (int[] c : cases) {
            server.status = c[0];
            ApiResult r = client().postAlert("t", "{}");
            assertEquals("status " + c[0], by[c[1]], r.outcome);
            assertEquals(c[0], r.status);
        }
    }

    @Test public void errorBodyIsReadWithoutThrowing() {
        server.status = 422;
        server.response = "{\"error\":\"invalid_body\"}";
        ApiResult r = client().postAlert("t", "{}");
        assertEquals(ApiResult.Outcome.REJECTED, r.outcome);
        assertEquals("{\"error\":\"invalid_body\"}", r.body);
    }

    @Test public void unreachableServerIsRetryNotACrash() {
        String url = server.baseUrl();
        server.close();
        ApiResult r = new ApiClient(url, true, 500, 500).postAlert("t", "{}");
        assertEquals(ApiResult.Outcome.RETRY, r.outcome);
        assertEquals(-1, r.status);
    }

    @Test public void slowServerTimesOutAsRetry() {
        server.delayMs = 1500;
        long t0 = System.currentTimeMillis();
        ApiResult r = new ApiClient(server.baseUrl(), true, 500, 300).postAlert("t", "{}");
        assertEquals(ApiResult.Outcome.RETRY, r.outcome);
        assertTrue("should give up well before the server answers", System.currentTimeMillis() - t0 < 1400);
    }

    @Test public void redirectsAreNotFollowed() {
        server.redirectTo = "http://127.0.0.1:1/elsewhere";
        ApiResult r = client().postAlert("t", "{}");
        assertEquals(ApiResult.Outcome.REJECTED, r.outcome);
        assertEquals(302, r.status);
    }

    @Test public void plainHttpNeedsExplicitPermissionAndTrailingSlashIsTolerated() {
        assertThrows(IllegalArgumentException.class, () -> new ApiClient("http://example.com", false));
        assertThrows(IllegalArgumentException.class, () -> new ApiClient("ftp://example.com", true));
        assertThrows(IllegalArgumentException.class, () -> new ApiClient("example.com", true));
        assertNotNull(new ApiClient("https://uii.studev.net/", false));
        new ApiClient(server.baseUrl() + "//", true).postSamples("t", "{}");
        assertEquals("/api/device/samples", server.path.get());
    }

    @Test public void tokenNeverAppearsInTheBodyOrResultOfASamplesCall() {
        ApiResult r = client().postSamples("secret-token-xyz", "{\"samples\":[]}");
        assertFalse(server.body.get().contains("secret-token-xyz"));
        assertFalse(r.body.contains("secret-token-xyz"));
    }
}
