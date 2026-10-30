package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;
import java.time.LocalDate;

public class LunarDatesTest {
    @Test public void revisitingCalendarDatesReusesAstronomicalCalculation() {
        LocalDate date = LocalDate.of(2026,10,23);
        assertSame(LunarDates.get(date), LunarDates.get(date));
        assertEquals("九月十四 · 马年", HomeCalendar.lunarDate(date));
        assertEquals("霜降", CalendarMonth.subtitle(date));
    }
    @Test public void evictionAndMemoryTrimPreserveCalendarAcrossNewYearAndLeapDay() {
        LocalDate leap = LocalDate.of(2024,2,29);
        String before = HomeCalendar.lunarDate(leap);
        for (int i=0; i<200; i++) LunarDates.get(leap.plusDays(i));
        assertEquals(before, HomeCalendar.lunarDate(leap));
        LunarDates.clear(); assertEquals(before, HomeCalendar.lunarDate(leap));
        assertTrue(HomeCalendar.lunarDate(LocalDate.of(2026,2,16)).endsWith("蛇年"));
        assertTrue(HomeCalendar.lunarDate(LocalDate.of(2026,2,17)).endsWith("马年"));
    }
}
