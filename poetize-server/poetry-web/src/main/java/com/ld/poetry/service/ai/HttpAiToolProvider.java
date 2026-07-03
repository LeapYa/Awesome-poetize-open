package com.ld.poetry.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.ld.poetry.entity.SysPlugin;
import com.ld.poetry.plugin.PluginManifest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 ai_tool 插件生成动态 ToolCallback，支持通过配置直接调用 HTTP 接口。
 */
@Slf4j
@Component
public class HttpAiToolProvider {

    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{\\{\\s*(args|config)\\.([a-zA-Z0-9_.-]+)\\s*}}");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final com.ld.poetry.service.SysPluginService sysPluginService;
    private final JsonMapper objectMapper;

    public HttpAiToolProvider(com.ld.poetry.service.SysPluginService sysPluginService, JsonMapper objectMapper) {
        this.sysPluginService = sysPluginService;
        this.objectMapper = objectMapper;
    }

    public List<ToolCallback> getEnabledToolCallbacks() {
        LambdaQueryWrapper<SysPlugin> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysPlugin::getPluginType, SysPlugin.TYPE_AI_TOOL)
                .eq(SysPlugin::getEnabled, true)
                .orderByAsc(SysPlugin::getSortOrder);

        List<SysPlugin> plugins = sysPluginService.list(wrapper);
        List<ToolCallback> callbacks = new ArrayList<>();
        for (SysPlugin plugin : plugins) {
            try {
                ToolCallback callback = buildToolCallback(plugin);
                if (callback != null) {
                    callbacks.add(callback);
                }
            } catch (Exception ex) {
                log.warn("AI工具插件加载失败: key={}, error={}", plugin.getPluginKey(), ex.getMessage());
            }
        }
        return callbacks;
    }

    public List<ToolDefinition> getEnabledToolDefinitions() {
        return getEnabledToolCallbacks().stream()
                .map(ToolCallback::getToolDefinition)
                .toList();
    }

    private ToolCallback buildToolCallback(SysPlugin plugin) throws JacksonException {
        if (!StringUtils.hasText(plugin.getManifest())) {
            log.warn("AI工具插件缺少 manifest，已跳过: {}", plugin.getPluginKey());
            return null;
        }

        JsonNode manifestNode = objectMapper.readTree(plugin.getManifest());
        PluginManifest.ToolConfig toolConfig = parseManifestNode(manifestNode.get("tool"), PluginManifest.ToolConfig.class);
        PluginManifest.HttpRuntimeConfig runtimeConfig = parseManifestNode(manifestNode.get("runtime"), PluginManifest.HttpRuntimeConfig.class);
        String useCase = textValue(manifestNode.get("useCase"));
        String expectedOutput = textValue(manifestNode.get("expectedOutput"));
        List<String> exampleQuestions = stringListValue(manifestNode.get("exampleQuestions"));

        if (toolConfig == null || runtimeConfig == null) {
            log.warn("AI工具插件缺少 tool/runtime 配置，已跳过: {}", plugin.getPluginKey());
            return null;
        }
        if (!StringUtils.hasText(toolConfig.getName()) || !StringUtils.hasText(runtimeConfig.getUrl())) {
            log.warn("AI工具插件配置不完整，已跳过: {}", plugin.getPluginKey());
            return null;
        }
        if (!"http".equalsIgnoreCase(runtimeConfig.getType())) {
            log.warn("AI工具插件 runtime.type 暂不支持: key={}, type={}", plugin.getPluginKey(), runtimeConfig.getType());
            return null;
        }

        Map<String, Object> pluginConfig = parseMap(plugin.getPluginConfig());
        String inputSchemaJson = objectMapper.writeValueAsString(
                toolConfig.getInputSchema() != null ? toolConfig.getInputSchema() : defaultInputSchema());
        String toolDescription = buildToolDescription(plugin, toolConfig, useCase, expectedOutput, exampleQuestions);
        ToolDefinition toolDefinition = ToolDefinition.builder()
                .name(toolConfig.getName())
                .description(toolDescription)
                .inputSchema(inputSchemaJson)
                .build();

        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return toolDefinition;
            }

            @Override
            public String call(String toolInput) {
                return call(toolInput, null);
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                try {
                    Map<String, Object> args = parseMap(toolInput);
                    return executeHttpTool(plugin, runtimeConfig, pluginConfig, args);
                } catch (Exception ex) {
                    throw new IllegalStateException("AI工具执行失败: " + ex.getMessage(), ex);
                }
            }
        };
    }

    private String buildToolDescription(SysPlugin plugin, PluginManifest.ToolConfig toolConfig,
            String useCase, String expectedOutput, List<String> exampleQuestions) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(toolConfig.getDescription())) {
            parts.add(toolConfig.getDescription());
        } else if (StringUtils.hasText(plugin.getPluginDescription())) {
            parts.add(plugin.getPluginDescription());
        }
        if (StringUtils.hasText(useCase)) {
            parts.add("用途：" + useCase);
        }
        if (StringUtils.hasText(expectedOutput)) {
            parts.add("期望返回：" + expectedOutput);
        }
        if (exampleQuestions != null && !exampleQuestions.isEmpty()) {
            parts.add("典型问题：" + String.join("；", exampleQuestions));
        }
        return String.join("\n", parts);
    }

    private <T> T parseManifestNode(JsonNode node, Class<T> type) throws JacksonException {
        if (node == null || node.isNull() || !node.isObject()) {
            return null;
        }
        return objectMapper.treeToValue(node, type);
    }

    private String textValue(JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : null;
    }

    private List<String> stringListValue(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item != null && item.isTextual()) {
                values.add(item.asText());
            }
        }
        return values;
    }

    private String executeHttpTool(SysPlugin plugin, PluginManifest.HttpRuntimeConfig runtimeConfig,
            Map<String, Object> pluginConfig, Map<String, Object> args) throws JacksonException {
        HttpMethod httpMethod = HttpMethod.valueOf(
                StringUtils.hasText(runtimeConfig.getMethod()) ? runtimeConfig.getMethod().toUpperCase() : "GET");

        Map<String, Object> resolvedQuery = castMap(resolveTemplateValue(runtimeConfig.getQuery(), args, pluginConfig));
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(runtimeConfig.getUrl());
        appendQueryParams(uriBuilder, resolvedQuery);

        HttpHeaders headers = buildHeaders(runtimeConfig, args, pluginConfig);
        Object resolvedBody = resolveTemplateValue(runtimeConfig.getBody(), args, pluginConfig);
        HttpEntity<?> requestEntity = buildRequestEntity(headers, resolvedBody, httpMethod);

        RestTemplate restTemplate = buildRestTemplate(runtimeConfig.getTimeoutMs());
        ResponseEntity<String> response = restTemplate.exchange(
                uriBuilder.build(true).toUri(),
                httpMethod,
                requestEntity,
                String.class);

        Object responseBody = parseResponseBody(response.getBody());
        Object extractedData = extractByPath(responseBody, runtimeConfig.getResponsePath());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tool", plugin.getPluginKey());
        result.put("status", response.getStatusCode().value());
        result.put("data", extractedData);
        return objectMapper.writeValueAsString(result);
    }

    private HttpHeaders buildHeaders(PluginManifest.HttpRuntimeConfig runtimeConfig,
            Map<String, Object> args, Map<String, Object> pluginConfig) {
        HttpHeaders headers = new HttpHeaders();
        Map<String, Object> resolvedHeaders = castMap(resolveTemplateValue(runtimeConfig.getHeaders(), args, pluginConfig));
        for (Map.Entry<String, Object> entry : resolvedHeaders.entrySet()) {
            if (entry.getValue() != null) {
                // header 名/值均可能由模型入参经模板解析注入，过滤 CR/LF 防 HTTP Header Injection。
                String headerName = sanitizeHeader(entry.getKey());
                String headerValue = sanitizeHeader(String.valueOf(entry.getValue()));
                if (headerName != null && !headerName.isEmpty()) {
                    headers.set(headerName, headerValue);
                }
            }
        }
        String contentType = StringUtils.hasText(runtimeConfig.getContentType())
                ? runtimeConfig.getContentType()
                : MediaType.APPLICATION_JSON_VALUE;
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN, MediaType.ALL));
        return headers;
    }

    /**
     * 清洗 header 名/值：剔除 CR/LF 及其他 ASCII 控制字符，防 HTTP Header Injection。
     * 若清洗后为空字符串则返回空（调用方据此跳过该 header）。
     */
    private String sanitizeHeader(String raw) {
        if (raw == null) {
            return null;
        }
        // 移除 CR(\r)、LF(\n) 及其余 C0 控制字符（0x00-0x1F、0x7F），保留可见字符与空格
        String cleaned = raw.replaceAll("[\\r\\n\\t\\x00-\\x1F\\x7F]", "");
        return cleaned.trim();
    }

    private HttpEntity<?> buildRequestEntity(HttpHeaders headers, Object resolvedBody, HttpMethod method)
            throws JacksonException {
        if (method == HttpMethod.GET || method == HttpMethod.DELETE) {
            return new HttpEntity<>(headers);
        }
        if (resolvedBody == null) {
            return new HttpEntity<>(headers);
        }
        if (resolvedBody instanceof String textBody) {
            return new HttpEntity<>(textBody, headers);
        }
        return new HttpEntity<>(objectMapper.writeValueAsString(resolvedBody), headers);
    }

    private RestTemplate buildRestTemplate(Integer timeoutMs) {
        int timeout = timeoutMs != null && timeoutMs > 0 ? timeoutMs : 10000;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return new RestTemplate(factory);
    }

    private void appendQueryParams(UriComponentsBuilder builder, Map<String, Object> queryParams) {
        if (queryParams.isEmpty()) {
            return;
        }
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        for (Map.Entry<String, Object> entry : queryParams.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            if (entry.getValue() instanceof Iterable<?> iterable) {
                for (Object item : iterable) {
                    params.add(entry.getKey(), item != null ? String.valueOf(item) : "");
                }
            } else {
                params.add(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        builder.queryParams(params);
    }

    private Object parseResponseBody(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        try {
            return objectMapper.readValue(body, Object.class);
        } catch (Exception ignored) {
            return body;
        }
    }

    private Map<String, Object> parseMap(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ex) {
            throw new IllegalArgumentException("JSON配置格式不正确");
        }
    }

    private Map<String, Object> defaultInputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of());
        return schema;
    }

    private Object resolveTemplateValue(Object template, Map<String, Object> args, Map<String, Object> config) {
        if (template == null) {
            return null;
        }
        if (template instanceof Map<?, ?> map) {
            Map<String, Object> resolved = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object value = resolveTemplateValue(entry.getValue(), args, config);
                if (value != null) {
                    resolved.put(String.valueOf(entry.getKey()), value);
                }
            }
            return resolved;
        }
        if (template instanceof List<?> list) {
            List<Object> resolved = new ArrayList<>();
            for (Object item : list) {
                resolved.add(resolveTemplateValue(item, args, config));
            }
            return resolved;
        }
        if (!(template instanceof String text)) {
            return template;
        }

        Matcher matcher = TEMPLATE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return text;
        }

        matcher.reset();
        if (matcher.matches()) {
            return evaluateExpression(buildTemplateExpression(matcher), args, config);
        }

        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            Object value = evaluateExpression(buildTemplateExpression(matcher), args, config);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value != null ? String.valueOf(value) : ""));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String buildTemplateExpression(Matcher matcher) {
        return matcher.group(1) + "." + matcher.group(2);
    }

    private Object evaluateExpression(String expression, Map<String, Object> args, Map<String, Object> config) {
        if (!StringUtils.hasText(expression)) {
            return null;
        }

        String[] segments = expression.split("\\|\\|");
        for (String segment : segments) {
            Object value = evaluateOperand(segment.trim(), args, config);
            if (hasTemplateValue(value)) {
                return value;
            }
        }
        return null;
    }

    private Object evaluateOperand(String operand, Map<String, Object> args, Map<String, Object> config) {
        if (!StringUtils.hasText(operand)) {
            return null;
        }
        if ((operand.startsWith("\"") && operand.endsWith("\""))
                || (operand.startsWith("'") && operand.endsWith("'"))) {
            return operand.substring(1, operand.length() - 1);
        }
        if ("true".equalsIgnoreCase(operand) || "false".equalsIgnoreCase(operand)) {
            return Boolean.parseBoolean(operand);
        }
        if ("null".equalsIgnoreCase(operand)) {
            return null;
        }
        if (operand.matches("-?\\d+")) {
            try {
                return Integer.parseInt(operand);
            } catch (NumberFormatException ignored) {
            }
        }
        if (operand.matches("-?\\d+\\.\\d+")) {
            try {
                return Double.parseDouble(operand);
            } catch (NumberFormatException ignored) {
            }
        }
        return lookupTemplateValue(operand, args, config);
    }

    private boolean hasTemplateValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String text) {
            return StringUtils.hasText(text);
        }
        return true;
    }

    private Object lookupTemplateValue(String reference, Map<String, Object> args, Map<String, Object> config) {
        if (!StringUtils.hasText(reference) || !reference.contains(".")) {
            return null;
        }
        int dotIndex = reference.indexOf('.');
        String source = reference.substring(0, dotIndex);
        String path = reference.substring(dotIndex + 1);
        Map<String, Object> root = "args".equals(source) ? args : "config".equals(source) ? config : null;
        return root != null ? extractByPath(root, path) : null;
    }

    private Object extractByPath(Object source, String path) {
        if (!StringUtils.hasText(path) || source == null) {
            return source;
        }
        String[] parts = path.split("\\.");
        Object current = source;
        for (String part : parts) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(part);
                continue;
            }
            if (current instanceof List<?> list && part.matches("\\d+")) {
                int index = Integer.parseInt(part);
                current = index >= 0 && index < list.size() ? list.get(index) : null;
                continue;
            }
            return null;
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        return new LinkedHashMap<>();
    }
}
