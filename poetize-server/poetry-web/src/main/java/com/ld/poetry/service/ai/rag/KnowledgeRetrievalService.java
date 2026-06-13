package com.ld.poetry.service.ai.rag;

import com.ld.poetry.dao.AiKnowledgeDocumentMapper;
import com.ld.poetry.entity.AiKnowledgeDocument;
import com.ld.poetry.service.ai.ContentSanitizer;
import com.ld.poetry.service.ai.rag.dto.KnowledgePromptContext;
import com.ld.poetry.service.ai.rag.dto.KnowledgeSearchHit;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class KnowledgeRetrievalService {

    private static final int RAW_TOP_K_CAP = 12;
    private static final int MAX_CONTEXT_DOCUMENTS = 3;
    private static final int NEIGHBOR_CHUNK_RADIUS = 1;
    private static final int CONTEXT_CHAR_BUDGET = 3500;

    private final AiKnowledgeDocumentMapper knowledgeDocumentMapper;
    private final RagRuntimeFactory ragRuntimeFactory;
    private final RagVersionService ragVersionService;
    private final ContentSanitizer contentSanitizer;

    public KnowledgeRetrievalService(AiKnowledgeDocumentMapper knowledgeDocumentMapper,
            RagRuntimeFactory ragRuntimeFactory,
            RagVersionService ragVersionService,
            ContentSanitizer contentSanitizer) {
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.ragRuntimeFactory = ragRuntimeFactory;
        this.ragVersionService = ragVersionService;
        this.contentSanitizer = contentSanitizer;
    }

    public KnowledgePromptContext buildPromptContext(String query, Map<String, Object> pageContext) {
        long startedAt = System.currentTimeMillis();
        String retrievalQuery = buildRetrievalQuery(query, pageContext);
        AiRagConfig config = ragRuntimeFactory.getCurrentConfig();
        String ragVersion = ragVersionService.getVersion(config.indexName());
        if (!StringUtils.hasText(retrievalQuery)) {
            return KnowledgePromptContext.empty(retrievalQuery, ragVersion, System.currentTimeMillis() - startedAt);
        }
        if (!config.isRunnable()) {
            return KnowledgePromptContext.empty(retrievalQuery, ragVersion, System.currentTimeMillis() - startedAt);
        }

        EmbeddingModel embeddingModel = ragRuntimeFactory.createEmbeddingModel(config);
        float[] embedding = embeddingModel.embed(retrievalQuery);
        List<KnowledgeSearchHit> rawHits = dedupeRawHits(knowledgeDocumentMapper.searchSimilarChunks(
                config.indexName(),
                toVectorText(embedding),
                KnowledgeVisibilityScope.PUBLIC_ARTICLE.name(),
                resolveRawTopK(config.topK()),
                config.distanceThreshold()));

        String assembledContext = assemblePromptContext(config, rawHits);
        return new KnowledgePromptContext(
                assembledContext,
                List.of(),
                retrievalQuery,
                rawHits,
                ragVersion,
                System.currentTimeMillis() - startedAt);
    }

    String buildRetrievalQuery(String query, Map<String, Object> pageContext) {
        Map<String, Object> sanitized = contentSanitizer.sanitizePageContext(pageContext);
        List<String> parts = new ArrayList<>();
        String normalizedQuery = RagTextUtils.normalize(query);
        String title = RagTextUtils.normalize((String) sanitized.getOrDefault("title", ""));
        String type = RagTextUtils.normalize((String) sanitized.getOrDefault("type", ""));
        if (StringUtils.hasText(normalizedQuery)) {
            parts.add(normalizedQuery);
        }
        if (StringUtils.hasText(title)) {
            parts.add("页面标题：" + title);
        }
        if (StringUtils.hasText(type)) {
            parts.add("页面类型：" + type);
        }
        return String.join("\n", parts).trim();
    }

    private String assemblePromptContext(AiRagConfig config, List<KnowledgeSearchHit> rawHits) {
        if (rawHits == null || rawHits.isEmpty()) {
            return "";
        }

        int maxDocuments = Math.min(MAX_CONTEXT_DOCUMENTS, Math.max(1, config.topK()));
        List<KnowledgeSearchHit> seedHits = selectSeedHits(rawHits, maxDocuments);
        if (seedHits.isEmpty()) {
            return "";
        }

        int remainingBudget = CONTEXT_CHAR_BUDGET;
        StringBuilder builder = new StringBuilder();
        builder.append("[RAG 检索上下文]\n");
        int index = 1;
        for (KnowledgeSearchHit seedHit : seedHits) {
            if (remainingBudget <= 0) {
                break;
            }
            String mergedContent = mergeNeighborChunks(config.indexName(), seedHit);
            if (!StringUtils.hasText(mergedContent)) {
                continue;
            }
            String header = index++
                    + ". 文章：" + defaultText(seedHit.getTitle(), "未命名文章")
                    + " (ID=" + defaultText(seedHit.getSourceId(), seedHit.getDocumentId())
                    + ", 相似度=" + String.format("%.3f", seedHit.getSimilarity() != null ? seedHit.getSimilarity() : 0D)
                    + ")\n片段：";
            int maxContentLength = Math.max(0, remainingBudget - header.length() - 2);
            if (maxContentLength <= 0) {
                break;
            }
            String clippedContent = mergedContent.length() > maxContentLength
                    ? RagTextUtils.abbreviate(mergedContent, maxContentLength)
                    : mergedContent;
            builder.append(header)
                    .append(clippedContent)
                    .append("\n");
            remainingBudget -= header.length() + clippedContent.length() + 1;
        }
        if (builder.length() <= "[RAG 检索上下文]\n".length()) {
            return "";
        }
        builder.append("请优先基于以上检索事实回答；若检索上下文不足，再结合通用知识补充，并避免编造站内事实。\n");
        return builder.toString();
    }

    private List<KnowledgeSearchHit> dedupeRawHits(List<KnowledgeSearchHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        Map<String, KnowledgeSearchHit> deduped = new LinkedHashMap<>();
        for (KnowledgeSearchHit hit : hits) {
            if (hit == null) {
                continue;
            }
            deduped.putIfAbsent(hit.getDocumentId() + ":" + hit.getChunkIndex(), hit);
        }
        return new ArrayList<>(deduped.values());
    }

    private List<KnowledgeSearchHit> selectSeedHits(List<KnowledgeSearchHit> rawHits, int maxDocuments) {
        Map<String, KnowledgeSearchHit> seeds = new LinkedHashMap<>();
        for (KnowledgeSearchHit hit : rawHits) {
            if (hit == null || !StringUtils.hasText(hit.getDocumentId())) {
                continue;
            }
            seeds.putIfAbsent(hit.getDocumentId(), hit);
            if (seeds.size() >= maxDocuments) {
                break;
            }
        }
        return new ArrayList<>(seeds.values());
    }

    private String mergeNeighborChunks(String indexName, KnowledgeSearchHit seedHit) {
        int startChunkIndex = Math.max(0, seedHit.getChunkIndex() - NEIGHBOR_CHUNK_RADIUS);
        int endChunkIndex = Math.min(seedHit.getChunkCount() - 1, seedHit.getChunkIndex() + NEIGHBOR_CHUNK_RADIUS);
        List<AiKnowledgeDocument> chunks = knowledgeDocumentMapper.selectChunksByDocumentRange(
                indexName, seedHit.getDocumentId(), startChunkIndex, endChunkIndex);
        if (chunks == null || chunks.isEmpty()) {
            return RagTextUtils.normalize(seedHit.getContent());
        }
        return RagTextUtils.normalize(chunks.stream()
                .sorted(Comparator.comparing(AiKnowledgeDocument::getChunkIndex))
                .map(AiKnowledgeDocument::getContent)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("\n")));
    }

    private int resolveRawTopK(int topK) {
        return Math.max(1, Math.min(RAW_TOP_K_CAP, Math.max(1, topK) * 3));
    }

    private String defaultText(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary : fallback;
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
}
