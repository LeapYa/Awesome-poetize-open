package com.ld.poetry.service;

import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.entity.Article;
import com.ld.poetry.entity.SysAiConfig;
import com.ld.poetry.event.ArticleSavedEvent;
import com.ld.poetry.service.impl.ArticleServiceImpl;
import com.ld.poetry.service.impl.ArticleServiceImpl.ArticleSaveStatus;
import com.ld.poetry.service.SummaryService;
import com.ld.poetry.service.SysAiConfigService;
import com.ld.poetry.service.TranslationService;
import com.ld.poetry.vo.ArticleVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ArticleServiceAsyncStatusTest {

    @Mock
    private TranslationService translationService;

    @Mock
    private SummaryService summaryService;

    @Mock
    private CacheService cacheService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private SysAiConfigService sysAiConfigService;

    private ArticleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = spy(new ArticleServiceImpl());

        ReflectionTestUtils.setField(service, "translationService", translationService);
        ReflectionTestUtils.setField(service, "summaryService", summaryService);
        ReflectionTestUtils.setField(service, "cacheService", cacheService);
        ReflectionTestUtils.setField(service, "eventPublisher", eventPublisher);
        ReflectionTestUtils.setField(service, "sysAiConfigService", sysAiConfigService);

        doAnswer(invocation -> {
            Article article = invocation.getArgument(0);
            article.setId(101);
            return true;
        }).when(service).save(any(Article.class));

        SummaryService.SummaryTaskResult summarySuccess =
                new SummaryService.SummaryTaskResult("saved", "摘要已生成", false);
        when(summaryService.generateAndSaveSummary(anyInt(), any())).thenReturn(summarySuccess);
        when(summaryService.updateSummary(anyInt(), anyString(), any())).thenReturn(summarySuccess);
        doNothing().when(cacheService).evictSortArticleList();
        doNothing().when(eventPublisher).publishEvent(any(ArticleSavedEvent.class));
        when(translationService.saveTranslationResult(anyInt(), anyString(), anyString(), anyString())).thenReturn(true);
    }

    @Test
    void saveArticleAsync_whenTranslationModeIsNone_marksSkippedAndStillSucceeds() throws Exception {
        when(sysAiConfigService.getArticleAiConfigInternal("default")).thenReturn(configWithType("none"));

        PoetryResult<String> result = service.saveArticleAsync(buildCreateArticle(), false, null);
        ArticleSaveStatus finalStatus = awaitTerminalStatus(result.getData());

        assertEquals("success", finalStatus.getStatus());
        assertEquals("complete", finalStatus.getStage());
        assertEquals("skipped", finalStatus.getTranslationStatus());
        assertTrue(finalStatus.getMessage().contains("已跳过AI翻译"));
        verify(summaryService, times(1)).generateAndSaveSummary(eq(101), any());
    }

    @Test
    void saveArticleAsync_whenPendingTranslationExists_marksManualSavedAndStillSucceeds() throws Exception {
        when(sysAiConfigService.getArticleAiConfigInternal("default")).thenReturn(configWithType("llm"));

        Map<String, String> pendingTranslation = Map.of(
                "title", "Manual Title",
                "content", "Manual Content",
                "language", "en");

        PoetryResult<String> result = service.saveArticleAsync(buildCreateArticle(), true, pendingTranslation);
        ArticleSaveStatus finalStatus = awaitTerminalStatus(result.getData());

        assertEquals("success", finalStatus.getStatus());
        assertEquals("manual_saved", finalStatus.getTranslationStatus());
        assertTrue(finalStatus.getMessage().contains("手动翻译"));
        verify(translationService, times(1)).saveTranslationResult(101, "Manual Title", "Manual Content", "en");
        verify(summaryService, times(1)).generateAndSaveSummary(eq(101), any());
    }

    @Test
    void saveArticleAsync_whenAiTranslationFails_stillRunsSummaryAndEndsPartialSuccess() throws Exception {
        when(sysAiConfigService.getArticleAiConfigInternal("default")).thenReturn(configWithType("llm"));
        when(translationService.translateArticleOnly(anyString(), anyString(), anyBoolean(), any(), any())).thenReturn(null);

        PoetryResult<String> result = service.saveArticleAsync(buildCreateArticle(), false, null);
        ArticleSaveStatus finalStatus = awaitTerminalStatus(result.getData());

        assertEquals("partial_success", finalStatus.getStatus());
        assertEquals("complete", finalStatus.getStage());
        assertEquals("failed", finalStatus.getTranslationStatus());
        assertTrue(finalStatus.getMessage().contains("翻译失败"));
        verify(summaryService, times(1)).generateAndSaveSummary(eq(101), any());
    }

    @Test
    void saveArticleAsync_whenTranslationStreamCompletes_taskKeepsProcessingUntilSummaryFinishes() throws Exception {
        when(sysAiConfigService.getArticleAiConfigInternal("default")).thenReturn(configWithType("llm"));

        CountDownLatch translationStreamCompleted = new CountDownLatch(1);
        CountDownLatch releaseTranslation = new CountDownLatch(1);

        when(translationService.translateArticleOnly(anyString(), anyString(), anyBoolean(), any(), any()))
                .thenAnswer(invocation -> {
                    TranslationService.TranslationProgressListener listener = invocation.getArgument(4);
                    listener.onEvent("complete", Map.of("message", "流式翻译完成，等待保存"));
                    translationStreamCompleted.countDown();

                    assertTrue(releaseTranslation.await(2, TimeUnit.SECONDS));
                    return Map.of(
                            "title", "Translated Title",
                            "content", "Translated Content",
                            "language", "en");
                });

        PoetryResult<String> result = service.saveArticleAsync(buildCreateArticle(), false, null);
        assertTrue(translationStreamCompleted.await(2, TimeUnit.SECONDS));

        PoetryResult<ArticleSaveStatus> interimResult = service.getArticleSaveStatus(result.getData());
        ArticleSaveStatus interimStatus = interimResult.getData();
        assertNotNull(interimStatus);
        assertEquals("processing", interimStatus.getStatus());
        assertEquals("translating", interimStatus.getStage());
        assertEquals("streaming", interimStatus.getTranslationStatus());

        releaseTranslation.countDown();

        ArticleSaveStatus finalStatus = awaitTerminalStatus(result.getData());
        assertEquals("success", finalStatus.getStatus());
        assertEquals("complete", finalStatus.getStage());
        assertEquals("saved", finalStatus.getTranslationStatus());
        verify(summaryService, times(1)).generateAndSaveSummary(eq(101), any());
    }

    @Test
    void saveArticleAsync_whenTranslationRetries_keepsReceivedCharsMonotonic() throws Exception {
        when(sysAiConfigService.getArticleAiConfigInternal("default")).thenReturn(configWithType("llm"));

        CountDownLatch progressEmitted = new CountDownLatch(1);
        CountDownLatch releaseTranslation = new CountDownLatch(1);

        when(translationService.translateArticleOnly(anyString(), anyString(), anyBoolean(), any(), any()))
                .thenAnswer(invocation -> {
                    TranslationService.TranslationProgressListener listener = invocation.getArgument(4);
                    listener.onEvent("translation_delta", Map.of(
                            "currentLength", 10000,
                            "receivedLength", 10000));
                    listener.onEvent("content_delta", Map.of(
                            "delta", "first partial",
                            "currentLength", 5000,
                            "receivedLength", 12000));
                    listener.onEvent("retry", Map.of(
                            "attempt", 2,
                            "message", "正在重试整篇流式翻译..."));
                    listener.onEvent("content_delta", Map.of(
                            "delta", "retry partial",
                            "currentLength", 5000,
                            "receivedLength", 15000));
                    progressEmitted.countDown();

                    assertTrue(releaseTranslation.await(2, TimeUnit.SECONDS));
                    return Map.of(
                            "title", "Translated Title",
                            "content", "Translated Content",
                            "language", "en");
                });

        PoetryResult<String> result = service.saveArticleAsync(buildCreateArticle(), false, null);
        assertTrue(progressEmitted.await(2, TimeUnit.SECONDS));

        ArticleSaveStatus interimStatus = service.getArticleSaveStatus(result.getData()).getData();
        assertNotNull(interimStatus);
        assertEquals(15000, interimStatus.getTranslationReceivedChars());
        assertTrue(interimStatus.getMessage().contains("已接收 15000 字"));

        releaseTranslation.countDown();
        ArticleSaveStatus finalStatus = awaitTerminalStatus(result.getData());
        assertEquals("success", finalStatus.getStatus());
    }

    @Test
    void updateArticleAsync_whenPendingTranslationExists_marksManualSavedAndStillSucceeds() throws Exception {
        when(sysAiConfigService.getArticleAiConfigInternal("default")).thenReturn(configWithType("llm"));
        stubUpdateWrapper(true);

        Map<String, String> pendingTranslation = Map.of(
                "title", "Updated Manual Title",
                "content", "Updated Manual Content",
                "language", "en");

        ArticleVO articleVO = buildUpdateArticle(202);
        PoetryResult<String> result = service.updateArticleAsync(articleVO, true, pendingTranslation);
        ArticleSaveStatus finalStatus = awaitTerminalStatus(result.getData());

        assertEquals("success", finalStatus.getStatus());
        assertEquals("manual_saved", finalStatus.getTranslationStatus());
        verify(translationService, times(1)).saveTranslationResult(202, "Updated Manual Title", "Updated Manual Content", "en");
        verify(summaryService, times(1)).updateSummary(eq(202), eq(articleVO.getArticleContent()), any());
    }

    @Test
    void updateArticleAsync_whenAiTranslationFails_stillRunsSummaryAndEndsPartialSuccess() throws Exception {
        when(sysAiConfigService.getArticleAiConfigInternal("default")).thenReturn(configWithType("llm"));
        when(translationService.translateArticleOnly(anyString(), anyString(), anyBoolean(), any(), any())).thenReturn(null);
        stubUpdateWrapper(true);

        ArticleVO articleVO = buildUpdateArticle(303);
        PoetryResult<String> result = service.updateArticleAsync(articleVO, false, null);
        ArticleSaveStatus finalStatus = awaitTerminalStatus(result.getData());

        assertEquals("partial_success", finalStatus.getStatus());
        assertEquals("failed", finalStatus.getTranslationStatus());
        verify(summaryService, times(1)).updateSummary(eq(303), eq(articleVO.getArticleContent()), any());
    }

    private void stubUpdateWrapper(boolean updateResult) {
        Article existingArticle = new Article();
        existingArticle.setId(202);
        existingArticle.setSortId(1);
        existingArticle.setLabelId(1);
        existingArticle.setArticleSlug("old-slug");
        doReturn(existingArticle).when(service).getById(anyInt());

        @SuppressWarnings("unchecked")
        LambdaUpdateChainWrapper<Article> updateWrapper =
                mock(LambdaUpdateChainWrapper.class, Answers.RETURNS_SELF);
        doReturn(updateWrapper).when(updateWrapper).eq(any(), any());
        doReturn(updateWrapper).when(updateWrapper).set(any(), any());
        doReturn(updateWrapper).when(updateWrapper).set(anyBoolean(), any(), any());
        when(updateWrapper.update()).thenReturn(updateResult);
        doReturn(updateWrapper).when(service).lambdaUpdate();
    }

    private ArticleSaveStatus awaitTerminalStatus(String taskId) throws Exception {
        assertNotNull(taskId);

        for (int i = 0; i < 200; i++) {
            PoetryResult<ArticleSaveStatus> result = service.getArticleSaveStatus(taskId);
            ArticleSaveStatus status = result.getData();
            if (status != null && isTerminal(status.getStatus())) {
                return status;
            }
            Thread.sleep(25);
        }

        throw new AssertionError("异步任务未在预期时间内完成: " + taskId);
    }

    private boolean isTerminal(String status) {
        return "success".equals(status) || "failed".equals(status) || "partial_success".equals(status);
    }

    private SysAiConfig configWithType(String translationType) {
        SysAiConfig config = new SysAiConfig();
        config.setTranslationType(translationType);
        return config;
    }

    private ArticleVO buildCreateArticle() {
        ArticleVO articleVO = new ArticleVO();
        articleVO.setUserId(1);
        articleVO.setUpdateBy("tester");
        articleVO.setArticleTitle("Async Save Title");
        articleVO.setArticleContent("Async save content");
        articleVO.setSortId(1);
        articleVO.setLabelId(1);
        articleVO.setViewStatus(false);
        articleVO.setPassword("secret");
        articleVO.setTips("test");
        articleVO.setCommentStatus(true);
        articleVO.setRecommendStatus(false);
        articleVO.setSubmitToSearchEngine(false);
        return articleVO;
    }

    private ArticleVO buildUpdateArticle(int articleId) {
        ArticleVO articleVO = buildCreateArticle();
        articleVO.setId(articleId);
        articleVO.setArticleTitle("Updated Title " + articleId);
        articleVO.setArticleContent("Updated content " + articleId);
        return articleVO;
    }
}
