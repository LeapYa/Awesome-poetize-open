package com.ld.poetry.utils;

import com.ld.poetry.handle.PoetryRuntimeException;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 时间段筛选参数解析工具
 *
 * <p>入参格式：每个区间为 {@code start~end}，两端均可省略其一（{@code ~end} 表示
 * 某时间点之前，{@code start~} 表示某时间点之后），但不能两端同时省略。
 * 时间支持 {@code yyyy-MM-dd HH:mm:ss}、{@code yyyy-MM-dd'T'HH:mm:ss}、
 * {@code yyyy-MM-dd HH:mm}、{@code yyyy-MM-dd} 四种写法；仅传日期时，
 * 起点取当天 00:00:00，终点取当天 23:59:59（闭区间，含端点）。
 *
 * <p>解析失败抛出 {@link PoetryRuntimeException}，避免筛选条件被静默忽略后
 * 返回未过滤的结果误导调用方。
 */
public class TimeRangeUtil {

    private static final DateTimeFormatter[] DATE_TIME_FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    };

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private TimeRangeUtil() {
    }

    /**
     * 解析后的单个时间区间，start/end 至少一个非空
     */
    public record TimeRange(LocalDateTime start, LocalDateTime end) {
    }

    /**
     * 解析一组 {@code start~end} 区间表达式；入参为空返回空列表
     *
     * @param expressions 区间表达式列表（HTTP 重复传参绑定而来）
     * @param fieldName   用于错误提示的字段名（如 createTimeRange）
     */
    public static List<TimeRange> parseRanges(List<String> expressions, String fieldName) {
        List<TimeRange> ranges = new ArrayList<>();
        if (CollectionUtils.isEmpty(expressions)) {
            return ranges;
        }
        for (String expression : expressions) {
            if (!StringUtils.hasText(expression)) {
                continue;
            }
            String trimmed = expression.trim();
            int separatorIndex = trimmed.indexOf('~');
            if (separatorIndex < 0 || trimmed.indexOf('~', separatorIndex + 1) >= 0) {
                throw new PoetryRuntimeException(fieldName + " 区间格式不正确：" + expression
                        + "，应为 start~end（如 2024-01-01~2024-03-31，两端可省略其一）");
            }
            String startText = trimmed.substring(0, separatorIndex).trim();
            String endText = trimmed.substring(separatorIndex + 1).trim();
            if (startText.isEmpty() && endText.isEmpty()) {
                throw new PoetryRuntimeException(fieldName + " 区间两端不能同时为空：" + expression);
            }
            LocalDateTime start = startText.isEmpty() ? null : parse(startText, false, fieldName);
            LocalDateTime end = endText.isEmpty() ? null : parse(endText, true, fieldName);
            if (start != null && end != null && start.isAfter(end)) {
                throw new PoetryRuntimeException(fieldName + " 区间起点晚于终点：" + expression);
            }
            ranges.add(new TimeRange(start, end));
        }
        return ranges;
    }

    private static LocalDateTime parse(String text, boolean isEnd, String fieldName) {
        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
                // 尝试下一种格式
            }
        }
        try {
            LocalDate date = LocalDate.parse(text, DATE_FORMATTER);
            // 仅传日期时终点取当天末秒，保证按天筛选为闭区间
            return isEnd ? date.atTime(23, 59, 59) : date.atStartOfDay();
        } catch (DateTimeParseException ignored) {
            throw new PoetryRuntimeException(fieldName + " 时间格式不正确：" + text
                    + "，支持 yyyy-MM-dd、yyyy-MM-dd HH:mm、yyyy-MM-dd HH:mm:ss");
        }
    }
}
