package com.gathin.tvhome;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class RecentOrderTest {
    @Test public void liveShortcutStaysBeforeEvenTheMostRecentApp() {
        Map<String, Long> times = new HashMap<>();
        times.put("video", 100L);
        assertTrue(RecentOrder.compare("live", "Live", "video", "Video", Arrays.asList("live"), times) < 0);
        assertTrue(RecentOrder.compare("video", "Video", "live", "Live", Arrays.asList("live"), times) > 0);
    }
    @Test public void recentAppPrecedesOlderAndNeverOpenedApps() {
        Map<String, Long> times = new HashMap<>();
        times.put("old", 10L);
        times.put("new", 20L);
        assertTrue(RecentOrder.compare("new", "Z", "old", "A", Collections.emptyList(), times) < 0);
        assertTrue(RecentOrder.compare("old", "Z", "never", "A", Collections.emptyList(), times) < 0);
        assertEquals(0, RecentOrder.compare("new", "Z", "new", "Z", Collections.emptyList(), times));
    }
    @Test public void tiesHaveStableLabelThenPackageOrder() {
        assertTrue(RecentOrder.compare("a", "Same", "b", "same", Collections.emptyList(), Collections.emptyMap()) < 0);
        assertTrue(RecentOrder.compare("z", "Apple", "a", "Zoo", Collections.emptyList(), Collections.emptyMap()) < 0);
    }
}
