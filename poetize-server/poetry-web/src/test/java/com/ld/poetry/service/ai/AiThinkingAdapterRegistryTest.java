package com.ld.poetry.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ld.poetry.entity.SysAiConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiThinkingAdapterRegistryTest {

    private final AiThinkingAdapterRegistry registry = new AiThinkingAdapterRegistry(new ObjectMapper());

    @Test
    void resolvesDeepSeekOfficialThinkingParameters() {
        SysAiConfig config = enabledConfig("deepseek", "https://api.deepseek.com/v1", "xhigh");

        AiThinkingAdapterRegistry.ThinkingRequest request = registry.resolve(config);

        assertEquals(AiThinkingAdapterRegistry.PROFILE_DEEPSEEK_OFFICIAL, request.profile());
        assertEquals("max", request.reasoningEffort());
        assertEquals(Map.of("type", "enabled"), request.extraBody().get("thinking"));
        assertTrue(request.visibleReasoningLikely());
    }

    @Test
    void resolvesSiliconFlowThinkingBudget() {
        SysAiConfig config = enabledConfig("siliconflow", "https://api.siliconflow.cn/v1", "high");

        AiThinkingAdapterRegistry.ThinkingRequest request = registry.resolve(config);

        assertEquals(AiThinkingAdapterRegistry.PROFILE_SILICONFLOW, request.profile());
        assertNull(request.reasoningEffort());
        assertEquals(2048, request.extraBody().get("thinking_budget"));
        assertTrue(request.visibleReasoningLikely());
    }

    @Test
    void resolvesOpenRouterReasoningObject() {
        SysAiConfig config = enabledConfig("custom", "https://openrouter.ai/api/v1", "low");

        AiThinkingAdapterRegistry.ThinkingRequest request = registry.resolve(config);

        assertEquals(AiThinkingAdapterRegistry.PROFILE_OPENROUTER, request.profile());
        @SuppressWarnings("unchecked")
        Map<String, Object> reasoning = (Map<String, Object>) request.extraBody().get("reasoning");
        assertEquals(true, reasoning.get("enabled"));
        assertEquals("low", reasoning.get("effort"));
        assertEquals(false, reasoning.get("exclude"));
    }

    @Test
    void resolvesWorldRouterByProviderWithOpenAiStyleReasoningEffort() {
        SysAiConfig config = enabledConfig("worldrouter", "", "high");

        AiThinkingAdapterRegistry.ThinkingRequest request = registry.resolve(config);

        assertEquals(AiThinkingAdapterRegistry.PROFILE_WORLDROUTER, request.profile());
        assertEquals("high", request.reasoningEffort());
        assertFalse(request.extraBody().containsKey("reasoning"));
        assertFalse(request.extraBody().containsKey("thinking_budget"));
        assertFalse(request.visibleReasoningLikely());
    }

    @Test
    void resolvesWorldRouterByApiBaseAndKeepsCustomExtraBody() {
        SysAiConfig config = enabledConfig("custom", "https://inference-api.worldrouter.ai/v1", "low");
        config.setExtraConfig("""
                {"thinkingExtraBody":{"metadata":{"source":"poetize"},"reasoning_effort":"medium"}}
                """);

        AiThinkingAdapterRegistry.ThinkingRequest request = registry.resolve(config);

        assertEquals(AiThinkingAdapterRegistry.PROFILE_WORLDROUTER, request.profile());
        assertEquals("low", request.reasoningEffort());
        assertEquals(Map.of("source", "poetize"), request.extraBody().get("metadata"));
        assertFalse(request.extraBody().containsKey("reasoning_effort"));
        assertFalse(request.extraBody().containsKey("reasoning"));
        assertFalse(request.extraBody().containsKey("thinking_budget"));
    }

    @Test
    void keepsGenericCustomExtraBodyWithoutInventingThinkingFields() {
        SysAiConfig config = enabledConfig("custom", "https://relay.example.com/v1", "medium");
        config.setExtraConfig("""
                {"thinkingExtraBody":{"include_reasoning":true}}
                """);

        AiThinkingAdapterRegistry.ThinkingRequest request = registry.resolve(config);

        assertEquals(AiThinkingAdapterRegistry.PROFILE_GENERIC_OPENAI_COMPATIBLE, request.profile());
        assertNull(request.reasoningEffort());
        assertEquals(true, request.extraBody().get("include_reasoning"));
        assertTrue(request.visibleReasoningLikely());
    }

    @Test
    void builtInThinkingParametersOverrideCustomConflicts() {
        SysAiConfig config = enabledConfig("custom", "https://openrouter.ai/api/v1", "high");
        config.setExtraConfig("""
                {"thinkingExtraBody":{"reasoning":{"enabled":false,"effort":"low","exclude":true}}}
                """);

        AiThinkingAdapterRegistry.ThinkingRequest request = registry.resolve(config);

        @SuppressWarnings("unchecked")
        Map<String, Object> reasoning = (Map<String, Object>) request.extraBody().get("reasoning");
        assertEquals(true, reasoning.get("enabled"));
        assertEquals("high", reasoning.get("effort"));
        assertEquals(false, reasoning.get("exclude"));
    }

    @Test
    void openAiUsesReasoningEffortButDoesNotPromiseVisibleReasoning() {
        SysAiConfig config = enabledConfig("openai", "", "medium");

        AiThinkingAdapterRegistry.ThinkingRequest request = registry.resolve(config);

        assertEquals(AiThinkingAdapterRegistry.PROFILE_OPENAI, request.profile());
        assertEquals("medium", request.reasoningEffort());
        assertFalse(request.visibleReasoningLikely());
    }

    private SysAiConfig enabledConfig(String provider, String apiBase, String effort) {
        SysAiConfig config = new SysAiConfig();
        config.setProvider(provider);
        config.setApiBase(apiBase);
        config.setEnableThinking(true);
        config.setReasoningEffort(effort);
        return config;
    }
}
