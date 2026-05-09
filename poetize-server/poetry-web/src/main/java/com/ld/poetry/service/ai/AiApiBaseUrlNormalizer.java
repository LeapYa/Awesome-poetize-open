package com.ld.poetry.service.ai;

import org.springframework.util.StringUtils;

/**
 * 统一规范化 OpenAI 兼容接口的 baseUrl。
 * Spring AI 会自行拼接 /v1/chat/completions 或 /v1/embeddings，
 * 因此前端若保存了完整端点，必须在进入 SDK 前移除尾部路径。
 * 当前聊天链路只支持 Chat Completions，不支持 legacy /v1/completions 调用。
 */
public final class AiApiBaseUrlNormalizer {

    private AiApiBaseUrlNormalizer() {
    }

    public static String normalizeOpenAiCompatibleBaseUrl(String url, String defaultUrl) {
        if (!StringUtils.hasText(url)) {
            return defaultUrl;
        }
        String normalized = url.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/chat/completions")) {
            normalized = normalized.substring(0, normalized.length() - "/chat/completions".length());
        }
        if (normalized.endsWith("/embeddings")) {
            normalized = normalized.substring(0, normalized.length() - "/embeddings".length());
        }
        if (normalized.endsWith("/completions")) {
            normalized = normalized.substring(0, normalized.length() - "/completions".length());
        }
        if (normalized.endsWith("/v1")) {
            normalized = normalized.substring(0, normalized.length() - 3);
        }
        return normalized;
    }
}
