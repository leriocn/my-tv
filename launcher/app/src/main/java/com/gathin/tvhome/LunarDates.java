package com.gathin.tvhome;

import com.nlf.calendar.Lunar;
import com.nlf.calendar.Solar;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reuse a date's astronomical calculation across cell, label and almanac reads; never mutate its Lunar. */
final class LunarDates {
    private static final Map<LocalDate, Lunar> CACHE = new LinkedHashMap<LocalDate, Lunar>(96, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<LocalDate, Lunar> entry) { return size() > 96; }
    };
    static synchronized Lunar get(LocalDate date) {
        Lunar value = CACHE.get(date);
        if (value == null) {
            value = Solar.fromYmd(date.getYear(), date.getMonthValue(), date.getDayOfMonth()).getLunar();
            CACHE.put(date, value);
        }
        return value;
    }
    static synchronized void clear() { CACHE.clear(); }
}
