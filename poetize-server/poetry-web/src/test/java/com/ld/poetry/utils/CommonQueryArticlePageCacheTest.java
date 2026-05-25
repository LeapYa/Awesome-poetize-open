package com.ld.poetry.utils;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ld.poetry.constants.CacheConstants;
import com.ld.poetry.dao.ArticleMapper;
import com.ld.poetry.entity.Article;
import com.ld.poetry.service.CacheService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommonQueryArticlePageCacheTest {

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Article.class);
    }

    @Test
    void cachesMissingNumericArticleToken() throws Exception {
        CommonQuery commonQuery = newCommonQuery();
        ArticleMapper articleMapper = mock(ArticleMapper.class);
        CacheService cacheService = mock(CacheService.class);
        ReflectionTestUtils.setField(commonQuery, "articleMapper", articleMapper);
        ReflectionTestUtils.setField(commonQuery, "cacheService", cacheService);

        String cacheKey = CacheConstants.buildArticlePageExistsKey("id", "404");
        when(cacheService.get(cacheKey)).thenReturn(null, "0");
        when(articleMapper.selectCount(anyArticleWrapper())).thenReturn(0L);

        assertFalse(isKnownArticlePage(commonQuery, "/article/404"));
        assertFalse(isKnownArticlePage(commonQuery, "/article/404"));

        verify(articleMapper, times(1)).selectCount(anyArticleWrapper());
        verify(cacheService).set(cacheKey, "0", CacheConstants.ARTICLE_PAGE_EXISTS_EXPIRE_TIME);
    }

    @Test
    void usesCachedSlugTokenWithoutDatabaseLookup() throws Exception {
        CommonQuery commonQuery = newCommonQuery();
        ArticleMapper articleMapper = mock(ArticleMapper.class);
        CacheService cacheService = mock(CacheService.class);
        ReflectionTestUtils.setField(commonQuery, "articleMapper", articleMapper);
        ReflectionTestUtils.setField(commonQuery, "cacheService", cacheService);

        String cacheKey = CacheConstants.buildArticlePageExistsKey("slug", "real-slug");
        when(cacheService.get(cacheKey)).thenReturn("1");

        assertTrue(isKnownArticlePage(commonQuery, "/article/Real_Slug"));

        verify(articleMapper, never()).selectCount(anyArticleWrapper());
    }

    private CommonQuery newCommonQuery() {
        return new CommonQuery();
    }

    private boolean isKnownArticlePage(CommonQuery commonQuery, String pageUri) throws Exception {
        Method method = CommonQuery.class.getDeclaredMethod("isKnownArticlePage", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(commonQuery, pageUri);
    }

    private Wrapper<Article> anyArticleWrapper() {
        return any();
    }
}
