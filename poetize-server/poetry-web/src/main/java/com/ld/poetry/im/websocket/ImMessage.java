package com.ld.poetry.im.websocket;

import tools.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImMessage {

    private Integer messageType;

    private String content;

    private Integer fromId;

    private Integer toId;

    private Integer groupId;

    private String avatar;

    private String username;

    private Integer onlineCount;

    private String createTime;

    private static final JsonMapper objectMapper = JsonMapper.builder().build();

    public String toJsonString() {
        try {
            return objectMapper.writeValueAsString(this);
        } catch (Exception e) {
            log.error("ImMessage序列化失败: messageType={}, content={}, fromId={}, toId={}, groupId={}", 
                messageType, content != null ? content.substring(0, Math.min(50, content.length())) : null, 
                fromId, toId, groupId, e);
            // 返回一个基本的错误消息，而不是空对象
            return "{\"messageType\":0,\"content\":\"system error\"}";
        }
    }
}
