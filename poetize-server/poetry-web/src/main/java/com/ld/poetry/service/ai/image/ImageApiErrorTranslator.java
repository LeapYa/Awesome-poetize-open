package com.ld.poetry.service.ai.image;

import org.springframework.web.client.RestClientResponseException;

/**
 * 生图上游 API HTTP 错误翻译工具。
 *
 * <p>将 {@link RestClientResponseException}（如 400/401/403/429）转换为面向管理员的可读错误信息：
 * 包含 HTTP 状态、上游响应体（截断），并针对常见错误码附加排查提示，
 * 避免测试对话框只能看到原始异常或被通用文案掩盖。
 */
final class ImageApiErrorTranslator {

    private static final int BODY_MAX_LENGTH = 500;

    private ImageApiErrorTranslator() {
    }

    /**
     * 翻译上游 HTTP 错误为可读的 RuntimeException。
     *
     * @param e        上游返回非 2xx 时 RestClient 抛出的异常
     * @param provider 当前 provider 标识（用于提示定位）
     */
    static RuntimeException translate(RestClientResponseException e, String provider) {
        int status = e.getStatusCode().value();
        String body = truncate(e.getResponseBodyAsString(), BODY_MAX_LENGTH);

        StringBuilder message = new StringBuilder();
        message.append("生图API返回错误 [").append(provider).append("] HTTP ").append(status);
        if (!body.isBlank()) {
            message.append("，响应: ").append(body);
        }

        String hint = buildHint(status, body);
        if (hint != null) {
            message.append("。排查提示：").append(hint);
        }
        return new RuntimeException(message.toString(), e);
    }

    /** 按状态码与响应体内容给出针对性排查提示。 */
    private static String buildHint(int status, String body) {
        // DashScope 原生端点收到 OpenAI 格式请求的典型报错
        if (body.contains("input.messages")) {
            return "该API地址是 DashScope 原生端点（multimodal-generation），不兼容 OpenAI 请求格式，"
                    + "请将生图服务商切换为“通义万相”后再使用此地址";
        }
        return switch (status) {
            case 401 -> "API密钥无效或已过期，请检查密钥是否正确";
            case 403 -> "API密钥无权访问该模型，请确认：①模型名称拼写正确 ②该模型已在服务商控制台开通 ③账户未欠费";
            case 404 -> "API地址或模型不存在，请检查API接口地址与模型名称";
            case 429 -> "请求被限流或配额不足，请稍后重试或检查服务商配额";
            default -> status >= 500 ? "服务商侧异常，请稍后重试" : null;
        };
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
