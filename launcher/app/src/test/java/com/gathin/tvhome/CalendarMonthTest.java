package com.gathin.tvhome;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class CalendarMonthTest {
    @Test public void octoberIncludesAdjacentMonthsInSixMondayFirstWeeks() {
        List<LocalDate> days = CalendarMonth.days(YearMonth.of(2026, 10));
        assertEquals(42, days.size());
        assertEquals(LocalDate.of(2026, 9, 28), days.get(0));
        assertEquals(LocalDate.of(2026, 11, 8), days.get(41));
        for (int i = 1; i < 42; i++) assertEquals(days.get(i - 1).plusDays(1), days.get(i));
    }
    @Test public void leapDayAndYearBoundaryRemainNavigable() {
        assertTrue(CalendarMonth.days(YearMonth.of(2028, 2)).contains(LocalDate.of(2028, 2, 29)));
        assertEquals(LocalDate.of(2027, 1, 31), CalendarMonth.changeMonth(LocalDate.of(2026, 12, 31), 1));
        assertEquals(LocalDate.of(2027, 2, 28), CalendarMonth.changeMonth(LocalDate.of(2027, 1, 31), 1));
    }
    @Test public void holidayAndSaturdayMakeupHaveExplicitDistinctBadges() {
        assertEquals("休", CalendarMonth.badge(LocalDate.of(2026, 10, 1)));
        assertEquals("班", CalendarMonth.badge(LocalDate.of(2026, 10, 10)));
        assertEquals("", CalendarMonth.badge(LocalDate.of(2026, 10, 11)));
        assertEquals("", CalendarMonth.badge(LocalDate.of(2027, 10, 1)));
    }
    @Test public void solarTermsFestivalsAndLunarMonthStartsGetUsefulLabels() {
        assertEquals("霜降", CalendarMonth.subtitle(LocalDate.of(2026, 10, 23)));
        assertEquals("国庆节", CalendarMonth.subtitle(LocalDate.of(2026, 10, 1)));
        assertEquals("春节", CalendarMonth.subtitle(LocalDate.of(2026, 2, 17)));
        assertEquals("九月", CalendarMonth.subtitle(LocalDate.of(2026, 10, 10)));
    }
}
