package com.ld.poetry.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.ld.poetry.constants.CacheConstants;
import com.ld.poetry.utils.CommonQuery;
import com.ld.poetry.utils.IpUtil;
import com.ld.poetry.utils.PageVisitUtils;
import com.ld.poetry.utils.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 消费 Nginx 页面访问日志，补充爬虫/搜索引擎直接抓取 HTML 时不会调用前端接口的问题。
 */
@Slf4j
@Component
public class NginxPageVisitLogConsumer {

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private CommonQuery commonQuery;

    @Value("${visit.nginx-log.enabled:true}")
    private boolean enabled;

    @Value("${visit.nginx-log.path:/app/nginx-logs/page_visit.log}")
    private String logPath;

    @Value("${visit.nginx-log.max-lines-per-run:5000}")
    private int maxLinesPerRun;

    @Scheduled(
            fixedDelayString = "${visit.nginx-log.interval-ms:60000}",
            initialDelayString = "${visit.nginx-log.initial-delay-ms:15000}"
    )
    public void consumePageVisitLog() {
        if (!enabled) {
            return;
        }

        Path path = Path.of(logPath);
        if (!Files.isRegularFile(path)) {
            return;
        }

        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            long fileLength = file.length();
            long offset = getOffset();
            if (offset > fileLength) {
                log.info("Nginx页面访问日志发生轮转，重置消费进度: {} -> 0", offset);
                offset = 0L;
            }

            file.seek(offset);
            int processed = 0;
            String line;
            while (processed < maxLinesPerRun && (line = file.readLine()) != null) {
                processed++;
                consumeLine(new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8));
            }

            saveOffset(file.getFilePointer());
        } catch (Exception e) {
            log.warn("消费Nginx页面访问日志失败: {}", e.getMessage());
        }
    }

    private void consumeLine(String line) {
        if (line == null || line.isBlank()) {
            return;
        }

        try {
            JSONObject json = JSON.parseObject(line);
            String method = json.getString("method");
            if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
                return;
            }

            int status = json.getIntValue("status");
            if (status < 200 || status >= 400) {
                return;
            }

            String uri = PageVisitUtils.normalizeVisitUri(json.getString("uri"));
            if (!PageVisitUtils.isPageVisit(uri)) {
                return;
            }

            String clientIp = IpUtil.extractPublicIpFromForwardedFor(json.getString("xff"));
            if (!hasText(clientIp) && IpUtil.isPublicRoutableIp(json.getString("ip"))) {
                clientIp = json.getString("ip").trim();
            }
            if (!hasText(clientIp)) {
                return;
            }

            commonQuery.saveHistory(
                    clientIp.trim(),
                    uri,
                    blankToNull(json.getString("user_agent")),
                    blankToNull(json.getString("referer")),
                    null
            );
        } catch (Exception e) {
            log.debug("跳过无法解析的Nginx页面访问日志: {}", e.getMessage());
        }
    }

    private long getOffset() {
        Object value = redisUtil.get(CacheConstants.NGINX_PAGE_VISIT_LOG_OFFSET_KEY);
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private void saveOffset(long offset) {
        redisUtil.set(CacheConstants.NGINX_PAGE_VISIT_LOG_OFFSET_KEY, offset, CacheConstants.PERMANENT_EXPIRE_TIME);
    }

    private String blankToNull(String value) {
        if (!hasText(value) || "-".equals(value.trim())) {
            return null;
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
