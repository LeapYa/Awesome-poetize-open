package com.ld.poetry.service.impl;

import cn.hutool.crypto.SecureUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.aop.ResourceCheck;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.dao.ArticleMapper;
import com.ld.poetry.dao.LabelMapper;
import com.ld.poetry.dao.SortMapper;
import com.ld.poetry.entity.*;
import com.ld.poetry.enums.CommentTypeEnum;
import com.ld.poetry.enums.PoetryEnum;
import com.ld.poetry.service.ArticleService;
import com.ld.poetry.service.UserService;
import com.ld.poetry.utils.*;
import com.ld.poetry.utils.cache.PoetryCache;
import com.ld.poetry.utils.mail.MailUtil;
import com.ld.poetry.vo.ArticleVO;
import com.ld.poetry.vo.BaseRequestVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 文章表 服务实现类
 * </p>
 *
 * @author sara
 * @since 2021-08-13
 */
@SuppressWarnings("unchecked")
@Service
@Slf4j
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService {

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private CommonQuery commonQuery;

    @Autowired
    private UserService userService;

    @Autowired
    private MailUtil mailUtil;

    @Autowired
    private SortMapper sortMapper;

    @Autowired
    private LabelMapper labelMapper;

    @Value("${user.subscribe.format}")
    private String subscribeFormat;

    @Override
    public PoetryResult saveArticle(ArticleVO articleVO) {
        long startTime = System.currentTimeMillis();
        log.info("【Service性能监控】开始保存文章到数据库");
        
        if (articleVO.getViewStatus() != null && !articleVO.getViewStatus() && !StringUtils.hasText(articleVO.getPassword())) {
            return PoetryResult.fail("请设置文章密码！");
        }
        Article article = new Article();
        if (StringUtils.hasText(articleVO.getArticleCover())) {
            article.setArticleCover(articleVO.getArticleCover());
        }
        if (StringUtils.hasText(articleVO.getVideoUrl())) {
            article.setVideoUrl(articleVO.getVideoUrl());
        }
        if (articleVO.getViewStatus() != null && !articleVO.getViewStatus() && StringUtils.hasText(articleVO.getPassword())) {
            article.setPassword(articleVO.getPassword());
            article.setTips(articleVO.getTips());
        }
        article.setViewStatus(articleVO.getViewStatus());
        article.setCommentStatus(articleVO.getCommentStatus());
        article.setRecommendStatus(articleVO.getRecommendStatus());
        article.setArticleTitle(articleVO.getArticleTitle());
        article.setArticleContent(articleVO.getArticleContent());
        article.setSortId(articleVO.getSortId());
        article.setLabelId(articleVO.getLabelId());
        
        // 增强的用户ID设置逻辑
        Integer userId = null;
        if (articleVO.getUserId() != null) {
            userId = articleVO.getUserId();
        } else {
            userId = PoetryUtil.getUserId();
            if (userId == null) {
                log.error("保存文章失败：无法获取用户ID");
                return PoetryResult.fail("无法确定文章作者，请重新登录后再试");
            }
        }
        
        article.setUserId(userId);
        
        long beforeSaveTime = System.currentTimeMillis();
        log.info("【Service性能监控】准备保存到数据库，耗时: {}ms", beforeSaveTime - startTime);
        
        boolean saved = save(article);
        if (!saved) {
            return PoetryResult.fail("保存文章失败");
        }
        
        long afterSaveTime = System.currentTimeMillis();
        log.info("【Service性能监控】数据库保存完成，耗时: {}ms", afterSaveTime - beforeSaveTime);
        
        // 将文章ID回填到VO对象
        articleVO.setId(article.getId());
        
        PoetryCache.remove(CommonConst.SORT_INFO);

        // 异步发送订阅邮件，避免阻塞保存操作
        if (articleVO.getViewStatus()) {
            final Integer labelId = articleVO.getLabelId();
            final String articleTitle = articleVO.getArticleTitle();
            
            new Thread(() -> {
                long mailStartTime = System.currentTimeMillis();
                log.info("【Service性能监控】开始异步发送订阅邮件");
                
                try {
                    List<User> users = userService.lambdaQuery().select(User::getEmail, User::getSubscribe).eq(User::getUserStatus, PoetryEnum.STATUS_ENABLE.getCode()).list();
                    List<String> emails = users.stream().filter(u -> {
                        List<Integer> sub = JSON.parseArray(u.getSubscribe(), Integer.class);
                        return !CollectionUtils.isEmpty(sub) && sub.contains(labelId);
                    }).map(User::getEmail).collect(Collectors.toList());

                    if (!CollectionUtils.isEmpty(emails)) {
                        LambdaQueryChainWrapper<Label> wrapper = new LambdaQueryChainWrapper<>(labelMapper);
                        Label label = wrapper.select(Label::getLabelName).eq(Label::getId, labelId).one();
                        String text = getSubscribeMail(label.getLabelName(), articleTitle);
                        WebInfo webInfo = (WebInfo) PoetryCache.get(CommonConst.WEB_INFO);
                        mailUtil.sendMailMessage(emails, "您有一封来自" + (webInfo == null ? "POETIZE" : webInfo.getWebName()) + "的回执！", text);
                        
                        long mailEndTime = System.currentTimeMillis();
                        log.info("【Service性能监控】订阅邮件发送完成，发送给{}个用户，耗时: {}ms", emails.size(), mailEndTime - mailStartTime);
                    } else {
                        log.info("【Service性能监控】无需发送订阅邮件（无匹配用户）");
                    }
                } catch (Exception e) {
                    long mailErrorTime = System.currentTimeMillis();
                    log.error("【Service性能监控】订阅邮件发送失败，耗时: {}ms，错误: {}", mailErrorTime - mailStartTime, e.getMessage(), e);
                }
            }).start();
        }
        
        long endTime = System.currentTimeMillis();
        log.info("【Service性能监控】saveArticle方法总耗时: {}ms", endTime - startTime);
        
        return PoetryResult.success(article.getId());
    }

    private String getSubscribeMail(String labelName, String articleTitle) {
        WebInfo webInfo = (WebInfo) PoetryCache.get(CommonConst.WEB_INFO);
        String webName = (webInfo == null ? "POETIZE" : webInfo.getWebName());
        return String.format(mailUtil.getMailText(),
                webName,
                String.format(MailUtil.notificationMail, PoetryUtil.getAdminUser().getUsername()),
                PoetryUtil.getAdminUser().getUsername(),
                String.format(subscribeFormat, labelName, articleTitle),
                "",
                webName);
    }

    @Override
    public PoetryResult deleteArticle(Integer id) {
        Integer userId = PoetryUtil.getUserId();
        
        // 检查文章是否存在
        Article article = lambdaQuery().eq(Article::getId, id).one();
        if (article == null) {
            return PoetryResult.fail("文章不存在！");
        }
        
        // 如果是文章作者或管理员，允许删除
        boolean canDelete = article.getUserId().equals(userId) || PoetryUtil.isBoss();
        if (!canDelete) {
            return PoetryResult.fail("没有权限删除此文章！");
        }
        
        // 删除文章
        removeById(id);
        PoetryCache.remove(CommonConst.SORT_INFO);
        return PoetryResult.success();
    }

    @Override
    public PoetryResult updateArticle(ArticleVO articleVO) {
        if (articleVO.getViewStatus() != null && !articleVO.getViewStatus() && !StringUtils.hasText(articleVO.getPassword())) {
            return PoetryResult.fail("请设置文章密码！");
        }

        Integer userId = PoetryUtil.getUserId();
        LambdaUpdateChainWrapper<Article> updateChainWrapper = lambdaUpdate()
                .eq(Article::getId, articleVO.getId())
                .eq(Article::getUserId, userId)
                .set(Article::getLabelId, articleVO.getLabelId())
                .set(Article::getSortId, articleVO.getSortId())
                .set(Article::getArticleTitle, articleVO.getArticleTitle())
                .set(Article::getUpdateBy, PoetryUtil.getUsername())
                .set(Article::getUpdateTime, LocalDateTime.now())
                .set(Article::getVideoUrl, StringUtils.hasText(articleVO.getVideoUrl()) ? articleVO.getVideoUrl() : null)
                .set(Article::getArticleContent, articleVO.getArticleContent());

        if (StringUtils.hasText(articleVO.getArticleCover())) {
            updateChainWrapper.set(Article::getArticleCover, articleVO.getArticleCover());
        }
        if (articleVO.getCommentStatus() != null) {
            updateChainWrapper.set(Article::getCommentStatus, articleVO.getCommentStatus());
        }
        if (articleVO.getRecommendStatus() != null) {
            updateChainWrapper.set(Article::getRecommendStatus, articleVO.getRecommendStatus());
        }
        if (articleVO.getViewStatus() != null && !articleVO.getViewStatus() && StringUtils.hasText(articleVO.getPassword())) {
            updateChainWrapper.set(Article::getPassword, articleVO.getPassword());
            updateChainWrapper.set(StringUtils.hasText(articleVO.getTips()), Article::getTips, articleVO.getTips());
        }
        if (articleVO.getViewStatus() != null) {
            updateChainWrapper.set(Article::getViewStatus, articleVO.getViewStatus());
        }
        updateChainWrapper.update();
        PoetryCache.remove(CommonConst.SORT_INFO);
        return PoetryResult.success();
    }

    @Override
    public PoetryResult<Page> listArticle(BaseRequestVO baseRequestVO) {
        List<Integer> ids = null;
        List<List<Integer>> idList = null;
        if (StringUtils.hasText(baseRequestVO.getArticleSearch())) {
            idList = commonQuery.getArticleIds(baseRequestVO.getArticleSearch());
            ids = idList.stream().flatMap(Collection::stream).collect(Collectors.toList());
            if (CollectionUtils.isEmpty(ids)) {
                baseRequestVO.setRecords(new ArrayList<>());
                return PoetryResult.success(baseRequestVO);
            }
        }

        LambdaQueryChainWrapper<Article> lambdaQuery = lambdaQuery();
        lambdaQuery.in(!CollectionUtils.isEmpty(ids), Article::getId, ids);
        lambdaQuery.like(StringUtils.hasText(baseRequestVO.getSearchKey()), Article::getArticleTitle, baseRequestVO.getSearchKey());
        lambdaQuery.eq(baseRequestVO.getRecommendStatus() != null && baseRequestVO.getRecommendStatus(), Article::getRecommendStatus, PoetryEnum.STATUS_ENABLE.getCode());


        if (baseRequestVO.getLabelId() != null) {
            lambdaQuery.eq(Article::getLabelId, baseRequestVO.getLabelId());
        } else if (baseRequestVO.getSortId() != null) {
            lambdaQuery.eq(Article::getSortId, baseRequestVO.getSortId());
        }

        lambdaQuery.orderByDesc(Article::getCreateTime);

        Page<Article> page = new Page<>(baseRequestVO.getCurrent(), baseRequestVO.getSize());
        lambdaQuery.page(page);

        List<Article> records = page.getRecords();
        if (!CollectionUtils.isEmpty(records)) {
            List<ArticleVO> articles = new ArrayList<>();
            List<ArticleVO> titles = new ArrayList<>();
            List<ArticleVO> contents = new ArrayList<>();

            for (Article article : records) {
                // 保存原始内容用于显示前的高亮处理
                String originalContent = article.getArticleContent();
                String originalTitle = article.getArticleTitle();
                
                if (article.getArticleContent().length() > CommonConst.SUMMARY) {
                    article.setArticleContent(article.getArticleContent().substring(0, CommonConst.SUMMARY).replace("`", "").replace("#", "").replace(">", ""));
                }
                
                ArticleVO articleVO = buildArticleVO(article, false);
                articleVO.setHasVideo(StringUtils.hasText(articleVO.getVideoUrl()));
                articleVO.setPassword(null);
                articleVO.setVideoUrl(null);
                
                // 如果是搜索结果，进行高亮处理
                if (StringUtils.hasText(baseRequestVO.getArticleSearch())) {
                    String searchText = baseRequestVO.getArticleSearch();
                    
                    // 使用高亮标签进行处理
                    String highlightStart = "<span class='search-highlight' style='color: var(--lightGreen); font-weight: bold;'>";
                    String highlightEnd = "</span>";
                    
                    // 对标题和内容进行高亮处理
                    if (idList.get(0).contains(articleVO.getId())) {
                        // 标题匹配的文章
                        articleVO.setArticleTitle(StringUtil.highlightText(originalTitle, searchText, highlightStart, highlightEnd));
                        titles.add(articleVO);
                    } else if (idList.get(1).contains(articleVO.getId())) {
                        // 内容匹配的文章
                        articleVO.setArticleContent(StringUtil.highlightText(articleVO.getArticleContent(), searchText, highlightStart, highlightEnd));
                        contents.add(articleVO);
                    }
                } else {
                    articles.add(articleVO);
                }
            }

            List<ArticleVO> collect = new ArrayList<>();
            collect.addAll(articles);
            collect.addAll(titles);
            collect.addAll(contents);
            baseRequestVO.setRecords(collect);
        }
        return PoetryResult.success(baseRequestVO);
    }

    @Override
    @ResourceCheck(CommonConst.RESOURCE_ARTICLE_DOC)
    public PoetryResult<ArticleVO> getArticleById(Integer id, String password) {
        return getArticleById(id, password, true);
    }
    
    /**
     * 获取文章详情，可以选择是否增加浏览量
     * @param id 文章ID
     * @param password 密码
     * @param incrementViewCount 是否增加浏览量
     * @return 文章详情
     */
    public PoetryResult<ArticleVO> getArticleById(Integer id, String password, boolean incrementViewCount) {
        LambdaQueryChainWrapper<Article> lambdaQuery = lambdaQuery();
        lambdaQuery.eq(Article::getId, id);

        Article article = lambdaQuery.one();
        if (article == null) {
            return PoetryResult.success();
        }
        if (!article.getViewStatus() && (!StringUtils.hasText(password) || !password.equals(article.getPassword()))) {
            return PoetryResult.fail("密码错误" + (StringUtils.hasText(article.getTips()) ? article.getTips() : "请联系作者获取密码"));
        }
        
        // 只有当需要增加浏览量时才调用updateViewCount
        if (incrementViewCount) {
            articleMapper.updateViewCount(id);
        }
        
        article.setPassword(null);
        if (StringUtils.hasText(article.getVideoUrl())) {
            article.setVideoUrl(SecureUtil.aes(CommonConst.CRYPOTJS_KEY.getBytes(StandardCharsets.UTF_8)).encryptBase64(article.getVideoUrl()));
        }
        ArticleVO articleVO = buildArticleVO(article, false);
        return PoetryResult.success(articleVO);
    }

    @Override
    public PoetryResult<Page> listAdminArticle(BaseRequestVO baseRequestVO, Boolean isBoss) {
        LambdaQueryChainWrapper<Article> lambdaQuery = lambdaQuery();
        lambdaQuery.select(Article.class, a -> !a.getColumn().equals("article_content"));
        if (!isBoss) {
            lambdaQuery.eq(Article::getUserId, PoetryUtil.getUserId());
        } else {
            if (baseRequestVO.getUserId() != null) {
                lambdaQuery.eq(Article::getUserId, baseRequestVO.getUserId());
            }
        }
        if (StringUtils.hasText(baseRequestVO.getSearchKey())) {
            lambdaQuery.like(Article::getArticleTitle, baseRequestVO.getSearchKey());
        }
        if (baseRequestVO.getRecommendStatus() != null && baseRequestVO.getRecommendStatus()) {
            lambdaQuery.eq(Article::getRecommendStatus, PoetryEnum.STATUS_ENABLE.getCode());
        }

        if (baseRequestVO.getLabelId() != null) {
            lambdaQuery.eq(Article::getLabelId, baseRequestVO.getLabelId());
        }

        if (baseRequestVO.getSortId() != null) {
            lambdaQuery.eq(Article::getSortId, baseRequestVO.getSortId());
        }

        Page<Article> page = new Page<>(baseRequestVO.getCurrent(), baseRequestVO.getSize());
        lambdaQuery.orderByDesc(Article::getCreateTime).page(page);

        List<Article> records = page.getRecords();
        if (!CollectionUtils.isEmpty(records)) {
            List<ArticleVO> collect = records.stream().map(article -> {
                article.setPassword(null);
                ArticleVO articleVO = buildArticleVO(article, true);
                return articleVO;
            }).collect(Collectors.toList());
            baseRequestVO.setRecords(collect);
        }
        return PoetryResult.success(baseRequestVO);
    }

    @Override
    public PoetryResult<ArticleVO> getArticleByIdForUser(Integer id) {
        // 先检查当前用户是否创建了这篇文章
        LambdaQueryChainWrapper<Article> lambdaQuery = lambdaQuery();
        lambdaQuery.eq(Article::getId, id).eq(Article::getUserId, PoetryUtil.getUserId());
        Article article = lambdaQuery.one();
        
        // 如果当前用户不是文章创建者，检查是否为管理员访问API创建的文章
        if (article == null) {
            // 检查当前用户是否有权限(Boss角色)
            if (PoetryUtil.isBoss()) {
                // 尝试直接通过ID获取文章
                article = lambdaQuery().eq(Article::getId, id).one();
                if (article != null) {
                    ArticleVO articleVO = new ArticleVO();
                    BeanUtils.copyProperties(article, articleVO);
                    return PoetryResult.success(articleVO);
                }
            }
            return PoetryResult.fail("文章不存在！");
        }
        
        ArticleVO articleVO = new ArticleVO();
        BeanUtils.copyProperties(article, articleVO);
        return PoetryResult.success(articleVO);
    }

    @Override
    public PoetryResult<Map<Integer, List<ArticleVO>>> listSortArticle() {
        Map<Integer, List<ArticleVO>> result = (Map<Integer, List<ArticleVO>>) PoetryCache.get(CommonConst.SORT_ARTICLE_LIST);
        if (result != null) {
            return PoetryResult.success(result);
        }

        synchronized (CommonConst.SORT_ARTICLE_LIST.intern()) {
            result = (Map<Integer, List<ArticleVO>>) PoetryCache.get(CommonConst.SORT_ARTICLE_LIST);
            if (result == null) {
                Map<Integer, List<ArticleVO>> map = new HashMap<>();

                List<Sort> sorts = new LambdaQueryChainWrapper<>(sortMapper).select(Sort::getId).list();
                for (Sort sort : sorts) {
                    LambdaQueryChainWrapper<Article> lambdaQuery = lambdaQuery()
                            .eq(Article::getSortId, sort.getId())
                            .orderByDesc(Article::getCreateTime)
                            .last("limit 6");
                    List<Article> articleList = lambdaQuery.list();
                    if (CollectionUtils.isEmpty(articleList)) {
                        continue;
                    }

                    List<ArticleVO> articleVOList = articleList.stream().map(article -> {
                        if (article.getArticleContent().length() > CommonConst.SUMMARY) {
                            article.setArticleContent(article.getArticleContent().substring(0, CommonConst.SUMMARY).replace("`", "").replace("#", "").replace(">", ""));
                        }

                        ArticleVO vo = buildArticleVO(article, false);
                        vo.setHasVideo(StringUtils.hasText(article.getVideoUrl()));
                        vo.setPassword(null);
                        vo.setVideoUrl(null);
                        return vo;
                    }).collect(Collectors.toList());
                    map.put(sort.getId(), articleVOList);
                }

                PoetryCache.put(CommonConst.SORT_ARTICLE_LIST, map, CommonConst.TOKEN_INTERVAL);
                return PoetryResult.success(map);
            } else {
                return PoetryResult.success(result);
            }
        }
    }

    private ArticleVO buildArticleVO(Article article, Boolean isAdmin) {
        ArticleVO articleVO = new ArticleVO();
        BeanUtils.copyProperties(article, articleVO);
        if (!isAdmin) {
            if (!StringUtils.hasText(articleVO.getArticleCover())) {
                articleVO.setArticleCover(PoetryUtil.getRandomCover(articleVO.getId().toString()));
            }
        }

        User user = commonQuery.getUser(articleVO.getUserId());
        if (user != null && StringUtils.hasText(user.getUsername())) {
            articleVO.setUsername(user.getUsername());
        } else if (!isAdmin) {
            articleVO.setUsername(PoetryUtil.getRandomName(articleVO.getUserId().toString()));
        }
        if (articleVO.getCommentStatus()) {
            articleVO.setCommentCount(commonQuery.getCommentCount(articleVO.getId(), CommentTypeEnum.COMMENT_TYPE_ARTICLE.getCode()));
        } else {
            articleVO.setCommentCount(0);
        }

        List<Sort> sortInfo = commonQuery.getSortInfo();
        if (!CollectionUtils.isEmpty(sortInfo)) {
            for (Sort s : sortInfo) {
                if (s.getId().intValue() == articleVO.getSortId().intValue()) {
                    Sort sort = new Sort();
                    BeanUtils.copyProperties(s, sort);
                    sort.setLabels(null);
                    articleVO.setSort(sort);
                    if (!CollectionUtils.isEmpty(s.getLabels())) {
                        for (int j = 0; j < s.getLabels().size(); j++) {
                            Label l = s.getLabels().get(j);
                            if (l.getId().intValue() == articleVO.getLabelId().intValue()) {
                                Label label = new Label();
                                BeanUtils.copyProperties(l, label);
                                articleVO.setLabel(label);
                                break;
                            }
                        }
                    }
                    break;
                }
            }
        }
        return articleVO;
    }
}
