package com.ld.poetry.service.prerender;

import com.ld.poetry.dao.ResourcePathMapper;
import com.ld.poetry.entity.Article;
import com.ld.poetry.entity.WebInfo;
import com.ld.poetry.service.ArticleService;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.service.SeoConfigService;
import com.ld.poetry.service.SeoMetaService;
import com.ld.poetry.service.TranslationService;
import com.ld.poetry.service.WebInfoService;
import com.ld.poetry.utils.CommonQuery;
import com.ld.poetry.utils.mail.MailUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrerenderServiceTest {

    @Mock
    private PrerenderEngine engine;

    @Mock
    private ArticleService articleService;

    @Mock
    private SeoMetaService seoMetaService;

    @Mock
    private SeoConfigService seoConfigService;

    @Mock
    private WebInfoService webInfoService;

    @Mock
    private CacheService cacheService;

    @Mock
    private TranslationService translationService;

    @Mock
    private PrerenderLanguageSupport languageSupport;

    @Mock
    private CommonQuery commonQuery;

    @Mock
    private ResourcePathMapper resourcePathMapper;

    @Mock
    private MailUtil mailUtil;

    @InjectMocks
    private PrerenderService service;

    @Test
    void renderArticleShouldUseAnonymousPreviewForPaidTranslations() {
        Article article = createPaidArticle();

        when(articleService.getById(1)).thenReturn(article);
        when(translationService.getArticleTranslation(1, "en")).thenReturn(Map.of(
                "title", "Translated",
                "content", "abcdefghij"));
        when(seoMetaService.generateArticleMeta(1, "en")).thenReturn(new LinkedHashMap<>());
        when(cacheService.getCachedWebInfo()).thenReturn(createWebInfo());
        when(seoConfigService.getSeoConfigAsJson()).thenReturn(new LinkedHashMap<>());
        when(languageSupport.getSourceLanguage()).thenReturn("zh");
        when(languageSupport.resolveLanguages(List.of("en"))).thenReturn(List.of("en"));
        when(mailUtil.getSiteUrl()).thenReturn("https://example.com");
        when(engine.renderMarkdown(anyString())).thenReturn("<p>preview</p>");
        when(engine.buildPage(any())).thenReturn("<html>preview</html>");

        service.renderArticle(1, List.of("en"));

        verify(engine).renderMarkdown("abc");
        verify(engine).writePage("article/1", "en", "<html>preview</html>");
    }

    @Test
    void renderArticleShouldSupportExtendedLanguages() {
        Article article = createPaidArticle();

        when(articleService.getById(1)).thenReturn(article);
        when(translationService.getArticleTranslation(1, "pt")).thenReturn(Map.of(
                "title", "Traduzido",
                "content", "conteudo-em-portugues"));
        when(seoMetaService.generateArticleMeta(1, "pt")).thenReturn(new LinkedHashMap<>());
        when(cacheService.getCachedWebInfo()).thenReturn(createWebInfo());
        when(seoConfigService.getSeoConfigAsJson()).thenReturn(new LinkedHashMap<>());
        when(languageSupport.getSourceLanguage()).thenReturn("zh");
        when(languageSupport.resolveLanguages(List.of("pt"))).thenReturn(List.of("pt"));
        when(mailUtil.getSiteUrl()).thenReturn("https://example.com");
        when(engine.renderMarkdown(anyString())).thenReturn("<p>preview</p>");
        when(engine.buildPage(any())).thenReturn("<html>preview</html>");

        assertDoesNotThrow(() -> service.renderArticle(1, List.of("pt")));

        verify(engine).writePage("article/1", "pt", "<html>preview</html>");
    }

    private Article createPaidArticle() {
        Article article = new Article();
        article.setId(1);
        article.setViewStatus(true);
        article.setArticleTitle("Paid Article");
        article.setArticleContent("0123456789");
        article.setPayType(1);
        article.setFreePercent(30);
        return article;
    }

    private WebInfo createWebInfo() {
        WebInfo webInfo = new WebInfo();
        webInfo.setWebTitle("Example Site");
        webInfo.setSiteAddress("https://example.com");
        return webInfo;
    }
}
