package com.gathin.tvhome;

import org.junit.Test;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

/** Fixture checked against 国办发明电〔2025〕7号, 2025-11-04 (gov.cn content_7047090). */
public class ChinaHolidaysTest {
    private LocalDate date(String value) { return LocalDate.parse("2026-" + value); }
    @Test public void allSixOfficialMakeupDaysOverrideWeekends() {
        String[] work = {"01-04", "02-14", "02-28", "05-09", "09-20", "10-10"};
        for (String value : work) assertEquals(value, ChinaHolidays.Kind.MAKEUP, ChinaHolidays.on(date(value)).kind);
    }
    @Test public void allSevenContinuousHolidayRangesMatchOfficialNotice() {
        String[][] blocks = {{"01-01","01-03","元旦"},{"02-15","02-23","春节"},
                {"04-04","04-06","清明节"},{"05-01","05-05","劳动节"},
                {"06-19","06-21","端午节"},{"09-25","09-27","中秋节"},{"10-01","10-07","国庆节"}};
        Set<LocalDate> holidays = new HashSet<>();
        for (String[] block : blocks) {
            for (LocalDate day = date(block[0]); !day.isAfter(date(block[1])); day = day.plusDays(1)) {
                holidays.add(day);
                assertEquals(day.toString(), ChinaHolidays.Kind.HOLIDAY, ChinaHolidays.on(day).kind);
                assertEquals(block[2], ChinaHolidays.on(day).holiday);
            }
        }
        assertEquals(33, holidays.size());
        int counted = 0;
        for (LocalDate day = date("01-01"); day.getYear() == 2026; day = day.plusDays(1)) {
            if (ChinaHolidays.on(day).kind == ChinaHolidays.Kind.HOLIDAY) {
                counted++; assertTrue("Unexpected holiday " + day, holidays.contains(day));
            }
        }
        assertEquals(33, counted);
    }
    @Test public void ordinaryWeekendAndWorkingDayRemainSeparate() {
        assertEquals(ChinaHolidays.Kind.WORK, ChinaHolidays.on(date("10-23")).kind);
        assertEquals(ChinaHolidays.Kind.WEEKEND, ChinaHolidays.on(date("10-24")).kind);
        assertEquals(ChinaHolidays.Kind.WEEKEND, ChinaHolidays.on(date("10-25")).kind);
        assertEquals(ChinaHolidays.Kind.WORK, ChinaHolidays.on(date("10-26")).kind);
    }
    @Test public void weekdayRestInsideBreakOverridesNormalWorkday() {
        assertEquals(ChinaHolidays.Kind.HOLIDAY, ChinaHolidays.on(date("04-06")).kind);
        assertEquals(ChinaHolidays.Kind.WORK, ChinaHolidays.on(date("04-07")).kind);
        assertEquals(ChinaHolidays.Kind.WORK, ChinaHolidays.on(date("10-08")).kind);
    }
    @Test public void tableContainsNoOverlapsOrDuplicateMakeupDays() {
        Set<LocalDate> days = new HashSet<>(), work = new HashSet<>();
        for (ChinaHolidays.Break b : ChinaHolidays.breaks(2026)) {
            assertTrue(b.days() > 0);
            for (LocalDate day = b.start; !day.isAfter(b.end); day = day.plusDays(1)) assertTrue(days.add(day));
            for (LocalDate day : b.makeup) assertTrue(work.add(day));
        }
        assertEquals(6, work.size());
        for (LocalDate day : work) assertFalse(days.contains(day));
    }
    @Test public void unknownYearDoesNotGuessHolidayOrMakeupWork() {
        assertFalse(ChinaHolidays.verified(2027));
        assertTrue(ChinaHolidays.breaks(2027).isEmpty());
        assertEquals(ChinaHolidays.Kind.UNKNOWN, ChinaHolidays.on(LocalDate.of(2027,1,1)).kind);
        assertEquals(ChinaHolidays.Kind.UNKNOWN, ChinaHolidays.on(LocalDate.of(2027,1,2)).kind);
        assertTrue(HomeCalendar.summary(LocalDate.of(2027,1,1)).contains("待更新"));
    }
    @Test public void festivalsDoNotAutomaticallyMakeWorkdaysHolidays() {
        assertTrue(HomeCalendar.festivals(date("09-10")).contains("教师节"));
        assertEquals(ChinaHolidays.Kind.WORK, ChinaHolidays.on(date("09-10")).kind);
        assertTrue(HomeCalendar.summary(date("09-10")).startsWith("工作日"));
    }
    @Test public void lunarFestivalNameAndSolarTermAreRetained() {
        assertTrue(HomeCalendar.festivals(date("02-17")).contains("春节"));
        assertTrue(HomeCalendar.festivals(date("02-16")).contains("除夕"));
        assertTrue(HomeCalendar.summary(date("10-23")).contains("今日霜降"));
        assertTrue(HomeCalendar.summary(date("10-23")).startsWith("工作日"));
    }
    @Test public void dayBeforeMakeupWorkWarnsAboutTomorrow() {
        assertTrue(HomeCalendar.summary(date("10-09")).contains("明日补班"));
        assertFalse(HomeCalendar.summary(date("10-10")).contains("明日补班"));
    }
    @Test public void newYearDoesNotAccidentallyReusePreviousYearSchedule() {
        assertEquals(ChinaHolidays.Kind.WORK, ChinaHolidays.on(date("12-31")).kind);
        assertFalse(HomeCalendar.summary(date("12-31")).contains("明日补班"));
    }
}
