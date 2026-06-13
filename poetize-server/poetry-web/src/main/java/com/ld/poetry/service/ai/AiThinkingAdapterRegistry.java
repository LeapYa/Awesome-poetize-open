package com.ld.poetry.service.ai;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import com.ld.poetry.entity.SysAiConfig;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves provider-specific thinking parameters for OpenAI-compatible relays.
 */
@Component
public class AiThinkingAdapterRegistry {

    public static final String PROFILE_AUTO = "auto";
    public static final String PROFILE_OPENAI = "openai";
    public static final String PROFILE_DEEPSEEK_OFFICIAL = "deepseek_official";
    public static final String PROFILE_SILICONFLOW = "siliconflow";
    public static final String PROFILE_OPENROUTER = "openrouter";
    public static final String PROFILE_WORLDROUTER = "worldrouter";
    public static final String PROFILE_ANTHROPIC = "anthropic";
    public static final String PROFILE_GENERIC_OPENAI_COMPATIBLE = "generic_openai_compatible";

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JsonMapper objectMapper;

    public AiThinkingAdapterRegistry(JsonMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ThinkingRequest resolve(SysAiConfig config) {
        String profile = resolveProfile(config);
        String effort = normalizeEffort(config != null ? config.getReasoningEffort() : null);
        Map<String, Object> customExtraBody = readCustomExtraBody(config);

        String reasoningEffort = null;
        Map<String, Object> extraBody = new LinkedHashMap<>(customExtraBody);
        Integer anthropicThinkingBudget = null;
        boolean visibleReasoningLikely = false;

        if (config != null && Boolean.TRUE.equals(config.getEnableThinking())) {
            switch (profile) {
                case PROFILE_DEEPSEEK_OFFICIAL -> {
                    reasoningEffort = mapOpenAiEffort(effort);
                    extraBody.put("thinking", Map.of("type", "enabled"));
                    visibleReasoningLikely = true;
                }
                case PROFILE_SILICONFLOW -> {
                    extraBody.put("thinking_budget", mapThinkingBudget(effort));
                    visibleReasoningLikely = true;
                }
                case PROFILE_OPENROUTER -> {
                    extraBody.put("reasoning", Map.of(
                            "enabled", true,
                            "effort", mapOpenRouterEffort(effort),
                            "exclude", false));
                    visibleReasoningLikely = true;
                }
                case PROFILE_WORLDROUTER -> reasoningEffort = mapOpenAiEffort(effort);
                case PROFILE_OPENAI -> reasoningEffort = mapOpenAiEffort(effort);
                case PROFILE_ANTHROPIC -> {
                    anthropicThinkingBudget = Math.max(1024, mapThinkingBudget(effort));
                    visibleReasoningLikely = true;
                }
                case PROFILE_GENERIC_OPENAI_COMPATIBLE -> {
                    reasoningEffort = mapOpenAiEffort(effort);
                    visibleReasoningLikely = hasAnyReasoningKey(extraBody);
                }
                default -> {
                    // Keep unknown profiles conservative.
                }
            }
        }
        if (reasoningEffort != null) {
            extraBody.remove("reasoning_effort");
        }

        return new ThinkingRequest(
                profile,
                displayName(profile),
                reasoningEffort,
                extraBody,
                anthropicThinkingBudget,
                visibleReasoningLikely,
                diagnostics(profile, reasoningEffort, extraBody, anthropicThinkingBudget, visibleReasoningLikely));
    }

    private String resolveProfile(SysAiConfig config) {
        Map<String, Object> extraConfig = readExtraConfig(config);
        Object configuredProfile = extraConfig.get("thinkingProfile");
        if (configuredProfile instanceof String value && StringUtils.hasText(value)
                && !PROFILE_AUTO.equalsIgnoreCase(value)) {
            return normalizeProfile(value);
        }

        String provider = lower(config != null ? config.getProvider() : null);
        String apiBase = lower(config != null ? config.getApiBase() : null);

        if (provider.contains("openrouter") || apiBase.contains("openrouter.ai")) {
            return PROFILE_OPENROUTER;
        }
        if (provider.contains("worldrouter") || apiBase.contains("worldrouter.ai")) {
            return PROFILE_WORLDROUTER;
        }
        if (provider.contains("siliconflow") || apiBase.contains("api.siliconflow.cn")
                || apiBase.contains("api.siliconflow.com")) {
            return PROFILE_SILICONFLOW;
        }
        if (provider.contains("deepseek") || apiBase.contains("api.deepseek.com")) {
            return PROFILE_DEEPSEEK_OFFICIAL;
        }
        if (provider.contains("anthropic") || apiBase.contains("api.anthropic.com")) {
            return PROFILE_ANTHROPIC;
        }
        if (provider.contains("openai") || apiBase.contains("api.openai.com")) {
            return PROFILE_OPENAI;
        }
        return PROFILE_GENERIC_OPENAI_COMPATIBLE;
    }

    private Map<String, Object> readCustomExtraBody(SysAiConfig config) {
        Object value = readExtraConfig(config).get("thinkingExtraBody");
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, mapValue) -> {
                if (key != null) {
                    result.put(String.valueOf(key), mapValue);
                }
            });
            return result;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return objectMapper.readValue(text, MAP_TYPE);
            } catch (Exception ignored) {
                return Map.of();
            }
        }
        return Map.of();
    }

    private Map<String, Object> readExtraConfig(SysAiConfig config) {
        if (config == null || !StringUtils.hasText(config.getExtraConfig())) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(config.getExtraConfig(), MAP_TYPE);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String normalizeProfile(String profile) {
        String normalized = lower(profile).replace('-', '_');
        return switch (normalized) {
            case "deepseek", "deepseek_official" -> PROFILE_DEEPSEEK_OFFICIAL;
            case "siliconflow" -> PROFILE_SILICONFLOW;
            case "openrouter" -> PROFILE_OPENROUTER;
            case "worldrouter" -> PROFILE_WORLDROUTER;
            case "anthropic", "claude" -> PROFILE_ANTHROPIC;
            case "openai" -> PROFILE_OPENAI;
            case "generic", "custom", "generic_openai_compatible" -> PROFILE_GENERIC_OPENAI_COMPATIBLE;
            default -> PROFILE_AUTO.equals(normalized) ? PROFILE_AUTO : PROFILE_GENERIC_OPENAI_COMPATIBLE;
        };
    }

    private String normalizeEffort(String reasoningEffort) {
        if (!StringUtils.hasText(reasoningEffort)) {
            return "medium";
        }
        return switch (reasoningEffort.trim().toLowerCase(Locale.ROOT)) {
            case "minimal", "none" -> reasoningEffort.trim().toLowerCase(Locale.ROOT);
            case "low", "低" -> "low";
            case "medium", "中" -> "medium";
            case "high", "高" -> "high";
            case "xhigh", "max", "maximum", "超高" -> "xhigh";
            default -> "medium";
        };
    }

    private String mapOpenAiEffort(String effort) {
        return switch (effort) {
            case "none", "minimal", "low", "high", "xhigh" -> effort;
            default -> "medium";
        };
    }

    private String mapOpenRouterEffort(String effort) {
        return switch (effort) {
            case "minimal", "low", "high", "xhigh" -> effort;
            case "none" -> "low";
            default -> "medium";
        };
    }

    private int mapThinkingBudget(String effort) {
        return switch (effort) {
            case "low" -> 1024;
            case "high" -> 2048;
            case "xhigh" -> 4096;
            default -> 1024;
        };
    }

    private boolean hasAnyReasoningKey(Map<String, Object> extraBody) {
        return extraBody.containsKey("reasoning")
                || extraBody.containsKey("thinking")
                || extraBody.containsKey("thinking_budget")
                || extraBody.containsKey("include_reasoning");
    }

    private Map<String, Object> diagnostics(String profile, String reasoningEffort, Map<String, Object> extraBody,
            Integer anthropicThinkingBudget, boolean visibleReasoningLikely) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("profile", profile);
        result.put("profileName", displayName(profile));
        result.put("reasoningEffort", reasoningEffort);
        result.put("extraBody", extraBody);
        result.put("anthropicThinkingBudget", anthropicThinkingBudget);
        result.put("visibleReasoningLikely", visibleReasoningLikely);
        return result;
    }

    private String displayName(String profile) {
        return switch (profile) {
            case PROFILE_OPENAI -> "OpenAI";
            case PROFILE_DEEPSEEK_OFFICIAL -> "DeepSeek 官方";
            case PROFILE_SILICONFLOW -> "硅基流动";
            case PROFILE_OPENROUTER -> "OpenRouter";
            case PROFILE_WORLDROUTER -> "WorldRouter";
            case PROFILE_ANTHROPIC -> "Anthropic";
            default -> "通用 OpenAI 兼容";
        };
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    public record ThinkingRequest(
            String profile,
            String profileName,
            String reasoningEffort,
            Map<String, Object> extraBody,
            Integer anthropicThinkingBudget,
            boolean visibleReasoningLikely,
            Map<String, Object> diagnostics) {
    }
}
