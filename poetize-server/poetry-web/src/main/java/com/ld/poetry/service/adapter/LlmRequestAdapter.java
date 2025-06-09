package com.ld.poetry.service.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * 大模型请求适配器接口
 * 用于适配不同大模型API的请求和响应格式
 */
public interface LlmRequestAdapter {
    
    /**
     * 构建请求体
     * 
     * @param text 要翻译的文本
     * @param prompt 翻译提示词
     * @param fromLang 源语言代码
     * @param toLang 目标语言代码
     * @param isMarkdown 是否是Markdown格式
     * @param modelName 大模型名称
     * @return 构建好的请求体
     */
    Map<String, Object> buildRequestBody(String text, String prompt, String fromLang, String toLang, boolean isMarkdown, String modelName);
    
    /**
     * 从响应中提取翻译结果
     * 
     * @param responseNode 响应JSON节点
     * @param originalText 原始文本(用于判断翻译是否有效)
     * @return 翻译后的文本，如果无法解析则返回null
     */
    String extractTranslation(JsonNode responseNode, String originalText);
} 