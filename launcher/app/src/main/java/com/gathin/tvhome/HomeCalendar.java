package com.gathin.tvhome;

import com.nlf.calendar.JieQi;
import com.nlf.calendar.Lunar;
import com.nlf.calendar.Solar;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

/** Chinese lunar dates and astronomical solar terms, calculated offline in the Chinese calendar. */
final class HomeCalendar {
    static String zodiacIcon(LocalDate date) {
        String zodiac = LunarDates.get(date).getYearShengXiao();
        String[] animals = {"鼠","牛","虎","兔","龙","蛇","马","羊","猴","鸡","狗","猪"};
        String[] icons = {"🐀","🐂","🐅","🐇","🐉","🐍","🐎","🐐","🐒","🐓","🐕","🐖"};
        for (int i = 0; i < animals.length; i++) if (animals[i].equals(zodiac)) return icons[i];
        return "";
    }
    static int season(LocalDate date) {
        String previous = LunarDates.get(date).getPrevJieQi(true).getName();
        String[] terms = {"立春","雨水","惊蛰","春分","清明","谷雨","立夏","小满","芒种","夏至","小暑","大暑",
                "立秋","处暑","白露","秋分","寒露","霜降","立冬","小雪","大雪","冬至","小寒","大寒"};
        for (int i = 0; i < terms.length; i++) if (terms[i].equals(previous)) return i / 6;
        return (date.getMonthValue() % 12) / 3;
    }
    static String festivals(LocalDate date) {
        Solar solar = Solar.fromYmd(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
        Set<String> names = new LinkedHashSet<>(solar.getFestivals());
        names.addAll(LunarDates.get(date).getFestivals());
        return String.join(" / ", names);
    }

    static String summary(LocalDate date) {
        ChinaHolidays.Day day = ChinaHolidays.on(date);
        String festival = festivals(date);
        // The official break and the actual festival day are separate concepts (e.g. the days before 春节).
        String extra = festival.isEmpty() || festival.equals(day.holiday) || festival.equals(day.holiday + "节")
                ? "" : " · " + festival;
        String tomorrow = ChinaHolidays.on(date.plusDays(1)).kind == ChinaHolidays.Kind.MAKEUP ? " · 明日补班" : "";
        return day.label() + " · " + describe(date) + extra + tomorrow;
    }

    static String lunarDate(LocalDate date) {
        Lunar lunar = LunarDates.get(date);
        return lunar.getMonthInChinese() + "月" + lunar.getDayInChinese() + " · " + lunar.getYearShengXiao() + "年";
    }

    static String status(LocalDate date) {
        String full = describe(date);
        String term = full.substring(full.indexOf(" · ") + 3);
        return summary(date).replace(full, term).replace("今日", "");
    }

    static String describe(LocalDate date) {
        Lunar lunar = LunarDates.get(date);
        String text = "农历 " + lunar.getYearInGanZhi() + "年 " + lunar.getMonthInChinese() + "月" + lunar.getDayInChinese();
        String term = lunar.getJieQi();
        if (!term.isEmpty()) return text + " · 今日" + term;
        JieQi next = lunar.getNextJieQi(true);
        Solar day = next.getSolar();
        return text + " · " + next.getName() + " " + day.getMonth() + "月" + day.getDay() + "日";
    }
}
