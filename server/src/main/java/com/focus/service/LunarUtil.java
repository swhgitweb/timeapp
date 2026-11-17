package com.focus.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** 1900—2100 年农历换算；数据表记录每年闰月及各月大小。 */
final class LunarUtil {
    private static final int[] LUNAR_INFO = {
        0x04bd8,0x04ae0,0x0a570,0x054d5,0x0d260,0x0d950,0x16554,0x056a0,0x09ad0,0x055d2,
        0x04ae0,0x0a5b6,0x0a4d0,0x0d250,0x1d255,0x0b540,0x0d6a0,0x0ada2,0x095b0,0x14977,
        0x04970,0x0a4b0,0x0b4b5,0x06a50,0x06d40,0x1ab54,0x02b60,0x09570,0x052f2,0x04970,
        0x06566,0x0d4a0,0x0ea50,0x16a95,0x05ad0,0x02b60,0x186e3,0x092e0,0x1c8d7,0x0c950,
        0x0d4a0,0x1d8a6,0x0b550,0x056a0,0x1a5b4,0x025d0,0x092d0,0x0d2b2,0x0a950,0x0b557,
        0x06ca0,0x0b550,0x15355,0x04da0,0x0a5b0,0x14573,0x052b0,0x0a9a8,0x0e950,0x06aa0,
        0x0aea6,0x0ab50,0x04b60,0x0aae4,0x0a570,0x05260,0x0f263,0x0d950,0x05b57,0x056a0,
        0x096d0,0x04dd5,0x04ad0,0x0a4d0,0x0d4d4,0x0d250,0x0d558,0x0b540,0x0b6a0,0x195a6,
        0x095b0,0x049b0,0x0a974,0x0a4b0,0x0b27a,0x06a50,0x06d40,0x0af46,0x0ab60,0x09570,
        0x04af5,0x04970,0x064b0,0x074a3,0x0ea50,0x06b58,0x055c0,0x0ab60,0x096d5,0x092e0,
        0x0c960,0x0d954,0x0d4a0,0x0da50,0x07552,0x056a0,0x0abb7,0x025d0,0x092d0,0x0cab5,
        0x0a950,0x0b4a0,0x0baa4,0x0ad50,0x055d9,0x04ba0,0x0a5b0,0x15176,0x052b0,0x0a930,
        0x07954,0x06aa0,0x0ad50,0x05b52,0x04b60,0x0a6e6,0x0a4e0,0x0d260,0x0ea65,0x0d530,
        0x05aa0,0x076a3,0x096d0,0x04afb,0x04ad0,0x0a4d0,0x1d0b6,0x0d250,0x0d520,0x0dd45,
        0x0b5a0,0x056d0,0x055b2,0x049b0,0x0a577,0x0a4b0,0x0aa50,0x1b255,0x06d20,0x0ada0,
        0x14b63,0x09370,0x049f8,0x04970,0x064b0,0x168a6,0x0ea50,0x06b20,0x1a6c4,0x0aae0,
        0x0a2e0,0x0d2e3,0x0c960,0x0d557,0x0d4a0,0x0da50,0x05d55,0x056a0,0x0a6d0,0x055d4,
        0x052d0,0x0a9b8,0x0a950,0x0b4a0,0x0b6a6,0x0ad50,0x055a0,0x0aba4,0x0a5b0,0x052b0,
        0x0b273,0x06930,0x07337,0x06aa0,0x0ad50,0x14b55,0x04b60,0x0a570,0x054e4,0x0d160,
        0x0e968,0x0d520,0x0daa0,0x16aa6,0x056d0,0x04ae0,0x0a9d4,0x0a2d0,0x0d150,0x0f252,
        0x0d520
    };
    private static final String[] MONTHS = {"正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊"};
    private static final String[] DAY_PREFIX = {"初", "十", "廿", "卅"};
    private static final String[] DAY_NAMES = {"一", "二", "三", "四", "五", "六", "七", "八", "九", "十"};

    private LunarUtil() {}

    static Info from(LocalDate date) {
        if (date.isBefore(LocalDate.of(1900, 1, 31)) || date.isAfter(LocalDate.of(2100, 12, 31))) {
            return new Info("", new ArrayList<String>());
        }
        int offset = (int) ChronoUnit.DAYS.between(LocalDate.of(1900, 1, 31), date);
        int year = 1900;
        int days = 0;
        for (; year < 2101 && offset > 0; year++) {
            days = yearDays(year);
            offset -= days;
        }
        if (offset < 0) {
            offset += days;
            year--;
        }
        int leap = leapMonth(year);
        boolean isLeap = false;
        int month = 1;
        for (; month < 13 && offset > 0; month++) {
            if (leap > 0 && month == leap + 1 && !isLeap) {
                month--;
                isLeap = true;
                days = leapDays(year);
            } else {
                days = monthDays(year, month);
            }
            offset -= days;
            if (isLeap && month == leap + 1) isLeap = false;
        }
        if (offset == 0 && leap > 0 && month == leap + 1) {
            if (isLeap) isLeap = false;
            else { isLeap = true; month--; }
        }
        if (offset < 0) {
            offset += days;
            month--;
        }
        int day = offset + 1;
        String monthText = (isLeap ? "闰" : "") + MONTHS[month - 1] + "月";
        return new Info(day == 1 ? monthText : dayText(day), festivals(month, day, isLeap));
    }

    private static String dayText(int day) {
        if (day == 10) return "初十";
        if (day == 20) return "二十";
        if (day == 30) return "三十";
        int prefixIndex = (day - 1) / 10;
        int nameIndex = day % 10 == 0 ? 9 : day % 10 - 1;
        return DAY_PREFIX[prefixIndex] + DAY_NAMES[nameIndex];
    }

    private static List<String> festivals(int month, int day, boolean isLeap) {
        List<String> result = new ArrayList<>();
        if (isLeap) return result;
        if (month == 1 && day == 1) result.add("春节");
        if (month == 1 && day == 15) result.add("元宵节");
        if (month == 2 && day == 2) result.add("龙抬头");
        if (month == 5 && day == 5) result.add("端午节");
        if (month == 7 && day == 7) result.add("七夕");
        if (month == 7 && day == 15) result.add("中元节");
        if (month == 8 && day == 15) result.add("中秋节");
        if (month == 9 && day == 9) result.add("重阳节");
        if (month == 12 && day == 8) result.add("腊八节");
        return result;
    }

    private static int yearDays(int year) {
        int sum = 348;
        for (int mask = 0x8000; mask > 0x8; mask >>= 1) {
            if ((LUNAR_INFO[year - 1900] & mask) != 0) sum++;
        }
        return sum + leapDays(year);
    }

    private static int leapMonth(int year) { return LUNAR_INFO[year - 1900] & 0xf; }

    private static int leapDays(int year) {
        if (leapMonth(year) == 0) return 0;
        return (LUNAR_INFO[year - 1900] & 0x10000) != 0 ? 30 : 29;
    }

    private static int monthDays(int year, int month) {
        return (LUNAR_INFO[year - 1900] & (0x10000 >> month)) != 0 ? 30 : 29;
    }

    static final class Info {
        private final String text;
        private final List<String> festivals;
        Info(String text, List<String> festivals) { this.text = text; this.festivals = festivals; }
        String getText() { return text; }
        List<String> getFestivals() { return festivals; }
    }
}
