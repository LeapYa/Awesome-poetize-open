package com.ld.poetry.service.prerender;

import com.ld.poetry.service.SysAiConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class PrerenderLanguageSupport {

    private static final Set<String> SUPPORTED_LANGUAGES = Set.of(
            "zh", "en", "ja", "zh-TW", "ko", "fr", "de", "es", "ru",
            "pt", "it", "ar", "th", "vi");

    private final SysAiConfigService sysAiConfigService;

    public String getSourceLanguage() {
        try {
            Map<String, Object> defaultLanguages = sysAiConfigService.getDefaultLanguages();
            if (defaultLanguages == null) {
                return "zh";
            }
            Object configured = defaultLanguages.get("default_source_lang");
            if (configured instanceof String value && StringUtils.hasText(value)) {
                return value.trim();
            }
        } catch (Exception e) {
            log.warn("获取默认源语言失败，回退到 zh: {}", e.getMessage());
        }
        return "zh";
    }

    public boolean isSupportedLanguage(String language) {
        return StringUtils.hasText(language) && SUPPORTED_LANGUAGES.contains(language.trim());
    }

    public List<String> resolveLanguages(Collection<String> languages) {
        if (CollectionUtils.isEmpty(languages)) {
            return List.of(getSourceLanguage());
        }

        LinkedHashSet<String> validLanguages = new LinkedHashSet<>();
        for (String language : languages) {
            if (isSupportedLanguage(language)) {
                validLanguages.add(language.trim());
            }
        }

        if (validLanguages.isEmpty()) {
            throw new IllegalArgumentException("未找到支持的预渲染语言: " + languages);
        }
        return List.copyOf(new ArrayList<>(validLanguages));
    }
}
