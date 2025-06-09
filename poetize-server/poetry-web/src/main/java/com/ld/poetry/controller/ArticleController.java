package com.ld.poetry.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.service.ArticleService;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.utils.cache.PoetryCache;
import com.ld.poetry.utils.PoetryUtil;
import com.ld.poetry.vo.ArticleVO;
import com.ld.poetry.vo.BaseRequestVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.ld.poetry.service.MailService;
import com.ld.poetry.service.UserService;
import com.ld.poetry.entity.User;
import lombok.extern.slf4j.Slf4j;
import com.ld.poetry.service.TranslationService;
import java.util.Map;
import java.util.HashMap;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.ld.poetry.service.impl.ArticleServiceImpl;

/**
 * <p>
 * 文章表 前端控制器
 * </p>
 *
 * @author sara
 * @since 2021-08-13
 */
@RestController
@RequestMapping("/article")
@Slf4j
public class ArticleController {

    @Autowired
    private ArticleService articleService;
    
    @Autowired
    private MailService mailService;
    
    @Autowired
    private UserService userService;

    @Autowired
    private TranslationService translationService;

    /**
     * 保存文章
     */
    @LoginCheck(1)
    @PostMapping("/saveArticle")
    public PoetryResult saveArticle(@Validated @RequestBody ArticleVO articleVO) {
        // 防止空指针异常，验证输入
        if (articleVO == null) {
            return PoetryResult.fail("文章内容不能为空");
        }
        
        try {
            long step1Time = System.currentTimeMillis();
            
            // 确保用户ID不为空
            if (articleVO.getUserId() == null) {
                // 尝试获取当前用户ID
                Integer currentUserId = PoetryUtil.getUserId();
                if (currentUserId == null) {
                    // 尝试从请求头获取token并解析
                    String token = PoetryUtil.getTokenWithoutBearer();
                    if (token != null) {
                        User user = (User) PoetryCache.get(token);
                        if (user != null) {
                            currentUserId = user.getId();
                        } else if (token.contains(CommonConst.ADMIN_ACCESS_TOKEN)) {
                            // 如果是管理员token，使用管理员ID
                            User adminUser = PoetryUtil.getAdminUser();
                            if (adminUser != null) {
                                currentUserId = adminUser.getId();
                            }
                        }
                    }
                }
                
                if (currentUserId == null) {
                    return PoetryResult.fail("无法获取当前用户信息，请重新登录后再试");
                }
                articleVO.setUserId(currentUserId);
            }
            
            // 缓存清理
            if (articleVO.getUserId() != null) {
                PoetryCache.remove(CommonConst.USER_ARTICLE_LIST + articleVO.getUserId().toString());
            }
            PoetryCache.remove(CommonConst.ARTICLE_LIST);
            PoetryCache.remove(CommonConst.SORT_ARTICLE_LIST);
            
            // 保存文章
            PoetryResult result = articleService.saveArticle(articleVO);
            
            // 如果保存成功并且文章有ID，自动创建英文翻译
            if (result.getCode() == 200 && articleVO.getId() != null) {
                final Integer articleId = articleVO.getId();
                
                // 异步执行翻译，避免阻塞用户操作
                new Thread(() -> {
                    try {
                        translationService.translateAndSaveArticle(articleId);
                    } catch (Exception e) {
                        // 翻译失败不影响保存结果
                        log.error("翻译文章失败: " + e.getMessage(), e);
                    }
                }).start();
            }
            
            return result;
        } catch (Exception e) {
            return PoetryResult.fail("保存文章失败: " + e.getMessage());
        }
    }


    /**
     * 删除文章
     */
    @GetMapping("/deleteArticle")
    @LoginCheck(1)
    public PoetryResult deleteArticle(@RequestParam("id") Integer id) {
        PoetryCache.remove(CommonConst.USER_ARTICLE_LIST + PoetryUtil.getUserId().toString());
        PoetryCache.remove(CommonConst.ARTICLE_LIST);
        PoetryCache.remove(CommonConst.SORT_ARTICLE_LIST);
        
        // 删除文章翻译
        try {
            translationService.refreshArticleTranslation(id);
        } catch (Exception e) {
            log.error("删除文章翻译失败", e);
        }
        
        return articleService.deleteArticle(id);
    }


    /**
     * 更新文章
     */
    @LoginCheck(1)
    @PostMapping("/updateArticle")
    public PoetryResult updateArticle(@Validated @RequestBody ArticleVO articleVO) {
        PoetryCache.remove(CommonConst.USER_ARTICLE_LIST + PoetryUtil.getUserId().toString());
        PoetryCache.remove(CommonConst.ARTICLE_LIST);
        PoetryCache.remove(CommonConst.SORT_ARTICLE_LIST);
        
        PoetryResult result = articleService.updateArticle(articleVO);
        
        // 更新文章成功后，自动更新英文翻译
        if (result.getCode() == 200 && articleVO.getId() != null) {
            final Integer articleId = articleVO.getId();
            
            // 异步执行翻译，避免阻塞用户操作
            new Thread(() -> {
                try {
                    translationService.translateAndSaveArticle(articleId);
                } catch (Exception e) {
                    log.error("文章更新后自动翻译失败", e);
                }
            }).start();
        }
        
        return result;
    }


    /**
     * 查询文章List
     */
    @PostMapping("/listArticle")
    public PoetryResult<Page> listArticle(@RequestBody BaseRequestVO baseRequestVO) {
        return articleService.listArticle(baseRequestVO);
    }

    /**
     * 查询分类文章List
     */
    @GetMapping("/listSortArticle")
    public PoetryResult<Map<Integer, List<ArticleVO>>> listSortArticle() {
        return articleService.listSortArticle();
    }

    /**
     * 查询文章
     */
    @GetMapping("/getArticleById")
    public PoetryResult<ArticleVO> getArticleById(@RequestParam("id") Integer id, @RequestParam(value = "password", required = false) String password) {
        return articleService.getArticleById(id, password);
    }

    /**
     * 查询文章(不增加浏览量)
     * 用于元数据获取、SEO等不需要增加访问量的场景
     */
    @GetMapping("/getArticleByIdNoCount")
    public PoetryResult<ArticleVO> getArticleByIdNoCount(@RequestParam("id") Integer id, @RequestParam(value = "password", required = false) String password) {
        return ((ArticleServiceImpl)articleService).getArticleById(id, password, false);
    }

    /**
     * 接收SEO推送结果并发送邮件通知
     * 此接口由Python SEO模块调用
     */
    @PostMapping("/notifySeoResult")
    public PoetryResult notifySeoResult(@RequestBody Map<String, Object> notificationData) {
        try {
            log.info("收到SEO推送结果通知: {}", notificationData);
            
            // 1. 提取所需数据
            Integer articleId = null;
            if (notificationData.containsKey("articleId") && notificationData.get("articleId") != null) {
                articleId = Integer.parseInt(notificationData.get("articleId").toString());
            }
            
            String title = notificationData.containsKey("title") ? notificationData.get("title").toString() : "未知文章";
            String url = notificationData.containsKey("url") ? notificationData.get("url").toString() : "";
            boolean success = notificationData.containsKey("success") && Boolean.parseBoolean(notificationData.get("success").toString());
            String timestamp = notificationData.containsKey("timestamp") ? notificationData.get("timestamp").toString() : 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            
            // 检查是否提供了通知邮箱
            String notificationEmail = notificationData.containsKey("notificationEmail") ? 
                notificationData.get("notificationEmail").toString() : null;
            
            @SuppressWarnings("unchecked")
            Map<String, Object> results = notificationData.containsKey("results") ? 
                (Map<String, Object>) notificationData.get("results") : new HashMap<>();
            
            // 确定收件人列表
            List<String> recipients = new ArrayList<>();
            
            // 如果提供了通知邮箱，优先使用
            if (notificationEmail != null && !notificationEmail.isEmpty()) {
                recipients.add(notificationEmail);
                log.info("使用SEO配置中指定的通知邮箱: {}", notificationEmail);
            } 
            // 如果没有提供通知邮箱，尝试使用文章作者的邮箱
            else if (articleId != null) {
                // 查询文章信息以获取作者ID
                ArticleVO article = articleService.getArticleById(articleId, null).getData();
                if (article == null) {
                    log.warn("未找到文章信息，文章ID: {}", articleId);
                    return PoetryResult.success("已接收SEO推送结果，但未找到文章信息");
                }
                
                Integer authorId = article.getUserId();
                if (authorId == null) {
                    log.warn("无法确定文章作者，文章ID: {}", articleId);
                    return PoetryResult.success("已接收SEO推送结果，但无法确定文章作者");
                }
                
                // 查询作者信息
                User author = userService.getById(authorId);
                if (author == null) {
                    log.warn("未找到文章作者信息，作者ID: {}", authorId);
                    return PoetryResult.success("已接收SEO推送结果，但未找到作者信息");
                }
                
                // 如果作者有邮箱，添加到收件人列表
                if (author.getEmail() != null && !author.getEmail().isEmpty()) {
                    recipients.add(author.getEmail());
                    log.info("使用文章作者邮箱: {}", author.getEmail());
                }
            }
            
            // 如果没有收件人，不发送邮件
            if (recipients.isEmpty()) {
                log.info("没有有效的收件人，不发送SEO推送结果通知");
                return PoetryResult.success("已接收SEO推送结果，但无法发送通知");
            }
            
            // 4. 构建HTML邮件内容
            StringBuilder emailContent = new StringBuilder();
            emailContent.append("<html><head><style>");
            emailContent.append("body{font-family:Arial,sans-serif;line-height:1.6;color:#333;}");
            emailContent.append("h2{color:#006699;}");
            emailContent.append("table{border-collapse:collapse;width:100%;margin:20px 0;}");
            emailContent.append("th,td{border:1px solid #ddd;padding:8px;text-align:left;}");
            emailContent.append("th{background-color:#f2f2f2;}");
            emailContent.append(".success{color:green;font-weight:bold;}");
            emailContent.append(".failure{color:red;}");
            emailContent.append("</style></head><body>");
            
            emailContent.append("<h2>搜索引擎推送结果通知</h2>");
            emailContent.append("<p>您的文章 <strong>\"").append(title).append("\"</strong> 已提交到搜索引擎。</p>");
            emailContent.append("<p>文章链接: <a href=\"").append(url).append("\">").append(url).append("</a></p>");
            emailContent.append("<p>推送时间: ").append(timestamp).append("</p>");
            
            emailContent.append("<h3>推送结果详情:</h3>");
            emailContent.append("<table><tr><th>搜索引擎</th><th>状态</th><th>详情</th></tr>");
            
            // 添加各搜索引擎结果
            if (results.isEmpty()) {
                emailContent.append("<tr><td colspan=\"3\">无推送结果数据</td></tr>");
            } else {
                for (Map.Entry<String, Object> entry : results.entrySet()) {
                    String engine = entry.getKey();
                    String engineName = getSearchEngineName(engine);
                    
                    if (entry.getValue() instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> resultDetails = (Map<String, Object>) entry.getValue();
                        boolean engineSuccess = resultDetails.containsKey("success") && 
                            Boolean.parseBoolean(resultDetails.get("success").toString());
                        
                        String statusClass = engineSuccess ? "success" : "failure";
                        String status = engineSuccess ? "成功" : "失败";
                        
                        String detail = "";
                        if (resultDetails.containsKey("result")) {
                            detail = resultDetails.get("result").toString();
                        } else if (resultDetails.containsKey("message")) {
                            detail = resultDetails.get("message").toString();
                        }
                        
                        emailContent.append("<tr>");
                        emailContent.append("<td>").append(engineName).append("</td>");
                        emailContent.append("<td class=\"").append(statusClass).append("\">").append(status).append("</td>");
                        emailContent.append("<td>").append(detail).append("</td>");
                        emailContent.append("</tr>");
                    }
                }
            }
            
            emailContent.append("</table>");
            
            // 添加推送总结
            if (success) {
                emailContent.append("<p class=\"success\">推送总结: 至少有一个搜索引擎推送成功。</p>");
            } else {
                emailContent.append("<p class=\"failure\">推送总结: 所有搜索引擎推送均失败。</p>");
            }
            
            emailContent.append("<p>此邮件由系统自动发送，请勿回复。</p>");
            emailContent.append("</body></html>");
            
            // 5. 发送邮件通知
            String subject = (success ? "SEO推送成功: " : "SEO推送失败: ") + title;
            boolean mailSent = mailService.sendMail(recipients, subject, emailContent.toString(), true, null);
            
            if (mailSent) {
                log.info("SEO推送结果通知邮件发送成功，收件人: {}", recipients);
                return PoetryResult.success("SEO推送结果通知已发送");
            } else {
                log.warn("SEO推送结果通知邮件发送失败，收件人: {}", recipients);
                return PoetryResult.fail("SEO推送结果通知邮件发送失败");
            }
        } catch (Exception e) {
            log.error("处理SEO推送结果通知出错", e);
            return PoetryResult.fail("处理SEO推送结果通知出错: " + e.getMessage());
        }
    }
    
    /**
     * 根据搜索引擎代码获取显示名称
     */
    private String getSearchEngineName(String engine) {
        String engineLower = engine.toLowerCase();
        if ("baidu".equals(engineLower)) {
            return "百度搜索";
        } else if ("google".equals(engineLower)) {
            return "谷歌搜索";
        } else if ("bing".equals(engineLower)) {
            return "必应搜索";
        } else if ("yandex".equals(engineLower)) {
            return "Yandex搜索";
        } else if ("sogou".equals(engineLower)) {
            return "搜狗搜索";
        } else if ("so".equals(engineLower)) {
            return "360搜索";
        } else if ("shenma".equals(engineLower)) {
            return "神马搜索";
        } else if ("yahoo".equals(engineLower)) {
            return "雅虎搜索";
        } else {
            return engine;
        }
    }

    /**
     * 获取文章翻译
     */
    @GetMapping("/getTranslation")
    public PoetryResult<Map<String, String>> getTranslation(@RequestParam("id") Integer id,
                                     @RequestParam(value = "language", defaultValue = "en") String language) {
        // 检查参数
        if (id == null) {
            return PoetryResult.fail("文章ID不能为空");
        }
        
        if (!StringUtils.hasText(language)) {
            return PoetryResult.fail("翻译语言不能为空");
        }
        
        try {
            // 获取文章翻译
            Map<String, String> translationResult = translationService.getArticleTranslation(id, language);
            return PoetryResult.success(translationResult);
        } catch (Exception e) {
            log.error("获取文章翻译失败", e);
            return PoetryResult.fail("获取翻译失败：" + e.getMessage());
        }
    }
}

