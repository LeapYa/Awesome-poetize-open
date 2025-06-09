package com.ld.poetry.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.dao.LabelMapper;
import com.ld.poetry.dao.SortMapper;
import com.ld.poetry.entity.Article;
import com.ld.poetry.entity.Label;
import com.ld.poetry.entity.Sort;
import com.ld.poetry.entity.User;
import com.ld.poetry.entity.WebInfo;
import com.ld.poetry.handle.PoetryRuntimeException;
import com.ld.poetry.service.ArticleService;
import com.ld.poetry.service.LabelService;
import com.ld.poetry.service.SeoService;
import com.ld.poetry.service.SortService;
import com.ld.poetry.service.TranslationService;
import com.ld.poetry.utils.PoetryUtil;
import com.ld.poetry.utils.cache.PoetryCache;
import com.ld.poetry.vo.ArticleVO;
import com.ld.poetry.vo.BaseRequestVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 外部API接口控制器
 * </p>
 *
 */
@RestController
@RequestMapping("/api")
@Slf4j
public class ApiController {

    private final ArticleService articleService;

    private final LabelMapper labelMapper;

    private final SortMapper sortMapper;
    
    private final SortService sortService;
    
    private final LabelService labelService;

    private final SeoService seoService;

    private final TranslationService translationService;

    public ApiController(ArticleService articleService,
                        LabelMapper labelMapper,
                        SortMapper sortMapper,
                        SortService sortService,
                        LabelService labelService,
                        SeoService seoService,
                        TranslationService translationService) {
        this.articleService = articleService;
        this.labelMapper = labelMapper;
        this.sortMapper = sortMapper;
        this.sortService = sortService;
        this.labelService = labelService;
        this.seoService = seoService;
        this.translationService = translationService;
    }

    /**
     * 验证API密钥
     */
    private WebInfo validateApiKey(HttpServletRequest request) {
        // 获取网站信息
        WebInfo webInfo = (WebInfo) PoetryCache.get(CommonConst.WEB_INFO);
        if (webInfo == null || webInfo.getApiEnabled() == null || !webInfo.getApiEnabled()) {
            log.warn("API请求被拒绝：API未启用");
            throw new PoetryRuntimeException("API未启用");
        }

        // 验证API密钥
        String apiKey = request.getHeader("X-API-KEY");
        if (!StringUtils.hasText(apiKey) || !apiKey.equals(webInfo.getApiKey())) {
            log.warn("API请求被拒绝：无效的API密钥");
            throw new PoetryRuntimeException("无效的API密钥");
        }
        
        return webInfo;
    }

    /**
     * API创建文章
     */
    @PostMapping("/article/create")
    public PoetryResult createArticle(@RequestBody ArticleVO articleVO, HttpServletRequest request) {
        try {
            // 验证API密钥
            validateApiKey(request);
            
            // 使用管理员账号创建文章
            User adminUser = PoetryUtil.getAdminUser();
            if (adminUser == null) {
                log.error("API请求失败：无法获取管理员账号信息");
                return PoetryResult.fail("服务器内部错误：无法获取管理员账号");
            }

            // 设置文章信息
            // 如果前端提供了userId，则优先使用前端提供的userId
            if (articleVO.getUserId() == null) {
                articleVO.setUserId(adminUser.getId());
            }
            
            // 处理分类名称
            if (StringUtils.hasText(articleVO.getSortName())) {
                // 通过分类名称查找分类ID
                Sort sort = sortMapper.selectOne(new QueryWrapper<Sort>().eq("sort_name", articleVO.getSortName()));
                
                // 如果分类不存在，则创建新分类
                if (sort == null) {
                    sort = new Sort();
                    sort.setSortName(articleVO.getSortName());
                    // 设置默认分类描述
                    sort.setSortDescription("通过API创建的分类");
                    // 设置默认分类类型和优先级
                    sort.setSortType(1); // 1表示普通分类
                    sort.setPriority(99); // 设置一个较低的优先级
                    sortMapper.insert(sort);
                }
                
                // 设置分类ID
                articleVO.setSortId(sort.getId());
            }
            
            // 处理标签名称
            if (StringUtils.hasText(articleVO.getLabelName())) {
                // 通过标签名称查找标签ID
                Label label = labelMapper.selectOne(new QueryWrapper<Label>().eq("label_name", articleVO.getLabelName()));
                
                // 如果标签不存在，则创建新标签
                if (label == null) {
                    label = new Label();
                    label.setLabelName(articleVO.getLabelName());
                    // 设置默认标签描述
                    label.setLabelDescription("通过API创建的标签");
                    // 如果分类ID已经设置，则关联到该分类
                    if (articleVO.getSortId() != null) {
                        label.setSortId(articleVO.getSortId());
                    } else {
                        // 默认关联到第一个分类
                        Sort defaultSort = sortMapper.selectOne(new QueryWrapper<Sort>().orderByAsc("id").last("LIMIT 1"));
                        if (defaultSort != null) {
                            label.setSortId(defaultSort.getId());
                        } else {
                            label.setSortId(1); // 如果没有分类，则设置为1
                        }
                    }
                    labelMapper.insert(label);
                }
                
                // 设置标签ID
                articleVO.setLabelId(label.getId());
            }
            
            // 标题和内容字段重命名处理
            if (articleVO.getArticleTitle() == null && articleVO.getTitle() != null) {
                articleVO.setArticleTitle(articleVO.getTitle());
            }
            
            if (articleVO.getArticleContent() == null && articleVO.getContent() != null) {
                articleVO.setArticleContent(articleVO.getContent());
            }
            
            // 分类ID和标签ID字段重命名处理
            if (articleVO.getSortId() == null && articleVO.getClassify() != null) {
                articleVO.setSortId(articleVO.getClassify());
            }
            
            // 使用API中传入的标签ID
            if (articleVO.getLabelId() == null) {
                Object labelObj = articleVO.getLabel();
                if (labelObj instanceof Number) {
                    articleVO.setLabelId(((Number)labelObj).intValue());
                }
            }
            
            // 封面图片处理
            if (articleVO.getArticleCover() == null && articleVO.getCover() != null) {
                articleVO.setArticleCover(articleVO.getCover());
            }
            
            // 文章摘要处理
            if (articleVO.getTips() == null && articleVO.getSummary() != null) {
                articleVO.setTips(articleVO.getSummary());
            }

            // 设置默认值
            if (articleVO.getViewStatus() == null) {
                articleVO.setViewStatus(true);
            }
            
            if (articleVO.getCommentStatus() == null) {
                articleVO.setCommentStatus(true);
            }
            
            if (articleVO.getRecommendStatus() == null) {
                articleVO.setRecommendStatus(false);
            }
            
            // 处理搜索引擎提交字段
            // 注意：submitToSearchEngine字段仅控制API请求时是否触发推送，
            // 实际的推送逻辑需要在文章保存成功后单独处理

            // 保存文章
            PoetryResult result = articleService.saveArticle(articleVO);
            
            // 保存成功，处理翻译
            if (result.getCode() == 200) {
                // 提取文章ID
                final Integer articleId;
                if (result.getData() instanceof Integer) {
                    articleId = (Integer) result.getData();
                } else if (result.getData() instanceof Map) {
                    Object idObj = ((Map<?, ?>) result.getData()).get("id");
                    if (idObj instanceof Integer) {
                        articleId = (Integer) idObj;
                    } else {
                        articleId = null;
                    }
                } else {
                    articleId = null;
                }
                
                // 异步执行翻译，避免阻塞API响应
                if (articleId != null) {
                    new Thread(() -> {
                        try {
                            log.info("API创建文章成功，开始生成翻译，文章ID: {}", articleId);
                            translationService.translateAndSaveArticle(articleId);
                        } catch (Exception e) {
                            log.error("API创建文章后自动翻译失败", e);
                        }
                    }).start();
                }
            }
            
            // 如果保存成功，返回文章ID
            if (result.getCode() == 0 && result.getData() != null) {
                Integer articleId = (Integer) result.getData();
                
                // 如果需要推送至搜索引擎
                if (Boolean.TRUE.equals(articleVO.getSubmitToSearchEngine())) {
                    try {
                        // 此处可以调用搜索引擎推送相关服务
                        // 如果系统中已有推送服务，可以直接调用
                        log.info("API创建的文章ID {} 标记为需要推送至搜索引擎", articleId);
                        System.out.println("【API控制器】文章ID " + articleId + " 标记为需要推送至搜索引擎");
                        
                        // 调用SEO服务进行推送
                        boolean seoResult = seoService.submitToSearchEngines(articleId);
                        System.out.println("【API控制器】SEO推送结果: " + (seoResult ? "成功" : "失败"));
                    } catch (Exception e) {
                        log.error("搜索引擎推送失败，但不影响文章创建", e);
                        System.out.println("【API控制器】搜索引擎推送失败: " + e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    System.out.println("【API控制器】文章ID " + articleId + " 未标记为需要推送至搜索引擎");
                }
                
                Map<String, Object> data = new HashMap<>();
                data.put("id", articleId);
                return PoetryResult.success(data);
            }
            
            return result;
        } catch (PoetryRuntimeException e) {
            log.error("API创建文章失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API创建文章出现未知错误", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }
    
    /**
     * API查询文章列表
     */
    @GetMapping("/article/list")
    public PoetryResult getArticleList(BaseRequestVO baseRequestVO, HttpServletRequest request) {
        try {
            // 验证API密钥
            validateApiKey(request);
            
            // 设置默认分页参数
            if (baseRequestVO.getCurrent() < 1) {
                baseRequestVO.setCurrent(1);
            }
            if (baseRequestVO.getSize() < 1) {
                baseRequestVO.setSize(10);
            }
            
            // 调用文章列表服务
            return articleService.listArticle(baseRequestVO);
        } catch (PoetryRuntimeException e) {
            log.error("API查询文章列表失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API查询文章列表出现未知错误", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }
    
    /**
     * API获取文章详情
     */
    @GetMapping("/article/{id}")
    public PoetryResult getArticleDetail(@PathVariable("id") Integer id, HttpServletRequest request) {
        try {
            // 验证API密钥
            validateApiKey(request);
            
            // 验证参数
            if (id == null || id <= 0) {
                return PoetryResult.fail("无效的文章ID");
            }
            
            // 查询文章
            Article article = articleService.getById(id);
            if (article == null) {
                return PoetryResult.fail("文章不存在");
            }
            
            // 将文章转为VO
            ArticleVO articleVO = new ArticleVO();
            articleVO.setId(article.getId());
            articleVO.setUserId(article.getUserId());
            articleVO.setArticleTitle(article.getArticleTitle());
            articleVO.setArticleContent(article.getArticleContent());
            articleVO.setArticleCover(article.getArticleCover());
            articleVO.setSortId(article.getSortId());
            articleVO.setLabelId(article.getLabelId());
            articleVO.setViewCount(article.getViewCount());
            articleVO.setLikeCount(article.getLikeCount());
            articleVO.setCommentStatus(article.getCommentStatus());
            articleVO.setRecommendStatus(article.getRecommendStatus());
            articleVO.setViewStatus(article.getViewStatus());
            articleVO.setCreateTime(article.getCreateTime());
            articleVO.setUpdateTime(article.getUpdateTime());
            
            // 获取分类和标签信息
            if (article.getSortId() != null) {
                Sort sort = sortService.getById(article.getSortId());
                if (sort != null) {
                    articleVO.setSort(sort);
                    articleVO.setSortName(sort.getSortName());
                }
            }
            
            if (article.getLabelId() != null) {
                Label label = labelService.getById(article.getLabelId());
                if (label != null) {
                    articleVO.setLabel(label);
                    articleVO.setLabelName(label.getLabelName());
                }
            }
            
            return PoetryResult.success(articleVO);
        } catch (PoetryRuntimeException e) {
            log.error("API获取文章详情失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API获取文章详情出现未知错误", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }
    
    /**
     * API查询分类列表
     */
    @GetMapping("/categories")
    public PoetryResult getCategoryList(HttpServletRequest request) {
        try {
            // 验证API密钥
            validateApiKey(request);
            
            // 查询所有分类
            List<Sort> sortList = sortService.list();
            
            return PoetryResult.success(sortList);
        } catch (PoetryRuntimeException e) {
            log.error("API查询分类列表失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API查询分类列表出现未知错误", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }
    
    /**
     * API查询标签列表
     */
    @GetMapping("/tags")
    public PoetryResult getTagList(HttpServletRequest request) {
        try {
            // 验证API密钥
            validateApiKey(request);
            
            // 查询所有标签
            List<Label> labelList = labelService.list();
            
            return PoetryResult.success(labelList);
        } catch (PoetryRuntimeException e) {
            log.error("API查询标签列表失败：{}", e.getMessage());
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("API查询标签列表出现未知错误", e);
            return PoetryResult.fail("服务器内部错误");
        }
    }
} 