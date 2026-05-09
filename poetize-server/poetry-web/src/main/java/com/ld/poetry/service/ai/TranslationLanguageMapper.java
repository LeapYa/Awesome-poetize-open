package com.ld.poetry.service.ai;

import org.springframework.util.StringUtils;

import java.util.Locale;

public final class TranslationLanguageMapper {

    private TranslationLanguageMapper() {
    }

    public static String map(String provider, String lang, boolean source) {
        String normalized = normalize(lang, source);
        return switch (provider) {
            case "baidu" -> mapBaidu(normalized);
            case "youdao" -> mapYoudao(normalized);
            case "google" -> mapGoogle(normalized);
            case "azure_translator" -> mapAzure(normalized);
            case "deepl" -> mapDeepL(normalized, source);
            default -> normalized;
        };
    }

    private static String normalize(String lang, boolean source) {
        if (!StringUtils.hasText(lang)) {
            return source ? "auto" : "en";
        }
        String value = lang.trim().replace('_', '-');
        String lower = value.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "cn", "zh-cn", "zh-hans", "zh-chs" -> "zh";
            case "zh-tw", "zh-hant", "zh-cht", "cht" -> "zh-TW";
            case "jp", "jpn" -> "ja";
            case "kr", "kor" -> "ko";
            default -> lower;
        };
    }

    private static String mapBaidu(String lang) {
        return switch (lang) {
            case "zh-TW" -> "cht";
            case "ja" -> "jp";
            case "ko" -> "kor";
            default -> lang;
        };
    }

    private static String mapYoudao(String lang) {
        return switch (lang) {
            case "zh" -> "zh-CHS";
            case "zh-TW" -> "zh-CHT";
            default -> lang;
        };
    }

    private static String mapGoogle(String lang) {
        return switch (lang) {
            case "zh" -> "zh-CN";
            default -> lang;
        };
    }

    private static String mapAzure(String lang) {
        return switch (lang) {
            case "zh" -> "zh-Hans";
            case "zh-TW" -> "zh-Hant";
            default -> lang;
        };
    }

    private static String mapDeepL(String lang, boolean source) {
        if ("auto".equals(lang) && source) {
            return "";
        }
        return switch (lang) {
            case "zh" -> "ZH";
            case "zh-TW" -> "ZH-HANT";
            case "en" -> source ? "EN" : "EN-US";
            default -> lang.toUpperCase(Locale.ROOT);
        };
    }
}
