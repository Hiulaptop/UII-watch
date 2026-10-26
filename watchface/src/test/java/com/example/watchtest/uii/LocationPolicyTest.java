package com.example.watchtest.uii;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

public class LocationPolicyTest {
    private static LocationFix fix(double acc, long t) { return new LocationFix(10.77, 106.69, acc, t); }

    @Test public void onlyFourMilestonesAndNothingAfterFiveMinutes() {
        assertEquals(2, LocationPolicy.FOLLOW_UP_AFTER_MS.length);
        assertEquals(2 * 60_000L, LocationPolicy.FOLLOW_UP_AFTER_MS[0]);
        assertEquals(5 * 60_000L, LocationPolicy.FOLLOW_UP_AFTER_MS[1]);
    }

    @Test public void aFixIsGoodEnoughWhenAccurateToFiftyMetres() {
        assertTrue(LocationPolicy.goodEnough(fix(50, 1)));
        assertFalse(LocationPolicy.goodEnough(fix(51, 1)));
        assertFalse(LocationPolicy.goodEnough(new LocationFix(1, 1, null, 1)));
        assertFalse(LocationPolicy.goodEnough(null));
    }

    @Test public void theCachedPositionIsUsedOnlyWhileFresh() {
        long now = 1_000_000_000L;
        assertTrue(LocationPolicy.isFresh(fix(10, now - 9 * 60_000L), now));
        assertFalse(LocationPolicy.isFresh(fix(10, now - 11 * 60_000L), now));
        assertFalse(LocationPolicy.isFresh(null, now));
    }

    @Test public void aMuchNewerFixWinsOtherwiseTheMoreAccurateOne() {
        LocationFix old = fix(5, 1_000_000);
        assertTrue("clearly newer", LocationPolicy.betterForNow(fix(500, 1_000_000 + 61_000), old));
        assertFalse("clearly older", LocationPolicy.betterForNow(fix(1, 1_000_000 - 61_000), old));
        assertTrue("same moment, more accurate", LocationPolicy.betterForNow(fix(3, 1_000_000 + 5_000), old));
        assertFalse("same moment, less accurate", LocationPolicy.betterForNow(fix(80, 1_000_000 + 5_000), old));
        assertTrue("nothing yet", LocationPolicy.betterForNow(old, null));
        assertFalse("invalid fix", LocationPolicy.betterForNow(new LocationFix(91, 0, 5.0, 1), null));
    }

    @Test public void anUpdateIsSentOnlyIfItBeatsWhatTheServerHas() {
        assertTrue(LocationPolicy.worthSending(fix(500, 2), fix(5, 1)));
        assertFalse(LocationPolicy.worthSending(fix(5, 1), fix(500, 2)));
        assertTrue(LocationPolicy.worthSending(fix(5, 2), fix(500, 2)));
        assertFalse(LocationPolicy.worthSending(fix(500, 2), fix(5, 2)));
        assertTrue(LocationPolicy.worthSending(fix(5, 1), null));
        assertFalse(LocationPolicy.worthSending(null, null));
    }

    @Test public void anAlertCarriesThePositionAsAPair() throws Exception {
        JSONObject o = new JSONObject(Payloads.alert("w-12345678", Payloads.FALL, Payloads.TRIGGER_MANUAL, 5L, null, null, null, null,
                new LocationFix(10.7712345678, 106.6987654321, 12.34, 4_000L)));
        assertEquals(10.771235, o.getDouble("lat"), 1e-9);
        assertEquals(106.698765, o.getDouble("lng"), 1e-9);
        assertEquals(12.3, o.getDouble("accuracy_m"), 1e-9);
        assertEquals(4_000L, o.getLong("fix_time"));
    }

    @Test public void noFixOrAnUnusableOneLeavesThePositionOut() throws Exception {
        for (LocationFix bad : new LocationFix[]{null, new LocationFix(95, 0, 5.0, 1), new LocationFix(0, 181, 5.0, 1), new LocationFix(0, 0, 5.0, 0)}) {
            JSONObject o = new JSONObject(Payloads.alert("w-12345678", Payloads.FALL, Payloads.TRIGGER_MANUAL, 5L, null, null, null, null, bad));
            assertFalse(o.has("lat"));
            assertFalse(o.has("lng"));
            assertFalse(o.has("fix_time"));
        }
    }

    @Test public void aLaterFixNamesTheAlertItBelongsTo() throws Exception {
        JSONObject o = new JSONObject(Payloads.location("w-12345678", fix(20, 9_000L)));
        assertEquals("w-12345678", o.getString("client_id"));
        assertEquals(9_000L, o.getLong("fix_time"));
        assertNull(Payloads.location(null, fix(20, 1)));
        assertNull(Payloads.location("w-12345678", null));
    }

    @Test public void locationUpdatesGoAfterAlertsAndBeforeSignalBatches() throws Exception {
        java.io.File dir = java.nio.file.Files.createTempDirectory("outbox").toFile();
        FileOutbox box = new FileOutbox(dir);
        box.add(Outbox.Kind.SAMPLES, "s1", 0); Thread.sleep(3);
        box.add(Outbox.Kind.LOCATION, "l1", 0); Thread.sleep(3);
        box.add(Outbox.Kind.ALERT, "a1", 0);
        UploaderTest.FakeApi api = new UploaderTest.FakeApi();
        new Uploader(box, api, new UploaderTest.MemoryStore("t")).flush(System.currentTimeMillis());
        assertEquals(List.of("A:a1", "L:l1", "S:s1"), api.calls);
        assertNotNull(box);
    }

    @Test public void aLocationUpdateIsHeldUntilItsAlertMayBeSent() throws Exception {
        java.io.File dir = java.nio.file.Files.createTempDirectory("outbox").toFile();
        FileOutbox box = new FileOutbox(dir);
        long now = System.currentTimeMillis();
        box.add(Outbox.Kind.LOCATION, "l1", now + 60_000);
        UploaderTest.FakeApi api = new UploaderTest.FakeApi();
        new Uploader(box, api, new UploaderTest.MemoryStore("t")).flush(now);
        assertTrue(api.calls.isEmpty());
        assertEquals(1, box.size());
    }
}
