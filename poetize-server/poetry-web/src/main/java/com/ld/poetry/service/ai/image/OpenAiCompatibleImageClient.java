package com.ld.poetry.service.ai.image;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * OpenAI 兼容生图客户端，适用于 openai / siliconflow / doubao / custom 四类 provider。
 *
 * <p>统一走 POST /v1/images/generations 端点，差异点：
 * <ul>
 *   <li>size 字段名：OpenAI/豆包/custom 用 "size"，SiliconFlow 用 "image_size"</li>
 *   <li>响应 key：OpenAI/豆包返回 data[].url，SiliconFlow 返回 images[].url</li>
 *   <li>quality：仅 openai/custom 透传（gpt-image-2 支持）</li>
 * </ul>
 */
@Slf4j
@Component
public class OpenAiCompatibleImageClient {

    private final JsonMapper objectMapper;

    public OpenAiCompatibleImageClient(JsonMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public GeneratedImage generate(String prompt, ImageConfigDto config) {
        String provider = config.getProvider();
        String apiUrl = config.getApiUrl();
        String apiKey = config.getApiKey();
        String model = config.getModel();
        // 优先用 resolution（像素值），没配或为比例格式则按 size 宽高比推导默认像素
        String size = resolvePixelSize(config);

        if (apiUrl == null || apiUrl.isBlank()) {
            throw new IllegalArgumentException("生图API地址为空，provider=" + provider);
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("生图API密钥为空，provider=" + provider);
        }

        // 拼接 style_prompt 风格前缀
        String finalPrompt = applyStylePrefix(prompt, config.getStylePrompt());

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);
        requestBody.put("prompt", finalPrompt);
        requestBody.put("n", 1);

        // size 字段名按 provider 区分
        boolean isSiliconFlow = "siliconflow".equalsIgnoreCase(provider);
        String sizeField = isSiliconFlow ? "image_size" : "size";
        if (size != null && !size.isBlank()) {
            requestBody.put(sizeField, size);
        }

        // quality 仅 openai/custom 透传（gpt-image-2 支持）
        if (("openai".equalsIgnoreCase(provider) || "custom".equalsIgnoreCase(provider))
                && config.getQuality() != null && !config.getQuality().isBlank()) {
            requestBody.put("quality", config.getQuality());
        }

        // 默认返回 URL
        requestBody.put("response_format", "url");

        int timeoutSeconds = config.getTimeout() > 0 ? config.getTimeout() : 60;
        RestClient restClient = RestClient.builder()
                .requestFactory(buildFactory(timeoutSeconds))
                .build();

        log.info("调用生图API [{}] model={} size={}", provider, model, size);

        String responseBody = restClient.post()
                .uri(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(requestBody.toString())
                .retrieve()
                .body(String.class);

        return parseResponse(responseBody, provider, model);
    }

    private GeneratedImage parseResponse(String responseBody, String provider, String model) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // 尝试 data[].url（OpenAI/豆包）
            if (root.has("data") && root.get("data").isArray() && !root.get("data").isEmpty()) {
                JsonNode first = root.get("data").get(0);
                if (first.has("url")) {
                    String url = first.get("url").asText();
                    log.info("生图成功 [{}] url={}", provider, url);
                    return GeneratedImage.ofUrl(url, provider, model);
                }
                if (first.has("b64_json")) {
                    byte[] bytes = java.util.Base64.getDecoder().decode(first.get("b64_json").asText());
                    log.info("生图成功 [{}] b64_json, {} bytes", provider, bytes.length);
                    return GeneratedImage.ofBytes(bytes, "image/png", provider, model);
                }
            }

            // 尝试 images[].url（SiliconFlow）
            if (root.has("images") && root.get("images").isArray() && !root.get("images").isEmpty()) {
                JsonNode first = root.get("images").get(0);
                if (first.has("url")) {
                    String url = first.get("url").asText();
                    log.info("生图成功 [{}] url={}", provider, url);
                    return GeneratedImage.ofUrl(url, provider, model);
                }
            }

            throw new RuntimeException("生图响应中未找到图片URL或base64数据: " + truncate(responseBody, 500));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("解析生图响应失败: " + e.getMessage(), e);
        }
    }

    private org.springframework.http.client.SimpleClientHttpRequestFactory buildFactory(int timeoutSeconds) {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(Math.max(timeoutSeconds, 10) * 1000);
        return factory;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    /** 将 style_prompt 风格前缀拼接到 prompt 前面 */
    private String applyStylePrefix(String prompt, String stylePrompt) {
        if (stylePrompt == null || stylePrompt.isBlank()) {
            return prompt;
        }
        return stylePrompt.trim() + ", " + prompt;
    }

    /**
     * 解析最终送入 API 的像素尺寸（如 "1024x1024"）。
     *
     * <p>优先级：
     * <ol>
     *   <li>{@code resolution} 字段（像素值，如 "1328x1328"）</li>
     *   <li>按 {@code size} 宽高比推导默认像素（1:1→1024x1024，16:9→1536x864，9:16→864x1536，4:3→1280x960，3:4→960x1280）</li>
     * </ol>
     */
    private String resolvePixelSize(ImageConfigDto config) {
        String resolution = config.getResolution();
        if (resolution != null && !resolution.isBlank() && resolution.contains("x")) {
            return resolution;
        }
        String ratio = config.getSize();
        if (ratio == null || ratio.isBlank()) {
            return "1024x1024";
        }
        return switch (ratio.trim()) {
            case "16:9" -> "1536x864";
            case "9:16" -> "864x1536";
            case "4:3" -> "1280x960";
            case "3:4" -> "960x1280";
            default -> "1024x1024";
        };
    }
}
