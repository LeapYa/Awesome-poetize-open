package com.ld.poetry.service.ai.image;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Google Gemini 生图客户端。
 *
 * <p>支持两类模型，按 model 名前缀自动选择端点：
 * <ul>
 *   <li>{@code gemini-*}（Nano Banana 系列）：走 {@code :generateContent} 端点，
 *       用 {@code responseModalities:["IMAGE","TEXT"]}，响应返回 base64 inlineData</li>
 *   <li>{@code imagen-*}（Imagen 4 系列）：走 {@code :predict} 端点，
 *       用 {@code instances+parameters} 格式，响应返回 base64 bytesBase64Encoded</li>
 * </ul>
 *
 * <p>鉴权：API Key 走 {@code ?key=xxx} 查询参数，不走 Bearer 头。
 * 尺寸通过 {@code aspectRatio}（"1:1"/"16:9"/"9:16"/"4:3"/"3:4"）控制。
 */
@Slf4j
@Component
public class GeminiImageClient {

    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    private final JsonMapper objectMapper;

    public GeminiImageClient(JsonMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public GeneratedImage generate(String prompt, ImageConfigDto config) {
        String baseUrl = config.getApiUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_BASE_URL;
        }
        // 去掉末尾斜杠
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        String apiKey = config.getApiKey();
        String model = config.getModel();
        if (model == null || model.isBlank()) {
            model = "gemini-3-pro-image-preview";
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("生图API密钥为空，provider=gemini");
        }

        // 按 model 前缀选择端点
        boolean isImagen = model.toLowerCase().startsWith("imagen");
        String endpoint = isImagen ? "predict" : "generateContent";

        String aspectRatio = resolveAspectRatio(config.getSize());
        int timeoutSeconds = config.getTimeout() > 0 ? config.getTimeout() : 60;

        String requestBody;
        if (isImagen) {
            requestBody = buildImagenRequest(prompt, config, aspectRatio);
        } else {
            requestBody = buildGenerateContentRequest(prompt, config, aspectRatio);
        }

        // 构建带 key 查询参数的 URL
        String url = baseUrl + "/models/" + model + ":" + endpoint + "?key=" +
                URLEncoder.encode(apiKey, StandardCharsets.UTF_8);

        RestClient restClient = RestClient.builder()
                .requestFactory(buildFactory(timeoutSeconds))
                .build();

        log.info("调用Gemini生图API model={} endpoint={} aspectRatio={}", model, endpoint, aspectRatio);

        String responseBody;
        try {
            // 用 URI 对象传入：.uri(String) 会当作 URI 模板再编码一次，
            // 而 key 查询参数已经 URLEncoder 编码，二次编码会破坏密钥
            responseBody = restClient.post()
                    .uri(java.net.URI.create(url))
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw ImageApiErrorTranslator.translate(e, "gemini");
        }

        return isImagen ? parseImagenResponse(responseBody, model) : parseGenerateContentResponse(responseBody, model);
    }

    /**
     * 构造 generateContent 请求体（Nano Banana 系列）
     */
    private String buildGenerateContentRequest(String prompt, ImageConfigDto config, String aspectRatio) {
        ObjectNode requestBody = objectMapper.createObjectNode();

        // contents: [{ parts: [{ text: prompt }] }]
        ArrayNode contents = objectMapper.createArrayNode();
        ObjectNode contentItem = objectMapper.createObjectNode();
        ArrayNode parts = objectMapper.createArrayNode();
        ObjectNode textPart = objectMapper.createObjectNode();
        textPart.put("text", prompt);
        parts.add(textPart);
        contentItem.set("parts", parts);
        contents.add(contentItem);
        requestBody.set("contents", contents);

        // generationConfig
        ObjectNode generationConfig = objectMapper.createObjectNode();
        ArrayNode modalities = objectMapper.createArrayNode();
        modalities.add("IMAGE");
        modalities.add("TEXT");
        generationConfig.set("responseModalities", modalities);

        // imageConfig: aspectRatio
        ObjectNode imageConfig = objectMapper.createObjectNode();
        imageConfig.put("aspectRatio", aspectRatio);
        generationConfig.set("imageConfig", imageConfig);

        requestBody.set("generationConfig", generationConfig);

        return requestBody.toString();
    }

    /**
     * 构造 predict 请求体（Imagen 4 系列）
     */
    private String buildImagenRequest(String prompt, ImageConfigDto config, String aspectRatio) {
        ObjectNode requestBody = objectMapper.createObjectNode();

        // instances: [{ prompt: prompt }]
        ArrayNode instances = objectMapper.createArrayNode();
        ObjectNode instance = objectMapper.createObjectNode();
        instance.put("prompt", prompt);
        instances.add(instance);
        requestBody.set("instances", instances);

        // parameters
        ObjectNode parameters = objectMapper.createObjectNode();
        parameters.put("sampleCount", 1);
        // Imagen 用 aspectRatio
        parameters.put("aspectRatio", aspectRatio);

        // quality（如果配置了）
        String quality = config.getQuality();
        if (quality != null && !quality.isBlank() && !"auto".equalsIgnoreCase(quality)) {
            parameters.put("quality", quality);
        }

        requestBody.set("parameters", parameters);

        return requestBody.toString();
    }

    /**
     * 解析 generateContent 响应（Nano Banana 系列）
     * 取 candidates[0].content.parts[].inlineData.data (base64)
     */
    private GeneratedImage parseGenerateContentResponse(String responseBody, String model) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // 检查错误
            if (root.has("error")) {
                JsonNode error = root.get("error");
                String message = error.has("message") ? error.get("message").asText() : "未知错误";
                throw new RuntimeException("Gemini生图失败: " + message);
            }

            JsonNode candidates = root.get("candidates");
            if (candidates == null || !candidates.isArray() || candidates.isEmpty()) {
                throw new RuntimeException("Gemini生图响应中无candidates: " + truncate(responseBody, 500));
            }

            JsonNode firstCandidate = candidates.get(0);
            JsonNode content = firstCandidate.get("content");
            if (content == null) {
                throw new RuntimeException("Gemini生图响应中无content: " + truncate(responseBody, 500));
            }

            JsonNode parts = content.get("parts");
            if (parts == null || !parts.isArray() || parts.isEmpty()) {
                throw new RuntimeException("Gemini生图响应中无parts: " + truncate(responseBody, 500));
            }

            // 遍历 parts 找 inlineData
            for (JsonNode part : parts) {
                if (part.has("inlineData")) {
                    JsonNode inlineData = part.get("inlineData");
                    String base64Data = inlineData.has("data") ? inlineData.get("data").asText() : null;
                    String mimeType = inlineData.has("mimeType") ? inlineData.get("mimeType").asText() : "image/png";

                    if (base64Data != null && !base64Data.isEmpty()) {
                        byte[] bytes = java.util.Base64.getDecoder().decode(base64Data);
                        log.info("Gemini生图成功 inlineData, {} bytes, mimeType={}", bytes.length, mimeType);
                        return GeneratedImage.ofBytes(bytes, mimeType, "gemini", model);
                    }
                }
                // 也可能有 fileData（GCS URI），暂不支持下载
            }

            throw new RuntimeException("Gemini生图响应parts中未找到inlineData: " + truncate(responseBody, 500));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("解析Gemini生图响应失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析 predict 响应（Imagen 4 系列）
     * 取 predictions[0].bytesBase64Encoded (base64)
     */
    private GeneratedImage parseImagenResponse(String responseBody, String model) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // 检查错误
            if (root.has("error")) {
                JsonNode error = root.get("error");
                String message = error.has("message") ? error.get("message").asText() : "未知错误";
                throw new RuntimeException("Gemini Imagen生图失败: " + message);
            }

            JsonNode predictions = root.get("predictions");
            if (predictions == null || !predictions.isArray() || predictions.isEmpty()) {
                throw new RuntimeException("Gemini Imagen生图响应中无predictions: " + truncate(responseBody, 500));
            }

            JsonNode firstPrediction = predictions.get(0);
            if (firstPrediction.has("bytesBase64Encoded")) {
                String base64Data = firstPrediction.get("bytesBase64Encoded").asText();
                String mimeType = firstPrediction.has("mimeType")
                        ? firstPrediction.get("mimeType").asText() : "image/png";
                byte[] bytes = java.util.Base64.getDecoder().decode(base64Data);
                log.info("Gemini Imagen生图成功, {} bytes, mimeType={}", bytes.length, mimeType);
                return GeneratedImage.ofBytes(bytes, mimeType, "gemini", model);
            }

            throw new RuntimeException("Gemini Imagen生图响应中未找到bytesBase64Encoded: " + truncate(responseBody, 500));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("解析Gemini Imagen生图响应失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将像素尺寸（如 1024x1024）映射为 Gemini aspectRatio（如 "1:1"）。
     * 如果已经是比例格式（如 "16:9"）则直接返回。
     */
    private String resolveAspectRatio(String size) {
        if (size == null || size.isBlank()) {
            return "1:1";
        }
        // 已经是比例格式
        if (size.contains(":")) {
            return size;
        }
        // 像素格式 xXx 或 x*x
        String normalized = size.replace("*", "x").toLowerCase();
        String[] parts = normalized.split("x");
        if (parts.length == 2) {
            try {
                int w = Integer.parseInt(parts[0].trim());
                int h = Integer.parseInt(parts[1].trim());
                return simplifyRatio(w, h);
            } catch (NumberFormatException ignored) {
            }
        }
        return "1:1";
    }

    private String simplifyRatio(int w, int h) {
        int gcd = gcd(w, h);
        int rw = w / gcd;
        int rh = h / gcd;
        // 常见比例映射
        if (rw == 1 && rh == 1) return "1:1";
        if (rw == 16 && rh == 9) return "16:9";
        if (rw == 9 && rh == 16) return "9:16";
        if (rw == 4 && rh == 3) return "4:3";
        if (rw == 3 && rh == 4) return "3:4";
        // 通用简化
        return rw + ":" + rh;
    }

    private int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
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
}
