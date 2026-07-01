package com.ld.poetry.service.ai.image;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 通义万相（DashScope）生图客户端。
 *
 * <p>使用 wan2.7-image 同步端点（multimodal-generation/generation），
 * 请求格式为 messages 数组，size 用 {@code *} 分隔（如 1280*1280）。
 *
 * <p>响应中图片 URL 在 output.choices[0].message.content[0].image。
 */
@Slf4j
@Component
public class DashScopeImageClient {

    private static final String DEFAULT_API_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";

    private final JsonMapper objectMapper;

    public DashScopeImageClient(JsonMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public GeneratedImage generate(String prompt, ImageConfigDto config) {
        String apiUrl = config.getApiUrl();
        if (apiUrl == null || apiUrl.isBlank()) {
            apiUrl = DEFAULT_API_URL;
        }
        String apiKey = config.getApiKey();
        String model = config.getModel();
        if (model == null || model.isBlank()) {
            model = "wan2.7-image";
        }

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("生图API密钥为空，provider=dashscope");
        }

        // 构造 messages 格式请求体
        String finalPrompt = prompt;

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);

        ObjectNode input = objectMapper.createObjectNode();
        ArrayNode messages = objectMapper.createArrayNode();
        ObjectNode userMessage = objectMapper.createObjectNode();
        userMessage.put("role", "user");
        ArrayNode content = objectMapper.createArrayNode();
        ObjectNode textPart = objectMapper.createObjectNode();
        textPart.put("text", finalPrompt);
        content.add(textPart);
        userMessage.set("content", content);
        messages.add(userMessage);
        input.set("messages", messages);
        requestBody.set("input", input);

        // parameters: size（* 分隔）, n
        ObjectNode parameters = objectMapper.createObjectNode();
        // 优先用 resolution 像素，没配则按 size 宽高比推导默认像素
        String pixelSize = resolvePixelSize(config);
        if (pixelSize != null && !pixelSize.isBlank()) {
            // 统一转换为 * 分隔（DashScope 要求 1280*1280 格式）
            String dashScopeSize = pixelSize.replace("x", "*");
            parameters.put("size", dashScopeSize);
        }
        parameters.put("n", 1);
        requestBody.set("parameters", parameters);

        int timeoutSeconds = config.getTimeout() > 0 ? config.getTimeout() : 60;
        RestClient restClient = RestClient.builder()
                .requestFactory(buildFactory(timeoutSeconds))
                .build();

        log.info("调用DashScope生图API model={} size={}", model, pixelSize);

        String responseBody = restClient.post()
                .uri(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(requestBody.toString())
                .retrieve()
                .body(String.class);

        return parseResponse(responseBody, model);
    }

    private GeneratedImage parseResponse(String responseBody, String model) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // 检查错误
            if (root.has("code")) {
                String code = root.get("code").asText();
                String message = root.has("message") ? root.get("message").asText() : "未知错误";
                throw new RuntimeException("DashScope生图失败: " + code + " - " + message);
            }

            // 解析 output.choices[0].message.content[0].image
            JsonNode output = root.get("output");
            if (output != null) {
                // wan2.7-image 同步模式：choices[].message.content[].image
                JsonNode choices = output.get("choices");
                if (choices != null && choices.isArray() && !choices.isEmpty()) {
                    JsonNode firstChoice = choices.get(0);
                    JsonNode message = firstChoice.get("message");
                    if (message != null) {
                        JsonNode content = message.get("content");
                        if (content != null && content.isArray() && !content.isEmpty()) {
                            JsonNode firstContent = content.get(0);
                            if (firstContent.has("image")) {
                                String url = firstContent.get("image").asText();
                                log.info("DashScope生图成功 url={}", url);
                                return GeneratedImage.ofUrl(url, "dashscope", model);
                            }
                        }
                    }
                }

                // 兼容旧格式 output.results[].url
                JsonNode results = output.get("results");
                if (results != null && results.isArray() && !results.isEmpty()) {
                    JsonNode first = results.get(0);
                    if (first.has("url")) {
                        String url = first.get("url").asText();
                        log.info("DashScope生图成功 url={}", url);
                        return GeneratedImage.ofUrl(url, "dashscope", model);
                    }
                    if (first.has("b64_image")) {
                        byte[] bytes = java.util.Base64.getDecoder().decode(first.get("b64_image").asText());
                        log.info("DashScope生图成功 b64_image, {} bytes", bytes.length);
                        return GeneratedImage.ofBytes(bytes, "image/png", "dashscope", model);
                    }
                }
            }

            throw new RuntimeException("DashScope生图响应中未找到图片数据: " + truncate(responseBody, 500));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("解析DashScope生图响应失败: " + e.getMessage(), e);
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



    /**
     * 解析最终送入 API 的像素尺寸（如 "1024x1024"）。
     * 优先 resolution 字段，没配则按 size 宽高比推导默认像素。
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
