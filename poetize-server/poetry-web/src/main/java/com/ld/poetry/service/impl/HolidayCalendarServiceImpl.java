package com.ld.poetry.service.impl;

import cn.hutool.core.date.ChineseDate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.ld.poetry.service.HolidayCalendarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class HolidayCalendarServiceImpl implements HolidayCalendarService {

    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String OFFICIAL_API_BASE_URL = "https://timor.tech/api/holiday";
    private static final int NEXT_FESTIVAL_SCAN_DAYS = 400;
    private static final int OFFICIAL_LOOKAHEAD_DAYS = 120;
    private static final int OFFICIAL_LOOKBACK_DAYS = 7;
    private static final int BATCH_LIMIT = 50;

    private static final Map<MonthDay, String> SOLAR_FESTIVALS = createSolarFestivals();
    private static final Set<String> STATUTORY_FESTIVALS = Set.of(
            "元旦", "春节", "清明节", "劳动节", "端午节", "中秋节", "国庆节");

    private final RestTemplate restTemplate;
    private final JsonMapper objectMapper;

    @Override
    public FestivalInfo getFestivalInfo(LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now(SHANGHAI_ZONE);
        ChineseDate chineseDate = buildChineseDate(targetDate);
        List<String> festivals = collectFestivals(targetDate, chineseDate);
        List<String> statutoryFestivals = festivals.stream()
                .filter(STATUTORY_FESTIVALS::contains)
                .toList();

        return new FestivalInfo(
                targetDate,
                formatLunarDate(chineseDate),
                chineseDate != null ? chineseDate.getChineseZodiac() : null,
                chineseDate != null ? chineseDate.getCyclical() : null,
                normalizeText(chineseDate != null ? chineseDate.getTerm() : null),
                festivals,
                statutoryFestivals,
                "calculated");
    }

    @Override
    public UpcomingFestival getNextFestival(LocalDate fromDate) {
        LocalDate startDate = fromDate != null ? fromDate : LocalDate.now(SHANGHAI_ZONE);

        for (int offset = 0; offset <= NEXT_FESTIVAL_SCAN_DAYS; offset++) {
            LocalDate targetDate = startDate.plusDays(offset);
            FestivalInfo info = getFestivalInfo(targetDate);
            if (!info.festivals().isEmpty()) {
                String name = !info.statutoryFestivals().isEmpty()
                        ? info.statutoryFestivals().getFirst()
                        : info.festivals().getFirst();
                return new UpcomingFestival(name, targetDate, offset, !info.statutoryFestivals().isEmpty());
            }
        }

        throw new IllegalStateException("未找到未来节日信息");
    }

    @Override
    public HolidayDayInfo getHolidayDayInfo(LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now(SHANGHAI_ZONE);
        try {
            return fetchOfficialDayInfo(targetDate);
        } catch (Exception ex) {
            log.warn("获取官方节假日信息失败，转为本地推断: date={}, error={}", targetDate, ex.getMessage());
            return buildFallbackHolidayDayInfo(targetDate,
                    "官方节假日接口暂时不可用，当前结果基于周末和节日规则推断");
        }
    }

    @Override
    public UpcomingHolidayBreak getNextHolidayBreak(LocalDate fromDate) {
        LocalDate startDate = fromDate != null ? fromDate : LocalDate.now(SHANGHAI_ZONE);
        try {
            Map<LocalDate, HolidayDayInfo> officialDays = fetchOfficialDayInfos(
                    startDate.minusDays(OFFICIAL_LOOKBACK_DAYS),
                    startDate.plusDays(OFFICIAL_LOOKAHEAD_DAYS));
            UpcomingHolidayBreak officialBreak = buildOfficialUpcomingBreak(startDate, officialDays);
            if (officialBreak != null) {
                return officialBreak;
            }
        } catch (Exception ex) {
            log.warn("获取最近官方放假安排失败，转为本地节日推断: date={}, error={}", startDate, ex.getMessage());
        }

        UpcomingFestival upcomingFestival = getNextStatutoryFestival(startDate);
        LocalDate predictedStart = upcomingFestival.date();
        LocalDate predictedEnd = upcomingFestival.date();
        while (isWeekend(predictedStart.minusDays(1))) {
            predictedStart = predictedStart.minusDays(1);
        }
        while (isWeekend(predictedEnd.plusDays(1))) {
            predictedEnd = predictedEnd.plusDays(1);
        }

        String note = "官方尚未发布或接口未返回该时段安排；当前仅按法定节日本身日期与自然周末拼接预测，不含调休补班";
        return new UpcomingHolidayBreak(
                upcomingFestival.name(),
                predictedStart,
                predictedEnd,
                Math.max(0, ChronoUnit.DAYS.between(startDate, predictedStart)),
                (int) ChronoUnit.DAYS.between(predictedStart, predictedEnd) + 1,
                false,
                true,
                "local-calculation",
                note);
    }

    private UpcomingHolidayBreak buildOfficialUpcomingBreak(LocalDate fromDate, Map<LocalDate, HolidayDayInfo> dayMap) {
        if (dayMap.isEmpty()) {
            return null;
        }

        List<HolidayDayInfo> sortedDays = dayMap.values().stream()
                .sorted(Comparator.comparing(HolidayDayInfo::date))
                .toList();

        List<List<HolidayDayInfo>> segments = new ArrayList<>();
        List<HolidayDayInfo> currentSegment = new ArrayList<>();
        LocalDate previousDate = null;

        for (HolidayDayInfo dayInfo : sortedDays) {
            if (!dayInfo.offDay()) {
                if (!currentSegment.isEmpty()) {
                    segments.add(List.copyOf(currentSegment));
                    currentSegment.clear();
                }
                previousDate = dayInfo.date();
                continue;
            }

            if (previousDate != null && !currentSegment.isEmpty()
                    && !previousDate.plusDays(1).equals(dayInfo.date())) {
                segments.add(List.copyOf(currentSegment));
                currentSegment.clear();
            }

            currentSegment.add(dayInfo);
            previousDate = dayInfo.date();
        }

        if (!currentSegment.isEmpty()) {
            segments.add(List.copyOf(currentSegment));
        }

        for (List<HolidayDayInfo> segment : segments) {
            boolean containsOfficialHoliday = segment.stream().anyMatch(day -> day.dayTypeCode() == 2);
            if (!containsOfficialHoliday) {
                continue;
            }

            LocalDate segmentStart = segment.getFirst().date();
            LocalDate segmentEnd = segment.getLast().date();
            if (segmentEnd.isBefore(fromDate)) {
                continue;
            }

            String holidayName = segment.stream()
                    .filter(day -> day.dayTypeCode() == 2 && StringUtils.hasText(day.holidayName()))
                    .map(HolidayDayInfo::holidayName)
                    .findFirst()
                    .orElseGet(() -> segment.stream()
                            .filter(day -> StringUtils.hasText(day.displayName()))
                            .map(HolidayDayInfo::displayName)
                            .findFirst()
                            .orElse("最近法定假期"));

            long daysLeft = Math.max(0, ChronoUnit.DAYS.between(fromDate, segmentStart));
            int durationDays = (int) ChronoUnit.DAYS.between(segmentStart, segmentEnd) + 1;

            return new UpcomingHolidayBreak(
                    holidayName,
                    segmentStart,
                    segmentEnd,
                    daysLeft,
                    durationDays,
                    true,
                    false,
                    "timor.tech",
                    "实时查询官方节假日安排");
        }

        return null;
    }

    private HolidayDayInfo fetchOfficialDayInfo(LocalDate date) {
        String url = OFFICIAL_API_BASE_URL + "/info/" + date.format(ISO_DATE);
        JsonNode root = readJson(url);
        JsonNode typeNode = root.path("type");
        JsonNode holidayNode = root.path("holiday");
        return buildOfficialHolidayDayInfo(date, typeNode, holidayNode);
    }

    private Map<LocalDate, HolidayDayInfo> fetchOfficialDayInfos(LocalDate startDate, LocalDate endDate) {
        LinkedHashMap<LocalDate, HolidayDayInfo> result = new LinkedHashMap<>();
        List<LocalDate> dates = new ArrayList<>();
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            dates.add(cursor);
            cursor = cursor.plusDays(1);
        }

        for (int start = 0; start < dates.size(); start += BATCH_LIMIT) {
            List<LocalDate> batchDates = dates.subList(start, Math.min(start + BATCH_LIMIT, dates.size()));
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromUriString(OFFICIAL_API_BASE_URL + "/batch")
                    .queryParam("type", "Y");
            for (LocalDate date : batchDates) {
                builder.queryParam("d", date.format(ISO_DATE));
            }

            JsonNode root = readJson(builder.toUriString());
            JsonNode typeNode = root.path("type");
            JsonNode holidayNode = root.path("holiday");
            for (LocalDate date : batchDates) {
                String key = date.format(ISO_DATE);
                result.put(date, buildOfficialHolidayDayInfo(date, typeNode.path(key), holidayNode.path(key)));
            }
        }

        return result;
    }

    private JsonNode readJson(String url) {
        try {
            String body = restTemplate.getForObject(url, String.class);
            if (!StringUtils.hasText(body)) {
                throw new IllegalStateException("响应体为空");
            }

            JsonNode root = objectMapper.readTree(body);
            if (root.path("code").asInt(-1) != 0) {
                throw new IllegalStateException("接口返回错误 code=" + root.path("code").asText());
            }
            return root;
        } catch (RestClientException ex) {
            throw new IllegalStateException("官方节假日接口请求失败", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("官方节假日接口解析失败", ex);
        }
    }

    private HolidayDayInfo buildOfficialHolidayDayInfo(LocalDate date, JsonNode typeNode, JsonNode holidayNode) {
        if ((typeNode == null || typeNode.isMissingNode() || typeNode.isNull())
                && (holidayNode == null || holidayNode.isMissingNode() || holidayNode.isNull())) {
            return buildFallbackHolidayDayInfo(date, "官方接口未返回该日期数据，当前结果基于本地规则推断");
        }

        int typeCode = typeNode.path("type").asInt(defaultDayTypeCode(date));
        String typeName = normalizeText(typeNode.path("name").asText());
        String dayType = mapDayType(typeCode);
        boolean workday = typeCode == 0 || typeCode == 3;
        boolean offDay = typeCode == 1 || typeCode == 2;
        Integer wage = holidayNode.hasNonNull("wage") ? holidayNode.path("wage").asInt() : null;

        String displayName = StringUtils.hasText(typeName) ? typeName : defaultDisplayName(date, typeCode);
        String holidayName = null;
        String note = null;

        if (!holidayNode.isMissingNode() && !holidayNode.isNull()) {
            boolean holiday = holidayNode.path("holiday").asBoolean(typeCode == 2);
            String holidayNodeName = normalizeText(holidayNode.path("name").asText());
            String targetName = normalizeText(holidayNode.path("target").asText());
            boolean after = holidayNode.path("after").asBoolean(false);

            if (holiday) {
                holidayName = firstNonBlank(holidayNodeName, targetName);
                displayName = firstNonBlank(holidayName, displayName);
            } else {
                holidayName = firstNonBlank(targetName, holidayNodeName);
                displayName = firstNonBlank(holidayNodeName, displayName, "调休上班");
                if (StringUtils.hasText(targetName)) {
                    note = (after ? "节后调休" : "节前调休") + "，服务于 " + targetName;
                }
            }
        }

        return new HolidayDayInfo(
                date,
                typeCode,
                dayType,
                displayName,
                holidayName,
                workday,
                offDay,
                true,
                false,
                "timor.tech",
                wage,
                note);
    }

    private HolidayDayInfo buildFallbackHolidayDayInfo(LocalDate date, String note) {
        return buildPredictedHolidayDayInfo(date, note);
    }

    private HolidayDayInfo buildPredictedHolidayDayInfo(LocalDate date, String note) {
        FestivalInfo festivalInfo = getFestivalInfo(date);
        boolean weekend = isWeekend(date);
        boolean statutoryFestival = !festivalInfo.statutoryFestivals().isEmpty();

        if (statutoryFestival) {
            String holidayName = festivalInfo.statutoryFestivals().getFirst();
            return new HolidayDayInfo(
                    date,
                    2,
                    "holiday",
                    holidayName,
                    holidayName,
                    false,
                    true,
                    false,
                    true,
                    "calculated",
                    null,
                    appendPredictionNote(note, "当前为预测结果，仅按节日本身日期判断，不含官方调休补班"));
        }

        if (weekend) {
            String weekendName = date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINESE);
            return new HolidayDayInfo(
                    date,
                    1,
                    "weekend",
                    weekendName,
                    null,
                    false,
                    true,
                    false,
                    true,
                    "calculated",
                    null,
                    appendPredictionNote(note, "当前为预测结果，周末休息不代表官方补休安排"));
        }

        return new HolidayDayInfo(
                date,
                0,
                "workday",
                "工作日",
                null,
                true,
                false,
                false,
                true,
                "calculated",
                null,
                appendPredictionNote(note, "当前为预测结果，后续若官方公布调休安排可能变化"));
    }

    private List<String> collectFestivals(LocalDate date, ChineseDate chineseDate) {
        LinkedHashSet<String> festivals = new LinkedHashSet<>();

        String solarFestival = getSolarFestival(date);
        if (StringUtils.hasText(solarFestival)) {
            festivals.add(solarFestival);
        }

        if (chineseDate != null) {
            String lunarFestivals = normalizeText(chineseDate.getFestivals());
            if (StringUtils.hasText(lunarFestivals)) {
                for (String festival : lunarFestivals.split(",")) {
                    String normalizedFestival = normalizeText(festival);
                    if (StringUtils.hasText(normalizedFestival)) {
                        festivals.add(normalizedFestival);
                    }
                }
            }
        }

        return List.copyOf(festivals);
    }

    private UpcomingFestival getNextStatutoryFestival(LocalDate startDate) {
        for (int offset = 0; offset <= NEXT_FESTIVAL_SCAN_DAYS; offset++) {
            LocalDate targetDate = startDate.plusDays(offset);
            FestivalInfo info = getFestivalInfo(targetDate);
            if (!info.statutoryFestivals().isEmpty()) {
                return new UpcomingFestival(info.statutoryFestivals().getFirst(), targetDate, offset, true);
            }
        }
        throw new IllegalStateException("未找到未来法定节日信息");
    }

    private ChineseDate buildChineseDate(LocalDate date) {
        try {
            return new ChineseDate(date);
        } catch (Exception ex) {
            log.debug("构建农历日期失败: date={}, error={}", date, ex.getMessage());
            return null;
        }
    }

    private String formatLunarDate(ChineseDate chineseDate) {
        if (chineseDate == null) {
            return "当前日期超出农历算法支持范围";
        }
        return String.format("%s年 %s%s (%s年)",
                chineseDate.getCyclical(),
                chineseDate.getChineseMonthName(),
                chineseDate.getChineseDay(),
                chineseDate.getChineseZodiac());
    }

    private String getSolarFestival(LocalDate date) {
        if (date.getMonthValue() == 4 && date.getDayOfMonth() == calculateQingming(date.getYear())) {
            return "清明节";
        }
        return SOLAR_FESTIVALS.get(MonthDay.from(date));
    }

    private static Map<MonthDay, String> createSolarFestivals() {
        LinkedHashMap<MonthDay, String> festivals = new LinkedHashMap<>();
        festivals.put(MonthDay.of(1, 1), "元旦");
        festivals.put(MonthDay.of(2, 14), "情人节");
        festivals.put(MonthDay.of(3, 8), "妇女节");
        festivals.put(MonthDay.of(3, 12), "植树节");
        festivals.put(MonthDay.of(4, 1), "愚人节");
        festivals.put(MonthDay.of(5, 1), "劳动节");
        festivals.put(MonthDay.of(5, 4), "青年节");
        festivals.put(MonthDay.of(6, 1), "儿童节");
        festivals.put(MonthDay.of(8, 1), "建军节");
        festivals.put(MonthDay.of(9, 10), "教师节");
        festivals.put(MonthDay.of(10, 1), "国庆节");
        festivals.put(MonthDay.of(12, 25), "圣诞节");
        return festivals;
    }

    private int calculateQingming(int year) {
        if (year == 2008) {
            return 4;
        }
        double base = year < 2000 ? 5.59 : 4.81;
        int c = year % 100;
        return (int) (c * 0.2422 + base) - (int) (c / 4.0);
    }

    private int defaultDayTypeCode(LocalDate date) {
        return isWeekend(date) ? 1 : 0;
    }

    private String mapDayType(int typeCode) {
        return switch (typeCode) {
            case 0 -> "workday";
            case 1 -> "weekend";
            case 2 -> "holiday";
            case 3 -> "makeup_workday";
            default -> "unknown";
        };
    }

    private String defaultDisplayName(LocalDate date, int typeCode) {
        return switch (typeCode) {
            case 1 -> date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINESE);
            case 2 -> "法定节假日";
            case 3 -> "调休上班";
            default -> "工作日";
        };
    }

    private boolean isWeekend(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String normalizeText(String value) {
        return value == null ? null : value.trim();
    }

    private String appendPredictionNote(String primary, String fallback) {
        if (StringUtils.hasText(primary)) {
            return StringUtils.hasText(fallback) ? primary + "；" + fallback : primary;
        }
        return fallback;
    }
}
