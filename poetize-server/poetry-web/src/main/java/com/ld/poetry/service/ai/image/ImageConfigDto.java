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
 *   "timeout": 60,
 *   "cover_template": "object | portrait | felt | cyberpunk | watercolor | ink | pixel | 3d | minimal | collage | custom",  // 封面模板
 *   "custom_refine_prompt": "用户自定义的 LLM 系统提示词",  // 仅 cover_template=custom 时使用
 *   "dedicated_llm": { model, api_url, api_key, interface_type, timeout }  // 仅 imageMode=dedicated 时存在
 * }
 *
 * cover_template 说明：
 *   - object:    物品类真实感模板（通用，不一定有人物）
 *   - portrait:  人物类真实感模板
 *   - felt:      毛毡Q版可爱风模板（羊毛毡手工质感）
 *   - cyberpunk: 赛博朋克霓虹风模板（未来都市霓虹质感）
 *   - watercolor:水彩手绘风模板（通透水痕晕染，文艺清新）
 *   - ink:       国风水墨画模板（传统水墨写意美学，留白意境）
 *   - pixel:     像素复古风模板（8-bit/16-bit 复古游戏美学）
 *   - 3d:        3D渲染卡通风模板（圆润黏土质感，现代精致）
 *   - minimal:   极简几何风模板（极简主义设计，高级克制）
 *   - collage:   复古拼贴风模板（旧杂志拼贴美学，怀旧创意）
 *   - custom:    自定义模板，使用 custom_refine_prompt 作为 LLM 系统提示词
 *
 * imageMode 说明（与摘要功能 summaryMode 设计一致）：
 *   - disabled: 关闭生图功能
 *   - plain:    生图开启，不用AI提炼prompt，直接用模板拼接（取文章标题/内容作为主体）
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

    /** 封面模板：object=物品类真实感，portrait=人物类真实感，felt=毛毡Q版，cyberpunk=赛博朋克，watercolor=水彩手绘，ink=国风水墨，pixel=像素复古，3d=3D卡通渲染，minimal=极简几何，collage=复古拼贴，custom=自定义 */
    private String coverTemplate = "object";
    /** 自定义模板的 LLM 系统提示词，仅 cover_template=custom 时使用 */
    private String customRefinePrompt = "";
    private int timeout = 120;
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

            dto.coverTemplate = textOrDefault(node, "cover_template", "object");
            // 兼容旧数据：历史上可能存过 none，统一归一化为 object
            if ("none".equalsIgnoreCase(dto.coverTemplate)) {
                dto.coverTemplate = "object";
            }
            dto.customRefinePrompt = textOrDefault(node, "custom_refine_prompt", "");
            dto.timeout = node.has("timeout") ? node.get("timeout").asInt(120) : 120;
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

    public String getCoverTemplate() { return coverTemplate; }
    public String getCustomRefinePrompt() { return customRefinePrompt; }
    public int getTimeout() { return timeout; }
    public JsonNode getDedicatedLlm() { return dedicatedLlm; }

    /** 是否使用人物类真实感模板 */
    public boolean usePortraitTemplate() {
        return "portrait".equalsIgnoreCase(coverTemplate);
    }

    /** 是否使用毛毡Q版可爱风模板 */
    public boolean useFeltTemplate() {
        return "felt".equalsIgnoreCase(coverTemplate);
    }

    /** 是否使用赛博朋克霓虹风模板 */
    public boolean useCyberpunkTemplate() {
        return "cyberpunk".equalsIgnoreCase(coverTemplate);
    }

    /** 是否使用水彩手绘风模板 */
    public boolean useWatercolorTemplate() {
        return "watercolor".equalsIgnoreCase(coverTemplate);
    }

    /** 是否使用国风水墨画模板 */
    public boolean useInkTemplate() {
        return "ink".equalsIgnoreCase(coverTemplate);
    }

    /** 是否使用像素复古风模板 */
    public boolean usePixelTemplate() {
        return "pixel".equalsIgnoreCase(coverTemplate);
    }

    /** 是否使用 3D 渲染卡通风模板 */
    public boolean use3dTemplate() {
        return "3d".equalsIgnoreCase(coverTemplate);
    }

    /** 是否使用极简几何风模板 */
    public boolean useMinimalTemplate() {
        return "minimal".equalsIgnoreCase(coverTemplate);
    }

    /** 是否使用复古拼贴风模板 */
    public boolean useCollageTemplate() {
        return "collage".equalsIgnoreCase(coverTemplate);
    }

    /** 是否使用自定义模板 */
    public boolean useCustomTemplate() {
        return "custom".equalsIgnoreCase(coverTemplate);
    }
}
