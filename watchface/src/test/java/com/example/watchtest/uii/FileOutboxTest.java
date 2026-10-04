package com.example.watchtest.uii;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public class FileOutboxTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static void pause() throws InterruptedException { Thread.sleep(3); }

    @Test public void itemsComeBackOldestFirstWithTheirContent() throws Exception {
        FileOutbox box = new FileOutbox(tmp.newFolder("q"));
        Outbox.Item a = box.add(Outbox.Kind.SAMPLES, "{\"n\":1}", 0); pause();
        Outbox.Item b = box.add(Outbox.Kind.ALERT, "{\"n\":2}", 0); pause();
        Outbox.Item c = box.add(Outbox.Kind.SAMPLES, "{\"n\":3}", 0);
        List<Outbox.Item> all = box.list();
        assertEquals(3, all.size());
        assertEquals(a.id, all.get(0).id);
        assertEquals(b.id, all.get(1).id);
        assertEquals(c.id, all.get(2).id);
        assertEquals("{\"n\":2}", all.get(1).json);
        assertEquals(Outbox.Kind.ALERT, all.get(1).kind);
        assertEquals(3, box.size());
    }

    @Test public void queueSurvivesARestartOfTheProcess() throws Exception {
        File dir = tmp.newFolder("persist");
        Outbox.Item item = new FileOutbox(dir).add(Outbox.Kind.ALERT, "{\"alert\":true}", 123L);
        List<Outbox.Item> after = new FileOutbox(dir).list();
        assertEquals(1, after.size());
        assertEquals(item.id, after.get(0).id);
        assertEquals("{\"alert\":true}", after.get(0).json);
        assertEquals(123L, after.get(0).notBeforeMs);
    }

    @Test public void removeDeletesOnlyThatItemAndReportsWhetherItExisted() throws Exception {
        FileOutbox box = new FileOutbox(tmp.newFolder("rm"));
        Outbox.Item a = box.add(Outbox.Kind.ALERT, "{}", 0); pause();
        Outbox.Item b = box.add(Outbox.Kind.ALERT, "{}", 0);
        assertTrue(box.remove(a.id));
        assertFalse("second removal finds nothing", box.remove(a.id));
        assertEquals(1, box.size());
        assertEquals(b.id, box.list().get(0).id);
        assertFalse(box.remove("nonexistent"));
    }

    @Test public void noTempFilesAreLeftBehind() throws Exception {
        File dir = tmp.newFolder("clean");
        FileOutbox box = new FileOutbox(dir);
        for (int i = 0; i < 5; i++) { box.add(Outbox.Kind.SAMPLES, "{}", 0); pause(); }
        for (String n : dir.list()) assertTrue(n, n.endsWith(".json"));
    }

    @Test public void aDamagedFileIsSetAsideAndDoesNotBlockTheRest() throws Exception {
        File dir = tmp.newFolder("bad");
        FileOutbox box = new FileOutbox(dir);
        box.add(Outbox.Kind.ALERT, "{\"ok\":1}", 0);
        Files.write(new File(dir, "0000000000001-A-deadbeef.json").toPath(), "{not json".getBytes(StandardCharsets.UTF_8));
        List<Outbox.Item> items = box.list();
        assertEquals(1, items.size());
        assertEquals("{\"ok\":1}", items.get(0).json);
        assertTrue(new File(dir, "0000000000001-A-deadbeef.json.bad").exists());
        assertEquals("the damaged file is not retried", 1, box.list().size());
    }

    @Test public void oldSampleBatchesAreDroppedWhenFullButAlertsNeverAre() throws Exception {
        FileOutbox box = new FileOutbox(tmp.newFolder("cap"), 3);
        Outbox.Item alert = box.add(Outbox.Kind.ALERT, "{\"alert\":1}", 0); pause();
        Outbox.Item first = box.add(Outbox.Kind.SAMPLES, "{\"s\":1}", 0); pause();
        for (int i = 2; i <= 6; i++) { box.add(Outbox.Kind.SAMPLES, "{\"s\":" + i + "}", 0); pause(); }
        List<Outbox.Item> items = box.list();
        int samples = 0;
        boolean hasAlert = false, hasFirst = false;
        for (Outbox.Item it : items) {
            if (it.kind == Outbox.Kind.SAMPLES) samples++;
            if (it.id.equals(alert.id)) hasAlert = true;
            if (it.id.equals(first.id)) hasFirst = true;
        }
        assertEquals(3, samples);
        assertTrue("the alert is kept", hasAlert);
        assertFalse("the oldest batch was discarded", hasFirst);
    }

    @Test public void idsAreUniqueEvenWhenAddedInTheSameMillisecond() throws Exception {
        FileOutbox box = new FileOutbox(tmp.newFolder("uniq"));
        for (int i = 0; i < 50; i++) box.add(Outbox.Kind.ALERT, "{}", 0);
        assertEquals(50, box.size());
    }
}
