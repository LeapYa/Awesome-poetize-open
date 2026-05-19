package com.ld.poetry.controller;

import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.service.ArticleService;
import com.ld.poetry.vo.ArticleVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class HomeControllerRoutingTest {

    @Mock
    private ArticleService articleService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ArticleController articleController = new ArticleController();
        ReflectionTestUtils.setField(articleController, "articleService", articleService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new HomeController(), articleController)
                .build();
    }

    @Test
    void articlePageRoute_whenBrowserRequestsHtml_forwardsToIndex() throws Exception {
        mockMvc.perform(get("/article/xzz-xx")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"))
                .andExpect(status().isOk())
                .andExpect(handler().handlerType(HomeController.class))
                .andExpect(forwardedUrl("/index.html"));

        verifyNoInteractions(articleService);
    }

    @Test
    void translatedArticlePageRoute_whenBrowserRequestsHtml_forwardsToIndex() throws Exception {
        mockMvc.perform(get("/article/zh-TW/xzz-xx")
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(handler().handlerType(HomeController.class))
                .andExpect(forwardedUrl("/index.html"));

        verifyNoInteractions(articleService);
    }

    @Test
    void numericArticlePageRoute_whenBrowserRequestsHtml_forwardsToIndex() throws Exception {
        mockMvc.perform(get("/article/2")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"))
                .andExpect(status().isOk())
                .andExpect(handler().handlerType(HomeController.class))
                .andExpect(handler().methodName("forwardNumericArticleToIndex"))
                .andExpect(forwardedUrl("/index.html"));

        verifyNoInteractions(articleService);
    }

    @Test
    void getArticleByPath_whenJsonApiRequest_usesArticleController() throws Exception {
        ArticleVO article = new ArticleVO();
        when(articleService.getArticleByPath(eq("xzz-xx"), isNull()))
                .thenReturn(PoetryResult.success(article));

        mockMvc.perform(get("/article/getArticleByPath")
                        .queryParam("path", "xzz-xx")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(handler().handlerType(ArticleController.class))
                .andExpect(handler().methodName("getArticleByPath"));

        verify(articleService).getArticleByPath("xzz-xx", null);
    }

    @Test
    void numericArticlePath_whenJsonApiRequest_usesArticleController() throws Exception {
        ArticleVO article = new ArticleVO();
        when(articleService.getArticleById(eq(2), isNull()))
                .thenReturn(PoetryResult.success(article));

        mockMvc.perform(get("/article/2")
                        .header("Accept", "application/json, text/plain, */*"))
                .andExpect(status().isOk())
                .andExpect(handler().handlerType(ArticleController.class))
                .andExpect(handler().methodName("getArticleByPathId"));

        verify(articleService).getArticleById(2, null);
    }
}
