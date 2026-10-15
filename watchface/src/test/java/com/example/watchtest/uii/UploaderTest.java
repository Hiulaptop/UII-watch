package com.example.watchtest.uii;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayList;
import java.util.List;

public class UploaderTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    static final class MemoryStore implements SecretStore {
        String id, tok;
        MemoryStore(String token) { tok = token; }
        @Override public String token() { return tok; }
        @Override public String deviceId() { return id; }
        @Override public void save(String d, String t) { id = d; tok = t; }
        @Override public void clear() { id = null; tok = null; }
    }

    /** Answers from a script, and records the order requests arrive in. */
    static final class FakeApi implements Api {
        final List<String> calls = new ArrayList<>();
        final List<ApiResult.Outcome> script = new ArrayList<>();
        ApiResult.Outcome fallback = ApiResult.Outcome.OK;

        private ApiResult answer(String what) {
            calls.add(what);
            ApiResult.Outcome o = script.isEmpty() ? fallback : script.remove(0);
            return new ApiResult(o, o == ApiResult.Outcome.OK ? 200 : 500, "");
        }
        @Override public ApiResult pair(String code, String name, String model) { return answer("pair"); }
        @Override public ApiResult postSamples(String token, String json) { return answer("S:" + json); }
        @Override public ApiResult postAlert(String token, String json) { return answer("A:" + json); }
        @Override public ApiResult getSosCard(String token) { return answer("C"); }
    }

    private FileOutbox box() throws Exception { return new FileOutbox(tmp.newFolder()); }
    private static void pause() throws InterruptedException { Thread.sleep(3); }

    @Test public void nothingIsSentUntilThePairedWatchHasAToken() throws Exception {
        FileOutbox box = box();
        box.add(Outbox.Kind.ALERT, "x", 0);
        FakeApi api = new FakeApi();
        Uploader.Result r = new Uploader(box, api, new MemoryStore(null)).flush(System.currentTimeMillis());
        assertEquals(Uploader.State.NOT_PAIRED, r.state);
        assertTrue(api.calls.isEmpty());
        assertEquals("the alert is kept for later", 1, box.size());
    }

    @Test public void alertsAreSentBeforeSignalBatchesEvenIfTheyAreNewer() throws Exception {
        FileOutbox box = box();
        box.add(Outbox.Kind.SAMPLES, "s1", 0); pause();
        box.add(Outbox.Kind.SAMPLES, "s2", 0); pause();
        box.add(Outbox.Kind.ALERT, "a1", 0);
        FakeApi api = new FakeApi();
        Uploader.Result r = new Uploader(box, api, new MemoryStore("t")).flush(System.currentTimeMillis());
        assertEquals(List.of("A:a1", "S:s1", "S:s2"), api.calls);
        assertEquals(Uploader.State.DONE, r.state);
        assertEquals(3, r.sent);
        assertEquals(0, box.size());
    }

    @Test public void aFailedRequestStopsTheRunAndKeepsEverythingNotYetAccepted() throws Exception {
        FileOutbox box = box();
        box.add(Outbox.Kind.ALERT, "a1", 0); pause();
        box.add(Outbox.Kind.ALERT, "a2", 0); pause();
        box.add(Outbox.Kind.SAMPLES, "s1", 0);
        FakeApi api = new FakeApi();
        api.script.add(ApiResult.Outcome.OK);
        api.script.add(ApiResult.Outcome.RETRY);
        Uploader.Result r = new Uploader(box, api, new MemoryStore("t")).flush(System.currentTimeMillis());
        assertEquals(Uploader.State.RETRY_LATER, r.state);
        assertEquals(1, r.sent);
        assertEquals(2, r.remaining);
        assertEquals("stops at the first failure instead of hammering the server", 2, api.calls.size());
        assertEquals(2, box.size());
    }

    @Test public void aRetryLaterSendsTheSameAlertAgainWithTheSameBody() throws Exception {
        FileOutbox box = box();
        box.add(Outbox.Kind.ALERT, "{\"client_id\":\"w-1\"}", 0);
        FakeApi api = new FakeApi();
        api.script.add(ApiResult.Outcome.RETRY);
        Uploader up = new Uploader(box, api, new MemoryStore("t"));
        up.flush(System.currentTimeMillis());
        up.flush(System.currentTimeMillis());
        assertEquals(List.of("A:{\"client_id\":\"w-1\"}", "A:{\"client_id\":\"w-1\"}"), api.calls);
        assertEquals(0, box.size());
    }

    @Test public void aPayloadTheServerRefusesIsDroppedSoItCannotBlockTheQueue() throws Exception {
        FileOutbox box = box();
        box.add(Outbox.Kind.ALERT, "bad", 0); pause();
        box.add(Outbox.Kind.ALERT, "good", 0);
        FakeApi api = new FakeApi();
        api.script.add(ApiResult.Outcome.REJECTED);
        java.util.List<String> droppedIds = new java.util.ArrayList<>();
        Uploader up = new Uploader(box, api, new MemoryStore("t"));
        up.setDropListener(item -> droppedIds.add(item.json));
        Uploader.Result r = up.flush(System.currentTimeMillis());
        assertEquals(Uploader.State.DONE, r.state);
        assertEquals(1, r.dropped);
        assertEquals("the listener is told which item was dropped", java.util.Arrays.asList("bad"), droppedIds);
        assertEquals(1, r.sent);
        assertEquals(0, box.size());
    }

    @Test public void aRejectedTokenClearsTheCredentialAndStops() throws Exception {
        FileOutbox box = box();
        box.add(Outbox.Kind.ALERT, "a", 0); pause();
        box.add(Outbox.Kind.SAMPLES, "s", 0);
        FakeApi api = new FakeApi();
        api.script.add(ApiResult.Outcome.UNAUTHORIZED);
        MemoryStore store = new MemoryStore("t");
        store.id = "dev";
        Uploader.Result r = new Uploader(box, api, store).flush(System.currentTimeMillis());
        assertEquals(Uploader.State.UNAUTHORIZED, r.state);
        assertNull(store.token());
        assertFalse(store.isPaired());
        assertEquals(1, api.calls.size());
        assertEquals("nothing is lost: both are kept in case the watch is paired again", 2, box.size());
    }

    @Test public void aHeldBackFallIsNotSentUntilItsCancelWindowEnds() throws Exception {
        FileOutbox box = box();
        long now = 1_000_000L;
        box.add(Outbox.Kind.ALERT, "fall", now + 20_000);
        FakeApi api = new FakeApi();
        Uploader up = new Uploader(box, api, new MemoryStore("t"));

        Uploader.Result early = up.flush(now + 5_000);
        assertTrue(api.calls.isEmpty());
        assertEquals(now + 20_000, early.nextDueMs);
        assertEquals(Uploader.State.DONE, early.state);
        assertEquals(1, early.remaining);

        up.flush(now + 20_000);
        assertEquals(List.of("A:fall"), api.calls);
        assertEquals(0, box.size());
    }

    @Test public void cancellingTheHeldBackFallMeansItIsNeverSent() throws Exception {
        FileOutbox box = box();
        long now = 1_000_000L;
        Outbox.Item fall = box.add(Outbox.Kind.ALERT, "fall", now + 20_000);
        assertTrue(box.remove(fall.id));
        FakeApi api = new FakeApi();
        new Uploader(box, api, new MemoryStore("t")).flush(now + 60_000);
        assertTrue(api.calls.isEmpty());
    }

    @Test public void aHeldBackItemDoesNotBlockOthersThatAreDue() throws Exception {
        FileOutbox box = box();
        long now = 1_000_000L;
        box.add(Outbox.Kind.ALERT, "later", now + 30_000); pause();
        box.add(Outbox.Kind.SAMPLES, "now", 0);
        FakeApi api = new FakeApi();
        Uploader.Result r = new Uploader(box, api, new MemoryStore("t")).flush(now);
        assertEquals(List.of("S:now"), api.calls);
        assertEquals(now + 30_000, r.nextDueMs);
    }
}
