package com.ld.poetry.service.ai.tools;

import com.ld.poetry.entity.AiSkill;
import com.ld.poetry.service.AiSkillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * Agent Skill 管理工具集（仅站长/管理员可用）。
 * <p>
 * 让站长和管理员在聊天对话中直接创建 / 更新 / 启停 / 删除 Skill，
 * 而不必进入后台管理面板。所有写操作都会校验当前用户身份，
 * 非管理员调用会返回权限拒绝提示，由 AI 转达给用户。
 * <p>
 * 该工具集仅在 {@code buildToolSpec} 判定当前用户为站长或管理员时注册，
 * 普通用户和匿名用户根本看不到这些工具。
 *
 * @author LeapYa
 * @since 2026-06-25
 */
@Service
public class SkillAdminTools {

    private static final Logger logger = LoggerFactory.getLogger(SkillAdminTools.class);

    private static final List<String> ALLOWED_SCENES = List.of(
            AiSkill.SCENE_COMMENT, AiSkill.SCENE_CHAT, AiSkill.SCENE_ARTICLE, AiSkill.SCENE_UNIVERSAL);

    @Autowired
    private AiSkillService aiSkillService;

    /**
     * 创建或更新一个 Skill。
     * <p>
     * 行为为 upsert：若 skill_key 已存在则更新同名 Skill 的内容与元数据，
     * 否则新建。AI 可在多轮对话中逐步细化 Skill 内容后再次调用以覆盖保存。
     *
     * @param skill_key  Skill 唯一标识，小写字母/数字/连字符，开头需为字母或数字
     * @param scene      适用场景：comment/chat/article/universal
     * @param description 一句话用途说明（frontmatter description）
     * @param body       Skill 指令正文（Markdown），将作为 AI 执行该 Skill 时的规范
     * @param version    可选，版本号，默认 1.0.0
     * @param author     可选，作者名
     * @param toolContext 工具上下文，用于校验当前用户身份
     * @return 创建/更新结果，含 skill_key、场景、是否新建
     */
    @Tool(description = "创建或更新一个 Agent Skill（仅站长/管理员可用）。"
            + "行为为 upsert：同名 skill_key 已存在则更新，否则新建。"
            + "AI 可在多轮对话中逐步细化内容后再次调用以覆盖保存。"
            + "调用前应已与用户确认 skill_key、scene、description 和 body。")
    public String create_skill(
            @ToolParam(description = "Skill 唯一标识，小写字母/数字/连字符组成，开头需为字母或数字，不超过64字符，例如 comment-reply") String skill_key,
            @ToolParam(description = "适用场景，取值：comment(评论)/chat(聊天)/article(文章)/universal(通用)") String scene,
            @ToolParam(description = "一句话用途说明，会写入 frontmatter description，AI 据此判断是否加载该 Skill") String description,
            @ToolParam(description = "Skill 指令正文（Markdown 格式），作为 AI 执行该 Skill 时的规范。不要包含 YAML frontmatter，只需正文部分") String body,
            @ToolParam(description = "版本号，可选，默认 1.0.0", required = false) String version,
            @ToolParam(description = "作者名，可选", required = false) String author,
            ToolContext toolContext) {
        try {
            String permissionError = requireAdmin(toolContext);
            if (permissionError != null) {
                return permissionError;
            }

            String validationError = validateSkillFields(skill_key, scene, description, body);
            if (validationError != null) {
                return validationError;
            }

            String content = buildSkillMarkdown(skill_key, scene, description, body, version, author);

            // 先判断是新建还是更新，用于返回信息
            boolean existsBefore = existsBySkillKey(skill_key);

            AiSkill saved = aiSkillService.installFromMarkdown(content, false);

            logger.info("AI 通过对话创建/更新 Skill: skill_key={}, scene={}, isNew={}, operator={}",
                    skill_key, scene, !existsBefore, resolveOperator(toolContext));

            return "Skill " + (existsBefore ? "更新" : "创建") + "成功！\n"
                    + "- skill_key: " + safe(saved.getSkillKey()) + "\n"
                    + "- 名称: " + safe(saved.getSkillName()) + "\n"
                    + "- 场景: " + safe(saved.getScene()) + "\n"
                    + "- 版本: " + safe(saved.getVersion()) + "\n"
                    + "- 状态: " + (Boolean.TRUE.equals(saved.getEnabled()) ? "已启用" : "已禁用") + "\n"
                    + (existsBefore ? "（已覆盖同名 Skill 的旧内容）" : "（启用后即生效，AI 会按意图自主加载）");
        } catch (IllegalArgumentException e) {
            logger.warn("create_skill 参数校验失败: skill_key={}, error={}", skill_key, e.getMessage());
            return "Skill 创建失败（参数不合法）：" + safe(e.getMessage());
        } catch (Exception e) {
            logger.error("create_skill 执行失败: skill_key={}, error={}", skill_key, e.getMessage(), e);
            return "Skill 创建失败：" + safe(e.getMessage());
        }
    }

    /**
     * 启用或禁用指定 Skill。
     *
     * @param skill_key Skill 唯一标识
     * @param toolContext 工具上下文，用于校验身份
     * @return 切换后的启用状态
     */
    @Tool(description = "切换指定 Skill 的启用/禁用状态（仅站长/管理员可用）。"
            + "禁用的 Skill 不会出现在 AI 的 Skill 索引中，也不会被加载。"
            + "调用一次切换一次状态，返回切换后的状态。")
    public String toggle_skill(
            @ToolParam(description = "Skill 唯一标识") String skill_key,
            ToolContext toolContext) {
        try {
            String permissionError = requireAdmin(toolContext);
            if (permissionError != null) {
                return permissionError;
            }
            if (!StringUtils.hasText(skill_key)) {
                return "skill_key 不能为空。";
            }
            AiSkill target = findBySkillKey(skill_key);
            if (target == null) {
                return "未找到 skill_key=\"" + skill_key + "\" 的 Skill。";
            }
            boolean success = aiSkillService.toggleEnabled(target.getId());
            if (!success) {
                return "切换状态失败，Skill 可能已不存在。";
            }
            AiSkill refreshed = aiSkillService.getSkill(target.getId());
            boolean nowEnabled = refreshed != null && Boolean.TRUE.equals(refreshed.getEnabled());
            logger.info("AI 通过对话切换 Skill 状态: skill_key={}, nowEnabled={}, operator={}",
                    skill_key, nowEnabled, resolveOperator(toolContext));
            return "Skill「" + safe(target.getSkillName()) + "」已"
                    + (nowEnabled ? "启用" : "禁用") + "。";
        } catch (Exception e) {
            logger.error("toggle_skill 执行失败: skill_key={}, error={}", skill_key, e.getMessage(), e);
            return "切换状态失败：" + safe(e.getMessage());
        }
    }

    /**
     * 删除指定 Skill（内置 Skill 不可删，由 service 层保护）。
     *
     * @param skill_key Skill 唯一标识
     * @param toolContext 工具上下文，用于校验身份
     * @return 删除结果
     */
    @Tool(description = "删除指定 Skill（仅站长/管理员可用，内置 Skill 不可删）。"
            + "删除后该 Skill 从索引中消失，关联的场景活跃绑定也会一并清除。"
            + "调用前应向用户确认删除意图。")
    public String delete_skill(
            @ToolParam(description = "Skill 唯一标识") String skill_key,
            ToolContext toolContext) {
        try {
            String permissionError = requireAdmin(toolContext);
            if (permissionError != null) {
                return permissionError;
            }
            if (!StringUtils.hasText(skill_key)) {
                return "skill_key 不能为空。";
            }
            AiSkill target = findBySkillKey(skill_key);
            if (target == null) {
                return "未找到 skill_key=\"" + skill_key + "\" 的 Skill，可能已被删除。";
            }
            boolean success = aiSkillService.deleteSkill(target.getId());
            if (!success) {
                return "删除失败，Skill 可能已不存在。";
            }
            logger.info("AI 通过对话删除 Skill: skill_key={}, operator={}", skill_key, resolveOperator(toolContext));
            return "Skill「" + safe(target.getSkillName()) + "」已删除。";
        } catch (IllegalArgumentException e) {
            return "删除失败：" + safe(e.getMessage());
        } catch (Exception e) {
            logger.error("delete_skill 执行失败: skill_key={}, error={}", skill_key, e.getMessage(), e);
            return "删除失败：" + safe(e.getMessage());
        }
    }

    // ========== 内部方法 ==========

    /**
     * 校验当前用户是否为站长/管理员。返回 null 表示通过，否则返回拒绝提示。
     */
    private String requireAdmin(ToolContext toolContext) {
        Map<String, Object> context = toolContext != null ? toolContext.getContext() : Map.of();
        Object isAdmin = context.get(com.ld.poetry.service.ai.ToolCallbackEventBridge.USER_IS_ADMIN_CONTEXT_KEY);
        if (!Boolean.TRUE.equals(isAdmin)) {
            return "权限不足：Skill 管理仅限站长或管理员操作。当前用户没有管理权限。";
        }
        return null;
    }

    private String resolveOperator(ToolContext toolContext) {
        if (toolContext == null) {
            return "unknown";
        }
        Map<String, Object> context = toolContext.getContext();
        Object userId = context.get(com.ld.poetry.service.ai.ToolCallbackEventBridge.USER_ID_CONTEXT_KEY);
        Object userName = context.get(com.ld.poetry.service.ai.ToolCallbackEventBridge.USER_NAME_CONTEXT_KEY);
        return "userId=" + userId + ", name=" + (userName != null ? userName : "");
    }

    private String validateSkillFields(String skillKey, String scene, String description, String body) {
        if (!StringUtils.hasText(skillKey)) {
            return "skill_key 不能为空。";
        }
        if (!skillKey.matches("^[a-z0-9][a-z0-9-]{0,63}$")) {
            return "skill_key 格式不合法：需为小写字母、数字和连字符组成，开头需为字母或数字，不超过64字符。";
        }
        if (!StringUtils.hasText(scene) || !ALLOWED_SCENES.contains(scene)) {
            return "scene 取值必须是 comment/chat/article/universal 之一。";
        }
        if (!StringUtils.hasText(description)) {
            return "description 不能为空，需一句话说明 Skill 用途。";
        }
        if (!StringUtils.hasText(body)) {
            return "body 不能为空，需提供 Skill 指令正文。";
        }
        if (body.getBytes().length > 64 * 1024) {
            return "Skill 正文超过 64KB 上限。";
        }
        return null;
    }

    /**
     * 拼装完整的 SKILL.md 文本（含 YAML frontmatter）。
     */
    private String buildSkillMarkdown(String skillKey, String scene, String description,
            String body, String version, String author) {
        String ver = StringUtils.hasText(version) ? version : "1.0.0";
        String desc = description.replace("\"", "'");
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("name: ").append(skillKey).append("\n");
        sb.append("description: \"").append(desc).append("\"\n");
        sb.append("version: ").append(ver).append("\n");
        if (StringUtils.hasText(author)) {
            sb.append("author: ").append(author.replace("\"", "")).append("\n");
        }
        sb.append("scene: ").append(scene).append("\n");
        sb.append("---\n");
        sb.append(body.strip());
        return sb.toString();
    }

    private boolean existsBySkillKey(String skillKey) {
        return findBySkillKey(skillKey) != null;
    }

    private AiSkill findBySkillKey(String skillKey) {
        List<AiSkill> all = aiSkillService.listSkills(null, null);
        if (all == null) {
            return null;
        }
        return all.stream()
                .filter(s -> skillKey.equalsIgnoreCase(s.getSkillKey()))
                .findFirst()
                .orElse(null);
    }

    private String sceneLabel(String scene) {
        return switch (scene) {
            case AiSkill.SCENE_COMMENT -> "评论";
            case AiSkill.SCENE_CHAT -> "聊天";
            case AiSkill.SCENE_ARTICLE -> "文章";
            case AiSkill.SCENE_UNIVERSAL -> "通用";
            default -> scene;
        };
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
