package com.example.watchtest.uii;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Outbox kept as one small file per request. Writes go to a temp file first and are renamed into place,
 * so a crash never leaves a half-written entry. Alerts are never discarded; old sample batches are, when full.
 */
public final class FileOutbox implements Outbox {
    public static final int MAX_SAMPLE_ITEMS = 300;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final File dir;
    private final int maxSampleItems;

    public FileOutbox(File dir) {
        this(dir, MAX_SAMPLE_ITEMS);
    }

    public FileOutbox(File dir, int maxSampleItems) {
        this.dir = dir;
        this.maxSampleItems = maxSampleItems;
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
    }

    @Override public synchronized Item add(Kind kind, String json, long notBeforeMs) {
        long now = System.currentTimeMillis();
        String id = String.format(Locale.ROOT, "%08x", RANDOM.nextInt());
        Item item = new Item(id, kind, json, now, notBeforeMs);
        try {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("kind", kind.name());
            o.put("json", json);
            o.put("createdAt", now);
            o.put("notBefore", notBeforeMs);
            File tmp = new File(dir, id + ".tmp");
            Files.write(tmp.toPath(), o.toString().getBytes(StandardCharsets.UTF_8));
            Files.move(tmp.toPath(), fileFor(item).toPath(), StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | JSONException e) {
            throw new IllegalStateException("Could not store outbox item", e);
        }
        if (kind == Kind.SAMPLES) trimSamples();
        return item;
    }

    @Override public synchronized List<Item> list() {
        File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
        List<Item> out = new ArrayList<>();
        if (files == null) return out;
        Arrays.sort(files);
        for (File f : files) {
            try {
                JSONObject o = new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
                out.add(new Item(o.getString("id"), Kind.valueOf(o.getString("kind")), o.getString("json"),
                        o.getLong("createdAt"), o.optLong("notBefore", 0)));
            } catch (IOException | JSONException | IllegalArgumentException e) {
                // Keep the damaged file for inspection instead of retrying it forever.
                //noinspection ResultOfMethodCallIgnored
                f.renameTo(new File(dir, f.getName() + ".bad"));
            }
        }
        return out;
    }

    @Override public synchronized boolean remove(String id) {
        File[] files = dir.listFiles((d, n) -> n.endsWith("-" + id + ".json"));
        boolean removed = false;
        if (files != null) for (File f : files) removed |= f.delete();
        return removed;
    }

    @Override public synchronized int size() {
        File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
        return files == null ? 0 : files.length;
    }

    private File fileFor(Item item) {
        // Sorted by creation time; the kind letter and id keep names unique and let remove() find the file.
        return new File(dir, String.format(Locale.ROOT, "%013d-%s-%s.json", item.createdAtMs, item.kind == Kind.ALERT ? "A" : item.kind == Kind.LOCATION ? "L" : "S", item.id));
    }

    private void trimSamples() {
        File[] files = dir.listFiles((d, n) -> n.contains("-S-") && n.endsWith(".json"));
        if (files == null || files.length <= maxSampleItems) return;
        Arrays.sort(files);
        for (int i = 0; i < files.length - maxSampleItems; i++) {
            //noinspection ResultOfMethodCallIgnored
            files[i].delete();
        }
    }
}
