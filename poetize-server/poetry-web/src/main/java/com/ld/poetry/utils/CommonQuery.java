package com.ld.poetry.utils;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.dao.*;
import com.ld.poetry.entity.*;
import com.ld.poetry.service.UserService;
import com.ld.poetry.utils.cache.PoetryCache;
import com.ld.poetry.vo.FamilyVO;
import org.apache.commons.io.IOUtils;
import org.lionsoul.ip2region.xdb.Searcher;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;


@Component
public class CommonQuery {
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

    private Searcher searcher;

    @PostConstruct
    public void init() {
        try {
            searcher = Searcher.newWithBuffer(IOUtils.toByteArray(new ClassPathResource("ip2region.xdb").getInputStream()));
        } catch (Exception e) {
        }
    }

    public void saveHistory(String ip) {
        Integer userId = PoetryUtil.getUserId();
        String ipUser = ip + (userId != null ? "_" + userId.toString() : "");

        @SuppressWarnings("unchecked")
        CopyOnWriteArraySet<String> ipHistory = (CopyOnWriteArraySet<String>) PoetryCache.get(CommonConst.IP_HISTORY);
        if (!ipHistory.contains(ipUser)) {
            synchronized (ipUser.intern()) {
                if (!ipHistory.contains(ipUser)) {
                    ipHistory.add(ipUser);
                    HistoryInfo historyInfo = new HistoryInfo();
                    historyInfo.setIp(ip);
                    historyInfo.setUserId(userId);
                    if (searcher != null) {
                        try {
                            String search = searcher.search(ip);
                            String[] region = search.split("\\|");
                            if (!"0".equals(region[0])) {
                                historyInfo.setNation(region[0]);
                            }
                            if (!"0".equals(region[2])) {
                                historyInfo.setProvince(region[2]);
                            }
                            if (!"0".equals(region[3])) {
                                historyInfo.setCity(region[3]);
                            }
                        } catch (Exception e) {
                        }
                    }
                    historyInfoMapper.insert(historyInfo);
                }
            }
        }
    }

    public User getUser(Integer userId) {
        User user = (User) PoetryCache.get(CommonConst.USER_CACHE + userId.toString());
        if (user != null) {
            return user;
        }
        User u = userService.getById(userId);
        if (u != null) {
            PoetryCache.put(CommonConst.USER_CACHE + userId.toString(), u, CommonConst.EXPIRE);
            return u;
        }
        return null;
    }

    public List<User> getAdmire() {
        @SuppressWarnings("unchecked")
        List<User> admire = (List<User>) PoetryCache.get(CommonConst.ADMIRE);
        if (admire != null) {
            return admire;
        }

        synchronized (CommonConst.ADMIRE.intern()) {
            @SuppressWarnings("unchecked")
            List<User> cachedAdmire = (List<User>) PoetryCache.get(CommonConst.ADMIRE);
            if (cachedAdmire != null) {
                return cachedAdmire;
            } else {
                List<User> users = userService.lambdaQuery().select(User::getId, User::getUsername, User::getAdmire, User::getAvatar).isNotNull(User::getAdmire).list();

                PoetryCache.put(CommonConst.ADMIRE, users, CommonConst.EXPIRE);

                return users;
            }
        }
    }

    public List<FamilyVO> getFamilyList() {
        @SuppressWarnings("unchecked")
        List<FamilyVO> familyVOList = (List<FamilyVO>) PoetryCache.get(CommonConst.FAMILY_LIST);
        if (familyVOList != null) {
            return familyVOList;
        }

        synchronized (CommonConst.FAMILY_LIST.intern()) {
            @SuppressWarnings("unchecked")
            List<FamilyVO> cachedFamilyVOList = (List<FamilyVO>) PoetryCache.get(CommonConst.FAMILY_LIST);
            if (cachedFamilyVOList != null) {
                return cachedFamilyVOList;
            } else {
                LambdaQueryChainWrapper<Family> queryChainWrapper = new LambdaQueryChainWrapper<>(familyMapper);
                List<Family> familyList = queryChainWrapper.eq(Family::getStatus, Boolean.TRUE).list();
                if (!CollectionUtils.isEmpty(familyList)) {
                    familyVOList = familyList.stream().map(family -> {
                        FamilyVO familyVO = new FamilyVO();
                        BeanUtils.copyProperties(family, familyVO);
                        return familyVO;
                    }).collect(Collectors.toList());
                } else {
                    familyVOList = new ArrayList<>();
                }

                PoetryCache.put(CommonConst.FAMILY_LIST, familyVOList);
                return familyVOList;
            }
        }
    }

    public Integer getCommentCount(Integer source, String type) {
        Object countObj = PoetryCache.get(CommonConst.COMMENT_COUNT_CACHE + source.toString() + "_" + type);
        if (countObj != null) {
            // 兼容处理，支持Integer和Long类型
            if (countObj instanceof Integer) {
                return (Integer) countObj;
            } else if (countObj instanceof Long) {
                return ((Long) countObj).intValue();
            }
        }
        LambdaQueryChainWrapper<Comment> wrapper = new LambdaQueryChainWrapper<>(commentMapper);
        Long c = wrapper.eq(Comment::getSource, source).eq(Comment::getType, type).count();
        Integer result = c.intValue();
        PoetryCache.put(CommonConst.COMMENT_COUNT_CACHE + source.toString() + "_" + type, result, CommonConst.EXPIRE);
        return result;
    }

    public List<Integer> getUserArticleIds(Integer userId) {
        @SuppressWarnings("unchecked")
        List<Integer> ids = (List<Integer>) PoetryCache.get(CommonConst.USER_ARTICLE_LIST + userId.toString());
        if (ids != null) {
            return ids;
        }

        synchronized ((CommonConst.USER_ARTICLE_LIST + userId.toString()).intern()) {
            @SuppressWarnings("unchecked")
            List<Integer> cachedIds = (List<Integer>) PoetryCache.get(CommonConst.USER_ARTICLE_LIST + userId.toString());
            if (cachedIds != null) {
                return cachedIds;
            } else {
                LambdaQueryChainWrapper<Article> wrapper = new LambdaQueryChainWrapper<>(articleMapper);
                List<Article> articles = wrapper.eq(Article::getUserId, userId).select(Article::getId).list();
                List<Integer> collect = articles.stream().map(Article::getId).collect(Collectors.toList());
                PoetryCache.put(CommonConst.USER_ARTICLE_LIST + userId.toString(), collect, CommonConst.EXPIRE);
                return collect;
            }
        }
    }

    public List<List<Integer>> getArticleIds(String searchText) {
        @SuppressWarnings("unchecked")
        List<Article> articles = (List<Article>) PoetryCache.get(CommonConst.ARTICLE_LIST);
        if (articles == null) {
            synchronized (CommonConst.ARTICLE_LIST.intern()) {
                @SuppressWarnings("unchecked")
                List<Article> cachedArticles = (List<Article>) PoetryCache.get(CommonConst.ARTICLE_LIST);
                if (cachedArticles == null) {
                    LambdaQueryChainWrapper<Article> wrapper = new LambdaQueryChainWrapper<>(articleMapper);
                    articles = wrapper
                            .select(Article::getId, Article::getArticleTitle, Article::getArticleContent)
                            .eq(Article::getDeleted, false)
                            .orderByDesc(Article::getCreateTime)
                            .list();
                    PoetryCache.put(CommonConst.ARTICLE_LIST, articles);
                } else {
                    articles = cachedArticles;
                }
            }
        }

        List<List<Integer>> ids = new ArrayList<>();
        List<Integer> titleIds = new ArrayList<>();
        List<Integer> contentIds = new ArrayList<>();

        for (Article article : articles) {
            if (StringUtil.matchString(article.getArticleTitle(), searchText)) {
                titleIds.add(article.getId());
            } else if (StringUtil.matchString(article.getArticleContent(), searchText)) {
                contentIds.add(article.getId());
            }
        }

        ids.add(titleIds);
        ids.add(contentIds);
        return ids;
    }

    public List<Sort> getSortInfo() {
        @SuppressWarnings("unchecked")
        List<Sort> sortInfo = (List<Sort>) PoetryCache.get(CommonConst.SORT_INFO);
        if (sortInfo != null) {
            return sortInfo;
        }

        synchronized (CommonConst.SORT_INFO.intern()) {
            @SuppressWarnings("unchecked")
            List<Sort> cachedSortInfo = (List<Sort>) PoetryCache.get(CommonConst.SORT_INFO);
            if (cachedSortInfo == null) {
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
                PoetryCache.put(CommonConst.SORT_INFO, sorts);
                return sorts;
            } else {
                return cachedSortInfo;
            }
        }
    }
}
