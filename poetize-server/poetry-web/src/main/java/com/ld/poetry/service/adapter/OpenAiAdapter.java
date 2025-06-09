package com.ld.poetry.service.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI (GPT) 适配器
 * 支持GPT-3.5/GPT-4等基于聊天格式的API
 */
public class OpenAiAdapter implements LlmRequestAdapter {
    
    @Override
    public Map<String, Object> buildRequestBody(String text, String prompt, String fromLang, String toLang, boolean isMarkdown, String modelName) {
        Map<String, Object> requestBody = new HashMap<>();
        List<Map<String, String>> messages = new ArrayList<>();
        
        // 系统提示词
        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", prompt);
        messages.add(systemMessage);
        
        // 用户消息（要翻译的文本）
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", text);
        messages.add(userMessage);
        
        requestBody.put("messages", messages);
        
        // OpenAI API需要指定模型名称
        // 如果传入了有效的模型名称，则使用传入的值，否则使用默认值
        if (modelName != null && !modelName.isEmpty()) {
            requestBody.put("model", modelName);
        } else {
            requestBody.put("model", "gpt-3.5-turbo"); // 默认模型
        }
        
        requestBody.put("temperature", 0.3); // 较低的temperature以提高准确性
        
        return requestBody;
    }
    
    @Override
    public String extractTranslation(JsonNode root, String originalText) {
        if (root.has("choices") && root.get("choices").isArray() && root.get("choices").size() > 0) {
            JsonNode choice = root.get("choices").get(0);
            if (choice.has("message") && choice.get("message").has("content")) {
                return choice.get("message").get("content").asText();
            }
        }
        return null;
    }
} 