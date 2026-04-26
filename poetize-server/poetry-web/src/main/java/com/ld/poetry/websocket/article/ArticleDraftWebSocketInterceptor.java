package com.ld.poetry.websocket.article;

import com.ld.poetry.entity.User;
import com.ld.poetry.service.ArticleDraftService;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.utils.CommonQuery;
import com.ld.poetry.utils.SecureTokenGenerator;
import com.ld.poetry.utils.TokenValidationUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
@Slf4j
public class ArticleDraftWebSocketInterceptor implements HandshakeInterceptor {

    @Autowired
    private CacheService cacheService;

    @Autowired
    private CommonQuery commonQuery;

    @Autowired
    private ArticleDraftService articleDraftService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return false;
        }

        String token = servletRequest.getServletRequest().getParameter("token");
        String draftId = servletRequest.getServletRequest().getParameter("draftId");
        if (!StringUtils.hasText(token) || !StringUtils.hasText(draftId)) {
            return false;
        }

        try {
            Integer userId = SecureTokenGenerator.validateWebSocketToken(token);
            if (userId == null) {
                log.warn("草稿WebSocket握手失败：token验证失败 - {}", TokenValidationUtil.getTokenPrefix(token));
                return false;
            }

            Integer cachedUserId = cacheService.getUserIdFromWebSocketSession(token);
            if (cachedUserId == null || !cachedUserId.equals(userId)) {
                log.warn("草稿WebSocket握手失败：token缓存失效 - userId={}", userId);
                return false;
            }

            User user = cacheService.getCachedUser(userId);
            if (user == null) {
                user = commonQuery.getUser(userId);
                if (user == null) {
                    return false;
                }
                cacheService.cacheUser(user);
            }

            boolean isBoss = user.getUserType() != null && user.getUserType() == 0;
            if (!articleDraftService.hasDraftAccess(userId, isBoss, draftId)) {
                log.warn("草稿WebSocket握手失败：无草稿权限 - userId={}, draftId={}", userId, draftId);
                return false;
            }

            attributes.put("userId", userId);
            attributes.put("username", user.getUsername());
            attributes.put("draftId", draftId);
            return true;
        } catch (Exception e) {
            log.error("草稿WebSocket握手异常", e);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
