package com.ld.poetry.service;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.ld.poetry.entity.Article;
import com.ld.poetry.utils.ArticleUrlUtil;
import lombok.extern.slf4j.Slf4j;
import java.util.Map;
import java.util.HashMap;

/**
 * SEO服务，用于将文章提交到搜索引擎
 * 
 * @author LeapYa
 * @since 2025-06-20
 */
@Service
@Slf4j
public class SeoService {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    @Lazy
    private ArticleService articleService;

    @Autowired
    private SearchEnginePushService searchEnginePushService;

    @Autowired
    private com.ld.poetry.utils.mail.MailUtil mailUtil;

    // [已迁移] Python 后端已合并到 Java，不再需要 PYTHON_SERVICE_URL

    /**
     * 将文章提交到搜索引擎
     *
     * @param articleId 文章ID
     * @return 推送结果详情 (success, status, message等)
     */
    public Map<String, Object> submitToSearchEngines(Integer articleId) {
        Map<String, Object> result = new HashMap<>();

        try {

            // 获取文章信息
            Article article = articleService.getById(articleId);
            if (article == null) {
                log.error("文章不存在，无法推送至搜索引擎，文章ID: {}", articleId);
                result.put("success", false);
                result.put("status", "error");
                result.put("message", "文章不存在");
                return result;
            }

            // 检查文章是否可见
            if (!Boolean.TRUE.equals(article.getViewStatus())) {
                result.put("success", true);
                result.put("status", "skipped");
                result.put("message", "文章不可见，跳过推送");
                return result;
            }

            // 使用Java端的完整搜索引擎推送功能
            if (searchEnginePushService != null && searchEnginePushService.isPushEnabled()) {

                // 直接从 MailUtil 获取网站地址并构建文章URL
                String siteAddress = mailUtil.getSiteUrl();

                if (org.springframework.util.StringUtils.hasText(siteAddress)) {

                    String articleUrl = siteAddress + ArticleUrlUtil.buildArticlePath(article.getId(), article.getArticleSlug());

                    // 推送到所有启用的搜索引擎
                    Map<String, Object> pushResult = searchEnginePushService.pushUrlToAllEngines(articleUrl);
                    boolean success = Boolean.TRUE.equals(pushResult.get("success"));

                    Integer successCount = (Integer) pushResult.get("successCount");
                    Integer totalEngines = (Integer) pushResult.get("totalEngines");

                    result.put("success", success);
                    result.put("status", success ? "pushed" : "failed");
                    result.put("message", String.format("推送到 %d/%d 个搜索引擎", successCount, totalEngines));
                    result.put("successCount", successCount);
                    result.put("totalEngines", totalEngines);
                    return result;
                } else {
                    log.error("网站地址或文章URL格式未配置，无法进行搜索引擎推送");
                    result.put("success", false);
                    result.put("status", "error");
                    result.put("message", "网站地址未配置");
                    return result;
                }
            } else {
                result.put("success", true);
                result.put("status", "disabled");
                result.put("message", "搜索引擎推送功能未启用");
                return result;
            }

        } catch (Exception e) {
            log.error("推送文章至搜索引擎出错", e);
            result.put("success", false);
            result.put("status", "error");
            result.put("message", "推送出错: " + e.getMessage());
            return result;
        }
    }
}
