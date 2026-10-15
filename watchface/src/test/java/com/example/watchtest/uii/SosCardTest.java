package com.example.watchtest.uii;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class SosCardTest {
    private static final String FULL = "{\"card\":{\"name\":\"An\",\"age\":27,\"blood_type\":\"O+\",\"epilepsy_type\":\"Tonic-clonic\","
            + "\"seizure_notes\":\"Thường kéo dài 2 phút\",\"allergies\":[\"Penicillin\",\" \"],\"conditions\":[],"
            + "\"meds\":[{\"name\":\"Valproate\",\"dosage\":\"500mg\"},{\"name\":\"Vitamin D\",\"dosage\":null}],"
            + "\"contacts\":[{\"name\":\"Mẹ\",\"relation\":\"Mẹ\",\"phone\":\"0900000001\"},{\"name\":\"Không số\",\"relation\":null,\"phone\":null}]}}";

    @Test public void aFullResponseIsReadIntoPlainFields() {
        SosCard c = SosCard.fromResponse(FULL);
        assertNotNull(c);
        assertEquals("An", c.name);
        assertEquals(Integer.valueOf(27), c.age);
        assertEquals("O+", c.bloodType);
        assertEquals(List.of("Penicillin"), c.allergies);
        assertTrue(c.conditions.isEmpty());
        assertEquals(List.of("Valproate 500mg", "Vitamin D"), c.meds);
        assertEquals("a contact without a phone is not callable, so it is dropped", 1, c.contacts.size());
        assertEquals("0900000001", c.contacts.get(0).phone);
    }

    @Test public void aWearerWithoutAProfileHasNoCard() {
        assertNull(SosCard.fromResponse("{\"card\":null}"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void anUnreadableAnswerIsAnErrorSoTheCachedCardIsKept() {
        SosCard.fromResponse("<html>502</html>");
    }

    @Test(expected = IllegalArgumentException.class)
    public void anAnswerWithoutTheCardFieldIsAnError() {
        SosCard.fromResponse("{\"error\":\"rate_limited\"}");
    }

    @Test public void theCacheRoundTrips() {
        SosCard c = SosCard.fromResponse(FULL);
        SosCard again = SosCard.fromJson(c.toJson());
        assertNotNull(again);
        assertEquals(c.name, again.name);
        assertEquals(c.meds, again.meds);
    }

    @Test public void anEmptyOrDamagedCacheIsNoCard() {
        assertNull(SosCard.fromJson(""));
        assertNull(SosCard.fromJson(null));
        assertNull(SosCard.fromJson("{not json"));
    }
}
