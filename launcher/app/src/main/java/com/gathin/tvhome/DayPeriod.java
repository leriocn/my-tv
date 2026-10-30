package com.gathin.tvhome;

import java.time.LocalTime;

/** Everyday Chinese day periods, independent of the system's 12/24-hour preference. */
final class DayPeriod {
    static String label(LocalTime time) {
        int hour = time.getHour();
        if (hour < 5) return "凌晨";
        if (hour < 8) return "清晨";
        if (hour < 11) return "上午";
        if (hour < 13) return "中午";
        if (hour < 17) return "下午";
        if (hour < 19) return "傍晚";
        return "晚上";
    }
}
