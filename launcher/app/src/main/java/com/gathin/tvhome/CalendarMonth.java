package com.gathin.tvhome;

import com.nlf.calendar.Lunar;
import com.nlf.calendar.Solar;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/** Monday-first, six-week calendar. Holiday claims use verified annual data only. */
final class CalendarMonth {
    static List<LocalDate> days(YearMonth month) {
        LocalDate first = month.atDay(1);
        LocalDate start = first.minusDays(first.getDayOfWeek().getValue() - 1);
        List<LocalDate> result = new ArrayList<>();
        for (int i = 0; i < 42; i++) result.add(start.plusDays(i));
        return result;
    }
    static LocalDate changeMonth(LocalDate date, int delta) { return date.plusMonths(delta); }
    static String subtitle(LocalDate date) {
        Lunar lunar = LunarDates.get(date);
        if (!lunar.getJieQi().isEmpty()) return lunar.getJieQi();
        String festivals = HomeCalendar.festivals(date);
        if (!festivals.isEmpty()) return festivals.split(" / ")[0];
        return lunar.getDay() == 1 ? lunar.getMonthInChinese() + "月" : lunar.getDayInChinese();
    }
    static String badge(LocalDate date) {
        ChinaHolidays.Kind kind = ChinaHolidays.on(date).kind;
        return kind == ChinaHolidays.Kind.HOLIDAY ? "休" : kind == ChinaHolidays.Kind.MAKEUP ? "班" : "";
    }
}
