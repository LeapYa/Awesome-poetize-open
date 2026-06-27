package com.ld.poetry.service.ai.image;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI生图配置 DTO，从 sys_ai_config.image_config JSON 解析而来。
 *
 * <p>JSON 结构：
 * <pre>
 * {
 *   "imageMode": "disabled | plain | global | dedicated",
 *   "provider": "openai | siliconflow | doubao | dashscope | gemini | custom",
 *   "model": "gpt-image-2",
 *   "api_url": "https://...",
 *   "api_key": "<明文>",
 *   "size": "1:1 | 16:9 | 9:16 | 4:3 | 3:4",
 *   "resolution": "1024x1024 | 1328x1328 | 1536x1024",  // 像素值，仅 OpenAI/SiliconFlow/DashScope/豆包/custom 等支持像素的 provider 生效
 *   "quality": "auto | low | medium | high",
 *   "style_prompt": "<给生图模型的风格前缀，所有非disabled模式均生效>",
 *   "refine_prompt": "<给AI模型的系统提示词，仅 global/dedicated 模式使用>",
 *   "timeout": 60,
 *   "dedicated_llm": { model, api_url, api_key, interface_type, timeout }  // 仅 imageMode=dedicated 时存在
 * }
 *
 * imageMode 说明（与摘要功能 summaryMode 设计一致）：
 *   - disabled: 关闭生图功能
 *   - plain:    生图开启，不用AI提炼prompt，直接用模板拼接
 *   - global:   生图开启，用全局 llm_config 提炼prompt
 *   - dedicated:生图开启，用 image_config.dedicated_llm 提炼prompt
 * </pre>
 */
public class ImageConfigDto {

    private String imageMode = "disabled";
    private String provider = "siliconflow";
    private String model = "";
    private String apiUrl = "";
    private String apiKey = "";
    /** 宽高比，跨 provider 通用（1:1 / 16:9 / 9:16 / 4:3 / 3:4） */
    private String size = "16:9";
    /** 像素值，仅支持像素的 provider 生效（OpenAI/SiliconFlow/DashScope/豆包/custom），如 1536x864 */
    private String resolution = "1536x864";
    private String quality = "auto";
    /** 给生图模型的风格前缀，所有非disabled模式均生效 */
    private String stylePrompt = "";
    /** 给AI模型的系统提示词，仅 global/dedicated 模式使用 */
    private String refinePrompt = "";
    private int timeout = 60;
    private JsonNode dedicatedLlm;

    public static ImageConfigDto fromJson(String json, JsonMapper objectMapper) {
        ImageConfigDto dto = new ImageConfigDto();
        if (json == null || json.isBlank()) {
            return dto;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            dto.imageMode = textOrDefault(node, "imageMode", "disabled");
            dto.provider = textOrDefault(node, "provider", "siliconflow");
            dto.model = textOrDefault(node, "model", "");
            dto.apiUrl = textOrDefault(node, "api_url", "");
            dto.apiKey = textOrDefault(node, "api_key", "");
            dto.size = textOrDefault(node, "size", "16:9");
            dto.resolution = textOrDefault(node, "resolution", "1536x864");
            dto.quality = textOrDefault(node, "quality", "auto");
            dto.stylePrompt = textOrDefault(node, "style_prompt", "");
            dto.refinePrompt = textOrDefault(node, "refine_prompt", "");
            dto.timeout = node.has("timeout") ? node.get("timeout").asInt(60) : 60;
            dto.dedicatedLlm = node.get("dedicated_llm");
        } catch (Exception ignored) {
            // 解析失败返回默认值
        }
        return dto;
    }

    private static String textOrDefault(JsonNode node, String key, String def) {
        if (node.has(key) && !node.get(key).isNull()) {
            String val = node.get(key).asText();
            return val != null && !val.isEmpty() ? val : def;
        }
        return def;
    }

    /** 生图功能是否启用（imageMode != disabled） */
    public boolean isEnabled() {
        return !"disabled".equalsIgnoreCase(imageMode);
    }

    /** 不用AI提炼，直接用模板拼接prompt */
    public boolean usePlainMode() {
        return "plain".equalsIgnoreCase(imageMode);
    }

    /** 使用全局 llm_config 提炼prompt */
    public boolean useGlobalMode() {
        return "global".equalsIgnoreCase(imageMode);
    }

    /** 使用 dedicated_llm 提炼prompt */
    public boolean useDedicatedLlm() {
        return "dedicated".equalsIgnoreCase(imageMode);
    }

    public String getImageMode() { return imageMode; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public String getApiUrl() { return apiUrl; }
    public String getApiKey() { return apiKey; }
    public String getSize() { return size; }
    public String getResolution() { return resolution; }
    public String getQuality() { return quality; }
    public String getStylePrompt() { return stylePrompt; }
    public String getRefinePrompt() { return refinePrompt; }
    public int getTimeout() { return timeout; }
    public JsonNode getDedicatedLlm() { return dedicatedLlm; }
}
