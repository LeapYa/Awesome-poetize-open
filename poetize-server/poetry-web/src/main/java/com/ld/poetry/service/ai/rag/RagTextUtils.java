package com.ld.poetry.service.ai.rag;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public final class RagTextUtils {

    private RagTextUtils() {
    }

    public static String normalize(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text
                .replaceAll("```[\\s\\S]*?```", " ")
                .replaceAll("`([^`]*)`", "$1")
                // 保留图片引用为文本标记，避免 RAG 检索片段完全丢失图片信息。
                // data URI 单独处理为短标记，防止 base64 膨胀 embedding 文本。
                .replaceAll("(?i)<img[^>]+src=[\"']data:[^\"']+[\"'][^>]*>", " [图片: 内联图片] ")
                .replaceAll("(?i)<img[^>]+src=[\"']([^\"']+)[\"'][^>]*>", " [图片: $1] ")
                // markdown 图片 ![alt](url) → [图片: url]，保留 URL 供后续按需识别
                .replaceAll("!\\[[^\\]]*]\\(([^)]*)\\)", " [图片: $1] ")
                .replaceAll("\\[[^\\]]*]\\(([^)]*)\\)", " ")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("[#>*_~\\-]{1,3}", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized;
    }

    public static List<String> split(String text, int chunkSize, int overlap) {
        String normalized = normalize(text);
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }
        if (normalized.length() <= chunkSize) {
            return List.of(normalized);
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        int step = Math.max(1, chunkSize - overlap);
        while (start < normalized.length()) {
            int end = Math.min(normalized.length(), start + chunkSize);
            if (end < normalized.length()) {
                int breakPoint = normalized.lastIndexOf(' ', end);
                if (breakPoint > start + (chunkSize / 2)) {
                    end = breakPoint;
                }
            }
            String chunk = normalized.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            if (end >= normalized.length()) {
                break;
            }
            start = Math.max(start + step, end - overlap);
        }
        return chunks;
    }

    public static String abbreviate(String text, int maxLength) {
        String normalized = normalize(text);
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }
}
