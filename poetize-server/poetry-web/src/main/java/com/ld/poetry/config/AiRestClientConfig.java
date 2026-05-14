package com.ld.poetry.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.concurrent.TimeUnit;

/**
 * AI API 调用统一 RestClient 配置。
 * 所有手动构建的 OpenAiApi / AnthropicApi 注入此 RestClient.Builder，
 * 确保超时策略与业务配置一致。重试由 spring.ai.retry.* 属性驱动，
 * OpenAiApi.build() 内部会添加 RetryInterceptor。
 */
@Configuration
public class AiRestClientConfig {

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 60000;

    @Bean
    public AiRestClientBuilderFactory aiRestClientBuilderFactory() {
        return new AiRestClientBuilderFactory();
    }

    @Bean
    public RestClient.Builder aiRestClientBuilder(AiRestClientBuilderFactory factory) {
        return factory.create(null);
    }

    public static class AiRestClientBuilderFactory {

        public RestClient.Builder create(Integer readTimeoutSeconds) {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(DEFAULT_CONNECT_TIMEOUT_MS);
            requestFactory.setReadTimeout(resolveReadTimeoutMs(readTimeoutSeconds));
            return RestClient.builder().requestFactory(requestFactory);
        }

        private int resolveReadTimeoutMs(Integer readTimeoutSeconds) {
            if (readTimeoutSeconds == null || readTimeoutSeconds <= 0) {
                return DEFAULT_READ_TIMEOUT_MS;
            }
            long readTimeoutMs = TimeUnit.SECONDS.toMillis(readTimeoutSeconds.longValue());
            return (int) Math.min(readTimeoutMs, Integer.MAX_VALUE);
        }
    }
}
