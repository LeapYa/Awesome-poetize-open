package com.ld.poetry.service.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;

/**
 * Anthropic (Claude) 适配器
 * 适用于Claude系列模型API
 */
public class AnthropicAdapter implements LlmRequestAdapter {
    
    @Override
    public Map<String, Object> buildRequestBody(String text, String prompt, String fromLang, String toLang, boolean isMarkdown, String modelName) {
        Map<String, Object> requestBody = new HashMap<>();
        
        // Claude的消息格式: system prompt + Human: + Assistant:
        StringBuilder messageContent = new StringBuilder();
        
        // 系统提示内容
        messageContent.append(prompt);
        messageContent.append("\n\nHuman: ");
        messageContent.append(text);
        messageContent.append("\n\nAssistant: ");
        
        requestBody.put("prompt", messageContent.toString());
        
        // Anthropic API需要指定模型名称
        // 如果传入了有效的模型名称，则使用传入的值，否则使用默认值
        if (modelName != null && !modelName.isEmpty()) {
            requestBody.put("model", modelName);
        } else {
            requestBody.put("model", "claude-2"); // 默认模型
        }
        
        requestBody.put("max_tokens_to_sample", 2000);
        requestBody.put("temperature", 0.3);
        
        return requestBody;
    }
    
    @Override
    public String extractTranslation(JsonNode root, String originalText) {
        if (root.has("completion")) {
            return root.get("completion").asText();
        }
        return null;
    }
} 