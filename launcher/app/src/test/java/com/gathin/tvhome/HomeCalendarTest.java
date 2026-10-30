package com.gathin.tvhome;

import org.junit.Test;
import java.time.LocalDate;
import static org.junit.Assert.*;

public class HomeCalendarTest {
    // Hong Kong Observatory 2026 Gregorian/Lunar conversion table (UTC+8).
    @Test public void frostDescent2026MatchesObservatoryCalendar() {
        assertEquals("农历 丙午年 九月十四 · 今日霜降", HomeCalendar.describe(LocalDate.of(2026,10,23)));
    }
    @Test public void lunarNewYear2026MatchesObservatoryCalendar() {
        assertTrue(HomeCalendar.describe(LocalDate.of(2026,2,17)).contains("正月初一"));
    }
    @Test public void termDoesNotLeakToThePreviousDay() {
        String value = HomeCalendar.describe(LocalDate.of(2026,10,22));
        assertTrue(value.contains("九月十三"));
        assertTrue(value.contains("霜降 10月23日"));
        assertFalse(value.contains("今日霜降"));
    }
    @Test public void winterSolstice2026MatchesObservatoryCalendar() {
        assertTrue(HomeCalendar.describe(LocalDate.of(2026,12,22)).contains("今日冬至"));
    }
    @Test public void weatherCodesCoverRainSnowAndUnknown() {
        assertEquals("晴", WeatherCodes.label(0));
        assertEquals("中雨", WeatherCodes.label(63));
        assertEquals("大雪", WeatherCodes.label(86));
        assertEquals("天气未知", WeatherCodes.label(-1));
    }
    @org.junit.Test public void zodiacFollowsLunarNewYearRatherThanJanuaryFirst() {
        org.junit.Assert.assertTrue(HomeCalendar.lunarDate(java.time.LocalDate.of(2026, 1, 1)).endsWith("蛇年"));
        org.junit.Assert.assertTrue(HomeCalendar.lunarDate(java.time.LocalDate.of(2026, 2, 17)).endsWith("马年"));
        org.junit.Assert.assertEquals("九月十四 · 马年", HomeCalendar.lunarDate(java.time.LocalDate.of(2026, 10, 23)));
        org.junit.Assert.assertEquals("工作日 · 霜降", HomeCalendar.status(java.time.LocalDate.of(2026, 10, 23)));
    }
}
