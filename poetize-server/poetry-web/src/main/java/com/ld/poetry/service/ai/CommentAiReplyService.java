package com.ld.poetry.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import tools.jackson.databind.json.JsonMapper;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.dao.ArticleMapper;
import com.ld.poetry.dao.LabelMapper;
import com.ld.poetry.dao.SortMapper;
import com.ld.poetry.dao.UserMapper;
import com.ld.poetry.entity.Article;
import com.ld.poetry.entity.Comment;
import com.ld.poetry.entity.Label;
import com.ld.poetry.entity.Sort;
import com.ld.poetry.entity.SysAiConfig;
import com.ld.poetry.entity.User;
import com.ld.poetry.entity.WebInfo;
import com.ld.poetry.enums.CommentTypeEnum;
import com.ld.poetry.event.CommentPublishedEvent;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.service.CommentService;
import com.ld.poetry.service.SysAiConfigService;
import com.ld.poetry.service.AiSkillService;
import com.ld.poetry.entity.AiSkill;
import com.ld.poetry.service.ai.rag.RagTextUtils;
import com.ld.poetry.vo.CommentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.knuddels.jtokkit.api.IntArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentAiReplyService {

    // 默认 maxInputTokens
    private static final int DEFAULT_MAX_INPUT_TOKENS = 128 * 1024; // 128K
    // 回退 token 预算（当系统提示词过大时保底）
    private static final int FALLBACK_TOKEN_BUDGET = 1024;

    private final SysAiConfigService sysAiConfigService;
    private final AiChatService aiChatService;
    private final CommentService commentService;
    private final CacheService cacheService;
    private final ArticleMapper articleMapper;
    private final SortMapper sortMapper;
    private final LabelMapper labelMapper;
    private final UserMapper userMapper;
    private final AiSkillService aiSkillService;

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Async
    @EventListener
    public void handleCommentPublished(CommentPublishedEvent event) {
        try {
            if (event == null || event.commentId() == null || !StringUtils.hasText(event.commentContent())) {
                return;
            }
            if (isAiReply(event.commentInfo())) {
                return;
            }

            SysAiConfig config = sysAiConfigService.getAiChatConfigInternal("default");
            if (!isRunnable(config)) {
                return;
            }

            String botName = resolveBotName(config);
            if (!containsMention(event.commentContent(), botName)) {
                return;
            }

            WebInfo webInfo = cacheService.getCachedWebInfo();
            int maxInputTokens = resolveMaxInputTokens(config);
            String userQuestion = stripMention(event.commentContent(), botName);
            String commentSkillDocument = resolveCommentSkillDocument(config, botName, webInfo);
            AiSkillDocument loadedSkill = AiSkillDocumentLoader.load(commentSkillDocument);
            // 动态测量系统提示词开销，余量留 10% 覆盖输出和工具调用
            int overheadTokens = measureSystemOverhead(userQuestion, loadedSkill, config);
            int contextBudget = (int) ((maxInputTokens - overheadTokens) * 0.90);
            if (contextBudget <= 0) {
                contextBudget = FALLBACK_TOKEN_BUDGET;
            }
            User commenter = event.userId() != null ? userMapper.selectById(event.userId()) : null;
            String commenterName = (commenter != null && StringUtils.hasText(commenter.getUsername()))
                    ? commenter.getUsername() : ("用户" + event.userId());
            Comment comment = event.commentId() != null ? commentService.getById(event.commentId()) : null;
            String commentTime = (comment != null && comment.getCreateTime() != null)
                    ? comment.getCreateTime().toString() : "未知时间";
            Map<String, Object> pageContext = buildPageContext(event, webInfo, contextBudget, commenterName, commentTime);

            // 构造调用上下文：评论事件触发 AI 回复，@Async 线程无法获取 HTTP 上下文，
            // 评论者身份与 IP/归属地（发布线程已捕获，随事件传入）一并带入，
            // 供审计日志与评论回复的环境注入使用
            AiChatService.AiCallContext commentCallCtx = AiChatService.AiCallContext.of(
                    event.userId(), commenterName,
                    StringUtils.hasText(event.commenterIp()) ? event.commenterIp() : null,
                    StringUtils.hasText(event.commenterLocation()) ? event.commenterLocation() : null);

            String answer = aiChatService.generateCommentReply(
                    userQuestion,
                    "comment:" + event.commentId(),
                    "comment-user:" + event.userId(),
                    pageContext,
                    loadedSkill,
                    commentCallCtx);

            if (!StringUtils.hasText(answer)) {
                log.warn("AI评论回复为空，跳过保存: commentId={}", event.commentId());
                return;
            }

            CommentVO reply = new CommentVO();
            reply.setSource(event.source());
            reply.setType(event.type());
            reply.setParentCommentId(event.commentId());
            reply.setParentUserId(event.userId());
            reply.setFloorCommentId(resolveReplyFloorCommentId(event));
            reply.setCommentContent(answer);
            reply.setCommentInfo(objectMapper.writeValueAsString(Map.of(
                    "aiReply", true,
                    "botName", botName)));

            PoetryResult<Comment> result = commentService.saveAiReplyComment(reply);
            if (!result.isSuccess()) {
                log.warn("保存AI评论回复失败: commentId={}, message={}", event.commentId(), result.getMessage());
            }
        } catch (Exception e) {
            log.error("AI评论回复生成失败: commentId={}, source={}, type={}, error={}",
                    event != null ? event.commentId() : null,
                    event != null ? event.source() : null,
                    event != null ? event.type() : null,
                    e.getMessage(),
                    e);
        }
    }

    private Map<String, Object> buildPageContext(CommentPublishedEvent event, WebInfo webInfo, int tokenBudget,
            String commenterName, String commentTime) {
        Map<String, Object> pageContext = new LinkedHashMap<>();
        String title = resolvePageTitle(event);
        pageContext.put("type", resolvePageType(event.type()));
        pageContext.put("source", event.source());
        pageContext.put("triggerCommentId", event.commentId());
        pageContext.put("title", title);
        pageContext.put("content", buildContextText(event, webInfo, tokenBudget, commenterName, commentTime));
        return pageContext;
    }

    private String buildContextText(CommentPublishedEvent event, WebInfo webInfo, int tokenBudget,
            String commenterName, String commentTime) {
        StringBuilder builder = new StringBuilder();
        appendSiteContext(builder, webInfo);

        // 预算分配：55% 文章 + 25% 楼层树 + 10% 线程（系统开销和 10% 安全余量已在调用方动态扣减）
        // 全局近期评论由 AI 通过 getRecentComments tool 按需获取
        int articleBudget = (int) (tokenBudget * 0.55);
        int floorBudget = (int) (tokenBudget * 0.25);
        int threadBudget = (int) (tokenBudget * 0.10);

        boolean isAuthor = false;
        if (CommentTypeEnum.COMMENT_TYPE_ARTICLE.getCode().equals(event.type())) {
            appendArticleContext(builder, event.source(), articleBudget);
            // 检查评论者是否就是文章作者
            if (event.userId() != null && event.source() != null) {
                Article article = articleMapper.selectById(event.source());
                if (article != null && event.userId().equals(article.getUserId())) {
                    isAuthor = true;
                }
            }
        } else {
            builder.append("评论区类型：").append(resolvePageType(event.type())).append("\n");
        }

        appendCommentThreadContext(builder, event, threadBudget, commenterName, isAuthor, commentTime);
        appendFloorConversationContext(builder, event, floorBudget);
        return builder.toString().trim();
    }

    private void appendSiteContext(StringBuilder builder, WebInfo webInfo) {
        if (webInfo == null) {
            return;
        }
        builder.append("网站信息：\n");
        if (StringUtils.hasText(webInfo.getWebName())) {
            builder.append("- 网站名称：").append(webInfo.getWebName()).append("\n");
        }
        if (StringUtils.hasText(webInfo.getWebTitle())) {
            builder.append("- 网站说明：").append(webInfo.getWebTitle()).append("\n");
        }
        if (StringUtils.hasText(webInfo.getSiteAddress())) {
            builder.append("- 网站地址：").append(webInfo.getSiteAddress()).append("\n");
        }
        builder.append("\n");
    }

    private void appendArticleContext(StringBuilder builder, Integer articleId, int budget) {
        Article article = articleId != null ? articleMapper.selectById(articleId) : null;
        if (article == null) {
            builder.append("文章上下文：未找到文章信息。\n\n");
            return;
        }

        builder.append("文章上下文：\n");
        builder.append("- 文章ID：").append(article.getId()).append("\n");
        if (StringUtils.hasText(article.getArticleTitle())) {
            builder.append("- 标题：").append(article.getArticleTitle()).append("\n");
        }

        // 文章作者感知
        if (article.getUserId() != null) {
            User author = userMapper.selectById(article.getUserId());
            if (author != null && StringUtils.hasText(author.getUsername())) {
                builder.append("- 文章作者：").append(author.getUsername()).append("\n");
            }
        }

        Sort sort = article.getSortId() != null ? sortMapper.selectById(article.getSortId()) : null;
        if (sort != null && StringUtils.hasText(sort.getSortName())) {
            builder.append("- 分类：").append(sort.getSortName()).append("\n");
        }
        Label label = article.getLabelId() != null ? labelMapper.selectById(article.getLabelId()) : null;
        if (label != null && StringUtils.hasText(label.getLabelName())) {
            builder.append("- 标签：").append(label.getLabelName()).append("\n");
        }
        if (article.getCreateTime() != null) {
            builder.append("- 创建时间：").append(article.getCreateTime().toString()).append("\n");
        }
        if (article.getUpdateTime() != null) {
            builder.append("- 更新时间：").append(article.getUpdateTime().toString()).append("\n");
        }
        if (StringUtils.hasText(article.getSummary())) {
            builder.append("- 摘要：").append(clipByTokens(RagTextUtils.normalize(article.getSummary()), Math.min(budget / 4, 400))).append("\n");
        }
        if (StringUtils.hasText(article.getArticleContent())) {
            builder.append("- 正文片段：")
                    .append(clipByTokens(RagTextUtils.normalize(article.getArticleContent()), budget))
                    .append("\n");
        }
        builder.append("\n");
    }

    private void appendCommentThreadContext(StringBuilder builder, CommentPublishedEvent event, int budget,
            String commenterName, boolean isAuthor, String commentTime) {
        builder.append("当前评论上下文：\n");
        builder.append("- 评论者用户名：").append(commenterName).append("\n");
        builder.append("- 评论时间：").append(commentTime).append("\n");
        if (isAuthor) {
            builder.append("- 注意：该评论者就是文章作者！\n");
        }
        if (event.parentCommentId() != null && !event.parentCommentId().equals(CommonConst.FIRST_COMMENT)) {
            Comment parent = commentService.getById(event.parentCommentId());
            if (parent != null && StringUtils.hasText(parent.getCommentContent())) {
                builder.append("- 被回复评论：")
                        .append(clipByTokens(RagTextUtils.normalize(parent.getCommentContent()), budget / 2))
                        .append("\n");
            }
        }

        if (event.floorCommentId() != null && !event.floorCommentId().equals(event.parentCommentId())) {
            Comment floor = commentService.getById(event.floorCommentId());
            if (floor != null && StringUtils.hasText(floor.getCommentContent())) {
                builder.append("- 所属楼层评论：")
                        .append(clipByTokens(RagTextUtils.normalize(floor.getCommentContent()), budget / 2))
                        .append("\n");
            }
        }

        builder.append("- 触发 @ 的用户评论：")
                .append(clipByTokens(RagTextUtils.normalize(event.commentContent()), budget))
                .append("\n");
    }

    /**
     * 完整楼层对话树上下文（深度优先）。
     * 通过 parentCommentId 重建嵌套树结构，按深度优先遍历，
     * 每次递归先处理当前节点的所有子节点再进入下一层，
     * 让 LLM 看到真实的嵌套对话脉络，而非打散的时间线。
     */
    private void appendFloorConversationContext(StringBuilder builder, CommentPublishedEvent event, int budget) {
        Integer floorId = resolveEffectiveFloorId(event);
        if (floorId == null) {
            return;
        }
        List<Comment> all = commentService.list(new QueryWrapper<Comment>()
                .and(wrapper -> wrapper.eq("id", floorId).or().eq("floor_comment_id", floorId))
                .orderByAsc("create_time"));
        if (all == null || all.isEmpty()) {
            return;
        }
        // 批量加载用户名
        Set<Integer> userIds = all.stream()
                .map(Comment::getUserId)
                .filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Integer, String> userNames = loadUserNames(userIds);

        // 构建 parentCommentId → children 映射
        java.util.Map<Integer, java.util.List<Comment>> childrenMap = new java.util.HashMap<>();
        for (Comment c : all) {
            Integer parentId = c.getParentCommentId() != null && c.getParentCommentId() > 0
                    ? c.getParentCommentId()
                    : CommonConst.FIRST_COMMENT;
            childrenMap.computeIfAbsent(parentId, k -> new java.util.ArrayList<>()).add(c);
        }
        // 对每个父节点下的子节点按创建时间排序（保证回复顺序）
        for (java.util.List<Comment> siblings : childrenMap.values()) {
            siblings.sort(java.util.Comparator.comparing(Comment::getCreateTime));
        }

        // 找到楼层根节点（parentCommentId == 0 且该评论本身是楼层根）
        Comment root = all.stream()
                .filter(c -> (c.getParentCommentId() == null || c.getParentCommentId().equals(CommonConst.FIRST_COMMENT))
                        && c.getId().equals(floorId))
                .findFirst()
                .orElse(null);
        if (root == null) {
            // 回退：取第一条作为根
            root = all.get(0);
        }

        builder.append("当前楼层完整对话树（深度优先嵌套结构）：\n");
        int[] remaining = {budget};
        if (remaining[0] <= 0) {
            return;
        }
        appendCommentNode(builder, root, userNames, childrenMap, 0, remaining);
        builder.append("\n");
    }

    /**
     * 深度优先递归输出评论节点及其所有子回复。
     */
    private void appendCommentNode(StringBuilder builder, Comment node, Map<Integer, String> userNames,
            java.util.Map<Integer, java.util.List<Comment>> childrenMap, int depth, int[] remaining) {
        if (remaining[0] <= 0 || node == null) {
            return;
        }
        // 缩进表示嵌套层级
        String indent = depth == 0 ? "" : "  ".repeat(Math.min(depth, 8));
        String name = resolveUserName(node.getUserId(), userNames);
        String content = RagTextUtils.normalize(node.getCommentContent());

        String line;
        if (node.getParentUserId() != null && node.getParentUserId() > 0
                && !node.getParentUserId().equals(node.getUserId())) {
            String parentName = resolveUserName(node.getParentUserId(), userNames);
            line = indent + name + " 回复 " + parentName + ": " + content;
        } else {
            line = indent + name + ": " + content;
        }

        String clipped = clipByTokens(line, Math.min(remaining[0], 400));
        if (clipped.length() <= 0) {
            return;
        }
        builder.append(clipped).append("\n");
        remaining[0] -= AiTokenEstimator.countTokens(clipped);

        // 递归处理子节点
        java.util.List<Comment> children = childrenMap.get(node.getId());
        if (children != null) {
            for (Comment child : children) {
                appendCommentNode(builder, child, userNames, childrenMap, depth + 1, remaining);
            }
        }
    }

    /**
     * 确定有效的楼层 ID（触发评论所属的楼层）。
     */
    private Integer resolveEffectiveFloorId(CommentPublishedEvent event) {
        if (event.floorCommentId() != null && event.floorCommentId() > 0) {
            return event.floorCommentId();
        }
        // 如果是顶级评论自身，则把自己作为楼层根
        if (event.parentCommentId() == null || event.parentCommentId().equals(CommonConst.FIRST_COMMENT)) {
            return event.commentId();
        }
        return null;
    }

    /**
     * 批量加载用户昵称映射。
     */
    private Map<Integer, String> loadUserNames(Set<Integer> userIds) {
        Map<Integer, String> result = new LinkedHashMap<>();
        if (userIds.isEmpty()) {
            return result;
        }
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
        if (userId == null) {
            return "匿名用户";
        }
        String name = userNames.get(userId);
        return StringUtils.hasText(name) ? name : ("用户" + userId);
    }

    /**
     * 动态测量系统提示词 Token 开销（Skill + 固定指令 + 用户问题 + 自定义指令）。
     * 固定开销估算：反泄露指令 ~800 + 工具摘要 ~1200 + Skill 元数据 ~200 + 上下文头部 ~300 ≈ 2500 tokens。
     */
    private int measureSystemOverhead(String userQuestion, AiSkillDocument skill, SysAiConfig config) {
        int skillTokens = (skill != null && skill.hasBody()) ? AiTokenEstimator.countTokens(skill.body()) : 0;
        int questionTokens = StringUtils.hasText(userQuestion) ? AiTokenEstimator.countTokens(userQuestion) : 0;
        int customTokens = (config != null && StringUtils.hasText(config.getCustomInstructions()))
                ? AiTokenEstimator.countTokens(config.getCustomInstructions()) : 0;
        int fixedOverhead = 2500; // 反泄露指令 + 工具摘要 + Skill 元数据 + 上下文头部
        return skillTokens + questionTokens + customTokens + fixedOverhead;
    }

    /**
     * 从配置读取 maxInputTokens，未填时使用默认 128K。
     */
    private int resolveMaxInputTokens(SysAiConfig config) {
        if (config != null && config.getMaxInputTokens() != null && config.getMaxInputTokens() > 0) {
            return config.getMaxInputTokens();
        }
        return DEFAULT_MAX_INPUT_TOKENS;
    }

    /**
     * 解析评论场景的 Skill 文档。
     * <p>
     * 优先从 {@code sys_ai_skill} 表查询评论场景优先级最高的启用 Skill
     * （按 sortOrder 升序、id 降序）；若无启用 Skill，则回退到
     * {@link AiCommentSkillDefaults} 的默认文档（兼容旧 {@code extraConfig.commentSkill}）。
     * 最后统一渲染占位符。
     */
    private String resolveCommentSkillDocument(SysAiConfig config, String botName, WebInfo webInfo) {
        String rawDocument;
        try {
            AiSkill enabledSkill = aiSkillService.getFirstEnabledSkillForScene(AiSkill.SCENE_COMMENT);
            if (enabledSkill != null && StringUtils.hasText(enabledSkill.getSkillContent())) {
                rawDocument = enabledSkill.getSkillContent();
            } else {
                rawDocument = AiCommentSkillDefaults.resolveCommentSkillDocument(config, objectMapper);
            }
        } catch (Exception e) {
            log.warn("查询评论场景启用 Skill 失败，回退到默认 Skill: {}", e.getMessage());
            rawDocument = AiCommentSkillDefaults.resolveCommentSkillDocument(config, objectMapper);
        }
        return AiCommentSkillDefaults.renderPlaceholders(rawDocument, botName, webInfo);
    }

    private String resolvePageTitle(CommentPublishedEvent event) {
        if (CommentTypeEnum.COMMENT_TYPE_ARTICLE.getCode().equals(event.type()) && event.source() != null) {
            Article article = articleMapper.selectById(event.source());
            if (article != null && StringUtils.hasText(article.getArticleTitle())) {
                return article.getArticleTitle();
            }
        }
        return switch (event.type() != null ? event.type() : "") {
            case "message" -> "留言板";
            case "love" -> "表白墙";
            default -> "评论区";
        };
    }

    private String resolvePageType(String type) {
        if (CommentTypeEnum.COMMENT_TYPE_ARTICLE.getCode().equals(type)) {
            return "article_comment";
        }
        if ("message".equals(type)) {
            return "message_comment";
        }
        if ("love".equals(type)) {
            return "love_comment";
        }
        return "comment";
    }

    private Integer resolveReplyFloorCommentId(CommentPublishedEvent event) {
        if (event.floorCommentId() != null) {
            return event.floorCommentId();
        }
        if (event.parentCommentId() == null || event.parentCommentId().equals(CommonConst.FIRST_COMMENT)) {
            return event.commentId();
        }
        return event.parentCommentId();
    }

    private boolean isRunnable(SysAiConfig config) {
        return config != null
                && Boolean.TRUE.equals(config.getEnabled())
                && StringUtils.hasText(config.getProvider())
                && StringUtils.hasText(config.getApiKey())
                && StringUtils.hasText(config.getModel());
    }

    private String resolveBotName(SysAiConfig config) {
        return config != null && StringUtils.hasText(config.getChatName()) ? config.getChatName() : "AI助手";
    }

    private boolean containsMention(String content, String botName) {
        return StringUtils.hasText(content)
                && StringUtils.hasText(botName)
                && content.contains("@" + botName);
    }

    private String stripMention(String content, String botName) {
        if (!StringUtils.hasText(content)) {
            return "请回复上面这条评论。";
        }
        String stripped = content.replace("@" + botName, "").trim();
        return StringUtils.hasText(stripped) ? stripped : "请回复上面这条评论。";
    }

    private boolean isAiReply(String commentInfo) {
        if (!StringUtils.hasText(commentInfo)) {
            return false;
        }
        String normalized = commentInfo.replaceAll("\\s+", "");
        return normalized.contains("\"aiReply\":true") || normalized.contains("\"ai_reply\":true");
    }

    /**
     * 基于 jtokkit CL100K_BASE 编码的 Token 感知截断。
     * 逐 token 累积，超过 maxTokens 后截断并追加省略标记。
     */
    private String clipByTokens(String text, int maxTokens) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        int tokenCount = AiTokenEstimator.countTokens(text);
        if (tokenCount <= maxTokens) {
            return text;
        }
        IntArrayList tokens = AiTokenEstimator.getEncoding().encode(text);
        if (tokens.size() <= maxTokens) {
            return text;
        }
        // 构建截断后的 token 序列（IntArrayList 不支持直接 subList 回传 decode）
        IntArrayList truncated = new IntArrayList(maxTokens);
        for (int i = 0; i < maxTokens; i++) {
            truncated.add(tokens.get(i));
        }
        return AiTokenEstimator.getEncoding().decode(truncated) + "...[已截断]";
    }
}
