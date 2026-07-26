package com.ld.poetry.service;

import java.util.Map;

/**
 * <p>
 * SEO元数据生成服务接口
 * </p>
 *
 * @author LeapYa
 * @since 2025-09-25
 */
public interface SeoMetaService {
    
    /**
     * 生成文章SEO元数据
     * @param articleId 文章ID
     * @param language 语言代码
     * @return SEO元数据Map
     */
    Map<String, Object> generateArticleMeta(Integer articleId, String language);
    
    /**
     * 生成网站首页SEO元数据
     * @param language 语言代码
     * @return SEO元数据Map
     */
    Map<String, Object> generateSiteMeta(String language);
    
    /**
     * 生成分类页面SEO元数据
     * @param categoryId 分类ID
     * @param language 语言代码
     * @return SEO元数据Map
     */
    Map<String, Object> generateCategoryMeta(Integer categoryId, String language);
    
    /**
     * 检测当前站点URL
     * @param request HTTP请求
     * @return 检测到的URL信息
     */
    Map<String, Object> detectSiteUrl(jakarta.servlet.http.HttpServletRequest request);
}
