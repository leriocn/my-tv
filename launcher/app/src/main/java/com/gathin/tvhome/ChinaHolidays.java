package com.gathin.tvhome;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Nationwide rest/work arrangements, explicitly transcribed from the State Council's annual notice.
 * A continuous holiday includes shifted rest and weekends; it is not a statutory overtime-pay classification.
 * Unknown years never inherit another year's schedule or assume all weekends are days off.
 */
final class ChinaHolidays {
    static final int VERIFIED_YEAR = 2026;
    static final String SOURCE = "国务院办公厅 · 国办发明电〔2025〕7号 · 2025年11月4日";
    static final String SOURCE_URL = "https://www.gov.cn/zhengce/zhengceku/202511/content_7047091.htm";
    static final String VERIFIED_MIRROR_URL = "https://www.beijing.gov.cn/fwcj/calendar/shts/690abd58091e1a7398401381.html";
    enum Kind { WORK, WEEKEND, HOLIDAY, MAKEUP, UNKNOWN }
    static final class Day {
        final Kind kind;
        final String holiday;
        Day(Kind kind, String holiday) { this.kind = kind; this.holiday = holiday; }
        String label() {
            switch (kind) {
                case HOLIDAY: return "放假 · " + holiday;
                case MAKEUP: return "调休补班 · " + holiday;
                case WEEKEND: return "周末休息";
                case WORK: return "工作日";
                default: return "调休安排待更新";
            }
        }
        int color() {
            switch (kind) {
                case HOLIDAY: case WEEKEND: return 0xFFB9E7C7;
                case MAKEUP: return 0xFFFFCE91;
                case UNKNOWN: return 0xFFCCCCCC;
                default: return 0xFFBDDFFF;
            }
        }
    }
    static final class Break {
        final String name;
        final LocalDate start, end;
        final List<LocalDate> makeup;
        Break(String name, String start, String end, String... makeup) {
            this.name = name; this.start = LocalDate.parse(start); this.end = LocalDate.parse(end);
            java.util.ArrayList<LocalDate> dates = new java.util.ArrayList<>();
            for (String date : makeup) dates.add(LocalDate.parse(date));
            this.makeup = Collections.unmodifiableList(dates);
        }
        int days() { return (int) ChronoUnit.DAYS.between(start, end) + 1; }
        boolean contains(LocalDate date) { return !date.isBefore(start) && !date.isAfter(end); }
    }
    private static final List<Break> YEAR_2026 = Collections.unmodifiableList(Arrays.asList(
            new Break("元旦", "2026-01-01", "2026-01-03", "2026-01-04"),
            new Break("春节", "2026-02-15", "2026-02-23", "2026-02-14", "2026-02-28"),
            new Break("清明节", "2026-04-04", "2026-04-06"),
            new Break("劳动节", "2026-05-01", "2026-05-05", "2026-05-09"),
            new Break("端午节", "2026-06-19", "2026-06-21"),
            new Break("中秋节", "2026-09-25", "2026-09-27"),
            new Break("国庆节", "2026-10-01", "2026-10-07", "2026-09-20", "2026-10-10")
    ));
    private static final java.util.Map<Integer,List<Break>> DOWNLOADED = new java.util.concurrent.ConcurrentHashMap<>();
    static boolean install(int year, List<Break> breaks) {
        // The manually verified bundled year is immutable; a remote file cannot weaken its baseline.
        if (year == VERIFIED_YEAR) return false;
        DOWNLOADED.put(year, Collections.unmodifiableList(new java.util.ArrayList<>(breaks))); return true;
    }
    static boolean verified(int year) { return year == VERIFIED_YEAR || DOWNLOADED.containsKey(year); }
    static List<Break> breaks(int year) { return year == VERIFIED_YEAR ? YEAR_2026 : DOWNLOADED.getOrDefault(year, Collections.emptyList()); }
    static Day on(LocalDate date) {
        if (!verified(date.getYear())) return new Day(Kind.UNKNOWN, "");
        for (Break block : breaks(date.getYear())) if (block.makeup.contains(date)) return new Day(Kind.MAKEUP, block.name);
        for (Break block : breaks(date.getYear())) if (block.contains(date)) return new Day(Kind.HOLIDAY, block.name);
        DayOfWeek weekday = date.getDayOfWeek();
        return new Day(weekday == DayOfWeek.SATURDAY || weekday == DayOfWeek.SUNDAY ? Kind.WEEKEND : Kind.WORK, "");
    }
}
