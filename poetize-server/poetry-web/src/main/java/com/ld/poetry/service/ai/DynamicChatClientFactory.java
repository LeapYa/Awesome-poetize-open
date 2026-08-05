package com.ld.poetry.service.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.AnthropicClientImpl;
import com.anthropic.core.ClientOptions;
import com.anthropic.models.messages.Model;
import com.ld.poetry.entity.SysAiConfig;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientImpl;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.OpenAIClientAsyncImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态 ChatModel 工厂 — Spring AI 2.0 RC2 实现
 * <p>
 * RC2 中移除了 OpenAiApi/AnthropicApi，改为直接使用原生 SDK Client（OpenAIClient / AnthropicClient）。
 * ChatModel 通过 Builder 接收 Client + Options 构建。
 */
@Service
public class DynamicChatClientFactory {

    private static final Logger logger = LoggerFactory.getLogger(DynamicChatClientFactory.class);

    /** ChatModel 缓存 TTL（秒）：30 分钟兜底，管理员变更配置时主动清空 */
    private static final long CACHE_TTL_SECONDS = 30 * 60L;

    private final AiThinkingAdapterRegistry thinkingAdapterRegistry;

    /** ChatModel 实例缓存，避免每次请求重建 OkHttpClient 导致的连接泄漏 */
    private final Map<String, CachedChatModel> chatModelCache = new ConcurrentHashMap<>();

    public DynamicChatClientFactory(AiThinkingAdapterRegistry thinkingAdapterRegistry) {
        this.thinkingAdapterRegistry = thinkingAdapterRegistry;
    }

    public ChatModel createChatModel(SysAiConfig config) {
        String provider = config.getProvider();
        if (provider == null || provider.isBlank()) {
            provider = "openai";
        }
        String cacheKey = buildCacheKey(config, provider);
        ChatModel cached = getIfValid(cacheKey);
        if (cached != null) {
            logger.debug("命中 ChatModel 缓存: provider={}, model={}, baseUrl={}",
                    provider, config.getModel(), config.getApiBase());
            return cached;
        }
        logger.debug("创建 ChatModel: provider={}, model={}, baseUrl={}",
                provider, config.getModel(), config.getApiBase());
        ChatModel chatModel = switch (provider.toLowerCase()) {
            case "openai", "deepseek", "siliconflow", "openrouter", "worldrouter", "custom" ->
                createOpenAiCompatible(config);
            case "anthropic" -> createAnthropic(config);
            default -> {
                logger.warn("不支持的 provider: {}, 降级为 OpenAI 兼容模式", provider);
                yield createOpenAiCompatible(config);
            }
        };
        chatModelCache.put(cacheKey, new CachedChatModel(chatModel, System.currentTimeMillis()));
        return chatModel;
    }

    /**
     * 失效全部 ChatModel 缓存。管理员保存/删除/切换 AI 配置时调用，
     * 防止旧配置（含已轮换的 API Key / 已变更的 baseUrl）继续被复用。
     */
    public void invalidateAll() {
        int size = chatModelCache.size();
        if (size > 0) {
            chatModelCache.clear();
            logger.info("已清空 ChatModel 缓存: {} 条", size);
        }
    }

    private ChatModel getIfValid(String cacheKey) {
        CachedChatModel entry = chatModelCache.get(cacheKey);
        if (entry == null) {
            return null;
        }
        long ageSeconds = (System.currentTimeMillis() - entry.createdAt()) / 1000L;
        if (ageSeconds > CACHE_TTL_SECONDS) {
            chatModelCache.remove(cacheKey);
            return null;
        }
        return entry.chatModel();
    }

    /**
     * 构建缓存 key：SHA-256(影响 ChatModel 构建的所有字段)。
     * 字段集与 createOpenAiCompatible/createAnthropic + thinkingAdapterRegistry.resolve 的输入对齐，
     * 任一字段变更都会产生新 key，确保配置变更后不复用旧实例。
     */
    private String buildCacheKey(SysAiConfig config, String normalizedProvider) {
        String raw = String.join("|",
                nullSafe(normalizedProvider),
                nullSafe(config.getApiKey()),
                nullSafe(config.getApiBase()),
                nullSafe(config.getModel()),
                nullSafe(config.getTemperature()),
                nullSafe(config.getMaxTokens()),
                nullSafe(config.getTopP()),
                nullSafe(config.getFrequencyPenalty()),
                nullSafe(config.getPresencePenalty()),
                nullSafe(config.getEnableThinking()),
                nullSafe(config.getReasoningEffort()));
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 标准算法，理论上不会缺失；退化为原始字符串 hash 兜底
            return Integer.toHexString(raw.hashCode());
        }
    }

    private static String nullSafe(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private record CachedChatModel(ChatModel chatModel, long createdAt) {}

    private ChatModel createOpenAiCompatible(SysAiConfig config) {
        String apiKey = config.getApiKey();
        String baseUrl = AiApiBaseUrlNormalizer.normalizeOpenAiCompatibleBaseUrl(
                config.getApiBase(), defaultOpenAiCompatibleBaseUrl(config.getProvider()));
        String model = config.getModel() != null ? config.getModel() : "gpt-4o-mini";
        double temperature = config.getTemperature() != null
                ? config.getTemperature().doubleValue() : 0.7;

        var clientOptions = com.openai.core.ClientOptions.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .httpClient(org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient.builder().build())
                .build();
        OpenAIClient client = new OpenAIClientImpl(clientOptions);

        var optionsBuilder = OpenAiChatOptions.builder()
                .model(model)
                .temperature(temperature);

        if (config.getMaxTokens() != null) {
            optionsBuilder.maxTokens(config.getMaxTokens());
        }
        if (config.getTopP() != null) {
            optionsBuilder.topP(config.getTopP().doubleValue());
        }
        if (config.getFrequencyPenalty() != null) {
            optionsBuilder.frequencyPenalty(config.getFrequencyPenalty().doubleValue());
        }
        if (config.getPresencePenalty() != null) {
            optionsBuilder.presencePenalty(config.getPresencePenalty().doubleValue());
        }

        AiThinkingAdapterRegistry.ThinkingRequest thinkingRequest = thinkingAdapterRegistry.resolve(config);
        if (thinkingRequest.reasoningEffort() != null) {
            optionsBuilder.reasoningEffort(thinkingRequest.reasoningEffort());
        }
        if (!thinkingRequest.extraBody().isEmpty()) {
            optionsBuilder.extraBody(thinkingRequest.extraBody());
        }

        com.openai.client.OpenAIClientAsync clientAsync = new com.openai.client.OpenAIClientAsyncImpl(clientOptions);

        return OpenAiChatModel.builder()
                .openAiClient(client)
                .openAiClientAsync(clientAsync)
                .options(optionsBuilder.build())
                .build();
    }

    private ChatModel createAnthropic(SysAiConfig config) {
        String apiKey = config.getApiKey();
        String baseUrl = normalizeAnthropicBaseUrl(config.getApiBase(), "https://api.anthropic.com");
        String model = config.getModel() != null ? config.getModel() : "claude-sonnet-4-20250514";
        double temperature = config.getTemperature() != null
                ? config.getTemperature().doubleValue() : 0.7;

        AnthropicClient client = new AnthropicClientImpl(
                ClientOptions.builder()
                        .baseUrl(baseUrl)
                        .build());

        var optionsBuilder = AnthropicChatOptions.builder()
                .apiKey(apiKey)
                .model(Model.of(model))
                .temperature(temperature);

        if (config.getMaxTokens() != null) {
            optionsBuilder.maxTokens(config.getMaxTokens());
        }
        if (config.getTopP() != null) {
            optionsBuilder.topP(config.getTopP().doubleValue());
        }

        AiThinkingAdapterRegistry.ThinkingRequest thinkingRequest = thinkingAdapterRegistry.resolve(config);
        if (thinkingRequest.anthropicThinkingBudget() != null) {
            optionsBuilder.thinkingEnabled(thinkingRequest.anthropicThinkingBudget());
        }

        return AnthropicChatModel.builder()
                .anthropicClient(client)
                .options(optionsBuilder.build())
                .build();
    }

    private String normalizeAnthropicBaseUrl(String url, String defaultUrl) {
        if (url == null || url.isBlank()) {
            return defaultUrl;
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String defaultOpenAiCompatibleBaseUrl(String provider) {
        if (provider == null) {
            return "https://api.openai.com";
        }
        return switch (provider.toLowerCase()) {
            case "deepseek" -> "https://api.deepseek.com";
            case "siliconflow" -> "https://api.siliconflow.cn";
            case "openrouter" -> "https://openrouter.ai/api/v1";
            case "worldrouter" -> "https://inference-api.worldrouter.ai/v1";
            default -> "https://api.openai.com";
        };
    }
}