package com.example.watchtest.uii;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RecentIdsTest {
    @Test public void addsAndFindsIds() {
        String s = RecentIds.add("", "a", 3);
        s = RecentIds.add(s, "b", 3);
        assertEquals("a,b", s);
        assertTrue(RecentIds.contains(s, "a"));
        assertFalse(RecentIds.contains(s, "c"));
        assertFalse("a part of an id is not the id", RecentIds.contains("abc", "b"));
    }

    @Test public void keepsOnlyTheNewestAndDoesNotDuplicate() {
        String s = "";
        for (String id : new String[]{"1", "2", "3", "4"}) s = RecentIds.add(s, id, 3);
        assertEquals("2,3,4", s);
        assertEquals("2,4,3", RecentIds.add(s, "3", 3));
    }

    @Test public void ignoresEmptyOrUnsafeIds() {
        assertEquals("a", RecentIds.add("a", "", 3));
        assertEquals("a", RecentIds.add("a", "x,y", 3));
        assertEquals("", RecentIds.add(null, null, 3));
        assertFalse(RecentIds.contains(null, "a"));
    }
}
