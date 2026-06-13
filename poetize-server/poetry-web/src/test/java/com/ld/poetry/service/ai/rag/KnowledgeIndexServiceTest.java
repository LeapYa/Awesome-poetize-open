package com.ld.poetry.service.ai.rag;

import tools.jackson.databind.json.JsonMapper;
import com.ld.poetry.dao.AiKnowledgeDocumentMapper;
import com.ld.poetry.entity.AiKnowledgeDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeIndexServiceTest {

    @Mock
    private AiKnowledgeDocumentMapper knowledgeDocumentMapper;

    @Mock
    private RagRuntimeFactory ragRuntimeFactory;

    @Mock
    private RagVersionService ragVersionService;

    private KnowledgeIndexService knowledgeIndexService;

    private AiRagConfig config;

    @BeforeEach
    void setUp() {
        knowledgeIndexService = new KnowledgeIndexService(
                knowledgeDocumentMapper,
                ragRuntimeFactory,
                ragVersionService,
                JsonMapper.builder().build(),
                List.of());
        config = new AiRagConfig(
                true,
                "poetize_ai_chat",
                "openai",
                "https://api.openai.com/v1",
                "test-key",
                "text-embedding-3-small",
                5,
                0.2D,
                700,
                120,
                1536,
                "mariadb",
                "11.7.1-MariaDB",
                true,
                true,
                null);
        when(ragRuntimeFactory.getCurrentConfig()).thenReturn(config);
        when(ragVersionService.getVersion(config.indexName())).thenReturn("rag-v3");
    }

    @Test
    void shouldUseAggregateStatsInsteadOfRecentSampleCounts() {
        LocalDateTime lastSyncTime = LocalDateTime.of(2026, 3, 23, 12, 30, 0);
        AiKnowledgeDocument recent = new AiKnowledgeDocument();
        recent.setDocumentId("doc-1");
        recent.setTitle("公开文章");
        recent.setSourceType(KnowledgeSourceType.ARTICLE.name());
        recent.setSourceId("1001");
        recent.setVisibilityScope(KnowledgeVisibilityScope.PUBLIC_ARTICLE.name());
        recent.setSyncStatus("SYNCED");
        recent.setLastError("");
        recent.setLastSyncTime(lastSyncTime);

        when(knowledgeDocumentMapper.countDistinctDocuments(config.indexName(), KnowledgeVisibilityScope.PUBLIC_ARTICLE.name()))
                .thenReturn(18L);
        when(knowledgeDocumentMapper.countChunks(config.indexName(), KnowledgeVisibilityScope.PUBLIC_ARTICLE.name()))
                .thenReturn(256L);
        when(knowledgeDocumentMapper.findLastSyncTime(config.indexName(), KnowledgeVisibilityScope.PUBLIC_ARTICLE.name()))
                .thenReturn(lastSyncTime);
        when(knowledgeDocumentMapper.selectRecentDocuments(
                config.indexName(),
                KnowledgeVisibilityScope.PUBLIC_ARTICLE.name(),
                20)).thenReturn(List.of(recent));

        Map<String, Object> status = knowledgeIndexService.getSyncStatus();

        assertEquals(18L, status.get("documentCount"));
        assertEquals(256L, status.get("chunkCount"));
        assertEquals("rag-v3", status.get("ragVersion"));
        assertEquals(lastSyncTime.toString(), status.get("lastSyncTime"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> recentDocuments = (List<Map<String, Object>>) status.get("recentDocuments");
        assertEquals(1, recentDocuments.size());
        assertEquals("公开文章", recentDocuments.get(0).get("title"));

        verify(knowledgeDocumentMapper).selectRecentDocuments(
                config.indexName(),
                KnowledgeVisibilityScope.PUBLIC_ARTICLE.name(),
                20);
    }

    @Test
    void shouldExposeRunnableBlockReasonInStatus() {
        config = new AiRagConfig(
                false,
                "poetize_ai_chat",
                "openai",
                "https://api.openai.com/v1",
                "test-key",
                "text-embedding-3-small",
                5,
                0.2D,
                700,
                120,
                1536,
                "mariadb",
                "11.8.1-MariaDB",
                true,
                true,
                null);
        when(ragRuntimeFactory.getCurrentConfig()).thenReturn(config);
        when(knowledgeDocumentMapper.countDistinctDocuments(config.indexName(), KnowledgeVisibilityScope.PUBLIC_ARTICLE.name()))
                .thenReturn(0L);
        when(knowledgeDocumentMapper.countChunks(config.indexName(), KnowledgeVisibilityScope.PUBLIC_ARTICLE.name()))
                .thenReturn(0L);
        when(knowledgeDocumentMapper.findLastSyncTime(config.indexName(), KnowledgeVisibilityScope.PUBLIC_ARTICLE.name()))
                .thenReturn(null);
        when(knowledgeDocumentMapper.selectRecentDocuments(
                config.indexName(),
                KnowledgeVisibilityScope.PUBLIC_ARTICLE.name(),
                20)).thenReturn(List.of());

        Map<String, Object> status = knowledgeIndexService.getSyncStatus();

        assertEquals(false, status.get("enabled"));
        assertEquals(false, status.get("runnable"));
        assertTrue(String.valueOf(status.get("disabledReason")).contains("保存外观与排版设置"));
    }
}
