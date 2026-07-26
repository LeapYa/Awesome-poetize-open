package com.ld.poetry.service.ai.tools;

import com.ld.poetry.service.HolidayCalendarService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * 时间相关 @Tool 工具
 *
 */
@Service
public class TimeTools {

    private final HolidayCalendarService holidayCalendarService;

    public TimeTools(HolidayCalendarService holidayCalendarService) {
        this.holidayCalendarService = holidayCalendarService;
    }

    @Tool(description = "获取当前日期和时间，包含星期信息")
    public String getCurrentTime() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));
        String dayOfWeek = now.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINESE);
        return String.format("当前时间: %s %s",
                now.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss")),
                dayOfWeek);
    }

    @Tool(description = "将时间转换到不同时区")
    public String convertTimezone(
            @ToolParam(description = "目标时区，如 America/New_York, Europe/London, Asia/Tokyo") String targetTimezone) {
        try {
            ZoneId target = ZoneId.of(targetTimezone);
            ZonedDateTime now = ZonedDateTime.now(target);
            String dayOfWeek = now.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINESE);
            return String.format("%s 当前时间: %s %s",
                    targetTimezone,
                    now.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss")),
                    dayOfWeek);
        } catch (Exception e) {
            return "无效的时区: " + targetTimezone + "。请使用如 Asia/Shanghai, America/New_York 格式。";
        }
    }

    @Tool(description = "倒计时到指定日期，计算天数差")
    public String countdownTo(
            @ToolParam(description = "目标日期，格式 yyyy-MM-dd") String targetDate,
            @ToolParam(description = "事件描述") String eventName) {
        try {
            LocalDate target = LocalDate.parse(targetDate);
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
            long days = java.time.temporal.ChronoUnit.DAYS.between(today, target);

            if (days > 0) {
                return String.format("距离 %s（%s）还有 %d 天。", eventName, targetDate, days);
            } else if (days == 0) {
                return String.format("今天就是 %s（%s）！", eventName, targetDate);
            } else {
                return String.format("%s（%s）已经过去 %d 天了。", eventName, targetDate, -days);
            }
        } catch (Exception e) {
            return "日期格式错误，请使用 yyyy-MM-dd 格式，如 2026-12-25";
        }
    }

    @Tool(description = "获取指定公历日期对应的农历日期，包含天干地支、生肖、节气和节日信息")
    public String getLunarDate(
            @ToolParam(description = "公历日期，格式 yyyy-MM-dd，不传则获取今天") String date) {
        try {
            LocalDate localDate = parseOrToday(date);
            var festivalInfo = holidayCalendarService.getFestivalInfo(localDate);
            StringBuilder sb = new StringBuilder();
            sb.append(localDate).append(" 的农历是 ").append(festivalInfo.lunarDate());
            if (festivalInfo.solarTerm() != null && !festivalInfo.solarTerm().isBlank()) {
                sb.append("，节气：").append(festivalInfo.solarTerm());
            }
            if (!festivalInfo.festivals().isEmpty()) {
                sb.append("，节日：").append(String.join("、", festivalInfo.festivals()));
            }
            sb.append("；数据来源：").append(festivalInfo.source());
            return sb.toString();
        } catch (Exception e) {
            return "日期格式错误，请使用 yyyy-MM-dd 格式";
        }
    }

    @Tool(description = "获取指定日期的节日、农历和节气信息；适合回答今天是什么节、某天是不是传统节日")
    public String getFestivalInfo(
            @ToolParam(description = "日期，格式 yyyy-MM-dd，不传则查询今天") String date) {
        try {
            LocalDate localDate = parseOrToday(date);
            var festivalInfo = holidayCalendarService.getFestivalInfo(localDate);

            StringBuilder sb = new StringBuilder();
            sb.append(localDate).append(" 的农历：").append(festivalInfo.lunarDate());
            if (festivalInfo.solarTerm() != null && !festivalInfo.solarTerm().isBlank()) {
                sb.append("；节气：").append(festivalInfo.solarTerm());
            }
            if (!festivalInfo.festivals().isEmpty()) {
                sb.append("；节日：").append(String.join("、", festivalInfo.festivals()));
            } else {
                sb.append("；当天未识别到常见节日");
            }
            if (!festivalInfo.statutoryFestivals().isEmpty()) {
                sb.append("；其中属于常见法定节日的有：")
                        .append(String.join("、", festivalInfo.statutoryFestivals()));
            }
            sb.append("；数据来源：").append(festivalInfo.source());
            return sb.toString();
        } catch (Exception e) {
            return "日期格式错误，请使用 yyyy-MM-dd 格式";
        }
    }

    @Tool(description = "获取从指定日期开始最近的节日及剩余天数；适合回答距离最近节日还有多少天")
    public String getNextFestival(
            @ToolParam(description = "起始日期，格式 yyyy-MM-dd，不传则从今天开始") String date) {
        try {
            LocalDate localDate = parseOrToday(date);
            var upcomingFestival = holidayCalendarService.getNextFestival(localDate);
            if (upcomingFestival.daysLeft() == 0) {
                return String.format("%s 就是最近的节日：%s%s。",
                        upcomingFestival.date(),
                        upcomingFestival.name(),
                        upcomingFestival.statutory() ? "（常见法定节日）" : "");
            }
            return String.format("距离最近的节日 %s（%s）还有 %d 天%s。",
                    upcomingFestival.name(),
                    upcomingFestival.date(),
                    upcomingFestival.daysLeft(),
                    upcomingFestival.statutory() ? "，它属于常见法定节日" : "");
        } catch (Exception e) {
            return "日期格式错误，请使用 yyyy-MM-dd 格式";
        }
    }

    @Tool(description = "查询指定日期是否为中国法定放假日、调休上班日或普通周末；适合回答某天放不放假、要不要上班")
    public String isHoliday(
            @ToolParam(description = "日期，格式 yyyy-MM-dd，不传则判断今天") String date) {
        return getHolidaySchedule(date);
    }

    @Tool(description = "查询指定日期的中国法定放假、调休上班或周末状态；优先实时查询官方接口")
    public String getHolidaySchedule(
            @ToolParam(description = "日期，格式 yyyy-MM-dd，不传则查询今天") String date) {
        try {
            LocalDate localDate = parseOrToday(date);
            var holidayDayInfo = holidayCalendarService.getHolidayDayInfo(localDate);

            StringBuilder sb = new StringBuilder();
            sb.append(localDate).append(" 的状态：");
            switch (holidayDayInfo.dayTypeCode()) {
                case 2 -> sb.append("法定放假日");
                case 3 -> sb.append("调休上班日");
                case 1 -> sb.append("普通周末");
                default -> sb.append("工作日");
            }
            if (holidayDayInfo.displayName() != null && !holidayDayInfo.displayName().isBlank()) {
                sb.append("（").append(holidayDayInfo.displayName()).append("）");
            }
            if (holidayDayInfo.holidayName() != null && !holidayDayInfo.holidayName().isBlank()
                    && !holidayDayInfo.holidayName().equals(holidayDayInfo.displayName())) {
                sb.append("，关联节日：").append(holidayDayInfo.holidayName());
            }
            sb.append("；是否休息：").append(holidayDayInfo.offDay() ? "是" : "否");
            sb.append("；结果口径：")
                    .append(holidayDayInfo.official() ? "official" : (holidayDayInfo.predicted() ? "predicted" : "calculated"));
            sb.append("；数据来源：").append(holidayDayInfo.source());
            if (holidayDayInfo.note() != null && !holidayDayInfo.note().isBlank()) {
                sb.append("；备注：").append(holidayDayInfo.note());
            }
            return sb.toString();
        } catch (Exception e) {
            return "日期格式错误，请使用 yyyy-MM-dd 格式";
        }
    }

    @Tool(description = "获取从指定日期开始最近一次中国法定放假安排及剩余天数；适合回答距离最近法定假期还有多久")
    public String getNextHolidayBreak(
            @ToolParam(description = "起始日期，格式 yyyy-MM-dd，不传则从今天开始") String date) {
        try {
            LocalDate localDate = parseOrToday(date);
            var upcomingBreak = holidayCalendarService.getNextHolidayBreak(localDate);

            StringBuilder sb = new StringBuilder();
            if (upcomingBreak.daysLeft() == 0) {
                sb.append("当前最近的法定放假安排是 ");
            } else {
                sb.append("距离最近的法定放假安排 ");
            }
            sb.append(upcomingBreak.name())
                    .append("（").append(upcomingBreak.startDate());
            if (!upcomingBreak.endDate().equals(upcomingBreak.startDate())) {
                sb.append(" 到 ").append(upcomingBreak.endDate());
            }
            sb.append("）");

            if (upcomingBreak.daysLeft() > 0) {
                sb.append(" 还有 ").append(upcomingBreak.daysLeft()).append(" 天");
            }
            sb.append("，连续休息 ").append(upcomingBreak.durationDays()).append(" 天");
            sb.append("；结果口径：")
                    .append(upcomingBreak.official() ? "official" : (upcomingBreak.predicted() ? "predicted" : "calculated"));
            sb.append("；数据来源：").append(upcomingBreak.source());
            if (upcomingBreak.note() != null && !upcomingBreak.note().isBlank()) {
                sb.append("；备注：").append(upcomingBreak.note());
            }
            return sb.toString();
        } catch (Exception e) {
            return "日期格式错误，请使用 yyyy-MM-dd 格式";
        }
    }

    private LocalDate parseOrToday(String date) {
        if (date == null || date.isBlank()) {
            return LocalDate.now(ZoneId.of("Asia/Shanghai"));
        }
        return LocalDate.parse(date);
    }
}
