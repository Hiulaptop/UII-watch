package com.example.watchtest.uii;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PairingTest {
    private static final class Store implements SecretStore {
        String id, tok;
        @Override public String token() { return tok; }
        @Override public String deviceId() { return id; }
        @Override public void save(String d, String t) { id = d; tok = t; }
        @Override public void clear() { id = null; tok = null; }
    }

    private static Api answering(ApiResult r) {
        return new Api() {
            String lastCode;
            @Override public ApiResult pair(String code, String name, String model) { lastCode = code; return r; }
            @Override public ApiResult postSamples(String token, String json) { throw new AssertionError(); }
            @Override public ApiResult postAlert(String token, String json) { throw new AssertionError(); }
            @Override public ApiResult getSosCard(String token) { throw new AssertionError(); }
        };
    }

    @Test public void normalizeAcceptsEightDigitsWithOrWithoutSeparators() {
        assertEquals("48291375", Pairing.normalize("4829 1375"));
        assertEquals("48291375", Pairing.normalize("4829-1375"));
        assertEquals("00000042", Pairing.normalize(" 00000042 "));
        assertNull(Pairing.normalize("1234567"));
        assertNull(Pairing.normalize("123456789"));
        assertNull(Pairing.normalize("1234abcd"));
        assertNull(Pairing.normalize(""));
        assertNull(Pairing.normalize(null));
    }

    @Test public void successStoresTheTokenAndDeviceId() {
        Store s = new Store();
        Api api = answering(new ApiResult(ApiResult.Outcome.OK, 201, "{\"device_id\":\"d-1\",\"token\":\"uii_abc\"}"));
        assertEquals(Pairing.Result.PAIRED, Pairing.pair(api, s, "4829 1375", "Watch", "SM"));
        assertEquals("uii_abc", s.token());
        assertEquals("d-1", s.deviceId());
        assertTrue(s.isPaired());
    }

    @Test public void aBadCodeIsRejectedLocallyWithoutCallingTheServer() {
        Api api = new Api() {
            @Override public ApiResult pair(String c, String n, String m) { throw new AssertionError("must not be called"); }
            @Override public ApiResult postSamples(String t, String j) { throw new AssertionError(); }
            @Override public ApiResult postAlert(String t, String j) { throw new AssertionError(); }
            @Override public ApiResult getSosCard(String t) { throw new AssertionError(); }
        };
        assertEquals(Pairing.Result.INVALID_CODE, Pairing.pair(api, new Store(), "123", "n", "m"));
    }

    @Test public void serverAnswersMapToUserFacingResultsAndNeverStoreAToken() {
        Object[][] cases = {
                {new ApiResult(ApiResult.Outcome.UNAUTHORIZED, 401, "{}"), Pairing.Result.INVALID_CODE},
                {new ApiResult(ApiResult.Outcome.REJECTED, 409, "{}"), Pairing.Result.TOO_MANY_DEVICES},
                {new ApiResult(ApiResult.Outcome.REJECTED, 422, "{}"), Pairing.Result.ERROR},
                {new ApiResult(ApiResult.Outcome.RETRY, 429, "{}"), Pairing.Result.RATE_LIMITED},
                {new ApiResult(ApiResult.Outcome.RETRY, -1, ""), Pairing.Result.NETWORK},
                {new ApiResult(ApiResult.Outcome.RETRY, 503, ""), Pairing.Result.NETWORK},
        };
        for (Object[] c : cases) {
            Store s = new Store();
            assertEquals(c[1], Pairing.pair(answering((ApiResult) c[0]), s, "12345678", "n", "m"));
            assertNull(s.token());
        }
    }

    @Test public void aSuccessResponseThatIsMalformedIsAnErrorAndStoresNothing() {
        for (String body : new String[]{"not json", "{}", "{\"device_id\":\"d\"}", "{\"device_id\":\"d\",\"token\":\"\"}"}) {
            Store s = new Store();
            assertEquals(body, Pairing.Result.ERROR, Pairing.pair(answering(new ApiResult(ApiResult.Outcome.OK, 201, body)), s, "12345678", "n", "m"));
            assertFalse(s.isPaired());
        }
    }
}
