package com.ld.poetry.config;

import com.ld.poetry.im.websocket.ImWebSocketHandler;
import com.ld.poetry.im.websocket.ImWebSocketInterceptor;
import com.ld.poetry.websocket.article.ArticleDraftWebSocketHandler;
import com.ld.poetry.websocket.article.ArticleDraftWebSocketInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * Spring WebSocket 配置
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    /**
     * 调大 WebSocket 消息缓冲区。
     * Tomcat 默认文本消息缓冲约 8KB，草稿协同的 state_update 帧（整篇文章内容同步）
     * 超过该限制会触发 handleTransportError 并断开连接，导致协同同步失败。
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        int maxBufferSize = 512 * 1024;
        container.setMaxTextMessageBufferSize(maxBufferSize);
        container.setMaxBinaryMessageBufferSize(maxBufferSize);
        return container;
    }

    @Autowired(required = false)
    private ImWebSocketHandler imWebSocketHandler;

    @Autowired(required = false)
    private ImWebSocketInterceptor imWebSocketInterceptor;

    @Autowired
    private ArticleDraftWebSocketHandler articleDraftWebSocketHandler;

    @Autowired
    private ArticleDraftWebSocketInterceptor articleDraftWebSocketInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        if (imWebSocketHandler != null && imWebSocketInterceptor != null) {
            registry.addHandler(imWebSocketHandler, "/ws/im")
                    .addInterceptors(imWebSocketInterceptor)
                    .setAllowedOrigins("*");
        }

        registry.addHandler(articleDraftWebSocketHandler, "/ws/article-draft")
                .addInterceptors(articleDraftWebSocketInterceptor)
                .setAllowedOrigins("*");
    }
}
