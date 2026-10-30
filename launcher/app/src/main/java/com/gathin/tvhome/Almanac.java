package com.gathin.tvhome;

import com.nlf.calendar.Lunar;
import com.nlf.calendar.Solar;
import java.time.LocalDate;

/** Traditional almanac data from the same offline calendar, kept separate from official holidays. */
final class Almanac {
    final String yi, ji, luck, clash, spirits, taboo;
    Almanac(LocalDate date) {
        Lunar lunar = LunarDates.get(date);
        yi = String.join(" · ", lunar.getDayYi());
        ji = String.join(" · ", lunar.getDayJi());
        luck = lunar.getDayInGanZhi() + "日 · " + lunar.getDayTianShen() + " · "
                + lunar.getDayTianShenType() + "（" + lunar.getDayTianShenLuck() + "）";
        clash = "冲" + lunar.getDayChongDesc() + " · 煞" + lunar.getDaySha();
        spirits = "喜神 " + lunar.getDayPositionXiDesc() + " · 财神 " + lunar.getDayPositionCaiDesc();
        taboo = lunar.getPengZuGan() + "\n" + lunar.getPengZuZhi();
    }
    String details() { return "传统黄历\n" + luck + "\n" + clash + "\n" + spirits
            + "\n\n宜\n" + yi + "\n\n忌\n" + ji + "\n\n彭祖百忌\n" + taboo; }
}
