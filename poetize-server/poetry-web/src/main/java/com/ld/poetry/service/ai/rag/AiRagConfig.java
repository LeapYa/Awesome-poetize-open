package com.ld.poetry.service.ai.rag;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.ld.poetry.entity.SysAiConfig;
import org.springframework.util.StringUtils;

/**
 * AI 聊天 RAG 配置。
 * 统一存放在 sys_ai_config.extra_config.rag 下。
 */
public record AiRagConfig(
        boolean enabled,
        String indexName,
        String embeddingProvider,
        String embeddingApiBase,
        String embeddingApiKey,
        String embeddingModel,
        int topK,
        double scoreThreshold,
        int chunkSize,
        int chunkOverlap,
        int embeddingDimensions,
        String databaseType,
        String databaseVersion,
        boolean vectorSearchSupported,
        boolean runtimeEnabled,
        String disabledReason) {

    public static final String EXTRA_CONFIG_KEY = "rag";
    public static final String DEFAULT_INDEX_NAME = "poetize_ai_chat";
    public static final String DEFAULT_EMBEDDING_MODEL = "text-embedding-3-small";
    public static final int DEFAULT_TOP_K = 5;
    public static final double DEFAULT_SCORE_THRESHOLD = 0.2D;
    public static final int DEFAULT_CHUNK_SIZE = 700;
    public static final int DEFAULT_CHUNK_OVERLAP = 120;
    public static final int DEFAULT_EMBEDDING_DIMENSIONS = 1536;

    public static AiRagConfig disabled() {
        return new AiRagConfig(false, DEFAULT_INDEX_NAME, "openai", null, null, DEFAULT_EMBEDDING_MODEL,
                DEFAULT_TOP_K, DEFAULT_SCORE_THRESHOLD, DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP,
                DEFAULT_EMBEDDING_DIMENSIONS, "mariadb", null, true, true, null);
    }

    public static AiRagConfig from(SysAiConfig config, JsonMapper objectMapper) {
        return from(config, objectMapper, "mariadb", null, true, true, null);
    }

    public static AiRagConfig from(SysAiConfig config, JsonMapper objectMapper, String databaseType,
            String databaseVersion, boolean vectorSearchSupported, boolean runtimeEnabled, String disabledReason) {
        if (config == null) {
            return disabled().withRuntime(databaseType, databaseVersion, vectorSearchSupported, runtimeEnabled, disabledReason);
        }
        try {
            JsonNode root = StringUtils.hasText(config.getExtraConfig())
                    ? objectMapper.readTree(config.getExtraConfig())
                    : objectMapper.createObjectNode();
            JsonNode ragNode = root.path(EXTRA_CONFIG_KEY);
            if (ragNode.isMissingNode() || ragNode.isNull()) {
                return disabled().withRuntime(databaseType, databaseVersion, vectorSearchSupported, runtimeEnabled, disabledReason);
            }

            boolean enabled = ragNode.path("enabled").asBoolean(false);
            String embeddingProvider = textOrDefault(ragNode.path("embeddingProvider").asText(null),
                    fallback(config.getProvider(), "openai"));
            String embeddingApiBase = textOrDefault(ragNode.path("embeddingApiBase").asText(null), config.getApiBase());
            String embeddingApiKey = textOrDefault(ragNode.path("embeddingApiKey").asText(null), config.getApiKey());
            String embeddingModel = textOrDefault(ragNode.path("embeddingModel").asText(null), DEFAULT_EMBEDDING_MODEL);
            String indexName = textOrDefault(ragNode.path("indexName").asText(null), DEFAULT_INDEX_NAME);
            int topK = positiveOrDefault(ragNode.path("topK").asInt(0), DEFAULT_TOP_K);
            double scoreThreshold = positiveDoubleOrDefault(ragNode.path("scoreThreshold").asDouble(-1D),
                    DEFAULT_SCORE_THRESHOLD);
            int chunkSize = positiveOrDefault(ragNode.path("chunkSize").asInt(0), DEFAULT_CHUNK_SIZE);
            int chunkOverlap = ragNode.path("chunkOverlap").asInt(DEFAULT_CHUNK_OVERLAP);
            int embeddingDimensions = positiveOrDefault(ragNode.path("embeddingDimensions").asInt(0),
                    DEFAULT_EMBEDDING_DIMENSIONS);

            if (chunkOverlap >= chunkSize) {
                chunkOverlap = Math.max(0, chunkSize / 5);
            }

            return new AiRagConfig(enabled, indexName, embeddingProvider, embeddingApiBase, embeddingApiKey,
                    embeddingModel, topK, scoreThreshold, chunkSize, chunkOverlap, embeddingDimensions,
                    normalizeDatabaseType(databaseType), databaseVersion, vectorSearchSupported, runtimeEnabled, disabledReason);
        } catch (Exception ignored) {
            return disabled().withRuntime(databaseType, databaseVersion, vectorSearchSupported, runtimeEnabled, disabledReason);
        }
    }

    public boolean isRunnable() {
        return runnableBlockReason() == null;
    }

    public String runnableBlockReason() {
        if (!runtimeEnabled) {
            return StringUtils.hasText(disabledReason) ? disabledReason : "当前部署未启用向量检索运行时。";
        }
        if (!vectorSearchSupported) {
            return StringUtils.hasText(disabledReason) ? disabledReason : "当前数据库环境不支持向量检索。";
        }
        if (!enabled) {
            return "RAG 当前未启用；如果刚修改过配置，请先点击页面底部的“保存外观与排版设置”。";
        }
        if (!StringUtils.hasText(embeddingApiKey)) {
            return "Embedding API Key 未配置，且 AI 聊天主模型 API Key 也不可用。";
        }
        if (!StringUtils.hasText(embeddingModel)) {
            return "Embedding 模型未配置。";
        }
        return null;
    }

    public double distanceThreshold() {
        double similarity = Math.max(0D, Math.min(1D, scoreThreshold));
        return 1D - similarity;
    }

    private static String textOrDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private static int positiveOrDefault(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private static double positiveDoubleOrDefault(double value, double fallback) {
        return value >= 0D ? value : fallback;
    }

    private static String fallback(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private AiRagConfig withRuntime(String databaseType, String databaseVersion, boolean vectorSearchSupported,
            boolean runtimeEnabled, String disabledReason) {
        return new AiRagConfig(enabled, indexName, embeddingProvider, embeddingApiBase, embeddingApiKey,
                embeddingModel, topK, scoreThreshold, chunkSize, chunkOverlap, embeddingDimensions,
                normalizeDatabaseType(databaseType), databaseVersion, vectorSearchSupported, runtimeEnabled, disabledReason);
    }

    private static String normalizeDatabaseType(String databaseType) {
        return StringUtils.hasText(databaseType) ? databaseType.toLowerCase() : "mariadb";
    }
}
