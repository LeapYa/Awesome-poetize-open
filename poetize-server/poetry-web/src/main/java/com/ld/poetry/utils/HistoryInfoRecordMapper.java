package com.ld.poetry.utils;

import com.ld.poetry.entity.HistoryInfo;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Redis访问记录到数据库实体的统一映射。
 */
public final class HistoryInfoRecordMapper {

    private HistoryInfoRecordMapper() {
    }

    public static HistoryInfo fromVisitRecord(Map<String, Object> record,
                                              DateTimeFormatter formatter,
                                              LocalDateTime defaultCreateTime) {
        HistoryInfo historyInfo = new HistoryInfo();
        historyInfo.setIp(text(record.get("ip")));

        Object userIdObj = record.get("userId");
        if (userIdObj != null) {
            historyInfo.setUserId(Integer.valueOf(userIdObj.toString()));
        }

        historyInfo.setNation(text(record.get("nation")));
        historyInfo.setProvince(text(record.get("province")));
        historyInfo.setCity(text(record.get("city")));
        historyInfo.setPageUri(text(record.get("pageUri")));
        historyInfo.setUserAgent(text(record.get("userAgent")));
        historyInfo.setUaType(text(firstNonNull(record.get("uaType"), record.get("ua_type"))));
        historyInfo.setUaName(text(firstNonNull(record.get("uaName"), record.get("ua_name"))));
        historyInfo.setBotVerifyStatus(text(firstNonNull(record.get("botVerifyStatus"), record.get("bot_verify_status"))));
        historyInfo.setBotVerifyReason(text(firstNonNull(record.get("botVerifyReason"), record.get("bot_verify_reason"))));

        String createTimeStr = text(record.get("createTime"));
        if (createTimeStr != null) {
            historyInfo.setCreateTime(LocalDateTime.parse(createTimeStr, formatter));
        } else {
            historyInfo.setCreateTime(defaultCreateTime);
        }

        return historyInfo;
    }

    private static Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }
}
