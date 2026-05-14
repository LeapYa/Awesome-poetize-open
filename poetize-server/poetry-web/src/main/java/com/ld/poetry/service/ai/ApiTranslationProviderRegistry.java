package com.ld.poetry.service.ai;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.ld.poetry.entity.SysAiConfig;
import com.ld.poetry.service.TranslationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class ApiTranslationProviderRegistry {

    private final Map<String, ApiTranslationProvider> providers = new LinkedHashMap<>();

    public ApiTranslationProviderRegistry(List<ApiTranslationProvider> providerList) {
        for (ApiTranslationProvider provider : providerList) {
            providers.put(provider.providerKey(), provider);
        }
    }

    public boolean isApiProvider(String providerKey) {
        return providers.containsKey(providerKey);
    }

    public ApiTranslationProvider getProvider(String providerKey) {
        return providers.get(providerKey);
    }

    public Set<String> providerKeys() {
        return providers.keySet();
    }

    public String translateText(SysAiConfig config, String text, String sourceLang, String targetLang) {
        ApiTranslationContext context = contextFromConfig(config);
        if (context == null) {
            return null;
        }
        return context.provider().translate(text, sourceLang, targetLang, context.providerConfig());
    }

    public Map<String, String> translateArticle(SysAiConfig config, String title, String content,
            String sourceLang, String targetLang) {
        return translateArticle(config, title, content, sourceLang, targetLang, null);
    }

    public Map<String, String> translateArticle(SysAiConfig config, String title, String content,
            String sourceLang, String targetLang,
            TranslationService.TranslationProgressListener progressListener) {
        ApiTranslationContext context = contextFromConfig(config);
        if (context == null) {
            return null;
        }
        return context.provider().translateArticle(title, content, sourceLang, targetLang,
                context.providerConfig(), progressListener);
    }

    public ApiTranslationContext contextFromConfig(SysAiConfig config) {
        if (config == null || !StringUtils.hasText(config.getTranslationType())) {
            return null;
        }
        String providerKey = config.getTranslationType();
        ApiTranslationProvider provider = providers.get(providerKey);
        if (provider == null) {
            return null;
        }

        JSONObject providerConfig = "baidu".equals(providerKey)
                ? parse(config.getBaiduConfig())
                : parse(config.getCustomConfig());
        if (providerConfig == null) {
            log.error("{} 翻译配置为空", provider.displayName());
            return null;
        }
        return new ApiTranslationContext(providerKey, provider, providerConfig);
    }

    public ApiTranslationContext contextFromTempConfig(String providerKey, Map<String, Object> rawConfig) {
        ApiTranslationProvider provider = providers.get(providerKey);
        if (provider == null) {
            return null;
        }
        JSONObject providerConfig = new JSONObject();
        if (rawConfig != null) {
            rawConfig.forEach(providerConfig::put);
        }
        return new ApiTranslationContext(providerKey, provider, providerConfig);
    }

    private JSONObject parse(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        return JSON.parseObject(json);
    }

    public record ApiTranslationContext(
            String providerKey,
            ApiTranslationProvider provider,
            JSONObject providerConfig) {
    }
}
