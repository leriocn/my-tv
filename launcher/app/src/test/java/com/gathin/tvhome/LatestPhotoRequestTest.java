package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class LatestPhotoRequestTest {
    @Test public void rapidKeysCoalesceInsteadOfQueuingOrDisplayingStalePhotos() {
        LatestPhotoRequest<String> requests = new LatestPhotoRequest<>();
        requests.request("a"); assertEquals("a", requests.start());
        for (int i = 0; i < 100; i++) { requests.request("photo" + i); assertNull(requests.start()); }
        assertFalse(requests.complete("a")); assertEquals("photo99", requests.start());
        assertTrue(requests.complete("photo99"));
    }
    @Test public void returningToSamePhotoAndClosingDuringDecode() {
        LatestPhotoRequest<String> requests = new LatestPhotoRequest<>();
        requests.request("a"); requests.start(); requests.request("b"); requests.request("a");
        assertTrue(requests.complete("a"));
        requests.request("b"); requests.start(); requests.close();
        assertFalse(requests.complete("b")); requests.request("c"); assertNull(requests.start());
    }
}
