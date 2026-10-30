package com.gathin.tvhome;

import java.util.List;
import java.util.Map;

/** Fixed shortcuts first, then most recently opened apps; deterministic ties for untouched apps. */
final class RecentOrder {
    static int compare(String pkgA, String labelA, String pkgB, String labelB,
                       List<String> pinned, Map<String, Long> opened) {
        int a = pinned.indexOf(pkgA), b = pinned.indexOf(pkgB);
        if (a >= 0 || b >= 0) {
            if (a < 0) return 1;
            if (b < 0) return -1;
            return Integer.compare(a, b);
        }
        int time = Long.compare(opened.getOrDefault(pkgB, 0L), opened.getOrDefault(pkgA, 0L));
        if (time != 0) return time;
        int label = labelA.compareToIgnoreCase(labelB);
        return label != 0 ? label : pkgA.compareTo(pkgB);
    }
}
