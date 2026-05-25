package com.ld.poetry.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ld.poetry.dao.ArticleMapper;
import com.ld.poetry.entity.Article;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebInfoControllerArticleStatsTest {

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Article.class);
    }

    @Test
    void buildArticleVisitStatsFiltersMissingArticleTokens() throws Exception {
        WebInfoController controller = new WebInfoController();
        ArticleMapper articleMapper = mock(ArticleMapper.class);
        ReflectionTestUtils.setField(controller, "articleMapper", articleMapper);

        Article article = new Article();
        article.setId(1);
        article.setArticleTitle("Known Article");
        article.setDeleted(false);

        when(articleMapper.selectList(anyArticleWrapper())).thenReturn(List.of(article));

        List<Map<String, Object>> stats = buildArticleVisitStats(controller, List.of(
                rawPageRow("/article/1", 5L),
                rawPageRow("/article/94", 3L)
        ), null);

        assertEquals(1, stats.size());
        assertEquals(1, stats.get(0).get("article_id"));
        assertEquals("Known Article", stats.get(0).get("article_title"));
        assertEquals(5L, stats.get(0).get("num"));
    }

    @Test
    void buildArticleVisitStatsNormalizesSlugTokens() throws Exception {
        WebInfoController controller = new WebInfoController();
        ArticleMapper articleMapper = mock(ArticleMapper.class);
        ReflectionTestUtils.setField(controller, "articleMapper", articleMapper);

        Article article = new Article();
        article.setId(2);
        article.setArticleSlug("real-slug");
        article.setArticleTitle("Slug Article");
        article.setDeleted(false);

        when(articleMapper.selectList(anyArticleWrapper())).thenReturn(List.of(article));

        List<Map<String, Object>> stats = buildArticleVisitStats(controller, List.of(
                rawPageRow("/article/Real_Slug", 2L)
        ), null);

        assertEquals(1, stats.size());
        assertEquals(2, stats.get(0).get("article_id"));
        assertEquals("Slug Article", stats.get(0).get("article_title"));
        assertEquals(2L, stats.get(0).get("num"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildArticleVisitStats(WebInfoController controller,
                                                             List<Map<String, Object>> rawPageRows,
                                                             List<Map<String, Object>> visitRecords) throws Exception {
        Method method = WebInfoController.class.getDeclaredMethod("buildArticleVisitStats", List.class, List.class);
        method.setAccessible(true);
        return (List<Map<String, Object>>) method.invoke(controller, rawPageRows, visitRecords);
    }

    private Map<String, Object> rawPageRow(String pageUri, long num) {
        Map<String, Object> row = new HashMap<>();
        row.put("page_uri", pageUri);
        row.put("num", num);
        return row;
    }

    private Wrapper<Article> anyArticleWrapper() {
        return any();
    }
}
