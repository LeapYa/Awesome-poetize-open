package com.ld.poetry.service.ai.tools;

import com.ld.poetry.service.ai.ContentSanitizer;
import com.ld.poetry.service.ai.ToolCallbackEventBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 页面上下文相关 @Tool 工具
 *
 * 让 AI 能够按需获取用户当前正在浏览的页面内容（标题、类型、URL、正文等），
 * 避免在用户未手动附加页面、却针对"当前页面/这篇文章"提问时凭空猜测。
 *
 * 页面上下文由前端随聊天请求一并提交（currentPage 字段），
 * 经 AiChatService 放入 ToolContext，本工具在调用时读取并净化后返回。
 */
@Service
public class PageTools {

    private static final Logger logger = LoggerFactory.getLogger(PageTools.class);

    private final ContentSanitizer contentSanitizer;

    public PageTools(ContentSanitizer contentSanitizer) {
        this.contentSanitizer = contentSanitizer;
    }

    @Tool(description = "获取用户当前正在浏览的页面内容（标题、类型、URL、正文等）。"
            + "当用户的提问涉及\"当前页面\"\"这个页面\"\"这篇文章\"\"本页\"\"这里\"等指代当前浏览内容，"
            + "但当前消息中并未附带页面内容时，调用此工具获取页面上下文后再作答，避免凭空猜测。无需传入参数。")
    public String getCurrentPage(ToolContext toolContext) {
        Map<String, Object> context = (toolContext != null) ? toolContext.getContext() : Map.of();
        Object raw = context.get(ToolCallbackEventBridge.CURRENT_PAGE_CONTEXT_KEY);

        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) {
            return "当前没有可用的页面上下文。可能用户未在可识别的页面上，或页面内容为空。"
                    + "如需了解页面内容，请提示用户通过「附加 > 页面」按钮附带页面，或在可识别的页面上提问。";
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> pageContext = (Map<String, Object>) map;
        Map<String, Object> sanitized = contentSanitizer.sanitizePageContext(pageContext);

        String title = (String) sanitized.getOrDefault("title", "");
        String type = (String) sanitized.getOrDefault("type", "");
        String content = (String) sanitized.getOrDefault("content", "");
        String url = contentSanitizer.sanitizeField(getString(pageContext, "url"), "url");

        if (!StringUtils.hasText(title) && !StringUtils.hasText(content)) {
            return "当前页面没有可提取的文本内容。";
        }

        StringBuilder sb = new StringBuilder("当前页面信息：");
        if (StringUtils.hasText(title)) {
            sb.append("\n标题：").append(title);
        }
        if (StringUtils.hasText(type)) {
            sb.append("\n类型：").append(type);
        }
        if (StringUtils.hasText(url)) {
            sb.append("\nURL：").append(url);
        }
        if (StringUtils.hasText(content)) {
            sb.append("\n正文：\n").append(content);
        }
        return sb.toString();
    }

    private String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return (v != null) ? String.valueOf(v) : "";
    }
}
