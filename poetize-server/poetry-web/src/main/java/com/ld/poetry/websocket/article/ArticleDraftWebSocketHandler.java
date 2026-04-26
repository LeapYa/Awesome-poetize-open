package com.ld.poetry.websocket.article;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class ArticleDraftWebSocketHandler extends TextWebSocketHandler {

    private final Map<String, Set<WebSocketSession>> draftSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String draftId = (String) session.getAttributes().get("draftId");
        if (!StringUtils.hasText(draftId)) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        draftSessions.computeIfAbsent(draftId, key -> ConcurrentHashMap.newKeySet()).add(session);
        broadcastSystemState(draftId, "awareness", session, true);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String draftId = (String) session.getAttributes().get("draftId");
        if (!StringUtils.hasText(draftId)) {
            return;
        }
        String payload = message.getPayload();
        if (!StringUtils.hasText(payload)) {
            return;
        }
        payload = enrichAwarenessPayload(session, draftId, payload);
        broadcast(draftId, payload, session);
    }

    private String enrichAwarenessPayload(WebSocketSession session, String draftId, String payload) {
        try {
            JSONObject jsonObject = JSON.parseObject(payload);
            if (!"awareness".equals(jsonObject.getString("type"))) {
                return payload;
            }
            if (!"editing".equals(jsonObject.getString("mode"))) {
                return payload;
            }
            jsonObject.put("draftId", draftId);
            jsonObject.put("userId", session.getAttributes().get("userId"));
            jsonObject.put("username", session.getAttributes().get("username"));
            return jsonObject.toJSONString();
        } catch (Exception exception) {
            return payload;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        removeSession(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("草稿WebSocket传输异常: {}", exception.getMessage());
        removeSession(session);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private void removeSession(WebSocketSession session) throws IOException {
        String draftId = (String) session.getAttributes().get("draftId");
        if (!StringUtils.hasText(draftId)) {
            return;
        }
        Set<WebSocketSession> sessions = draftSessions.get(draftId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                draftSessions.remove(draftId);
            }
        }
        broadcastSystemState(draftId, "awareness", session, false);
    }

    private void broadcastSystemState(String draftId, String type, WebSocketSession source, boolean joined) throws IOException {
        List<Map<String, Object>> onlineUsers = getOnlineUsers(draftId);
        Map<String, Object> payload = Map.of(
                "type", type,
                "joined", joined,
                "draftId", draftId,
                "userId", source.getAttributes().get("userId"),
                "username", source.getAttributes().get("username"),
                "onlineCount", onlineUsers.size(),
                "onlineUsers", onlineUsers
        );
        broadcast(draftId, JSON.toJSONString(payload), null);
    }

    private List<Map<String, Object>> getOnlineUsers(String draftId) {
        Set<WebSocketSession> sessions = draftSessions.get(draftId);
        if (sessions == null || sessions.isEmpty()) {
            return List.of();
        }
        Map<Object, Map<String, Object>> uniqueUsers = new LinkedHashMap<>();
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                continue;
            }
            Object userId = session.getAttributes().get("userId");
            if (userId == null) {
                continue;
            }
            uniqueUsers.putIfAbsent(userId, Map.of(
                    "userId", userId,
                    "username", String.valueOf(session.getAttributes().get("username"))
            ));
        }
        return new ArrayList<>(uniqueUsers.values());
    }

    private void broadcast(String draftId, String payload, WebSocketSession excludeSession) throws IOException {
        Set<WebSocketSession> sessions = draftSessions.get(draftId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        TextMessage textMessage = new TextMessage(payload);
        for (WebSocketSession target : sessions) {
            if (excludeSession != null && target.getId().equals(excludeSession.getId())) {
                continue;
            }
            if (!target.isOpen()) {
                continue;
            }
            target.sendMessage(textMessage);
        }
    }
}
