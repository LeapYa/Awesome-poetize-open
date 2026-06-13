package com.ld.poetry.im.websocket;

import com.ld.poetry.utils.JsonUtils;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.entity.User;
import com.ld.poetry.im.http.entity.ImChatGroupUser;
import com.ld.poetry.im.http.entity.ImChatLastRead;
import com.ld.poetry.im.http.entity.ImChatUserGroupMessage;
import com.ld.poetry.im.http.entity.ImChatUserMessage;
import com.ld.poetry.im.http.service.ImChatGroupUserService;
import com.ld.poetry.im.http.service.ImChatUserMessageService;
import com.ld.poetry.im.http.service.ImChatUserGroupMessageService;
import com.ld.poetry.im.http.service.ImChatLastReadService;
import com.ld.poetry.im.http.vo.LastMessageVO;
import com.ld.poetry.utils.CommonQuery;
import com.ld.poetry.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;

/**
 * WebSocket 消息处理器
 * 替换 t-io 的 ImWsMsgHandler
 */
@Component
@Slf4j
public class ImWebSocketHandler extends TextWebSocketHandler {

    // 限制IM并发查询数量，避免耗尽数据库连接池
    private static final int MAX_CONCURRENT_DB_QUERIES = 35;
    private final Semaphore dbQuerySemaphore = new Semaphore(MAX_CONCURRENT_DB_QUERIES);

    @Autowired
    private ImSessionManager sessionManager;

    @Autowired
    private ImChatGroupUserService imChatGroupUserService;

    @Autowired
    private ImChatUserMessageService imChatUserMessageService;

    @Autowired
    private ImChatUserGroupMessageService imChatUserGroupMessageService;

    @Autowired
    private ImChatLastReadService imChatLastReadService;

    @Autowired
    private MessageCache messageCache;

    @Autowired
    private CommonQuery commonQuery;

    /**
     * 连接建立成功后调用
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Integer userId = (Integer) session.getAttributes().get("userId");
        String username = (String) session.getAttributes().get("username");
        String userType = (String) session.getAttributes().get("userType");

        if (userId == null) {
            log.warn("WebSocket连接失败：用户ID为空");
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        // 支持多端登录，直接绑定

        sessionManager.bindUser(userId, session);
        log.info("[Multi-Device] WebSocket连接绑定成功：用户ID：{}, 用户名：{}, token类型：{}", userId, username, userType);

        // 处理未读的用户消息
        processUnreadMessages(session, userId);

        // 绑定用户所在的群组
        bindUserGroups(session, userId);

        // 推送聊天列表和未读数
        pushChatData(session, userId);

        // 延迟广播在线用户数
        broadcastOnlineCount(session, userId);
    }

    /**
     * 接收到文本消息时调用
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String text = message.getPayload();
        if (!StringUtils.hasText(text)) {
            return;
        }

        Integer fromUserId = (Integer) session.getAttributes().get("userId");
        if (fromUserId == null) {
            return;
        }

        try {
            ImMessage imMessage = JsonUtils.parseObject(text, ImMessage.class);
            String content = StringUtil.removeHtml(imMessage.getContent());
            if (!StringUtils.hasText(content)) {
                return;
            }
            imMessage.setContent(content);
            imMessage.setCreateTime(LocalDateTime.now().toString());

            String jsonString = imMessage.toJsonString();

            if (imMessage.getMessageType().intValue() == ImEnum.MESSAGE_TYPE_MSG_SINGLE.getCode()) {
                // 单聊
                handleSingleMessage(session, imMessage, jsonString, fromUserId);
            } else if (imMessage.getMessageType().intValue() == ImEnum.MESSAGE_TYPE_MSG_GROUP.getCode()) {
                // 群聊
                handleGroupMessage(session, imMessage, jsonString, fromUserId);
            }
        } catch (Exception e) {
            log.error("解析消息失败：{}", e.getMessage());
        }
    }

    /**
     * 连接关闭时调用
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Integer userId = (Integer) session.getAttributes().get("userId");

        // 广播在线人数更新（在移除会话前执行）
        if (userId != null) {
            Set<String> groups = sessionManager.getUserGroups(session);
            if (groups != null) {
                for (String groupId : groups) {
                    // 当前用户还在群组中，需要减1
                    int onlineCount = Math.max(0, sessionManager.getGroupOnlineCount(groupId) - 1);

                    ImMessage onlineMsg = new ImMessage();
                    onlineMsg.setMessageType(CommonConst.ONLINE_COUNT_MESSAGE_TYPE);
                    onlineMsg.setOnlineCount(onlineCount);
                    onlineMsg.setGroupId(Integer.valueOf(groupId));

                    sessionManager.sendToGroupExclude(groupId, onlineMsg.toJsonString(), session);
                }
            }
        }

        sessionManager.removeSession(session);
        log.info("WebSocket连接关闭：用户ID：{}, 状态：{}", userId, status);
    }

    /**
     * 处理传输错误
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        Integer userId = (Integer) session.getAttributes().get("userId");
        log.error("WebSocket传输错误：用户ID：{}, 错误：{}", userId, exception.getMessage());
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 处理未读消息
     */
    private void processUnreadMessages(WebSocketSession session, Integer userId) {
        try {
            List<ImChatUserMessage> userMessages = imChatUserMessageService.lambdaQuery()
                    .eq(ImChatUserMessage::getToId, userId)
                    .eq(ImChatUserMessage::getMessageStatus, ImConfigConst.USER_MESSAGE_STATUS_FALSE)
                    .orderByAsc(ImChatUserMessage::getCreateTime)
                    .list();

            if (!CollectionUtils.isEmpty(userMessages)) {
                for (ImChatUserMessage userMessage : userMessages) {
                    ImMessage imMessage = new ImMessage();
                    imMessage.setContent(userMessage.getContent());
                    imMessage.setFromId(userMessage.getFromId());
                    imMessage.setToId(userMessage.getToId());
                    imMessage.setMessageType(ImEnum.MESSAGE_TYPE_MSG_SINGLE.getCode());

                    User friend = commonQuery.getUserForDisplay(userMessage.getFromId());
                    if (friend != null) {
                        imMessage.setAvatar(friend.getAvatar());
                    }

                    String jsonString = imMessage.toJsonString();

                    session.sendMessage(new TextMessage(jsonString));
                }
            }
        } catch (Exception e) {
            log.error("处理用户未读消息时发生错误 - userId: {}", userId, e);
        }
    }

    /**
     * 绑定用户群组
     */
    private void bindUserGroups(WebSocketSession session, Integer userId) {
        try {
            LambdaQueryChainWrapper<ImChatGroupUser> lambdaQuery = imChatGroupUserService.lambdaQuery();
            lambdaQuery.select(ImChatGroupUser::getGroupId);
            lambdaQuery.eq(ImChatGroupUser::getUserId, userId);
            lambdaQuery.in(ImChatGroupUser::getUserStatus,
                    ImConfigConst.GROUP_USER_STATUS_PASS, ImConfigConst.GROUP_USER_STATUS_SILENCE);

            List<ImChatGroupUser> groupUsers = lambdaQuery.list();
            if (!CollectionUtils.isEmpty(groupUsers)) {
                for (ImChatGroupUser groupUser : groupUsers) {
                    sessionManager.joinGroup(groupUser.getGroupId().toString(), session);
                }
            }
        } catch (Exception e) {
            log.error("绑定用户群组时发生错误 - userId: {}", userId, e);
        }
    }

    /**
     * 推送聊天数据
     */
    private void pushChatData(WebSocketSession session, Integer userId) {
        try (var scope = StructuredTaskScope.open()) {
            Subtask<List<Integer>> friendChatListTask = scope
                    .fork(() -> imChatLastReadService.getFriendChatList(userId));
            Subtask<List<Integer>> groupChatListTask = scope.fork(() -> imChatLastReadService.getGroupChatList(userId));
            Subtask<Map<Integer, Integer>> friendUnreadCountsTask = scope
                    .fork(() -> imChatLastReadService.getFriendUnreadCounts(userId));
            Subtask<Map<Integer, Integer>> groupUnreadCountsTask = scope
                    .fork(() -> imChatLastReadService.getGroupUnreadCounts(userId));

            scope.join();

            List<Integer> friendChatList = getTaskResult(friendChatListTask, new ArrayList<>());
            List<Integer> groupChatList = getTaskResult(groupChatListTask, new ArrayList<>());
            Map<Integer, Integer> friendUnreadCounts = getTaskResult(friendUnreadCountsTask, new HashMap<>());
            Map<Integer, Integer> groupUnreadCounts = getTaskResult(groupUnreadCountsTask, new HashMap<>());

            // 并行获取最后一条消息
            Map<String, LastMessageVO> friendLastMessages = new ConcurrentHashMap<>();
            Map<String, LastMessageVO> groupLastMessages = new ConcurrentHashMap<>();

            try (var msgScope = StructuredTaskScope.open()) {
                for (Integer friendId : friendChatList) {
                    msgScope.fork(() -> {
                        try {
                            dbQuerySemaphore.acquire();
                            try {
                                LastMessageVO lastMsg = imChatUserMessageService.getLastMessageWithFriend(userId,
                                        friendId);
                                if (lastMsg != null) {
                                    friendLastMessages.put(friendId.toString(), lastMsg);
                                }
                            } finally {
                                dbQuerySemaphore.release();
                            }
                        } catch (Exception e) {
                            log.error("获取好友 {} 的最后一条消息失败", friendId, e);
                        }
                        return null;
                    });
                }

                for (Integer groupId : groupChatList) {
                    msgScope.fork(() -> {
                        try {
                            dbQuerySemaphore.acquire();
                            try {
                                LastMessageVO lastMsg = imChatUserGroupMessageService.getLastGroupMessage(groupId);
                                if (lastMsg != null) {
                                    groupLastMessages.put(groupId.toString(), lastMsg);
                                }
                            } finally {
                                dbQuerySemaphore.release();
                            }
                        } catch (Exception e) {
                            log.error("获取群组 {} 的最后一条消息失败", groupId, e);
                        }
                        return null;
                    });
                }

                msgScope.join();
            }

            // 构造同步消息
            Map<String, Object> syncMessage = new HashMap<>();
            syncMessage.put("messageType", 5);
            syncMessage.put("friendChatList", friendChatList);
            syncMessage.put("groupChatList", groupChatList);
            syncMessage.put("friendUnreadCounts", friendUnreadCounts);
            syncMessage.put("groupUnreadCounts", groupUnreadCounts);
            syncMessage.put("friendLastMessages", friendLastMessages);
            syncMessage.put("groupLastMessages", groupLastMessages);

            String jsonString = JsonUtils.toJsonString(syncMessage);

            session.sendMessage(new TextMessage(jsonString));

        } catch (Exception e) {
            log.error("推送聊天数据失败 - userId: {}", userId, e);
        }
    }

    /**
     * 广播在线用户数
     */
    private void broadcastOnlineCount(WebSocketSession session, Integer userId) {
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(200); // 延迟等待旧连接关闭

                Set<String> groups = sessionManager.getUserGroups(session);
                if (groups != null) {
                    for (String groupId : groups) {
                        int onlineCount = sessionManager.getGroupOnlineCount(groupId);

                        ImMessage onlineMsg = new ImMessage();
                        onlineMsg.setMessageType(CommonConst.ONLINE_COUNT_MESSAGE_TYPE);
                        onlineMsg.setOnlineCount(onlineCount);
                        onlineMsg.setGroupId(Integer.valueOf(groupId));

                        sessionManager.sendToGroup(groupId, onlineMsg.toJsonString());
                    }
                }
            } catch (Exception e) {
                log.error("延迟广播在线用户数失败 - userId: {}", userId, e);
            }
        });
    }

    /**
     * 处理单聊消息
     */
    private void handleSingleMessage(WebSocketSession session, ImMessage imMessage, String jsonString,
            Integer fromUserId) {
        ImChatUserMessage userMessage = new ImChatUserMessage();
        userMessage.setFromId(imMessage.getFromId());
        userMessage.setToId(imMessage.getToId());
        userMessage.setContent(imMessage.getContent());
        userMessage.setCreateTime(LocalDateTime.now());

        // 自动取消隐藏
        imChatLastReadService.unhideChat(imMessage.getFromId(), ImChatLastRead.CHAT_TYPE_FRIEND, imMessage.getToId());
        imChatLastReadService.unhideChat(imMessage.getToId(), ImChatLastRead.CHAT_TYPE_FRIEND, imMessage.getFromId());

        boolean sent = sessionManager.sendToUser(imMessage.getToId(), jsonString);
        userMessage.setMessageStatus(
                sent ? ImConfigConst.USER_MESSAGE_STATUS_TRUE : ImConfigConst.USER_MESSAGE_STATUS_FALSE);

        messageCache.putUserMessage(userMessage);
        sessionManager.sendToUser(imMessage.getFromId(), jsonString);
    }

    /**
     * 处理群聊消息
     */
    private void handleGroupMessage(WebSocketSession session, ImMessage imMessage, String jsonString,
            Integer fromUserId) {
        ImChatUserGroupMessage groupMessage = new ImChatUserGroupMessage();
        groupMessage.setContent(imMessage.getContent());
        groupMessage.setFromId(imMessage.getFromId());
        groupMessage.setGroupId(imMessage.getGroupId());
        groupMessage.setCreateTime(LocalDateTime.now());
        messageCache.putGroupMessage(groupMessage);

        // 自动取消隐藏
        imChatLastReadService.unhideChat(imMessage.getFromId(), ImChatLastRead.CHAT_TYPE_GROUP, imMessage.getGroupId());

        sessionManager.sendToGroup(imMessage.getGroupId().toString(), jsonString);
    }

    /**
     * 安全获取任务结果
     */
    private <T> T getTaskResult(Subtask<T> task, T defaultValue) {
        if (task.state() == Subtask.State.SUCCESS && task.get() != null) {
            return task.get();
        }
        return defaultValue;
    }
}
