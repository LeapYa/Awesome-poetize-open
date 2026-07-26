package com.ld.poetry.controller;

import com.ld.poetry.service.RssService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * RSS订阅控制器
 * 提供rss.xml访问接口
 *
 * @author LeapYa
 * @since 2026-07-25
 */
@RestController
@Slf4j
public class RssController {

    private static final MediaType RSS_MEDIA_TYPE = MediaType.parseMediaType("application/rss+xml;charset=UTF-8");

    @Autowired
    private RssService rssService;

    /**
     * 获取RSS订阅源
     * 访问路径：/rss.xml（/feed、/feed.xml 为别名，兼容各类阅读器的路径惯例）
     * 可选参数：lang=xx 输出该语言翻译版订阅源（条目链接指向 /article/xx/id 翻译页）
     */
    @GetMapping(value = {"/rss.xml", "/feed", "/feed.xml"}, produces = "application/rss+xml;charset=UTF-8")
    public ResponseEntity<String> getRss(@RequestParam(value = "lang", required = false) String lang) {
        try {
            String rssContent = rssService.generateRss(lang);

            if (StringUtils.hasText(rssContent)) {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(RSS_MEDIA_TYPE);
                headers.setCacheControl("max-age=3600"); // 缓存1小时
                headers.add("X-Robots-Tag", "noindex"); // 防止搜索引擎索引订阅源本身

                return ResponseEntity.ok()
                        .headers(headers)
                        .body(rssContent);
            } else {
                log.warn("RSS生成失败");
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("获取rss.xml时发生错误", e);
            return ResponseEntity.internalServerError()
                    .body("<?xml version=\"1.0\" encoding=\"UTF-8\"?><error>RSS生成失败</error>");
        }
    }
}
