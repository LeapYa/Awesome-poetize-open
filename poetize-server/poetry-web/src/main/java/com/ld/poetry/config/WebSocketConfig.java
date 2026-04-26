package com.ld.poetry.config;

import com.ld.poetry.im.websocket.ImWebSocketHandler;
import com.ld.poetry.im.websocket.ImWebSocketInterceptor;
import com.ld.poetry.websocket.article.ArticleDraftWebSocketHandler;
import com.ld.poetry.websocket.article.ArticleDraftWebSocketInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Spring WebSocket 配置
 * 替换原 t-io WebSocket 实现
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

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
