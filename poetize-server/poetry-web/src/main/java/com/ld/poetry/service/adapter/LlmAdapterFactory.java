package com.ld.poetry.service.adapter;

/**
 * 大模型适配器工厂
 * 根据指定的大模型类型创建对应的适配器
 */
public class LlmAdapterFactory {
    
    /**
     * 获取大模型适配器
     * 
     * @param llmType 大模型类型
     * @return 对应类型的适配器实例
     */
    public static LlmRequestAdapter getAdapter(String llmType) {
        if (llmType == null || llmType.trim().isEmpty()) {
            return new CustomAdapter(); // 默认使用自定义适配器
        }
        
        switch (llmType.toLowerCase()) {
            case "openai":
                return new OpenAiAdapter();
            case "anthropic":
                return new AnthropicAdapter();
            case "azure":
                return new AzureOpenAiAdapter();
            case "custom":
            default:
                return new CustomAdapter();
        }
    }
} 