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

/**
 * 动态 ChatModel 工厂 — Spring AI 2.0 RC2 实现
 * <p>
 * RC2 中移除了 OpenAiApi/AnthropicApi，改为直接使用原生 SDK Client（OpenAIClient / AnthropicClient）。
 * ChatModel 通过 Builder 接收 Client + Options 构建。
 */
@Service
public class DynamicChatClientFactory {

    private static final Logger logger = LoggerFactory.getLogger(DynamicChatClientFactory.class);

    private final AiThinkingAdapterRegistry thinkingAdapterRegistry;

    public DynamicChatClientFactory(AiThinkingAdapterRegistry thinkingAdapterRegistry) {
        this.thinkingAdapterRegistry = thinkingAdapterRegistry;
    }

    public ChatModel createChatModel(SysAiConfig config) {
        String provider = config.getProvider();
        if (provider == null || provider.isBlank()) {
            provider = "openai";
        }
        logger.debug("创建 ChatModel: provider={}, model={}, baseUrl={}",
                provider, config.getModel(), config.getApiBase());
        return switch (provider.toLowerCase()) {
            case "openai", "deepseek", "siliconflow", "openrouter", "worldrouter", "custom" ->
                createOpenAiCompatible(config);
            case "anthropic" -> createAnthropic(config);
            default -> {
                logger.warn("不支持的 provider: {}, 降级为 OpenAI 兼容模式", provider);
                yield createOpenAiCompatible(config);
            }
        };
    }

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