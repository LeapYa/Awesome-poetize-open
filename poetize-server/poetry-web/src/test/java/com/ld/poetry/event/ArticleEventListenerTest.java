package com.ld.poetry.event;

import com.ld.poetry.service.ArticleService;
import com.ld.poetry.service.SeoService;
import com.ld.poetry.service.SitemapService;
import com.ld.poetry.service.prerender.PrerenderFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ArticleEventListenerTest {

    @Mock
    private PrerenderFacade prerenderFacade;

    @Mock
    private SitemapService sitemapService;

    @Mock
    private SeoService seoService;

    @Mock
    private ArticleService articleService;

    private ArticleEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new ArticleEventListener();
        ReflectionTestUtils.setField(listener, "prerenderFacade", prerenderFacade);
        ReflectionTestUtils.setField(listener, "sitemapService", sitemapService);
        ReflectionTestUtils.setField(listener, "seoService", seoService);
        ReflectionTestUtils.setField(listener, "articleService", articleService);
    }

    @Test
    void handleArticleSavedEventShouldDelegateToFacade() {
        ArticleSavedEvent event = new ArticleSavedEvent(42, 2, 9, 1, 7, null, true, "UPDATE", false);

        listener.handleArticleSavedEvent(event);

        verify(prerenderFacade, timeout(500)).handleArticleEvent(event);
        verify(sitemapService, timeout(500)).updateArticleSitemap(42);
    }
}
