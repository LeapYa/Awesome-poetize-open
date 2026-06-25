package com.ld.poetry.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.dao.SysAuditLogMapper;
import com.ld.poetry.entity.SysAuditLog;
import com.ld.poetry.entity.User;
import com.ld.poetry.service.LocationService;
import com.ld.poetry.service.SysAuditLogService;
import com.ld.poetry.utils.JsonUtils;
import com.ld.poetry.utils.PoetryUtil;
import com.ld.poetry.vo.SysAuditLogQueryVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
public class SysAuditLogServiceImpl extends ServiceImpl<SysAuditLogMapper, SysAuditLog> implements SysAuditLogService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter ISO_DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Autowired
    private LocationService locationService;

    @Override
    public PoetryResult<Page<SysAuditLog>> listLogs(SysAuditLogQueryVO queryVO) {
        SysAuditLogQueryVO query = queryVO == null ? new SysAuditLogQueryVO() : queryVO;
        long current = query.getCurrent() > 0 ? query.getCurrent() : 1;
        long size = query.getSize() > 0 ? Math.min(query.getSize(), MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;

        Page<SysAuditLog> page = new Page<>(current, size);
        LambdaQueryWrapper<SysAuditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(query.getLogType()), SysAuditLog::getLogType, query.getLogType());
        wrapper.eq(StringUtils.hasText(query.getAction()), SysAuditLog::getAction, query.getAction());
        wrapper.eq(query.getSuccess() != null, SysAuditLog::getSuccess, query.getSuccess());
        wrapper.like(StringUtils.hasText(query.getIp()), SysAuditLog::getIp, query.getIp());

        LocalDateTime startTime = parseDateTime(query.getStartTime(), false);
        LocalDateTime endTime = parseDateTime(query.getEndTime(), true);
        wrapper.ge(startTime != null, SysAuditLog::getCreateTime, startTime);
        wrapper.le(endTime != null, SysAuditLog::getCreateTime, endTime);

        if (StringUtils.hasText(query.getSearchKey())) {
            String searchKey = query.getSearchKey().trim();
            wrapper.and(item -> item
                    .like(SysAuditLog::getUsername, searchKey)
                    .or().like(SysAuditLog::getMaskedAccount, searchKey)
                    .or().like(SysAuditLog::getSummary, searchKey)
                    .or().like(SysAuditLog::getAction, searchKey)
                    .or().like(SysAuditLog::getTargetId, searchKey));
        }

        wrapper.orderByDesc(SysAuditLog::getCreateTime).orderByDesc(SysAuditLog::getId);
        return PoetryResult.success(page(page, wrapper));
    }

    @Override
    public void recordLogin(String action, boolean success, String account, Integer userId, String username,
                            String summary, Map<String, Object> detail) {
        record("LOGIN", action, success, account, userId, username, null, null, summary, detail, null, null, null);
    }

    @Override
    public void recordSecurity(String action, boolean success, String account, Integer userId, String username,
                               String summary, Map<String, Object> detail) {
        record("SECURITY", action, success, account, userId, username, null, null, summary, detail, null, null, null);
    }

    @Override
    public void recordOperation(String logType, String action, boolean success, String targetType, String targetId,
                                String summary, Map<String, Object> detail) {
        User user = PoetryUtil.getCurrentUser();
        record(StringUtils.hasText(logType) ? logType : "OPERATION", action, success,
                null,
                user == null ? null : user.getId(),
                user == null ? null : user.getUsername(),
                targetType, targetId, summary, detail, null, null, null);
    }

    @Override
    public void recordAi(String action, boolean success, String targetType, String targetId,
                         String summary, Map<String, Object> detail,
                         Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        // 同步调用：HTTP 请求上下文可用，从 PoetryUtil 解析调用主体
        User user = PoetryUtil.getCurrentUser();
        record("AI", action, success,
                null,
                user == null ? null : user.getId(),
                user == null ? null : user.getUsername(),
                targetType, targetId, summary, detail,
                promptTokens, completionTokens, totalTokens);
    }

    @Override
    public void recordAi(String action, boolean success, String targetType, String targetId,
                         String summary, Map<String, Object> detail,
                         Integer promptTokens, Integer completionTokens, Integer totalTokens,
                         Integer callerUserId, String callerUsername, String callerIp, String callerLocation) {
        // 流式回调/异步任务：HTTP 上下文已丢失，直接使用调用方预先捕获的 caller 信息
        record("AI", action, success,
                null,
                callerUserId, callerUsername,
                targetType, targetId, summary, detail,
                promptTokens, completionTokens, totalTokens,
                callerIp, callerLocation);
    }

    @Override
    public int cleanExpiredLogs(int retentionDays) {
        int days = retentionDays > 0 ? retentionDays : 180;
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        LambdaQueryWrapper<SysAuditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.lt(SysAuditLog::getCreateTime, cutoff);
        long count = count(wrapper);
        if (count > 0) {
            remove(wrapper);
        }
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }

    private void record(String logType, String action, boolean success, String account, Integer userId, String username,
                        String targetType, String targetId, String summary, Map<String, Object> detail,
                        Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        record(logType, action, success, account, userId, username,
                targetType, targetId, summary, detail,
                promptTokens, completionTokens, totalTokens,
                null, null);
    }

    /**
     * 写入审计日志核心方法。
     *
     * @param callerIp       调用方 IP（可空）：非空时直接使用，null 时从 HTTP 请求兜底
     * @param callerLocation 调用方地理位置（可空）：非空时直接使用，null 时根据 ip 解析
     */
    private void record(String logType, String action, boolean success, String account, Integer userId, String username,
                        String targetType, String targetId, String summary, Map<String, Object> detail,
                        Integer promptTokens, Integer completionTokens, Integer totalTokens,
                        String callerIp, String callerLocation) {
        try {
            HttpServletRequest request = PoetryUtil.getRequest();
            String ip = callerIp != null ? callerIp : (request == null ? "unknown" : PoetryUtil.getIpAddr(request));

            SysAuditLog auditLog = new SysAuditLog();
            auditLog.setLogType(limit(logType, 32));
            auditLog.setAction(limit(action, 64));
            auditLog.setSuccess(success);
            String loginAccount = normalizeAccount(account);
            if (sameText(loginAccount, username)) {
                loginAccount = null;
            }
            auditLog.setMaskedAccount(limit(loginAccount, 128));
            auditLog.setUserId(userId);
            auditLog.setUsername(limit(username, 64));
            auditLog.setIp(limit(ip, 128));
            auditLog.setLocation(limit(callerLocation != null ? callerLocation : resolveLocation(ip), 128));
            auditLog.setUserAgent(limit(request == null ? null : request.getHeader("User-Agent"), 512));
            auditLog.setRequestUri(limit(request == null ? null : request.getRequestURI(), 512));
            auditLog.setTargetType(limit(targetType, 64));
            auditLog.setTargetId(limit(targetId, 128));
            auditLog.setSummary(limit(summary, 512));
            auditLog.setDetail(limit(toDetailJson(detail), 4096));
            auditLog.setPromptTokens(promptTokens);
            auditLog.setCompletionTokens(completionTokens);
            auditLog.setTotalTokens(totalTokens);
            auditLog.setCreateTime(LocalDateTime.now());
            save(auditLog);
        } catch (Exception e) {
            log.warn("写入审计日志失败: action={}, success={}, error={}", action, success, e.getMessage());
        }
    }

    private LocalDateTime parseDateTime(String value, boolean endOfDay) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        try {
            if (text.length() == 10) {
                LocalDate date = LocalDate.parse(text);
                return endOfDay ? date.atTime(LocalTime.MAX) : date.atStartOfDay();
            }
            if (text.contains("T")) {
                return LocalDateTime.parse(text, ISO_DATE_TIME_FORMATTER);
            }
            return LocalDateTime.parse(text, DATE_TIME_FORMATTER);
        } catch (Exception e) {
            log.warn("审计日志时间筛选参数解析失败: {}", value);
            return null;
        }
    }

    private String resolveLocation(String ip) {
        try {
            return locationService.getLocationByIp(ip);
        } catch (Exception e) {
            return "未知";
        }
    }

    private String toDetailJson(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) {
            return null;
        }
        Map<String, Object> safeDetail = new LinkedHashMap<>();
        detail.forEach((key, value) -> {
            if (!isSensitiveKey(key)) {
                safeDetail.put(key, sanitizeValue(value));
            }
        });
        return safeDetail.isEmpty() ? null : JsonUtils.toJsonString(safeDetail);
    }

    private Object sanitizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof CharSequence text) {
            return limit(text.toString(), 512);
        }
        return value;
    }

    private boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String lowerKey = key.toLowerCase(Locale.ROOT);
        return lowerKey.contains("password")
                || lowerKey.contains("token")
                || lowerKey.contains("secret")
                || lowerKey.contains("credential")
                || lowerKey.contains("api_key")
                || lowerKey.contains("apikey")
                || lowerKey.endsWith("key")
                || lowerKey.contains("verification");
    }

    private String normalizeAccount(String account) {
        if (!StringUtils.hasText(account)) {
            return null;
        }
        return account.trim();
    }

    private boolean sameText(String left, String right) {
        return StringUtils.hasText(left) && StringUtils.hasText(right) && left.trim().equalsIgnoreCase(right.trim());
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
