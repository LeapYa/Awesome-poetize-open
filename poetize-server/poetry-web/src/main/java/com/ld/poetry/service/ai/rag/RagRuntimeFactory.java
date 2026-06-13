package com.ld.poetry.service.ai.rag;

import tools.jackson.databind.json.JsonMapper;

import com.ld.poetry.entity.SysAiConfig;
import com.ld.poetry.service.SysAiConfigService;
import com.ld.poetry.service.ai.AiApiBaseUrlNormalizer;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientImpl;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RagRuntimeFactory {

    private final SysAiConfigService sysAiConfigService;
    private final Environment environment;
    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper jsonMapper;

    public RagRuntimeFactory(SysAiConfigService sysAiConfigService,
            Environment environment,
            JdbcTemplate jdbcTemplate,
            JsonMapper jsonMapper) {
        this.sysAiConfigService = sysAiConfigService;
        this.environment = environment;
        this.jdbcTemplate = jdbcTemplate;
        this.jsonMapper = jsonMapper;
    }

    public AiRagConfig getCurrentConfig() {
        SysAiConfig config = sysAiConfigService.getAiChatConfigInternal("default");
        String databaseType = resolveDatabaseType();
        boolean runtimeEnabled = resolveRuntimeEnabled();
        RagDatabaseCapability capability = detectDatabaseCapability(databaseType, runtimeEnabled);
        return AiRagConfig.from(config, jsonMapper, databaseType, capability.version(),
                capability.vectorSearchSupported(), runtimeEnabled, capability.disabledReason());
    }

    public EmbeddingModel createEmbeddingModel(AiRagConfig config) {
        if (config == null || !config.isRunnable()) {
            throw new IllegalStateException("RAG embedding 配置未就绪");
        }
        String provider = StringUtils.hasText(config.embeddingProvider())
                ? config.embeddingProvider().toLowerCase()
                : "openai";
        if ("anthropic".equals(provider)) {
            throw new IllegalStateException("当前 RAG 暂不支持 Anthropic Embedding");
        }

        OpenAIClient client = new OpenAIClientImpl(
                com.openai.core.ClientOptions.builder()
                        .apiKey(config.embeddingApiKey())
                        .baseUrl(AiApiBaseUrlNormalizer.normalizeOpenAiCompatibleBaseUrl(
                                config.embeddingApiBase(), "https://api.openai.com"))
                        .httpClient(org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient.builder().build())
                        .build());

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(config.embeddingModel())
                .dimensions(config.embeddingDimensions())
                .build();

        return OpenAiEmbeddingModel.builder()
                .openAiClient(client)
                .options(options)
                .metadataMode(MetadataMode.NONE)
                .build();
    }

    private String resolveDatabaseType() {
        String databaseType = environment.getProperty("DB_TYPE");
        if (StringUtils.hasText(databaseType)) {
            return databaseType;
        }
        String datasourceUrl = environment.getProperty("spring.datasource.url");
        if (StringUtils.hasText(datasourceUrl)) {
            String lowerUrl = datasourceUrl.toLowerCase();
            if (lowerUrl.startsWith("jdbc:mysql:")) return "mysql";
            if (lowerUrl.startsWith("jdbc:mariadb:")) return "mariadb";
        }
        return "mariadb";
    }

    private boolean resolveRuntimeEnabled() {
        return Boolean.parseBoolean(environment.getProperty("RAG_ENABLED", "true"));
    }

    private boolean isMariaDb(String databaseType) {
        return "mariadb".equalsIgnoreCase(databaseType);
    }

    private RagDatabaseCapability detectDatabaseCapability(String databaseType, boolean runtimeEnabled) {
        if (!runtimeEnabled) {
            return new RagDatabaseCapability(null, false, "当前部署已关闭向量搜索。");
        }
        if (!isMariaDb(databaseType)) {
            return new RagDatabaseCapability(readDatabaseVersionSafely(), false, "当前数据库为 MySQL，已关闭向量搜索。");
        }
        String version = readDatabaseVersionSafely();
        DatabaseVersion databaseVersion = parseDatabaseVersion(version);
        if (databaseVersion == null) {
            return new RagDatabaseCapability(version, false, "无法识别当前 MariaDB 版本，已关闭向量搜索。");
        }
        if (!databaseVersion.isAtLeast(11, 7)) {
            return new RagDatabaseCapability(version, false, "当前 MariaDB 版本低于 11.7，已关闭向量搜索。");
        }
        if (!probeVectorFunctions()) {
            return new RagDatabaseCapability(version, false, "当前 MariaDB 未启用向量函数能力，已关闭向量搜索。");
        }
        return new RagDatabaseCapability(version, true, null);
    }

    private String readDatabaseVersionSafely() {
        try {
            return jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
        } catch (DataAccessException ex) {
            return null;
        }
    }

    private DatabaseVersion parseDatabaseVersion(String version) {
        if (!StringUtils.hasText(version)) return null;
        String normalized = version.trim();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)\\.(\\d+)").matcher(normalized);
        if (!matcher.find()) return null;
        try {
            return new DatabaseVersion(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean probeVectorFunctions() {
        try {
            Double result = jdbcTemplate.queryForObject(
                    "SELECT VEC_DISTANCE_COSINE(VEC_FromText('[1,0]'), VEC_FromText('[1,0]'))",
                    Double.class);
            return result != null;
        } catch (DataAccessException ex) {
            return false;
        }
    }

    private record RagDatabaseCapability(
            String version,
            boolean vectorSearchSupported,
            String disabledReason) {
    }

    private record DatabaseVersion(int major, int minor) {
        private boolean isAtLeast(int expectedMajor, int expectedMinor) {
            return major > expectedMajor || (major == expectedMajor && minor >= expectedMinor);
        }
    }
}