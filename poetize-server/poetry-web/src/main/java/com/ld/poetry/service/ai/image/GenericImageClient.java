package com.ld.poetry.service.ai.image;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 万能模板生图客户端（provider=generic）。
 *
 * <p>目标：让任意"静态鉴权 + JSON 收发"的生图 API 纯配置接入，无需为每个新厂商写原生适配。
 * 管理员在配置中提供请求体模板与响应提取路径，本客户端负责渲染模板、发起请求、按路径取图。
 *
 * <p>配置项（image_config JSON）：
 * <ul>
 *   <li>{@code generic_headers}：请求头 JSON 模板（可选），如 {@code {"Authorization": "Bearer {{api_key}}"}}；
 *       留空且配置了密钥时默认加 Bearer 头</li>
 *   <li>{@code generic_body}：请求体模板（必填），支持占位符</li>
 *   <li>{@code generic_image_path}：图片提取路径（必填），如 {@code data[0].url}、
 *       {@code output.choices[0].message.content[0].image}</li>
 *   <li>{@code generic_task_id_path}：任务ID提取路径（可选）；配置后进入异步模式：
 *       提交后按 {@code generic_poll_url} 轮询，直到 {@code generic_poll_image_path} 取到图片或超时</li>
 * </ul>
 *
 * <p>占位符：{@code {{prompt}}}（自动JSON转义）、{@code {{model}}}、{@code {{width}}}、{@code {{height}}}、
 * {@code {{size}}}（如 1024x1024）、{@code {{ratio}}}（如 16:9）、{@code {{api_key}}}；
 * 轮询 URL 中额外支持 {@code {{task_id}}}。
 *
 * <p>提取到的值自动识别形态：http(s) 开头视为 URL，data: 开头视为 data URI，其余按 base64 解码。
 *
 * <p>局限：无法覆盖动态签名鉴权（如可灵 JWT 按请求签名），此类服务仍需原生适配或经中转网关。
 */
@Slf4j
@Component
public class GenericImageClient {

    /** 异步任务轮询间隔（毫秒） */
    private static final long POLL_INTERVAL_MS = 3000;

    private static final Pattern INDEX_PATTERN = Pattern.compile("\\[(\\d+)]");

    private final JsonMapper objectMapper;

    public GenericImageClient(JsonMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public GeneratedImage generate(String prompt, ImageConfigDto config) {
        String apiUrl = config.getApiUrl();
        if (apiUrl == null || apiUrl.isBlank()) {
            throw new IllegalArgumentException("生图API地址为空，provider=generic");
        }
        String bodyTemplate = config.getGenericBody();
        if (bodyTemplate == null || bodyTemplate.isBlank()) {
            throw new IllegalArgumentException("万能模板端点未配置请求体模板（generic_body）");
        }
        String imagePath = config.getGenericImagePath();
        if (imagePath == null || imagePath.isBlank()) {
            throw new IllegalArgumentException("万能模板端点未配置图片提取路径（generic_image_path）");
        }

        String requestBody = renderTemplate(bodyTemplate, prompt, config);
        int timeoutSeconds = config.getTimeout() > 0 ? config.getTimeout() : 120;
        RestClient restClient = RestClient.builder()
                .requestFactory(buildFactory(timeoutSeconds))
                .build();

        log.info("调用万能模板生图API model={} url={}", config.getModel(), apiUrl);

        String responseBody = execute(restClient, "POST", apiUrl, requestBody, config);
        JsonNode root = readTree(responseBody);

        String taskIdPath = config.getGenericTaskIdPath();
        if (taskIdPath == null || taskIdPath.isBlank()) {
            // 同步模式：直接从响应提取图片
            return extractImage(root, imagePath, config, responseBody);
        }

        // 异步模式：提取任务ID后轮询结果
        JsonNode taskIdNode = extractByPath(root, taskIdPath);
        if (taskIdNode == null || taskIdNode.asText().isBlank()) {
            throw new RuntimeException("按路径 " + taskIdPath + " 未提取到任务ID: " + truncate(responseBody, 500));
        }
        return pollTask(restClient, taskIdNode.asText(), prompt, config, timeoutSeconds);
    }

    /** 轮询异步任务，直到取到图片或超时。 */
    private GeneratedImage pollTask(RestClient restClient, String taskId, String prompt,
                                    ImageConfigDto config, int timeoutSeconds) {
        String pollUrlTemplate = config.getGenericPollUrl();
        if (pollUrlTemplate == null || pollUrlTemplate.isBlank()) {
            throw new IllegalArgumentException("异步模式未配置轮询地址模板（generic_poll_url）");
        }
        String pollImagePath = config.getGenericPollImagePath();
        if (pollImagePath == null || pollImagePath.isBlank()) {
            // 未单独配置时复用同步提取路径
            pollImagePath = config.getGenericImagePath();
        }
        String pollUrl = renderTemplate(pollUrlTemplate, prompt, config).replace("{{task_id}}", taskId);

        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        String lastResponse = "";
        log.info("万能模板生图进入异步轮询 taskId={} pollUrl={}", taskId, pollUrl);
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("生图任务轮询被中断", e);
            }
            lastResponse = execute(restClient, "GET", pollUrl, null, config);
            JsonNode root = readTree(lastResponse);
            JsonNode imageNode = extractByPath(root, pollImagePath);
            if (imageNode != null && !imageNode.asText().isBlank()) {
                return toGeneratedImage(imageNode.asText(), config);
            }
        }
        throw new RuntimeException("生图任务轮询超时（" + timeoutSeconds + "秒），最后一次响应: "
                + truncate(lastResponse, 500));
    }

    /** 发起 HTTP 请求，统一附加模板请求头并翻译上游错误。 */
    private String execute(RestClient restClient, String method, String url,
                           String body, ImageConfigDto config) {
        try {
            if ("GET".equals(method)) {
                RestClient.RequestHeadersSpec<?> spec = restClient.get().uri(java.net.URI.create(url));
                applyHeaders(spec, config);
                return spec.retrieve().body(String.class);
            }
            RestClient.RequestBodySpec spec = restClient.post()
                    .uri(java.net.URI.create(url))
                    .header("Content-Type", "application/json");
            applyHeaders(spec, config);
            if (body != null) {
                spec.body(body);
            }
            return spec.retrieve().body(String.class);
        } catch (RestClientResponseException e) {
            throw ImageApiErrorTranslator.translate(e, "generic");
        }
    }

    /** 解析 generic_headers JSON 模板并应用到请求；未配置时按密钥默认加 Bearer 头。 */
    private void applyHeaders(RestClient.RequestHeadersSpec<?> spec, ImageConfigDto config) {
        String headersTemplate = config.getGenericHeaders();
        String apiKey = config.getApiKey() != null ? config.getApiKey() : "";
        if (headersTemplate == null || headersTemplate.isBlank()) {
            if (!apiKey.isBlank()) {
                spec.header("Authorization", "Bearer " + apiKey);
            }
            return;
        }
        String rendered = headersTemplate.replace("{{api_key}}", apiKey);
        JsonNode node;
        try {
            node = objectMapper.readTree(rendered);
        } catch (Exception e) {
            throw new IllegalArgumentException("请求头模板不是合法JSON（generic_headers）: " + e.getMessage());
        }
        node.properties().forEach(entry -> spec.header(entry.getKey(), entry.getValue().asText()));
    }

    /** 渲染占位符模板（prompt 做 JSON 转义，可安全嵌入 JSON 字符串值中）。 */
    private String renderTemplate(String template, String prompt, ImageConfigDto config) {
        String pixelSize = resolvePixelSize(config);
        String width = "";
        String height = "";
        int xIndex = pixelSize.indexOf('x');
        if (xIndex > 0) {
            width = pixelSize.substring(0, xIndex);
            height = pixelSize.substring(xIndex + 1);
        }
        return template
                .replace("{{prompt}}", escapeJson(prompt))
                .replace("{{model}}", config.getModel() != null ? config.getModel() : "")
                .replace("{{size}}", pixelSize)
                .replace("{{width}}", width)
                .replace("{{height}}", height)
                .replace("{{ratio}}", config.getSize() != null ? config.getSize() : "")
                .replace("{{api_key}}", config.getApiKey() != null ? config.getApiKey() : "");
    }

    /** 从响应提取图片并转换，失败时携带响应片段便于排错。 */
    private GeneratedImage extractImage(JsonNode root, String imagePath,
                                        ImageConfigDto config, String responseBody) {
        JsonNode imageNode = extractByPath(root, imagePath);
        if (imageNode == null || imageNode.asText().isBlank()) {
            throw new RuntimeException("按路径 " + imagePath + " 未提取到图片数据，请检查图片提取路径配置。响应: "
                    + truncate(responseBody, 500));
        }
        return toGeneratedImage(imageNode.asText(), config);
    }

    /** 自动识别提取值形态：URL / data URI / 裸 base64。 */
    private GeneratedImage toGeneratedImage(String value, ImageConfigDto config) {
        String model = config.getModel();
        if (value.startsWith("http://") || value.startsWith("https://")) {
            log.info("万能模板生图成功 url={}", value);
            return GeneratedImage.ofUrl(value, "generic", model);
        }
        String mimeType = "image/png";
        String base64 = value;
        if (value.startsWith("data:")) {
            int comma = value.indexOf(',');
            if (comma > 0) {
                String meta = value.substring(5, comma);
                int semi = meta.indexOf(';');
                mimeType = semi > 0 ? meta.substring(0, semi) : meta;
                base64 = value.substring(comma + 1);
            }
        }
        try {
            byte[] bytes = java.util.Base64.getDecoder().decode(base64);
            log.info("万能模板生图成功 base64, {} bytes", bytes.length);
            return GeneratedImage.ofBytes(bytes, mimeType, "generic", model);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("提取到的图片数据既不是URL也不是合法base64: " + truncate(value, 200));
        }
    }

    /**
     * 按点号+下标路径提取 JSON 节点，如 {@code data[0].url}、{@code output.choices[0].message.content[0].image}。
     */
    private JsonNode extractByPath(JsonNode root, String path) {
        JsonNode current = root;
        for (String segment : path.trim().split("\\.")) {
            if (current == null || segment.isBlank()) {
                return null;
            }
            int bracket = segment.indexOf('[');
            String field = bracket >= 0 ? segment.substring(0, bracket) : segment;
            if (!field.isBlank()) {
                current = current.get(field);
            }
            if (bracket >= 0) {
                Matcher matcher = INDEX_PATTERN.matcher(segment.substring(bracket));
                while (matcher.find()) {
                    if (current == null) {
                        return null;
                    }
                    current = current.get(Integer.parseInt(matcher.group(1)));
                }
            }
        }
        return current;
    }

    private JsonNode readTree(String responseBody) {
        try {
            return objectMapper.readTree(responseBody);
        } catch (Exception e) {
            throw new RuntimeException("生图API响应不是合法JSON: " + truncate(responseBody, 500));
        }
    }

    /** JSON 字符串值转义（利用 Jackson 序列化后去掉首尾引号）。 */
    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        String quoted = objectMapper.writeValueAsString(value);
        return quoted.substring(1, quoted.length() - 1);
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
