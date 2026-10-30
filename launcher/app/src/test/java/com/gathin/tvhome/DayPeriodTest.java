package com.gathin.tvhome;

import java.time.LocalTime;
import org.junit.Test;
import static org.junit.Assert.*;

public class DayPeriodTest {
    @Test public void allBoundariesSwitchAtTheirExactMinute() {
        int[] hours = {0, 5, 8, 11, 13, 17, 19};
        String[] labels = {"凌晨", "清晨", "上午", "中午", "下午", "傍晚", "晚上"};
        for (int i = 0; i < hours.length; i++) {
            LocalTime boundary = LocalTime.of(hours[i], 0);
            assertEquals(labels[i], DayPeriod.label(boundary));
            assertEquals(labels[(i + 6) % 7], DayPeriod.label(boundary.minusMinutes(1)));
        }
    }
}
