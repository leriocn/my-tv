package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class PhotoDateTest {
    @Test public void originalExifDateIsDisplayedWithoutGuessingTimezone() {
        assertEquals("2024-02-29", PhotoRenderer.photoDate("2024:02:29 23:59:59"));
    }
    @Test public void missingAndInvalidDatesStayHidden() {
        assertEquals("", PhotoRenderer.photoDate(null));
        assertEquals("", PhotoRenderer.photoDate(""));
        assertEquals("", PhotoRenderer.photoDate("2025:02:29 12:00:00"));
        assertEquals("", PhotoRenderer.photoDate("2024:01:01 99:00:00"));
        assertEquals("", PhotoRenderer.photoDate("not a date"));
    }
}
