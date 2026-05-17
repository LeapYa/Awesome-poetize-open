package com.ld.poetry.controller;

import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.service.SeoConfigService;
import com.ld.poetry.service.SeoMetaService;
import com.ld.poetry.service.SeoStaticService;
import com.ld.poetry.service.SeoImageService;
import com.ld.poetry.service.SearchEnginePushService;
import com.ld.poetry.service.SitemapService;
import com.ld.poetry.service.ai.rag.RagSyncService;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.service.prerender.PrerenderFacade;
import com.ld.poetry.utils.security.FileSecurityValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * <p>
 * SEO管理功能控制器
 * 提供管理员专用的SEO配置和管理功能
 * </p>
 *
 * @author sara
 * @since 2024-12-23
 */
@RestController
@RequestMapping("/admin/seo")
@Slf4j
public class AdminSeoController {

    @Autowired
    private SeoConfigService seoConfigService;

    @Autowired
    private RagSyncService ragSyncService;

    @Autowired
    private SeoMetaService seoMetaService;

    @Autowired
    private SeoStaticService seoStaticService;

    @Autowired
    private SeoImageService seoImageService;

    @Autowired
    private SearchEnginePushService searchEnginePushService;

    @Autowired
    private SitemapService sitemapService;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private com.ld.poetry.service.RobotsService robotsService;

    @Autowired
    private PrerenderFacade prerenderFacade;

    @Autowired
    private FileSecurityValidator fileSecurityValidator;

    @Value("${prerender.template-path:/app/web-dist/index.html}")
    private String webTemplatePath;

    // ========== 配置管理API ==========

    /**
     * 检测网站URL
     */
    @GetMapping("/detectSiteUrl")
    public PoetryResult<Map<String, Object>> detectSiteUrl(HttpServletRequest request) {
        try {
            Map<String, Object> result = seoMetaService.detectSiteUrl(request);
            return PoetryResult.success(result);
        } catch (Exception e) {
            log.error("检测网站URL失败", e);
            return PoetryResult.fail("检测网站URL失败");
        }
    }

    /**
     * 获取SEO配置（管理员权限）
     */
    @GetMapping("/getSeoConfig")
    @LoginCheck(1)
    public PoetryResult<Map<String, Object>> getSeoConfig(HttpServletRequest request) {
        try {
            Map<String, Object> config = seoConfigService.getSeoConfigAsJson();

            return PoetryResult.success(config);
        } catch (Exception e) {
            log.error("获取SEO配置失败", e);
            return PoetryResult.fail("获取SEO配置失败");
        }
    }

    /**
     * 更新SEO配置（自动清理缓存并触发预渲染）
     */
    @PostMapping("/updateSeoConfig")
    @LoginCheck(1)
    public PoetryResult<Boolean> updateSeoConfig(@RequestBody Map<String, Object> configData) {
        try {
            boolean success = seoConfigService.updateSeoConfigFromJson(configData);
            if (success) {
                // SEO配置更新时，清除缓存、重新生成sitemap并推送、重新渲染页面
                try {
                    // 1. 重新生成sitemap并推送到搜索引擎（SEO配置变更影响所有URL）
                    if (sitemapService != null) {
                        sitemapService.updateSitemapAndPush("SEO配置更新");
                    }

                    // 2. 异步触发预渲染，避免阻塞主流程，并确保缓存数据已完全生效
                    CompletableFuture.runAsync(() -> {
                        try {
                            // 等待2秒确保缓存完全生效并可被预渲染服务读取
                            Thread.sleep(2000);
                            prerenderFacade.rebuildSite();
                        } catch (Exception e) {
                            log.warn("异步预渲染失败", e);
                        }
                    });

                } catch (Exception e) {
                    // 预渲染失败不影响主流程，只记录日志
                    log.warn("缓存清除或预渲染失败", e);
                }

                return PoetryResult.success(true);
            } else {
                return PoetryResult.fail("SEO配置更新失败");
            }
        } catch (Exception e) {
            log.error("更新SEO配置失败", e);
            return PoetryResult.fail("更新SEO配置失败");
        }
    }

    /**
     * 更新SEO启用状态（自动清理缓存）
     */
    @PostMapping("/updateEnableStatus")
    @LoginCheck(1)
    public PoetryResult<Boolean> updateEnableStatus(@RequestBody Map<String, Object> data) {
        try {
            Object enableObj = data.get("enable");
            boolean enable = enableObj instanceof Boolean ? (Boolean) enableObj
                    : Boolean.parseBoolean(enableObj.toString());

            Map<String, Object> config = seoConfigService.getSeoConfigAsJson();
            config.put("enable", enable);

            boolean success = seoConfigService.updateSeoConfigFromJson(config);
            if (success) {
                // 自动清理相关缓存
                clearSeoCache();
                return PoetryResult.success(true);
            } else {
                return PoetryResult.fail("SEO状态更新失败");
            }
        } catch (Exception e) {
            log.error("更新SEO状态失败", e);
            return PoetryResult.fail("更新SEO状态失败");
        }
    }

    // ========== 缓存管理API ==========

    /**
     * 清理SEO缓存（管理员功能）
     */
    @PostMapping("/clearCache")
    @LoginCheck(1)
    public PoetryResult<Boolean> clearCache() {
        try {
            clearSeoCache();
            return PoetryResult.success(true);
        } catch (Exception e) {
            log.error("清理SEO缓存失败", e);
            return PoetryResult.fail("清理SEO缓存失败");
        }
    }

    /**
     * 清理特定文章缓存
     */
    @PostMapping("/clearArticleCache")
    @LoginCheck(1)
    public PoetryResult<Boolean> clearArticleCache(@RequestBody Map<String, Object> data) {
        try {
            Object articleIdObj = data.get("articleId");
            if (articleIdObj != null) {
                String cacheKey = "seo:article:" + articleIdObj;
                cacheService.deleteKey(cacheKey);
            }
            return PoetryResult.success(true);
        } catch (Exception e) {
            log.error("清理文章SEO缓存失败", e);
            return PoetryResult.fail("清理文章SEO缓存失败");
        }
    }

    /**
     * 批量清理文章缓存
     */
    @PostMapping("/clearArticlesCache")
    @LoginCheck(1)
    public PoetryResult<Boolean> clearArticlesCache(@RequestBody Map<String, Object> data) {
        try {
            Object articleIdsObj = data.get("articleIds");
            if (articleIdsObj instanceof Iterable<?> articleIds) {
                for (Object articleId : articleIds) {
                    String cacheKey = "seo:article:" + articleId;
                    cacheService.deleteKey(cacheKey);
                }
            }
            return PoetryResult.success(true);
        } catch (Exception e) {
            log.error("批量清理文章SEO缓存失败", e);
            return PoetryResult.fail("批量清理文章SEO缓存失败");
        }
    }

    // ========== SEO分析API ==========

    /**
     * 分析网站SEO配置
     */
    @GetMapping("/analyzeSite")
    @LoginCheck(1)
    public PoetryResult<Map<String, Object>> analyzeSite() {
        try {
            Map<String, Object> seoConfig = seoConfigService.getSeoConfigAsJson();

            // 检查SEO是否启用
            if (!Boolean.TRUE.equals(seoConfig.get("enable"))) {
                return PoetryResult.fail(403, "SEO功能未启用");
            }

            Map<String, Object> analysis = performSeoAnalysis(seoConfig);
            return PoetryResult.success(analysis);
        } catch (Exception e) {
            log.error("SEO分析失败", e);
            return PoetryResult.fail("SEO分析失败");
        }
    }

    // ========== 图像处理API ==========

    /**
     * 处理单个图片
     */
    @PostMapping("/processImage")
    @LoginCheck(1)
    public PoetryResult<Map<String, Object>> processImage(
            @RequestParam("image") MultipartFile imageFile,
            @RequestParam(value = "target_type", defaultValue = "logo") String targetType,
            @RequestParam(value = "preferred_format", required = false) String preferredFormat) {
        try {
            // 检查文件大小是否超过Integer.MAX_VALUE，防止溢出
            long fileSize = imageFile.getSize();
            if (fileSize > Integer.MAX_VALUE) {
                log.error("文件大小超过系统限制: {} bytes, 最大允许: {} bytes", fileSize, Integer.MAX_VALUE);
                return PoetryResult.fail("文件大小超过系统限制(" + (Integer.MAX_VALUE / 1024 / 1024) + "MB)，请上传较小的文件");
            }

            // 验证文件安全性
            FileSecurityValidator.ValidationResult validationResult = fileSecurityValidator.validateFile(imageFile,
                    imageFile.getOriginalFilename(), imageFile.getContentType());

            if (!validationResult.isSuccess()) {
                log.warn("文件安全验证失败: {}", validationResult.getMessage());
                return PoetryResult.fail("文件验证失败: " + validationResult.getMessage());
            }

            Map<String, Object> result = seoImageService.processImage(imageFile, targetType, preferredFormat);

            if ((Integer) result.get("code") != 200) {
                return PoetryResult.fail(result.get("message").toString());
            }

            return PoetryResult.success(getResultData(result));
        } catch (Exception e) {
            log.error("图片处理失败", e);
            return PoetryResult.fail("图片处理失败");
        }
    }

    /**
     * 批量处理图标
     */
    @PostMapping("/batchProcessIcons")
    @LoginCheck(1)
    public PoetryResult<Map<String, Object>> batchProcessIcons(
            @RequestParam("image") MultipartFile imageFile,
            @RequestParam(value = "iconTypes", required = false) java.util.List<String> iconTypes) {
        try {
            // 检查文件大小是否超过Integer.MAX_VALUE，防止溢出
            long fileSize = imageFile.getSize();
            if (fileSize > Integer.MAX_VALUE) {
                log.error("文件大小超过系统限制: {} bytes, 最大允许: {} bytes", fileSize, Integer.MAX_VALUE);
                return PoetryResult.fail("文件大小超过系统限制(" + (Integer.MAX_VALUE / 1024 / 1024) + "MB)，请上传较小的文件");
            }

            // 验证文件安全性
            FileSecurityValidator.ValidationResult validationResult = fileSecurityValidator.validateFile(imageFile,
                    imageFile.getOriginalFilename(), imageFile.getContentType());

            if (!validationResult.isSuccess()) {
                log.warn("文件安全验证失败: {}", validationResult.getMessage());
                return PoetryResult.fail("文件验证失败: " + validationResult.getMessage());
            }

            Map<String, Object> result = seoImageService.batchProcessIcons(imageFile, iconTypes);

            if ((Integer) result.get("code") != 200) {
                return PoetryResult.fail(result.get("message").toString());
            }

            return PoetryResult.success(getResultData(result));
        } catch (Exception e) {
            log.error("批量图标处理失败", e);
            return PoetryResult.fail("批量图标处理失败");
        }
    }

    /**
     * 替换前台默认静态图标。
     */
    @PostMapping("/replacePoetizeIcon")
    @LoginCheck(1)
    public PoetryResult<String> replacePoetizeIcon(@RequestParam("image") MultipartFile imageFile) {
        try {
            if (imageFile == null || imageFile.isEmpty()) {
                return PoetryResult.fail("图片文件为空");
            }

            FileSecurityValidator.ValidationResult validationResult = fileSecurityValidator.validateFile(
                    imageFile, imageFile.getOriginalFilename(), imageFile.getContentType());

            if (!validationResult.isSuccess()) {
                log.warn("默认静态图标安全验证失败: {}", validationResult.getMessage());
                return PoetryResult.fail("文件验证失败: " + validationResult.getMessage());
            }

            Path webRoot = Paths.get(webTemplatePath).toAbsolutePath().normalize().getParent();
            if (webRoot == null) {
                return PoetryResult.fail("前台静态资源目录配置不正确");
            }

            Path target = webRoot.resolve("static/assets/poetize.jpg").normalize();
            if (!target.startsWith(webRoot)) {
                return PoetryResult.fail("默认静态图标路径不合法");
            }

            Files.createDirectories(target.getParent());
            Path tempFile = Files.createTempFile(target.getParent(), "poetize-", ".jpg.tmp");
            try {
                try (InputStream inputStream = imageFile.getInputStream()) {
                    Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
                }
                try {
                    Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tempFile);
            }

            return PoetryResult.success("/static/assets/poetize.jpg");
        } catch (IOException e) {
            log.error("替换默认静态图标失败", e);
            return PoetryResult.fail("替换默认静态图标失败");
        } catch (Exception e) {
            log.error("替换默认静态图标失败", e);
            return PoetryResult.fail("替换默认静态图标失败");
        }
    }

    /**
     * 获取图片信息
     */
    @PostMapping("/getImageInfo")
    @LoginCheck(1)
    public PoetryResult<Map<String, Object>> getImageInfo(@RequestParam("image") MultipartFile imageFile) {
        try {
            // 检查文件大小是否超过Integer.MAX_VALUE，防止溢出
            long fileSize = imageFile.getSize();
            if (fileSize > Integer.MAX_VALUE) {
                log.error("文件大小超过系统限制: {} bytes, 最大允许: {} bytes", fileSize, Integer.MAX_VALUE);
                return PoetryResult.fail("文件大小超过系统限制(" + (Integer.MAX_VALUE / 1024 / 1024) + "MB)，请上传较小的文件");
            }

            // 验证文件安全性
            FileSecurityValidator.ValidationResult validationResult = fileSecurityValidator.validateFile(imageFile,
                    imageFile.getOriginalFilename(), imageFile.getContentType());

            if (!validationResult.isSuccess()) {
                log.warn("文件安全验证失败: {}", validationResult.getMessage());
                return PoetryResult.fail("文件验证失败: " + validationResult.getMessage());
            }

            Map<String, Object> result = seoImageService.getImageInfo(imageFile);

            if ((Integer) result.get("code") != 200) {
                return PoetryResult.fail(result.get("message").toString());
            }

            return PoetryResult.success(getResultData(result));
        } catch (Exception e) {
            log.error("获取图片信息失败", e);
            return PoetryResult.fail("获取图片信息失败");
        }
    }

    // ========== 私有辅助方法 ==========

    private void clearSeoCache() {
        // 清理静态文件缓存
        seoStaticService.clearStaticCache(null);

        // 清理业务缓存
        cacheService.deleteKeysByPattern("seo:*");

        // 清理搜索引擎推送服务的SEO配置缓存
        searchEnginePushService.clearSeoConfigCache();

        // 清理sitemap缓存
        sitemapService.clearSitemapCache();

    }

    private Map<String, Object> getResultData(Map<String, Object> result) {
        Object data = result.get("data");
        if (data instanceof Map<?, ?> rawMap) {
            Map<String, Object> typedData = new HashMap<>(rawMap.size());
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    typedData.put(key, entry.getValue());
                }
            }
            return typedData;
        }
        return Map.of();
    }

    private Map<String, Object> performSeoAnalysis(Map<String, Object> seoConfig) {
        Map<String, Object> analysis = new HashMap<>();
        List<Map<String, Object>> suggestions = new ArrayList<>();
        int seoScore = 100;

        String description = getStringConfig(seoConfig, "site_description");
        if (!StringUtils.hasText(description)) {
            seoScore = addSuggestion(suggestions, seoScore, 18, "error", "网站描述未设置，建议补充 50-160 个字符的摘要描述");
        } else if (description.length() < 50) {
            seoScore = addSuggestion(suggestions, seoScore, 10, "warning", "网站描述偏短，建议扩展到 50-160 个字符以提升摘要展示效果");
        } else if (description.length() > 160) {
            seoScore = addSuggestion(suggestions, seoScore, 4, "info", "网站描述偏长，建议控制在 160 个字符内，避免搜索结果摘要被截断");
        }

        String keywords = getStringConfig(seoConfig, "site_keywords");
        if (!StringUtils.hasText(keywords)) {
            seoScore = addSuggestion(suggestions, seoScore, 8, "warning", "网站关键词未设置，建议补充与站点主题高度相关的关键词");
        } else if (keywords.split("\\s*,\\s*").length > 10) {
            seoScore = addSuggestion(suggestions, seoScore, 3, "info", "网站关键词偏多，建议聚焦 5-10 个核心关键词，避免过度堆砌");
        }

        if (!StringUtils.hasText(getStringConfig(seoConfig, "default_author"))) {
            seoScore = addSuggestion(suggestions, seoScore, 3, "info", "默认作者未设置，建议补充作者信息以完善结构化元数据");
        }

        if (!StringUtils.hasText(getStringConfig(seoConfig, "og_image"))) {
            seoScore = addSuggestion(suggestions, seoScore, 4, "info", "默认分享图片未设置，建议配置 OG 图片以提升搜索结果和社交分享展示");
        }

        if (!StringUtils.hasText(getStringConfig(seoConfig, "site_logo"))) {
            seoScore = addSuggestion(suggestions, seoScore, 3, "info", "网站 Logo 未设置，建议补充站点标识以完善品牌展示");
        }

        if (!hasAnyText(seoConfig, "site_icon", "apple_touch_icon", "site_icon_192", "site_icon_512")) {
            seoScore = addSuggestion(suggestions, seoScore, 6, "warning", "网站图标与 PWA 图标均未配置，建议至少补充 favicon 和移动端图标");
        }

        if (!Boolean.TRUE.equals(seoConfig.get("auto_generate_meta_tags"))) {
            seoScore = addSuggestion(suggestions, seoScore, 8, "warning", "自动生成 META 标签未启用，可能影响文章页的标题、描述和分享标签完整性");
        }

        if (!Boolean.TRUE.equals(seoConfig.get("generate_sitemap"))) {
            seoScore = addSuggestion(suggestions, seoScore, 18, "error", "Sitemap 生成功能未启用，建议开启并提交到 Google Search Console / Bing Webmaster Tools");
        } else {
            String robotsTxt = getStringConfig(seoConfig, "robots_txt");
            if (!StringUtils.hasText(robotsTxt)) {
                seoScore = addSuggestion(suggestions, seoScore, 8, "warning", "robots.txt 未配置，建议至少声明基础抓取规则和 Sitemap 地址");
            } else if (!robotsTxt.toLowerCase().contains("sitemap:")) {
                seoScore = addSuggestion(suggestions, seoScore, 4, "info", "robots.txt 中未声明 Sitemap 地址，建议添加 Sitemap: /sitemap.xml");
            }
        }

        boolean baiduEnabled = Boolean.TRUE.equals(seoConfig.get("baidu_push_enabled"));
        boolean bingEnabled = Boolean.TRUE.equals(seoConfig.get("bing_push_enabled"));
        boolean yandexEnabled = Boolean.TRUE.equals(seoConfig.get("yandex_push_enabled"));
        boolean sogouEnabled = Boolean.TRUE.equals(seoConfig.get("sogou_push_enabled"));
        boolean shenmaEnabled = Boolean.TRUE.equals(seoConfig.get("shenma_push_enabled"));

        if (!baiduEnabled && !bingEnabled && !yandexEnabled && !sogouEnabled && !shenmaEnabled) {
            seoScore = addSuggestion(suggestions, seoScore, 12, "warning", "当前未启用任何实时推送渠道，建议至少启用百度推送或 Bing(IndexNow)");
        }

        seoScore = checkEnabledCredential(suggestions, seoConfig, seoScore, "baidu_push_enabled", "baidu_push_token", 14,
                "百度推送已启用，但 Token 未配置，提交会直接失败");
        seoScore = checkEnabledCredential(suggestions, seoConfig, seoScore, "bing_push_enabled", "bing_api_key", 14,
                "Bing 推送已启用，但 IndexNow Key 未配置，提交会直接失败");
        seoScore = checkEnabledCredential(suggestions, seoConfig, seoScore, "yandex_push_enabled", "yandex_api_key", 12,
                "Yandex 推送已启用，但 IndexNow Key 未配置，提交会直接失败");
        seoScore = checkEnabledCredential(suggestions, seoConfig, seoScore, "sogou_push_enabled", "sogou_push_token", 10,
                "搜狗推送已启用，但 Token 未配置，提交会直接失败");
        seoScore = checkEnabledCredential(suggestions, seoConfig, seoScore, "shenma_push_enabled", "shenma_token", 10,
                "神马推送已启用，但 Token 未配置，提交会直接失败");

        seoScore = checkSiteVerification(suggestions, seoConfig, seoScore, "baidu_site_verification", 4,
                "百度站点验证未设置，建议完成验证以提高百度对站点的可信度");
        seoScore = checkSiteVerification(suggestions, seoConfig, seoScore, "google_site_verification", 4,
                "Google 站点验证未设置，建议完成 Search Console 验证并提交 Sitemap");
        if (bingEnabled) {
            seoScore = checkSiteVerification(suggestions, seoConfig, seoScore, "bing_site_verification", 3,
                    "Bing 站点验证未设置，建议在启用 IndexNow 的同时完成 Bing Webmaster Tools 验证");
        }

        analysis.put("suggestions", suggestions);
        analysis.put("seo_score", Math.max(seoScore, 10));
        analysis.put("issue_summary", buildIssueSummary(suggestions));

        return analysis;
    }

    private int addSuggestion(List<Map<String, Object>> suggestions, int currentScore, int penalty, String type,
            String message) {
        suggestions.add(createSuggestion(type, message));
        return currentScore - penalty;
    }

    private int checkEnabledCredential(List<Map<String, Object>> suggestions, Map<String, Object> config, int currentScore,
            String enabledKey, String credentialKey, int penalty, String message) {
        if (Boolean.TRUE.equals(config.get(enabledKey)) && !StringUtils.hasText(getStringConfig(config, credentialKey))) {
            return addSuggestion(suggestions, currentScore, penalty, "error", message);
        }
        return currentScore;
    }

    private int checkSiteVerification(List<Map<String, Object>> suggestions, Map<String, Object> config, int currentScore,
            String key, int penalty, String message) {
        if (!StringUtils.hasText(getStringConfig(config, key))) {
            return addSuggestion(suggestions, currentScore, penalty, "info", message);
        }
        return currentScore;
    }

    private boolean hasAnyText(Map<String, Object> config, String... keys) {
        for (String key : keys) {
            if (StringUtils.hasText(getStringConfig(config, key))) {
                return true;
            }
        }
        return false;
    }

    private String getStringConfig(Map<String, Object> config, String key) {
        Object value = config.get(key);
        return value instanceof String ? (String) value : null;
    }

    private Map<String, Object> buildIssueSummary(List<Map<String, Object>> suggestions) {
        Map<String, Object> summary = new HashMap<>();
        long errors = suggestions.stream().filter(item -> "error".equals(item.get("type"))).count();
        long warnings = suggestions.stream().filter(item -> "warning".equals(item.get("type"))).count();
        long infos = suggestions.stream().filter(item -> "info".equals(item.get("type"))).count();
        summary.put("error_count", errors);
        summary.put("warning_count", warnings);
        summary.put("info_count", infos);
        return summary;
    }

    private Map<String, Object> createSuggestion(String type, String message) {
        Map<String, Object> suggestion = new HashMap<>();
        suggestion.put("type", type);
        suggestion.put("message", message);
        return suggestion;
    }
}
