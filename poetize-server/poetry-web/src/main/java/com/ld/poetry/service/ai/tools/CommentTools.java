package com.ld.poetry.service.ai.tools;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.dao.UserMapper;
import com.ld.poetry.entity.Comment;
import com.ld.poetry.entity.User;
import com.ld.poetry.service.CommentService;
import com.ld.poetry.service.SysAiConfigService;
import com.ld.poetry.service.ai.rag.RagTextUtils;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 评论区相关 @Tool 工具。
 * 让 AI 按需调用获取评论数据，而非预加载到上下文中浪费 Token。
 */
@Service
public class CommentTools {

    private static final Logger logger = LoggerFactory.getLogger(CommentTools.class);

    private static final Encoding TOKEN_ENCODING = Encodings.newDefaultEncodingRegistry()
            .getEncoding(EncodingType.CL100K_BASE);
    // 工具调用单次最大输出 Token，防御大量评论时上下文溢出
    private static final int MAX_TOOL_OUTPUT_TOKENS = 16384; // 16K

    @Autowired
    private CommentService commentService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SysAiConfigService sysAiConfigService;

    /**
     * 分页获取当前页面的近期非 AI 评论，用于总结评论区趋势。
     * 返回总评论数、当前页范围、是否还有更多，AI 可传 offset 翻页。
     */
    @Tool(description = "分页获取页面的评论区概况，以楼层为单位展示深度优先嵌套对话树（带缩进表示回复层级）。返回总评论数（含AI回复拆解）、楼层数、当前页范围。当需要总结评论区、分析讨论趋势或了解大家看法时调用。可通过 offset 参数翻页获取更多楼层。")
    public String getRecentComments(
            @ToolParam(description = "评论来源ID（文章ID 或 留言板/表白墙固定值）") Integer source,
            @ToolParam(description = "评论类型，如 article_comment / message_comment / love_comment") String type,
            @ToolParam(description = "每页楼层数，默认10，最大20") int limit,
            @ToolParam(description = "跳过的楼层数，默认0，翻页时传 offset=上一次的 offset+limit") int offset,
            @ToolParam(description = "触发AI回复的评论ID（可选，来自页面上下文的triggerCommentId），传入后在树中标记为 >>> 以区分其他评论") Integer triggerCommentId) {
        try {
            if (limit <= 0) limit = 10;
            if (limit > 20) limit = 20;
            if (offset < 0) offset = 0;

            // 映射 page context type 到 DB 存储值（如 article_comment → article）
            String dbType = toDbType(type);

            // 查询当前 source/type 下所有评论（上限 500）
            List<Comment> all = commentService.list(new QueryWrapper<Comment>()
                    .eq("source", source)
                    .eq("type", dbType)
                    .orderByDesc("create_time")
                    .last("LIMIT 500"));
            if (all == null || all.isEmpty()) {
                return "暂无评论。";
            }

            int totalCount = all.size();
            int aiReplyCount = (int) all.stream().filter(c -> isAiReply(c.getCommentInfo())).count();
            int userCount = totalCount - aiReplyCount;

            // 分离楼层根评论（parent_comment_id = 0 的顶级评论），按时间倒序
            List<Comment> floors = all.stream()
                    .filter(c -> c.getParentCommentId() == null || c.getParentCommentId().equals(CommonConst.FIRST_COMMENT))
                    .sorted(Comparator.comparing(Comment::getCreateTime).reversed())
                    .toList();

            // 按 floor_comment_id 分组所有子回复
            Map<Integer, List<Comment>> repliesByFloor = all.stream()
                    .filter(c -> c.getFloorCommentId() != null && c.getFloorCommentId() > 0)
                    .collect(Collectors.groupingBy(Comment::getFloorCommentId));

            // 按楼层分页
            int floorCount = floors.size();
            int fromFloor = Math.min(offset, floorCount);
            int toFloor = Math.min(offset + limit, floorCount);
            if (fromFloor >= floorCount) {
                return "当前页无楼层（共 " + totalCount + " 条评论，" + floorCount + " 个楼层，offset=" + offset + " 已超出范围）。";
            }
            List<Comment> pageFloors = floors.subList(fromFloor, toFloor);
            boolean hasMore = toFloor < floorCount;

            // 加载所有相关用户名（楼层作者 + 子回复作者 + 父评论者）
            Set<Integer> allUserIds = new LinkedHashSet<>();
            for (Comment f : pageFloors) {
                if (f.getUserId() != null) allUserIds.add(f.getUserId());
                List<Comment> floorReplies = repliesByFloor.getOrDefault(f.getId(), List.of());
                for (Comment r : floorReplies) {
                    if (r.getUserId() != null) allUserIds.add(r.getUserId());
                    if (r.getParentUserId() != null && r.getParentUserId() > 0) allUserIds.add(r.getParentUserId());
                }
            }
            Map<Integer, String> userNames = loadUserNamesByIds(allUserIds);

            // 构建输出
            StringBuilder sb = new StringBuilder();
            sb.append("评论区概况：共 ").append(totalCount).append(" 条评论");
            if (aiReplyCount > 0) {
                sb.append("（含 ").append(aiReplyCount).append(" 条AI回复，").append(userCount).append(" 条用户评论）");
            }
            sb.append(" | ").append(floorCount).append(" 个楼层");
            sb.append(" | 当前：第 ").append(fromFloor + 1).append("-").append(toFloor).append(" 层");
            if (hasMore) {
                sb.append(" | 还有更多（可传 offset=").append(toFloor).append(" 获取下一页）");
            } else {
                sb.append(" | 已全部展示");
            }
            sb.append("\n\n");

            int floorNum = fromFloor + 1;
            int[] remainingTokens = {MAX_TOOL_OUTPUT_TOKENS};
            for (Comment floor : pageFloors) {
                if (remainingTokens[0] <= 0) {
                    sb.append("...[本页输出已达 token 上限，省略剩余楼层]\n");
                    break;
                }
                String floorName = resolveCommentName(floor, userNames);
                String floorContent = RagTextUtils.normalize(floor.getCommentContent());
                boolean floorIsAi = isAiReply(floor.getCommentInfo());
                String prefix = floorIsAi ? "[AI]" : "";
                boolean isTrigger = triggerCommentId != null && floor.getId().equals(triggerCommentId);
                String titleLine = "【楼层" + floorNum + "】" + (isTrigger ? ">>> " : "") + prefix + floorName + "：" + clipSafe(floorContent, 400);
                sb.append(titleLine).append("\n");
                remainingTokens[0] -= TOKEN_ENCODING.countTokens(titleLine);

                // 构建本楼层的完整嵌套对话树
                List<Comment> floorReplies = repliesByFloor.getOrDefault(floor.getId(), List.of());
                if (!floorReplies.isEmpty()) {
                    List<Comment> floorAll = new ArrayList<>();
                    floorAll.add(floor);
                    floorAll.addAll(floorReplies);

                    Map<Integer, List<Comment>> childrenMap = new HashMap<>();
                    for (Comment c : floorAll) {
                        Integer parentId = c.getParentCommentId() != null && c.getParentCommentId() > 0
                                ? c.getParentCommentId()
                                : CommonConst.FIRST_COMMENT;
                        childrenMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(c);
                    }
                    for (List<Comment> siblings : childrenMap.values()) {
                        siblings.sort(Comparator.comparing(Comment::getCreateTime));
                    }

                    // 构建 commentId → Comment 映射用于父节点 AI 身份判断
                    Map<Integer, Comment> allComments = new HashMap<>();
                    for (Comment c : floorAll) {
                        allComments.put(c.getId(), c);
                    }

                    // 深度优先输出子回复（根节点已作为标题输出）
                    List<Comment> rootChildren = childrenMap.getOrDefault(floor.getId(), List.of());
                    for (Comment child : rootChildren) {
                        buildNode(sb, child, userNames, childrenMap, 1, remainingTokens, triggerCommentId, allComments);
                    }
                }
                floorNum++;
                sb.append("\n");
            }
            String result = sb.toString();
            logger.info("getRecentComments 工具输出 ({} chars):\n{}", result.length(), result);
            return result;
        } catch (Exception e) {
            logger.warn("getRecentComments 工具调用失败: source={}, type={}, error={}", source, type, e.getMessage());
            return "获取评论失败：" + e.getMessage();
        }
    }

    /**
     * 获取某个楼层的完整对话树（深度优先）。
     * 用于理解楼层内的争论、讨论上下文。
     */
    @Tool(description = "获取指定楼层的完整嵌套对话树（深度优先结构，带缩进表示回复层级）。当需要理解楼层内的讨论脉络、'评评理'、分析争论时调用。")
    public String getFloorConversation(
            @ToolParam(description = "楼层根评论的ID") Integer floorCommentId) {
        try {
            if (floorCommentId == null || floorCommentId <= 0) {
                return "无效的楼层ID。";
            }
            List<Comment> all = commentService.list(new QueryWrapper<Comment>()
                    .and(wrapper -> wrapper.eq("id", floorCommentId).or().eq("floor_comment_id", floorCommentId))
                    .orderByAsc("create_time"));
            if (all == null || all.isEmpty()) {
                return "该楼层暂无评论。";
            }

            Map<Integer, String> userNames = loadUserNames(all);

            // 构建 commentId → Comment 映射
            Map<Integer, Comment> allComments = new HashMap<>();
            for (Comment c : all) {
                allComments.put(c.getId(), c);
            }

            // 构建 parentCommentId → children 映射
            Map<Integer, List<Comment>> childrenMap = new HashMap<>();
            for (Comment c : all) {
                Integer parentId = c.getParentCommentId() != null && c.getParentCommentId() > 0
                        ? c.getParentCommentId()
                        : CommonConst.FIRST_COMMENT;
                childrenMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(c);
            }
            for (List<Comment> siblings : childrenMap.values()) {
                siblings.sort(Comparator.comparing(Comment::getCreateTime));
            }

            // 找到楼层根节点
            Comment root = all.stream()
                    .filter(c -> (c.getParentCommentId() == null
                            || c.getParentCommentId().equals(CommonConst.FIRST_COMMENT))
                            && c.getId().equals(floorCommentId))
                    .findFirst()
                    .orElse(all.get(0));

            StringBuilder sb = new StringBuilder();
            int totalNodes = all.size();
            sb.append("楼层对话树（深度优先，共 ").append(totalNodes).append(" 个节点）：\n");
            int[] remaining = {MAX_TOOL_OUTPUT_TOKENS};
            buildNode(sb, root, userNames, childrenMap, 0, remaining, null, allComments);
            if (remaining[0] <= 0) {
                sb.append("[树过大已截断，共 ").append(totalNodes).append(" 个节点未全部展示，可重新调用获取完整树]\n");
            }
            return sb.toString();
        } catch (Exception e) {
            logger.warn("getFloorConversation 工具调用失败: floorCommentId={}, error={}", floorCommentId, e.getMessage());
            return "获取楼层对话失败：" + e.getMessage();
        }
    }

    private void buildNode(StringBuilder sb, Comment node, Map<Integer, String> userNames,
            Map<Integer, List<Comment>> childrenMap, int depth, int[] remaining, Integer triggerCommentId,
            Map<Integer, Comment> allComments) {
        if (node == null || remaining[0] <= 0) return;
        String indent = depth == 0 ? "" : "  ".repeat(Math.min(depth, 8));
        String name = resolveCommentName(node, userNames);
        String content = RagTextUtils.normalize(node.getCommentContent());
        boolean isTrigger = triggerCommentId != null && node.getId().equals(triggerCommentId);

        String line;
        if (node.getParentUserId() != null && node.getParentUserId() > 0
                && !node.getParentUserId().equals(node.getUserId())) {
            Comment parentComment = allComments != null ? allComments.get(node.getParentCommentId()) : null;
            String parentName = resolveCommentName(parentComment, userNames);
            line = (isTrigger ? ">>> " : "") + indent + name + " 回复 " + parentName + ": " + clipSafe(content, 400);
        } else {
            line = (isTrigger ? ">>> " : "") + indent + name + ": " + clipSafe(content, 400);
        }
        sb.append(line).append("\n");
        remaining[0] -= TOKEN_ENCODING.countTokens(line);

        if (remaining[0] <= 0) {
            sb.append("...[工具输出已达上限]\n");
            return;
        }

        List<Comment> children = childrenMap.get(node.getId());
        if (children != null) {
            for (Comment child : children) {
                buildNode(sb, child, userNames, childrenMap, depth + 1, remaining, triggerCommentId, allComments);
            }
        }
    }

    private String resolveCommentName(Comment c, Map<Integer, String> userNames) {
        if (c != null && isAiReply(c.getCommentInfo())) {
            return getBotName();
        }
        return resolveUserName(c != null ? c.getUserId() : null, userNames);
    }

    private Map<Integer, String> loadUserNames(List<Comment> comments) {
        Map<Integer, String> result = new LinkedHashMap<>();
        Set<Integer> userIds = comments.stream()
                .map(Comment::getUserId)
                .filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (userIds.isEmpty()) return result;
        List<User> users = userMapper.selectByIds(userIds);
        if (users != null) {
            for (User u : users) {
                if (u != null && StringUtils.hasText(u.getUsername())) {
                    result.put(u.getId(), u.getUsername());
                }
            }
        }
        return result;
    }

    private Map<Integer, String> loadUserNamesByIds(Set<Integer> userIds) {
        Map<Integer, String> result = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty()) return result;
        List<User> users = userMapper.selectByIds(userIds);
        if (users != null) {
            for (User u : users) {
                if (u != null && StringUtils.hasText(u.getUsername())) {
                    result.put(u.getId(), u.getUsername());
                }
            }
        }
        return result;
    }

    private String resolveUserName(Integer userId, Map<Integer, String> userNames) {
        if (userId == null) return "匿名用户";
        String name = userNames.get(userId);
        return StringUtils.hasText(name) ? name : ("用户" + userId);
    }

    private boolean isAiReply(String commentInfo) {
        if (!StringUtils.hasText(commentInfo)) return false;
        String normalized = commentInfo.replaceAll("\\s+", "");
        return normalized.contains("\"aiReply\":true") || normalized.contains("\"ai_reply\":true");
    }

    private String getBotName() {
        try {
            var config = sysAiConfigService.getAiChatConfigInternal("default");
            if (config != null && StringUtils.hasText(config.getChatName())) {
                return config.getChatName();
            }
        } catch (Exception ignored) {
        }
        return "AI助手";
    }

    /**
     * 将 page context 中的 type 值映射为 DB 存储值。
     * page context 使用 article_comment/message_comment/love_comment，
     * DB 存储 article/message/love。
     */
    private static String toDbType(String type) {
        if (type == null) return type;
        return switch (type) {
            case "article_comment" -> "article";
            case "message_comment" -> "message";
            case "love_comment" -> "love";
            default -> type;
        };
    }

    private String clipSafe(String text, int maxTokens) {
        if (!StringUtils.hasText(text)) return text;
        int tokenCount = TOKEN_ENCODING.countTokens(text);
        if (tokenCount <= maxTokens) return text;
        com.knuddels.jtokkit.api.IntArrayList tokens = TOKEN_ENCODING.encode(text);
        if (tokens.size() <= maxTokens) return text;
        com.knuddels.jtokkit.api.IntArrayList truncated = new com.knuddels.jtokkit.api.IntArrayList(maxTokens);
        for (int i = 0; i < maxTokens; i++) {
            truncated.add(tokens.get(i));
        }
        return TOKEN_ENCODING.decode(truncated) + "...[已截断]";
    }
}
