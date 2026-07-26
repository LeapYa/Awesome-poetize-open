package com.ld.poetry.controller;

import com.ld.poetry.service.SeoStaticService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 * SEO公开功能控制器
 * 提供SEO静态文件服务（manifest.json、robots.txt，由nginx代理访问）
 * </p>
 *
 * @author LeapYa
 * @since 2025-09-26
 */
@RestController
@RequestMapping("/seo")
@Slf4j
public class SeoController {

    @Autowired
    private SeoStaticService seoStaticService;

    // ========== 静态文件生成API（公开接口） ==========

    /**
     * 动态生成PWA manifest.json
     */
    @GetMapping(value = "/manifest.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getManifestJson(HttpServletRequest request) {
        try {
            Map<String, Object> manifest = seoStaticService.generateManifestJson(request);
            
            if (manifest.containsKey("error")) {
                return ResponseEntity.status(404).body(manifest);
            }

            return ResponseEntity.ok()
                    .header("Cache-Control", "public, max-age=3600")
                    .body(manifest);
        } catch (Exception e) {
            log.error("生成manifest.json失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "生成PWA manifest失败");
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 生成robots.txt
     */
    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getRobotsTxt(HttpServletRequest request) {
        try {
            String robotsTxt = seoStaticService.generateRobotsTxt(request);
            
            return ResponseEntity.ok()
                    .header("Cache-Control", "public, max-age=3600")
                    .body(robotsTxt);
        } catch (Exception e) {
            log.error("生成robots.txt失败", e);
            return ResponseEntity.status(500)
                    .body("# robots.txt生成失败");
        }
    }
}
