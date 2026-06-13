package com.ld.poetry.service.ai.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import com.ld.poetry.dao.AiKnowledgeDocumentMapper;
import com.ld.poetry.entity.AiKnowledgeDocument;
import com.ld.poetry.service.ai.rag.dto.KnowledgeSourceDocument;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class KnowledgeIndexService {

    private final AiKnowledgeDocumentMapper knowledgeDocumentMapper;
    private final RagRuntimeFactory ragRuntimeFactory;
    private final RagVersionService ragVersionService;
    private final JsonMapper objectMapper;
    private final Map<String, KnowledgeDocumentProvider> providerMap;

    public KnowledgeIndexService(AiKnowledgeDocumentMapper knowledgeDocumentMapper,
            RagRuntimeFactory ragRuntimeFactory,
            RagVersionService ragVersionService,
            JsonMapper objectMapper,
            List<KnowledgeDocumentProvider> providers) {
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.ragRuntimeFactory = ragRuntimeFactory;
        this.ragVersionService = ragVersionService;
        this.objectMapper = objectMapper;
        this.providerMap = providers.stream()
                .collect(Collectors.toMap(KnowledgeDocumentProvider::sourceType, Function.identity()));
    }

    public boolean isEnabled() {
        return ragRuntimeFactory.getCurrentConfig().isRunnable();
    }

    public String getBlockingReason() {
        return ragRuntimeFactory.getCurrentConfig().runnableBlockReason();
    }

    @Transactional(rollbackFor = Exception.class)
    public void rebuildAll() {
        AiRagConfig config = ragRuntimeFactory.getCurrentConfig();
        if (!config.isRunnable()) {
            throw new IllegalStateException(config.runnableBlockReason());
        }
        EmbeddingModel embeddingModel = ragRuntimeFactory.createEmbeddingModel(config);
        knowledgeDocumentMapper.delete(new LambdaQueryWrapper<AiKnowledgeDocument>()
                .eq(AiKnowledgeDocument::getIndexName, config.indexName()));
        for (KnowledgeDocumentProvider provider : providerMap.values()) {
            for (KnowledgeSourceDocument document : provider.listAllDocuments()) {
                upsertDocument(config, embeddingModel, document);
            }
        }
        ragVersionService.bumpVersion(config.indexName());
    }

    @Transactional(rollbackFor = Exception.class)
    public void syncSource(String sourceType, String sourceId) {
        AiRagConfig config = ragRuntimeFactory.getCurrentConfig();
        KnowledgeDocumentProvider provider = providerMap.get(sourceType);
        if (provider == null) {
            return;
        }
        deleteDocument(config.indexName(), sourceType, sourceId);
        if (!config.isRunnable()) {
            ragVersionService.bumpVersion(config.indexName());
            return;
        }
        EmbeddingModel embeddingModel = ragRuntimeFactory.createEmbeddingModel(config);
        Optional<KnowledgeSourceDocument> document = provider.getDocument(sourceId);
        document.ifPresent(value -> upsertDocument(config, embeddingModel, value));
        ragVersionService.bumpVersion(config.indexName());
    }

    public Map<String, Object> getSyncStatus() {
        AiRagConfig config = ragRuntimeFactory.getCurrentConfig();
        String visibilityScope = KnowledgeVisibilityScope.PUBLIC_ARTICLE.name();
        Long documentCount = knowledgeDocumentMapper.countDistinctDocuments(config.indexName(), visibilityScope);
        Long chunkCount = knowledgeDocumentMapper.countChunks(config.indexName(), visibilityScope);
        LocalDateTime lastSyncTime = knowledgeDocumentMapper.findLastSyncTime(config.indexName(), visibilityScope);
        List<AiKnowledgeDocument> recentDocuments = knowledgeDocumentMapper.selectRecentDocuments(
                config.indexName(), visibilityScope, 20);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", config.enabled());
        result.put("runnable", config.isRunnable());
        result.put("indexName", config.indexName());
        result.put("ragVersion", ragVersionService.getVersion(config.indexName()));
        result.put("databaseType", config.databaseType());
        result.put("databaseVersion", config.databaseVersion());
        result.put("vectorSearchSupported", config.vectorSearchSupported());
        result.put("runtimeEnabled", config.runtimeEnabled());
        result.put("disabledReason", config.runnableBlockReason());
        result.put("documentCount", documentCount != null ? documentCount : 0L);
        result.put("chunkCount", chunkCount != null ? chunkCount : 0L);
        result.put("lastSyncTime", lastSyncTime != null ? lastSyncTime.toString() : null);
        result.put("recentDocuments", recentDocuments.stream().map(row -> Map.of(
                "documentId", row.getDocumentId(),
                "title", row.getTitle() != null ? row.getTitle() : "",
                "sourceType", row.getSourceType(),
                "sourceId", row.getSourceId(),
                "visibilityScope", row.getVisibilityScope(),
                "syncStatus", row.getSyncStatus() != null ? row.getSyncStatus() : "SYNCED",
                "lastError", row.getLastError() != null ? row.getLastError() : "",
                "lastSyncTime", row.getLastSyncTime() != null ? row.getLastSyncTime().toString() : ""
        )).toList());
        return result;
    }

    private void upsertDocument(AiRagConfig config, EmbeddingModel embeddingModel,
            KnowledgeSourceDocument sourceDocument) {
        String normalized = RagTextUtils.normalize(sourceDocument.content());
        if (!StringUtils.hasText(normalized)) {
            return;
        }

        List<String> chunks = RagTextUtils.split(normalized, config.chunkSize(), config.chunkOverlap());
        if (chunks.isEmpty()) {
            return;
        }

        List<AiKnowledgeDocument> entities = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            float[] embedding = embeddingModel.embed(chunk);
            AiKnowledgeDocument entity = new AiKnowledgeDocument();
            entity.setIndexName(config.indexName());
            entity.setDocumentId(sourceDocument.documentId());
            entity.setSourceType(sourceDocument.sourceType());
            entity.setSourceId(sourceDocument.sourceId());
            entity.setTitle(sourceDocument.title());
            entity.setContent(chunk);
            entity.setVisibilityScope(sourceDocument.visibilityScope().name());
            entity.setMetadataJson(toJson(sourceDocument.metadata()));
            entity.setChunkIndex(i);
            entity.setChunkCount(chunks.size());
            entity.setContentHash(md5Hex(chunk));
            entity.setEmbeddingText(toVectorText(embedding));
            entity.setSyncStatus("SYNCED");
            entity.setLastError(null);
            entity.setLastSyncTime(now);
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            entities.add(entity);
        }

        deleteDocument(config.indexName(), sourceDocument.sourceType(), sourceDocument.sourceId());
        for (AiKnowledgeDocument entity : entities) {
            knowledgeDocumentMapper.insert(entity);
        }
    }

    private void deleteDocument(String indexName, String sourceType, String sourceId) {
        knowledgeDocumentMapper.delete(new LambdaQueryWrapper<AiKnowledgeDocument>()
                .eq(AiKnowledgeDocument::getIndexName, indexName)
                .eq(AiKnowledgeDocument::getSourceType, sourceType)
                .eq(AiKnowledgeDocument::getSourceId, sourceId));
    }

    private String toJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata != null ? metadata : Map.of());
        } catch (JacksonException e) {
            return "{}";
        }
    }

    private String toVectorText(float[] embedding) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(Float.toString(embedding[i]));
        }
        builder.append(']');
        return builder.toString();
    }

    private String md5Hex(String content) {
        return DigestUtils.md5DigestAsHex(content.getBytes(StandardCharsets.UTF_8));
    }
}
