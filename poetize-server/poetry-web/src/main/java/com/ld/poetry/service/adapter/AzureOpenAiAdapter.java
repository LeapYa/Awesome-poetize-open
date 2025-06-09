package com.ld.poetry.service.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Azure OpenAI 适配器
 * 适用于部署在Azure上的OpenAI模型
 */
public class AzureOpenAiAdapter implements LlmRequestAdapter {
    
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
        
        // Azure OpenAI API通常不需要指定模型，因为在API URL中已经指定了部署ID
        // 忽略传入的modelName参数
        
        requestBody.put("temperature", 0.3);
        requestBody.put("max_tokens", 2000);
        
        return requestBody;
    }
    
    @Override
    public String extractTranslation(JsonNode root, String originalText) {
        // Azure OpenAI使用与OpenAI相同的响应格式
        if (root.has("choices") && root.get("choices").isArray() && root.get("choices").size() > 0) {
            JsonNode choice = root.get("choices").get(0);
            if (choice.has("message") && choice.get("message").has("content")) {
                return choice.get("message").get("content").asText();
            }
        }
        return null;
    }
} 