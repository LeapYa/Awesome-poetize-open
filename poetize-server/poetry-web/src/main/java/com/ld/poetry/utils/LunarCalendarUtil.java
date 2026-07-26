package com.ld.poetry.utils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 农历 + 节假日工具类
 * <p>
 * 使用查表法实现 1900-2100 年的农历转换。
 */
public class LunarCalendarUtil {

    private LunarCalendarUtil() {
    }

    // ==== 农历数据 (1900-2100, 每年一个 int, 共201个) ====
    // 每个 int 的含义:
    // bit[0-11]: 12个月（或13个月中的前12个），1=大月(30天), 0=小月(29天)
    // bit[12-15]: 闰月月份 (0=无闰月, 1-12=闰几月)
    // bit[16]: 闰月大小 (0=小月29天, 1=大月30天)
    private static final int[] LUNAR_INFO = {
            0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2,
            0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977,
            0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970,
            0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950,
            0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557,
            0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0,
            0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0,
            0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6,
            0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570,
            0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x05ac0, 0x0ab60, 0x096d5, 0x092e0,
            0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5,
            0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930,
            0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530,
            0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45,
            0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0,
            0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0,
            0x092e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4,
            0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0,
            0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160,
            0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a4d0, 0x0d150, 0x0f252,
            0x0d520
    };

    private static final int LUNAR_BASE_YEAR = 1900;
    // 1900年1月31日是农历正月初一
    private static final LocalDate LUNAR_BASE_DATE = LocalDate.of(1900, 1, 31);

    private static final String[] TIAN_GAN = { "甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸" };
    private static final String[] DI_ZHI = { "子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥" };
    private static final String[] SHENG_XIAO = { "鼠", "牛", "虎", "兔", "龙", "蛇", "马", "羊", "猴", "鸡", "狗", "猪" };

    private static final String[] MONTH_CN = { "正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊" };
    private static final String[] DAY_CN = {
            "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
            "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
            "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
    };

    // ======================== 公开 API ========================

    /**
     * 获取指定公历日期的农历信息
     *
     * @param date 公历日期
     * @return 农历描述，如 "甲子年 正月初一 (鼠年)"
     */
    public static String getLunarDate(LocalDate date) {
        int[] lunar = solarToLunar(date);
        if (lunar == null)
            return "日期超出范围 (1900-2100)";

        int year = lunar[0], month = lunar[1], day = lunar[2];
        boolean isLeap = lunar[3] == 1;

        String ganZhi = getGanZhi(year);
        String shengXiao = SHENG_XIAO[(year - 4) % 12];
        String monthStr = (isLeap ? "闰" : "") + MONTH_CN[month - 1] + "月";
        String dayStr = DAY_CN[day - 1];

        return String.format("%s年 %s%s (%s年)", ganZhi, monthStr, dayStr, shengXiao);
    }

    /**
     * 获取今天的农历日期
     */
    public static String getLunarToday() {
        return getLunarDate(LocalDate.now(ZoneId.of("Asia/Shanghai")));
    }

    /**
     * 判断指定日期是否为中国法定节假日
     *
     * @param date 公历日期
     * @return 节假日名称，非节假日返回 null
     */
    public static String getHoliday(LocalDate date) {
        // 公历节日（含清明动态计算）
        String solarHoliday = getSolarHoliday(date);
        if (solarHoliday != null)
            return solarHoliday;

        // 农历节日
        int[] lunar = solarToLunar(date);
        if (lunar != null) {
            int year = lunar[0], month = lunar[1], day = lunar[2];
            boolean isLeap = lunar[3] == 1;

            // 除夕特殊处理：腊月最后一天
            if (!isLeap && month == 12) {
                int lastDay = getLunarMonthDays(year, 12);
                if (day == lastDay) {
                    return "除夕";
                }
            }

            return getLunarHoliday(month, day, isLeap);
        }
        return null;
    }

    /**
     * 判断是否为节假日（公历+农历）
     */
    public static boolean isHoliday(LocalDate date) {
        return getHoliday(date) != null;
    }

    /**
     * 获取距离下一个主要节日的天数
     */
    public static Map<String, Object> getNextMajorHoliday(LocalDate from) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 公历固定节日
        int[][] majorSolarDates = {
                { 1, 1 }, { 2, 14 }, { 3, 8 }, { 5, 1 }, { 6, 1 }, { 10, 1 }, { 12, 25 }
        };
        String[] majorSolarNames = {
                "元旦", "情人节", "妇女节", "劳动节", "儿童节", "国庆节", "圣诞节"
        };

        String nearest = null;
        long minDays = Long.MAX_VALUE;
        LocalDate nearestDate = null;

        for (int i = 0; i < majorSolarDates.length; i++) {
            LocalDate holiday = LocalDate.of(from.getYear(), majorSolarDates[i][0], majorSolarDates[i][1]);
            if (!holiday.isAfter(from)) {
                holiday = holiday.plusYears(1);
            }
            long days = java.time.temporal.ChronoUnit.DAYS.between(from, holiday);
            if (days < minDays) {
                minDays = days;
                nearest = majorSolarNames[i];
                nearestDate = holiday;
            }
        }

        if (nearest != null) {
            result.put("holiday", nearest);
            result.put("date", nearestDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
            result.put("daysLeft", minDays);
        }

        return result;
    }

    // ======================== 核心转换 ========================

    /**
     * 公历转农历
     *
     * @return [year, month, day, isLeap(0/1)]，超出范围返回 null
     */
    public static int[] solarToLunar(LocalDate solarDate) {
        if (solarDate.isBefore(LUNAR_BASE_DATE) ||
                solarDate.isAfter(LocalDate.of(2100, 12, 31))) {
            return null;
        }

        int offset = (int) java.time.temporal.ChronoUnit.DAYS.between(LUNAR_BASE_DATE, solarDate);

        int lunarYear = LUNAR_BASE_YEAR;
        int yearDays;
        for (; lunarYear < LUNAR_BASE_YEAR + LUNAR_INFO.length; lunarYear++) {
            yearDays = getLunarYearDays(lunarYear);
            if (offset < yearDays)
                break;
            offset -= yearDays;
        }

        if (lunarYear >= LUNAR_BASE_YEAR + LUNAR_INFO.length)
            return null;

        int leapMonth = getLeapMonth(lunarYear);
        boolean isLeap = false;
        int lunarMonth = 1;
        int monthDays;

        for (; lunarMonth <= 12; lunarMonth++) {
            // 非闰月天数
            monthDays = getLunarMonthDays(lunarYear, lunarMonth);
            if (offset < monthDays)
                break;
            offset -= monthDays;

            // 闰月
            if (lunarMonth == leapMonth) {
                monthDays = getLeapMonthDays(lunarYear);
                if (offset < monthDays) {
                    isLeap = true;
                    break;
                }
                offset -= monthDays;
            }
        }

        int lunarDay = offset + 1;
        return new int[] { lunarYear, lunarMonth, lunarDay, isLeap ? 1 : 0 };
    }

    // ======================== 农历数据查询 ========================

    /** 农历某年的总天数 */
    private static int getLunarYearDays(int year) {
        int idx = year - LUNAR_BASE_YEAR;
        if (idx < 0 || idx >= LUNAR_INFO.length)
            return 0;

        int sum = 0;
        int info = LUNAR_INFO[idx];
        for (int i = 0x8000; i > 0x8; i >>= 1) {
            sum += (info & i) != 0 ? 30 : 29;
        }
        // 加上闰月
        int leapDays = getLeapMonthDays(year);
        return sum + leapDays;
    }

    /** 农历某年某月的天数 (非闰月) */
    private static int getLunarMonthDays(int year, int month) {
        int idx = year - LUNAR_BASE_YEAR;
        if (idx < 0 || idx >= LUNAR_INFO.length)
            return 0;
        return (LUNAR_INFO[idx] & (0x10000 >> month)) != 0 ? 30 : 29;
    }

    /** 农历某年闰月的天数 (0=无闰月) */
    private static int getLeapMonthDays(int year) {
        int leapMonth = getLeapMonth(year);
        if (leapMonth == 0)
            return 0;
        int idx = year - LUNAR_BASE_YEAR;
        return (LUNAR_INFO[idx] & 0x10000) != 0 ? 30 : 29;
    }

    /** 农历某年闰几月 (0=无闰月) */
    private static int getLeapMonth(int year) {
        int idx = year - LUNAR_BASE_YEAR;
        if (idx < 0 || idx >= LUNAR_INFO.length)
            return 0;
        return LUNAR_INFO[idx] & 0xf;
    }

    /** 天干地支 */
    private static String getGanZhi(int year) {
        int ganIdx = (year - 4) % 10;
        int zhiIdx = (year - 4) % 12;
        return TIAN_GAN[ganIdx] + DI_ZHI[zhiIdx];
    }

    // ======================== 节假日 ========================

    /**
     * 计算清明节日期（寿星天文算法）
     * <p>
     * 清明是二十四节气之一，非固定日期，需通过天文公式计算。
     * 公式来源：寿星万年历算法
     *
     * @param year 公历年份（1900-2100）
     * @return 4月的某天（通常是4日或5日）
     */
    public static int calculateQingming(int year) {
        // 特殊年份修正
        if (year == 2008) return 4;

        double base = (year < 2000) ? 5.59 : 4.81;
        int c = year % 100;
        int day = (int) (c * 0.2422 + base) - (int) (c / 4.0);
        return day;
    }

    /** 公历节日查询（含清明动态计算） */
    private static String getSolarHoliday(LocalDate date) {
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();

        // 清明节：4月动态日期
        if (month == 4 && day == calculateQingming(date.getYear())) {
            return "清明节";
        }

        return switch (month * 100 + day) {
            case 101 -> "元旦";
            case 214 -> "情人节";
            case 308 -> "妇女节";
            case 312 -> "植树节";
            case 401 -> "愚人节";
            case 501 -> "劳动节";
            case 504 -> "青年节";
            case 601 -> "儿童节";
            case 701 -> "建党节";
            case 801 -> "建军节";
            case 910 -> "教师节";
            case 1001 -> "国庆节";
            case 1225 -> "圣诞节";
            default -> null;
        };
    }

    /** 农历节日查询 */
    private static String getLunarHoliday(int month, int day, boolean isLeap) {
        if (isLeap)
            return null; // 闰月不算节日

        // 除夕：腊月最后一天（可能是29或30）
        if (month == 12 && (day == 29 || day == 30)) {
            // 需要判断当年腊月是大月还是小月
            // 如果是30日一定是除夕（大月最后一天）
            // 如果是29日需要确认腊月只有29天（小月最后一天）
            if (day == 30) return "除夕";
            // day == 29 时，因为调用方已知是腊月，此处简化处理：
            // 实际上应确认腊月天数，但查表需要年份信息；
            // 为简化，当月=12且日=29时也标记为可能的除夕
            // 更精确的判断在 getHoliday() 层处理
        }

        return switch (month * 100 + day) {
            case 101 -> "春节";
            case 115 -> "元宵节";
            case 202 -> "龙抬头";
            case 505 -> "端午节";
            case 707 -> "七夕";
            case 715 -> "中元节";
            case 815 -> "中秋节";
            case 909 -> "重阳节";
            case 1208 -> "腊八节";
            case 1223 -> "小年（北方）";
            case 1224 -> "小年（南方）";
            default -> null;
        };
    }
}
