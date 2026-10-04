package com.example.watchtest.uii;

import java.util.ArrayList;
import java.util.List;

/** A short most-recent-last list of ids kept as one comma-separated string (outbox ids contain no commas). */
public final class RecentIds {
    private RecentIds() {}

    public static String add(String csv, String id, int max) {
        if (id == null || id.isEmpty() || id.indexOf(',') >= 0) return csv == null ? "" : csv;
        List<String> kept = new ArrayList<>();
        if (csv != null && !csv.isEmpty()) {
            for (String p : csv.split(",")) if (!p.equals(id)) kept.add(p);
        }
        while (kept.size() > max - 1) kept.remove(0);
        kept.add(id);
        return String.join(",", kept);
    }

    public static boolean contains(String csv, String id) {
        if (csv == null || id == null || id.isEmpty()) return false;
        for (String p : csv.split(",")) if (p.equals(id)) return true;
        return false;
    }
}
