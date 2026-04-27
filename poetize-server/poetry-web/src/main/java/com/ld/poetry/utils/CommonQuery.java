package com.ld.poetry.utils;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ld.poetry.constants.CacheConstants;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.dao.*;
import com.ld.poetry.entity.*;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.service.UserService;
import com.ld.poetry.service.provider.Ip2RegionProvider;
import com.ld.poetry.vo.FamilyVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Slf4j
@Component
public class CommonQuery {
    private static final String DELETED_USER_NAME = "用户已注销";

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private HistoryInfoMapper historyInfoMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private SortMapper sortMapper;

    @Autowired
    private LabelMapper labelMapper;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private FamilyMapper familyMapper;

    @Autowired
    private ArticleTranslationMapper articleTranslationMapper;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private LockManager lockManager;

    @Autowired
    private Ip2RegionProvider ip2RegionProvider;

    public void saveHistory(String ip) {
        saveHistory(ip, null, null, null, null);
    }

    /**
     * 记录访问历史（带用户请求元数据）
     * @param ip 客户端IP
     * @param pageUri 访问的页面URI
     * @param userAgent 用户浏览器User-Agent
     * @param referer 来源页面Referer
     * @param acceptLanguage 用户语言偏好Accept-Language
     */
    public void saveHistory(String ip, String pageUri, String userAgent, String referer, String acceptLanguage) {
        try {
            // 过滤无效IP，避免记录Docker内部IP和无效地址
            if (ip == null || ip.isEmpty() || "unknown".equals(ip) || isInvalidIP(ip)) {
                return;
            }

            Integer userId = PoetryUtil.getUserId();
            String ipUser = ip + (userId != null ? "_" + userId.toString() : "");

            // 记录每次访问 - 使用 LockManager 替代 String.intern()，避免内存泄漏
            lockManager.executeWithLock("saveHistory:" + ipUser, () -> {
                // log.info("[saveHistory] 记录访问到Redis: {}", ipUser);

                // 解析IP地理位置信息（结构化：国家、省份、城市）
                String nation = null, province = null, city = null;
                if (ip2RegionProvider != null && ip2RegionProvider.isAvailable()) {
                    try {
                        String[] detail = ip2RegionProvider.resolveLocationDetail(ip);
                        nation = detail[0];    // 国家（如 "中国"、"美国"）
                        province = detail[1];  // 省份（如 "广东"、"加利福尼亚"）
                        city = detail[2];      // 城市（如 "深圳"、"洛杉矶"）
                    } catch (Exception e) {
                        log.warn("[saveHistory] IP地理位置解析失败: {}, 错误: {}", ip, e.getMessage());
                    }
                }

                // 记录访问信息到Redis（不立即写数据库）
                cacheService.recordVisitToRedis(ip, userId, nation, province, city,
                        pageUri, userAgent, referer, acceptLanguage);

                // log.info("[saveHistory] 访问记录已保存到Redis缓存，等待定时同步到数据库: {}", ipUser);
            });
        } catch (Exception e) {
            log.error("[saveHistory] 保存访问记录时发生异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 判断是否为无效IP地址
     * 在开发环境下放宽IP过滤条件，允许内网IP访问统计
     */
    private boolean isInvalidIP(String ip) {
        if (ip == null || ip.isEmpty()) {
            return true;
        }

        ip = ip.trim();

        // IPv4本地回环地址
        if (ip.equals("127.0.0.1") || ip.equals("localhost")) {
            return true;
        }

        // IPv6本地回环地址
        if (ip.equals("::1") || ip.equals("0:0:0:0:0:0:0:1") ||
                ip.equalsIgnoreCase("localhost")) {
            return true;
        }

        // 其他明确无效的IP
        if (ip.equals("unknown") || ip.equals("0.0.0.0") || ip.equals("null") ||
                ip.equals("-") || ip.equals("undefined")) {
            return true;
        }

        // 在开发/测试环境下，允许内网IP进行访问统计
        // 检查是否为有效的IP格式
        if (!isValidIpFormat(ip)) {
            return true;
        }

        // 生产环境下可以考虑过滤内网IP，但开发环境需要统计
        // 注释掉严格的内网IP过滤，允许所有有效格式的IP
        // if (ip.startsWith("172.") || ip.startsWith("10.") ||
        // ip.startsWith("192.168.")) {
        // return true;
        // }

        return false;
    }

    /**
     * 验证IP格式是否有效（支持IPv4和IPv6）
     */
    private boolean isValidIpFormat(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            return false;
        }

        ip = ip.trim();

        // 使用Java内置的InetAddress来验证IP格式
        try {
            java.net.InetAddress.getByName(ip);
            return true;
        } catch (java.net.UnknownHostException e) {
            // 如果InetAddress无法解析，再尝试手动验证IPv4格式
            return isValidIPv4(ip);
        }
    }

    /**
     * 验证IPv4格式
     */
    private boolean isValidIPv4(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            return false;
        }

        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }

        try {
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) {
                    return false;
                }
                // 检查前导零（除了"0"本身）
                if (part.length() > 1 && part.startsWith("0")) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public User getUser(Integer userId) {
        if (userId == null) {
            return null;
        }
        // 使用Redis缓存替换PoetryCache
        User user = cacheService.getCachedUser(userId);
        if (user != null) {
            return user;
        }
        User u = userService.getById(userId);
        if (u != null) {
            cacheService.cacheUser(u);
            return u;
        }
        return null;
    }

    public User getUserForDisplay(Integer userId) {
        User user = getUser(userId);
        if (user != null) {
            return user;
        }

        User deletedUser = new User();
        deletedUser.setId(userId);
        deletedUser.setUsername(DELETED_USER_NAME);
        deletedUser.setAvatar(PoetryUtil.getRandomAvatar(userId == null ? null : userId.toString()));
        deletedUser.setUserStatus(false);
        deletedUser.setDeleted(true);
        return deletedUser;
    }

    public List<User> getAdmire() {
        // 使用Redis缓存替换PoetryCache
        String cacheKey = CacheConstants.CACHE_PREFIX + "admire:list";

        // 先使用读锁尝试获取缓存（允许多个线程并发读取）
        @SuppressWarnings("unchecked")
        List<User> admire = (List<User>) cacheService.get(cacheKey);
        if (admire != null) {
            return admire;
        }

        // 缓存未命中，使用写锁更新缓存（独占访问）
        return lockManager.executeWithWriteLock("cache:" + CommonConst.ADMIRE, () -> {
            // 双重检查锁定
            @SuppressWarnings("unchecked")
            List<User> cachedAdmire = (List<User>) cacheService.get(cacheKey);
            if (cachedAdmire != null) {
                return cachedAdmire;
            } else {
                List<User> users = userService.lambdaQuery()
                        .select(User::getId, User::getUsername, User::getAdmire, User::getAvatar)
                        .isNotNull(User::getAdmire).list();

                cacheService.set(cacheKey, users, CacheConstants.LONG_EXPIRE_TIME);

                return users;
            }
        });
    }

    public List<FamilyVO> getFamilyList() {
        // 使用Redis缓存替换PoetryCache
        String cacheKey = CacheConstants.CACHE_PREFIX + "family:list";

        // 先尝试获取缓存（无需锁）
        @SuppressWarnings("unchecked")
        List<FamilyVO> familyVOList = (List<FamilyVO>) cacheService.get(cacheKey);
        if (familyVOList != null) {
            return familyVOList;
        }

        // 缓存未命中，使用写锁更新缓存
        return lockManager.executeWithWriteLock("cache:" + CommonConst.FAMILY_LIST, () -> {
            // 双重检查锁定
            @SuppressWarnings("unchecked")
            List<FamilyVO> cachedFamilyVOList = (List<FamilyVO>) cacheService.get(cacheKey);
            if (cachedFamilyVOList != null) {
                return cachedFamilyVOList;
            } else {
                LambdaQueryChainWrapper<Family> queryChainWrapper = new LambdaQueryChainWrapper<>(familyMapper);
                List<Family> familyList = queryChainWrapper.eq(Family::getStatus, Boolean.TRUE).list();
                List<FamilyVO> result;
                if (!CollectionUtils.isEmpty(familyList)) {
                    result = familyList.stream().map(family -> {
                        FamilyVO familyVO = new FamilyVO();
                        BeanUtils.copyProperties(family, familyVO);
                        return familyVO;
                    }).collect(Collectors.toList());
                } else {
                    result = new ArrayList<>();
                }

                cacheService.set(cacheKey, result, CacheConstants.LONG_EXPIRE_TIME);
                return result;
            }
        });
    }

    public Integer getCommentCount(Integer source, String type) {
        // 使用Redis缓存替换PoetryCache
        Long cachedCount = cacheService.getCachedCommentCount(source, type);
        if (cachedCount != null) {
            return cachedCount.intValue();
        }

        LambdaQueryChainWrapper<Comment> wrapper = new LambdaQueryChainWrapper<>(commentMapper);
        Long c = wrapper.eq(Comment::getSource, source).eq(Comment::getType, type).count();
        Integer result = c.intValue();

        // 缓存评论数量
        cacheService.cacheCommentCount(source, type, c);
        return result;
    }

    public List<Integer> getUserArticleIds(Integer userId) {
        // 使用Redis缓存替换PoetryCache
        String cacheKey = CacheConstants.CACHE_PREFIX + "user:article:list:" + userId;

        // 先尝试获取缓存（无需锁）
        @SuppressWarnings("unchecked")
        List<Integer> ids = (List<Integer>) cacheService.get(cacheKey);
        if (ids != null) {
            return ids;
        }

        // 缓存未命中，使用写锁更新缓存
        return lockManager.executeWithWriteLock("cache:" + CommonConst.USER_ARTICLE_LIST + ":" + userId, () -> {
            // 双重检查锁定
            @SuppressWarnings("unchecked")
            List<Integer> cachedIds = (List<Integer>) cacheService.get(cacheKey);
            if (cachedIds != null) {
                return cachedIds;
            } else {
                LambdaQueryChainWrapper<Article> wrapper = new LambdaQueryChainWrapper<>(articleMapper);
                List<Article> articles = wrapper.eq(Article::getUserId, userId).select(Article::getId).list();
                List<Integer> collect = articles.stream().map(Article::getId).collect(Collectors.toList());
                cacheService.set(cacheKey, collect, CacheConstants.LONG_EXPIRE_TIME);
                return collect;
            }
        });
    }

    public List<List<Integer>> getArticleIds(String searchText) {
        // 如果搜索文本为空，返回空结果
        if (!StringUtils.hasText(searchText)) {
            return Arrays.asList(new ArrayList<>(), new ArrayList<>());
        }

        // 检测是否为正则表达式（以 / 开头和结尾）
        boolean isRegex = searchText.startsWith("/") && searchText.endsWith("/") && searchText.length() > 2;
        String actualSearchText = isRegex ? searchText.substring(1, searchText.length() - 1) : searchText;

        // 限制文章搜索关键词长度，避免过长搜索导致性能问题
        if (actualSearchText.length() > 50) {
            actualSearchText = actualSearchText.substring(0, 50);
        }

        // 使用Redis缓存替换PoetryCache
        String cacheKey = CacheConstants.buildSearchArticleKey(searchText);

        @SuppressWarnings("unchecked")
        List<List<Integer>> cachedIds = (List<List<Integer>>) cacheService.get(cacheKey);
        if (cachedIds != null) {
            return cachedIds;
        }

        // 直接在数据库层面执行搜索，避免加载所有文章内容到内存
        List<List<Integer>> ids = new ArrayList<>();
        Set<Integer> titleIds = new HashSet<>();
        Set<Integer> contentIds = new HashSet<>();

        if (StringUtils.hasText(actualSearchText)) {
            if (isRegex) {
                // 正则表达式搜索
                searchWithRegex(actualSearchText, titleIds, contentIds);
            } else {
                // 普通文本搜索
                searchWithText(actualSearchText, titleIds, contentIds);
            }
        }

        // 转换为List并添加到结果中
        ids.add(new ArrayList<>(titleIds));
        ids.add(new ArrayList<>(contentIds));

        // 缓存搜索结果10分钟
        cacheService.set(cacheKey, ids, 600);

        return ids;
    }

    /**
     * 普通文本搜索
     */
    private void searchWithText(String searchText, Set<Integer> titleIds, Set<Integer> contentIds) {
        // 1. 搜索原文标题包含关键词的文章ID
        LambdaQueryChainWrapper<Article> titleWrapper = new LambdaQueryChainWrapper<>(articleMapper);
        List<Integer> originalTitleIds = titleWrapper
                .select(Article::getId)
                .eq(Article::getDeleted, false)
                .like(Article::getArticleTitle, searchText)
                .last("LIMIT 100") // 限制结果数量
                .list()
                .stream()
                .map(Article::getId)
                .collect(Collectors.toList());
        titleIds.addAll(originalTitleIds);

        // 2. 搜索原文内容包含关键词的文章ID
        LambdaQueryChainWrapper<Article> contentWrapper = new LambdaQueryChainWrapper<>(articleMapper);
        List<Integer> originalContentIds = contentWrapper
                .select(Article::getId)
                .eq(Article::getDeleted, false)
                .like(Article::getArticleContent, searchText)
                .last("LIMIT 100") // 限制结果数量
                .list()
                .stream()
                .map(Article::getId)
                .collect(Collectors.toList());
        contentIds.addAll(originalContentIds);

        // 3. 搜索翻译标题包含关键词的文章ID
        try {
            LambdaQueryChainWrapper<ArticleTranslation> translationTitleWrapper = new LambdaQueryChainWrapper<>(
                    articleTranslationMapper);
            List<Integer> translationTitleIds = translationTitleWrapper
                    .select(ArticleTranslation::getArticleId)
                    .like(ArticleTranslation::getTitle, searchText)
                    .last("LIMIT 100") // 限制结果数量
                    .list()
                    .stream()
                    .map(ArticleTranslation::getArticleId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            // 验证这些文章ID对应的文章是否存在且未删除
            if (!translationTitleIds.isEmpty()) {
                LambdaQueryChainWrapper<Article> validationWrapper = new LambdaQueryChainWrapper<>(articleMapper);
                List<Integer> validTitleIds = validationWrapper
                        .select(Article::getId)
                        .in(Article::getId, translationTitleIds)
                        .eq(Article::getDeleted, false)
                        .list()
                        .stream()
                        .map(Article::getId)
                        .collect(Collectors.toList());
                titleIds.addAll(validTitleIds);
            }
        } catch (Exception e) {
            log.warn("搜索翻译标题时出错，跳过翻译标题搜索: {}", e.getMessage());
        }

        // 4. 搜索翻译内容包含关键词的文章ID
        try {
            LambdaQueryChainWrapper<ArticleTranslation> translationContentWrapper = new LambdaQueryChainWrapper<>(
                    articleTranslationMapper);
            List<Integer> translationContentIds = translationContentWrapper
                    .select(ArticleTranslation::getArticleId)
                    .like(ArticleTranslation::getContent, searchText)
                    .last("LIMIT 100") // 限制结果数量
                    .list()
                    .stream()
                    .map(ArticleTranslation::getArticleId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            // 验证这些文章ID对应的文章是否存在且未删除
            if (!translationContentIds.isEmpty()) {
                LambdaQueryChainWrapper<Article> validationWrapper = new LambdaQueryChainWrapper<>(articleMapper);
                List<Integer> validContentIds = validationWrapper
                        .select(Article::getId)
                        .in(Article::getId, translationContentIds)
                        .eq(Article::getDeleted, false)
                        .list()
                        .stream()
                        .map(Article::getId)
                        .collect(Collectors.toList());
                contentIds.addAll(validContentIds);
            }
        } catch (Exception e) {
            log.warn("搜索翻译内容时出错，跳过翻译内容搜索: {}", e.getMessage());
        }
    }

    /**
     * 正则表达式搜索
     */
    private void searchWithRegex(String regexPattern, Set<Integer> titleIds, Set<Integer> contentIds) {
        try {
            // 验证正则表达式是否有效
            Pattern.compile(regexPattern);

            // 使用MySQL的REGEXP进行数据库层面的正则搜索
            // 1. 搜索原文标题
            try {
                List<Integer> originalTitleIds = articleMapper.selectIdsByTitleRegex(regexPattern);
                titleIds.addAll(originalTitleIds);
            } catch (Exception e) {
                log.warn("正则搜索原文标题时出错: {}", e.getMessage());
            }

            // 2. 搜索原文内容
            try {
                List<Integer> originalContentIds = articleMapper.selectIdsByContentRegex(regexPattern);
                contentIds.addAll(originalContentIds);
            } catch (Exception e) {
                log.warn("正则搜索原文内容时出错: {}", e.getMessage());
            }

            // 3. 搜索翻译标题
            try {
                List<Integer> translationTitleIds = articleTranslationMapper.selectArticleIdsByTitleRegex(regexPattern)
                        .stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

                // 验证这些文章ID对应的文章是否存在且未删除
                if (!translationTitleIds.isEmpty()) {
                    LambdaQueryChainWrapper<Article> validationWrapper = new LambdaQueryChainWrapper<>(articleMapper);
                    List<Integer> validTitleIds = validationWrapper
                            .select(Article::getId)
                            .in(Article::getId, translationTitleIds)
                            .eq(Article::getDeleted, false)
                            .list()
                            .stream()
                            .map(Article::getId)
                            .collect(Collectors.toList());
                    titleIds.addAll(validTitleIds);
                }
            } catch (Exception e) {
                log.warn("正则搜索翻译标题时出错: {}", e.getMessage());
            }

            // 4. 搜索翻译内容
            try {
                List<Integer> translationContentIds = articleTranslationMapper.selectArticleIdsByContentRegex(regexPattern)
                        .stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

                // 验证这些文章ID对应的文章是否存在且未删除
                if (!translationContentIds.isEmpty()) {
                    LambdaQueryChainWrapper<Article> validationWrapper = new LambdaQueryChainWrapper<>(articleMapper);
                    List<Integer> validContentIds = validationWrapper
                            .select(Article::getId)
                            .in(Article::getId, translationContentIds)
                            .eq(Article::getDeleted, false)
                            .list()
                            .stream()
                            .map(Article::getId)
                            .collect(Collectors.toList());
                    contentIds.addAll(validContentIds);
                }
            } catch (Exception e) {
                log.warn("正则搜索翻译内容时出错: {}", e.getMessage());
            }

        } catch (PatternSyntaxException e) {
            log.warn("无效的正则表达式: {}, 错误: {}", regexPattern, e.getMessage());
            // 如果正则表达式无效，回退到普通文本搜索
            searchWithText(regexPattern, titleIds, contentIds);
        } catch (Exception e) {
            log.warn("正则搜索时发生异常: {}, 回退到普通文本搜索", e.getMessage());
            // 如果数据库不支持REGEXP，回退到普通文本搜索
            searchWithText(regexPattern, titleIds, contentIds);
        }
    }

    /**
     * 检查文章是否匹配翻译内容
     */
    public String getMatchedTranslationLanguage(Integer articleId, String searchText) {
        if (articleId == null || !StringUtils.hasText(searchText)) {
            return null;
        }

        try {
            LambdaQueryChainWrapper<ArticleTranslation> wrapper = new LambdaQueryChainWrapper<>(
                    articleTranslationMapper);

            List<ArticleTranslation> translations = wrapper
                    .eq(ArticleTranslation::getArticleId, articleId)
                    .and(w -> w.like(ArticleTranslation::getTitle, searchText)
                            .or()
                            .like(ArticleTranslation::getContent, searchText))
                    .list();

            return translations.isEmpty() ? null : translations.get(0).getLanguage();
        } catch (Exception e) {
            log.warn("检查翻译匹配失败，文章ID: {}, 搜索词: {}, 错误: {}", articleId, searchText, e.getMessage());
            return null;
        }
    }

    /**
     * 获取匹配的翻译内容
     */
    public Map<String, String> getMatchedTranslation(Integer articleId, String searchText, String language) {
        if (articleId == null || !StringUtils.hasText(searchText) || !StringUtils.hasText(language)) {
            return null;
        }

        try {
            LambdaQueryChainWrapper<ArticleTranslation> wrapper = new LambdaQueryChainWrapper<>(
                    articleTranslationMapper);

            ArticleTranslation translation = wrapper
                    .eq(ArticleTranslation::getArticleId, articleId)
                    .eq(ArticleTranslation::getLanguage, language)
                    .and(w -> w.like(ArticleTranslation::getTitle, searchText)
                            .or()
                            .like(ArticleTranslation::getContent, searchText))
                    .one();

            if (translation != null) {
                Map<String, String> result = new HashMap<>();
                result.put("language", translation.getLanguage());
                result.put("title", translation.getTitle());
                result.put("content", translation.getContent());
                return result;
            }
        } catch (Exception e) {
            log.warn("获取匹配翻译内容失败，文章ID: {}, 语言: {}, 错误: {}", articleId, language, e.getMessage());
        }

        return null;
    }

    public List<Sort> getSortInfo() {
        // 直接从数据库查询，不使用缓存
        List<Sort> sorts = new LambdaQueryChainWrapper<>(sortMapper).list();
        if (!CollectionUtils.isEmpty(sorts)) {
            sorts.forEach(sort -> {
                LambdaQueryChainWrapper<Article> sortWrapper = new LambdaQueryChainWrapper<>(articleMapper);
                Long countOfSort = sortWrapper
                        .eq(Article::getSortId, sort.getId())
                        .eq(Article::getDeleted, false)
                        .count();
                sort.setCountOfSort(countOfSort.intValue());

                LambdaQueryChainWrapper<Label> wrapper = new LambdaQueryChainWrapper<>(labelMapper);
                List<Label> labels = wrapper.eq(Label::getSortId, sort.getId()).list();
                if (!CollectionUtils.isEmpty(labels)) {
                    labels.forEach(label -> {
                        LambdaQueryChainWrapper<Article> labelWrapper = new LambdaQueryChainWrapper<>(articleMapper);
                        Long countOfLabel = labelWrapper
                                .eq(Article::getLabelId, label.getId())
                                .eq(Article::getDeleted, false)
                                .count();
                        label.setCountOfLabel(countOfLabel.intValue());
                    });
                    sort.setLabels(labels);
                }
            });
        }
        return sorts;
    }
}
