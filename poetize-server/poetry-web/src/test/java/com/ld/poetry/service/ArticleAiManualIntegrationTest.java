package com.ld.poetry.service;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.ld.poetry.config.AiRestClientConfig;
import com.ld.poetry.entity.SysAiConfig;
import com.ld.poetry.service.ai.ApiTranslationProviderRegistry;
import com.ld.poetry.service.ai.AiThinkingAdapterRegistry;
import com.ld.poetry.service.ai.BaiduTranslationProvider;
import com.ld.poetry.service.ai.DynamicChatClientFactory;
import com.ld.poetry.service.ai.LlmTranslationService;
import com.ld.poetry.service.impl.SysAiConfigServiceImpl;
import com.ld.poetry.service.impl.TranslationServiceImpl;
import com.ld.poetry.utils.AESCryptoUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = ArticleAiManualIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.main.lazy-initialization=true"
})
class ArticleAiManualIntegrationTest {

    @Autowired
    private SysAiConfigService sysAiConfigService;

    @Autowired
    private TranslationService translationService;

    @Test
    void translateArticleWithCurrentArticleAiConfig() {
        SysAiConfig config = sysAiConfigService.getArticleAiConfigInternal("default");
        assertNotNull(config, "未读取到 article_ai/default 配置");

        System.out.println("ARTICLE_AI_CONFIG: type=" + config.getTranslationType()
                + ", enabled=" + config.getEnabled()
                + ", source=" + config.getDefaultSourceLang()
                + ", target=" + config.getDefaultTargetLang());

        Map<String, String> result = translationService.translateArticleOnly(
                "OpenClaw 部署测试",
                "这是一条用于验证真实 AI 翻译链路的集成测试消息，请将标题和内容翻译为英文，并保持语义自然。",
                false,
                null);

        System.out.println("TRANSLATION_RESULT: " + result);

        assertNotNull(result, "翻译结果为空");
        assertFalse(result.isEmpty(), "翻译结果为空 Map");
        assertTrue(StringUtils.hasText(result.get("title")), "翻译标题为空");
        assertTrue(StringUtils.hasText(result.get("content")), "翻译内容为空");
        assertTrue(StringUtils.hasText(result.get("language")), "翻译语言为空");
    }

    @TestConfiguration
    @EnableAutoConfiguration
    @org.mybatis.spring.annotation.MapperScan({"com.ld.poetry.dao", "com.ld.poetry.im.http.dao"})
    @Import({
            AESCryptoUtil.class,
            AiRestClientConfig.class,
            AiThinkingAdapterRegistry.class,
            ApiTranslationProviderRegistry.class,
            DynamicChatClientFactory.class,
            LlmTranslationService.class,
            BaiduTranslationProvider.class,
            SysAiConfigServiceImpl.class,
            TranslationServiceImpl.class
    })
    static class TestApplication {

        @Bean
        RestTemplate restTemplate() {
            return new RestTemplate();
        }

        @Bean
        MybatisPlusInterceptor mybatisPlusInterceptor() {
            MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MARIADB));
            return interceptor;
        }

        @Bean
        SitemapService sitemapService() {
            return new SitemapService() {
                @Override
                public String generateSitemap() {
                    return "";
                }

                @Override
                public String generateSitemapDirect() {
                    return "";
                }

                @Override
                public void updateArticleSitemap(Integer articleId) {
                }

                @Override
                public void updateSitemapAndPush(String reason) {
                }

                @Override
                public void clearSitemapCache() {
                }

                @Override
                public List<com.ld.poetry.entity.Article> getVisibleArticles() {
                    return List.of();
                }

                @Override
                public String getSiteBaseUrl() {
                    return "";
                }

                @Override
                public Map<String, Object> pingSitemapToSearchEngines() {
                    return Map.of();
                }

                @Override
                public Map<String, Object> pingSitemapToSearchEnginesDirect() {
                    return Map.of();
                }

                @Override
                public boolean isSearchEnginePingEnabled() {
                    return false;
                }
            };
        }
    }
}
