package com.example.watchtest.uii;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The wearer's emergency card as shown on the watch after an alert. It mirrors the server's reduced "watch card"
 * (name, age, blood type, epilepsy notes, allergies, conditions, medications, a few contacts) and is cached on the
 * watch so it can be shown with no network.
 */
public final class SosCard {
    public static final class Contact {
        public final String name;
        public final String relation;   // may be null
        public final String phone;

        Contact(String name, String relation, String phone) {
            this.name = name;
            this.relation = relation;
            this.phone = phone;
        }
    }

    public final String name;            // each of these may be null
    public final Integer age;
    public final String bloodType;
    public final String epilepsyType;
    public final String notes;
    public final List<String> allergies;
    public final List<String> conditions;
    /** One entry per medication: "Name dosage". */
    public final List<String> meds;
    public final List<Contact> contacts;

    private final JSONObject source;

    private SosCard(JSONObject o) {
        source = o;
        name = text(o, "name");
        age = o.isNull("age") ? null : Integer.valueOf(o.optInt("age"));
        bloodType = text(o, "blood_type");
        epilepsyType = text(o, "epilepsy_type");
        notes = text(o, "seizure_notes");
        allergies = strings(o.optJSONArray("allergies"));
        conditions = strings(o.optJSONArray("conditions"));
        List<String> m = new ArrayList<>();
        JSONArray meds = o.optJSONArray("meds");
        for (int i = 0; meds != null && i < meds.length(); i++) {
            JSONObject x = meds.optJSONObject(i);
            if (x == null) continue;
            String n = text(x, "name");
            if (n == null) continue;
            String dose = text(x, "dosage");
            m.add(dose == null ? n : n + " " + dose);
        }
        this.meds = Collections.unmodifiableList(m);
        List<Contact> c = new ArrayList<>();
        JSONArray contacts = o.optJSONArray("contacts");
        for (int i = 0; contacts != null && i < contacts.length(); i++) {
            JSONObject x = contacts.optJSONObject(i);
            if (x == null) continue;
            String n = text(x, "name"), phone = text(x, "phone");
            if (n != null && phone != null) c.add(new Contact(n, text(x, "relation"), phone));
        }
        this.contacts = Collections.unmodifiableList(c);
    }

    /**
     * @param body the response of GET /api/device/sos-card, {@code {"card": {...}}} or {@code {"card": null}}
     * @return the card, or null when the wearer has no profile
     * @throws IllegalArgumentException when the body is not a valid answer (keep the old cached card then)
     */
    public static SosCard fromResponse(String body) {
        try {
            JSONObject root = new JSONObject(body);
            if (!root.has("card")) throw new IllegalArgumentException("no card field");
            if (root.isNull("card")) return null;
            return new SosCard(root.getJSONObject("card"));
        } catch (JSONException e) {
            throw new IllegalArgumentException("unreadable card", e);
        }
    }

    /** Reads the cached form written by {@link #toJson()}; null for an empty or damaged cache. */
    public static SosCard fromJson(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            return new SosCard(new JSONObject(json));
        } catch (JSONException e) {
            return null;
        }
    }

    public String toJson() {
        return source.toString();
    }

    private static String text(JSONObject o, String key) {
        if (o.isNull(key)) return null;
        String s = o.optString(key, "").trim();
        return s.isEmpty() ? null : s;
    }

    private static List<String> strings(JSONArray a) {
        List<String> out = new ArrayList<>();
        for (int i = 0; a != null && i < a.length(); i++) {
            String s = a.optString(i, "").trim();
            if (!s.isEmpty()) out.add(s);
        }
        return Collections.unmodifiableList(out);
    }
}
