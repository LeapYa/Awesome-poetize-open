package com.ld.poetry.service.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;

/**
 * 自定义大模型适配器
 * 使用文本拼接方法，适用于不确定API格式的场景
 */
public class CustomAdapter implements LlmRequestAdapter {
    
    @Override
    public Map<String, Object> buildRequestBody(String text, String prompt, String fromLang, String toLang, boolean isMarkdown, String modelName) {
        Map<String, Object> requestBody = new HashMap<>();
        
        // 将提示词与原文合并，中间用2个换行符分隔
        String combinedText = prompt + "\n\n" + text;
        
        // 使用传入的模型名称，如果为空则使用默认值
        // 注意：这里是自定义适配器，为Ollama API设计，必须传递model参数
        String model = (modelName != null && !modelName.isEmpty()) ? modelName : "qwen3:8b-q8_0";
        
        // 添加model字段，这是Ollama API的必需字段
        requestBody.put("model", model);
        requestBody.put("prompt", combinedText);  // Ollama使用prompt而非text
        requestBody.put("source_language", fromLang);
        requestBody.put("target_language", toLang);
        requestBody.put("format", isMarkdown ? "markdown" : "text");
        
        return requestBody;
    }
    
    @Override
    public String extractTranslation(JsonNode root, String originalText) {
        // 通用的响应解析逻辑
        
        // 尝试各种可能的响应字段
        if (root.has("translation")) {
            return root.get("translation").asText();
        } else if (root.has("translated_text")) {
            return root.get("translated_text").asText();
        } else if (root.has("result")) {
            return root.get("result").asText();
        } else if (root.has("content")) {
            return root.get("content").asText();
        } else if (root.has("output")) {
            return root.get("output").asText();
        } else if (root.has("text")) {
            return root.get("text").asText();
        } else if (root.has("response")) {
            // 嵌套JSON的情况
            JsonNode response_node = root.get("response");
            if (response_node.isTextual()) {
                return response_node.asText();
            } else if (response_node.has("text")) {
                return response_node.get("text").asText();
            } else if (response_node.has("content")) {
                return response_node.get("content").asText();
            }
        } else if (root.has("data")) {
            // 嵌套JSON的情况
            JsonNode data_node = root.get("data");
            if (data_node.isTextual()) {
                return data_node.asText();
            } else if (data_node.has("translation")) {
                return data_node.get("translation").asText();
            } else if (data_node.has("text")) {
                return data_node.get("text").asText();
            } else if (data_node.has("content")) {
                return data_node.get("content").asText();
            }
        }
        
        // 如果是简单JSON但格式不是上述任何一种，尝试找到第一个字符串字段
        Iterator<String> fieldNames = root.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            JsonNode node = root.get(fieldName);
            if (node.isTextual() && node.asText().length() > originalText.length() / 3) {
                // 找到一个文本字段，其长度至少达到原文的1/3，可能是翻译内容
                return node.asText();
            }
        }
        
        // 找不到合适的字段，返回null让调用方处理
        return null;
    }
} 