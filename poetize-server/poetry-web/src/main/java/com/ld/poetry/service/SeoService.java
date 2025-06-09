package com.ld.poetry.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ld.poetry.entity.Article;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * SEO服务，用于将文章提交到搜索引擎
 */
@SuppressWarnings("unchecked")
@Service
@Slf4j
public class SeoService {

    private final RestTemplate restTemplate;
    private final ArticleService articleService;
    
    @Value("${python.server.url:http://localhost:5000}")
    private String pythonServerUrl;
    
    public SeoService(RestTemplate restTemplate, ArticleService articleService) {
        this.restTemplate = restTemplate;
        this.articleService = articleService;
    }

    /**
     * 将文章提交到搜索引擎
     *
     * @param articleId 文章ID
     * @return 是否提交成功
     */
    public boolean submitToSearchEngines(Integer articleId) {
        try {
            log.info("开始将文章推送至搜索引擎，文章ID: {}", articleId);
            System.out.println("【SEO服务】开始将文章推送至搜索引擎，文章ID: " + articleId);
            
            // 获取文章信息
            Article article = articleService.getById(articleId);
            if (article == null) {
                log.error("文章不存在，无法推送至搜索引擎，文章ID: {}", articleId);
                System.out.println("【SEO服务】文章不存在，无法推送至搜索引擎，文章ID: " + articleId);
                return false;
            }
            
            // 构建请求数据
            Map<String, Object> requestData = new HashMap<>();
            requestData.put("articleId", articleId);
            requestData.put("title", article.getArticleTitle());
            requestData.put("url", null); // 由Python服务根据文章ID构建URL
            
            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add("Accept-Charset", "UTF-8");
            
            // 创建RestTemplate配置并设置字符编码
            if (restTemplate.getMessageConverters() != null) {
                for (HttpMessageConverter<?> converter : restTemplate.getMessageConverters()) {
                    if (converter instanceof StringHttpMessageConverter) {
                        ((StringHttpMessageConverter) converter).setDefaultCharset(java.nio.charset.StandardCharsets.UTF_8);
                    }
                }
            }
            
            // 创建请求实体
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestData, headers);
            
            // 发送请求到Python SEO服务
            String url = pythonServerUrl + "/python/seo/submitArticle";
            log.info("发送SEO推送请求到Python服务: {}, 请求数据: {}", url, new ObjectMapper().writeValueAsString(requestData));
            System.out.println("【SEO服务】发送SEO推送请求到Python服务: " + url);
            System.out.println("【SEO服务】请求数据: " + new ObjectMapper().writeValueAsString(requestData));
            
            try {
                System.out.println("【SEO服务】开始发送HTTP POST请求...");
                // 发送请求并获取响应
                Map<String, Object> response = restTemplate.postForObject(
                    url, 
                    requestEntity, 
                    Map.class
                );
                System.out.println("【SEO服务】收到HTTP响应: " + (response != null ? new ObjectMapper().writeValueAsString(response) : "null"));
                
                // 检查响应结果
                if (response != null && response.containsKey("code")) {
                    int code = Integer.parseInt(response.get("code").toString());
                    boolean success = code == 200;
                    log.info("SEO推送请求响应: code={}, message={}, success={}, 完整响应: {}", 
                             code, response.get("message"), success, new ObjectMapper().writeValueAsString(response));
                    System.out.println("【SEO服务】推送结果: " + (success ? "成功" : "失败") + ", 状态码: " + code);
                    return success;
                } else {
                    log.error("SEO推送响应格式不正确: {}", response != null ? new ObjectMapper().writeValueAsString(response) : "null");
                    System.out.println("【SEO服务】SEO推送响应格式不正确");
                    return false;
                }
            } catch (Exception e) {
                log.error("SEO推送请求发送失败，可能Python服务未运行或配置错误: URL={}", url, e);
                log.error("详细错误信息:", e);
                System.out.println("【SEO服务】SEO推送请求发送失败，可能Python服务未运行或配置错误: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        } catch (Exception e) {
            log.error("推送文章至搜索引擎出错", e);
            System.out.println("【SEO服务】推送文章至搜索引擎出错: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
} 