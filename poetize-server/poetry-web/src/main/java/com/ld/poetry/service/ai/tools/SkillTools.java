package com.ld.poetry.service.ai.tools;

import com.ld.poetry.entity.AiSkill;
import com.ld.poetry.service.AiSkillService;
import com.ld.poetry.service.ai.AiSkillDocument;
import com.ld.poetry.service.ai.AiSkillDocumentLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent Skill 发现 / 搜索 / 加载工具集。
 * <p>
 * 让 AI 在对话过程中自主决定是否加载某个 Skill 的完整指令，
 * 而不是由后端按场景硬塞。符合 Agent Skill 范式：
 * <ul>
 *   <li>{@link #listSkills(String)} - Discovery：列出可用 Skill 索引（不含 body）</li>
 *   <li>{@link #searchSkills(String)} - Search：按关键词模糊检索 Skill</li>
 *   <li>{@link #loadSkill(String)} - Loading：按需拉取 Skill 完整正文</li>
 * </ul>
 * 仅返回已启用的 Skill；body 只在 AI 显式调用 loadSkill 时才下发，控制 token 成本。
 *
 * @author LeapYa
 * @since 2026-06-24
 */
@Service
public class SkillTools {

    private static final Logger logger = LoggerFactory.getLogger(SkillTools.class);

    @Autowired
    private AiSkillService aiSkillService;

    /**
     * 列出当前可用的 Skill 索引（仅元数据，不含正文）。
     * <p>
     * AI 应先调用此工具了解有哪些 Skill 可用，再根据用户意图决定是否调用
     * {@link #loadSkill(String)} 加载完整指令。
     *
     * @param scene 可选场景过滤：comment/chat/article/universal，为空时返回全部
     * @return Skill 索引列表（name + description + scene + version）
     */
    @Tool(description = "列出当前可用的 Agent Skill 索引（仅元数据，不含正文）。"
            + "AI 应先调用此工具了解有哪些 Skill 可用，再根据用户意图决定是否调用 load_skill 加载完整指令。"
            + "返回字段：skill_key(唯一标识)、skill_name(显示名)、description(用途说明)、scene(适用场景)、version。")
    public String list_skills(
            @ToolParam(description = "可选场景过滤：comment(评论)/chat(聊天)/article(文章)/universal(通用)，为空时返回全部", required = false) String scene) {
        try {
            List<AiSkill> skills = aiSkillService.listSkills(scene, Boolean.TRUE);
            if (skills == null || skills.isEmpty()) {
                return "当前没有可用的 Skill" + (scene != null && !scene.isBlank() ? "（场景: " + scene + "）" : "") + "。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("可用 Skill 共 ").append(skills.size()).append(" 个：\n\n");
            for (AiSkill skill : skills) {
                sb.append("- skill_key: ").append(safe(skill.getSkillKey()))
                        .append(" | name: ").append(safe(skill.getSkillName()))
                        .append(" | scene: ").append(safe(skill.getScene()))
                        .append(" | version: ").append(safe(skill.getVersion()))
                        .append("\n  description: ").append(safe(skill.getDescription()))
                        .append("\n\n");
            }
            sb.append("提示：如需使用某个 Skill 的完整指令，调用 load_skill(skill_key) 加载。");
            return sb.toString();
        } catch (Exception e) {
            logger.warn("list_skills 执行失败: {}", e.getMessage());
            return "Skill 列表查询失败：" + safe(e.getMessage());
        }
    }

    /**
     * 按关键词模糊搜索 Skill（匹配 name / description / skill_key）。
     * <p>
     * 当用户意图不明确、AI 不确定该加载哪个 Skill 时使用。
     *
     * @param query 搜索关键词
     * @return 匹配的 Skill 索引列表
     */
    @Tool(description = "按关键词模糊搜索 Skill（匹配 name/description/skill_key）。"
            + "当用户意图不明确、AI 不确定该加载哪个 Skill 时使用。"
            + "返回匹配的 Skill 索引（不含正文），需再用 load_skill 加载。")
    public String search_skills(
            @ToolParam(description = "搜索关键词，例如：评论、翻译、代码审查") String query) {
        try {
            if (query == null || query.isBlank()) {
                return "搜索关键词不能为空。";
            }

            List<AiSkill> all = aiSkillService.listSkills(null, Boolean.TRUE);
            if (all == null || all.isEmpty()) {
                return "未找到任何已启用的 Skill。";
            }

            String q = query.toLowerCase();
            List<AiSkill> matched = all.stream()
                    .filter(s -> containsIgnoreCase(s.getSkillKey(), q)
                            || containsIgnoreCase(s.getSkillName(), q)
                            || containsIgnoreCase(s.getDescription(), q))
                    .collect(Collectors.toList());

            if (matched.isEmpty()) {
                return "未找到与 \"" + query + "\" 匹配的 Skill。可调用 list_skills 查看全部可用 Skill。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("匹配到 ").append(matched.size()).append(" 个 Skill：\n\n");
            for (AiSkill skill : matched) {
                sb.append("- skill_key: ").append(safe(skill.getSkillKey()))
                        .append(" | name: ").append(safe(skill.getSkillName()))
                        .append(" | scene: ").append(safe(skill.getScene()))
                        .append("\n  description: ").append(safe(skill.getDescription()))
                        .append("\n\n");
            }
            sb.append("提示：调用 load_skill(skill_key) 加载完整指令。");
            return sb.toString();
        } catch (Exception e) {
            logger.warn("search_skills 执行失败: {}", e.getMessage());
            return "Skill 搜索失败：" + safe(e.getMessage());
        }
    }

    /**
     * 按需加载指定 Skill 的完整指令正文。
     * <p>
     * AI 在判断某个 Skill 适用于当前对话后调用此工具获取完整 body，
     * 然后按 body 中的指令执行任务。加载后的指令应作为本次对话的执行规范。
     *
     * @param skill_key Skill 唯一标识（来自 list_skills / search_skills 的返回）
     * @return Skill 完整正文；若不存在或未启用返回提示信息
     */
    @Tool(description = "按需加载指定 Skill 的完整指令正文。"
            + "AI 在判断某个 Skill 适用于当前对话后调用此工具获取完整 body，然后按 body 中的指令执行任务。"
            + "加载后的指令应作为本次对话的执行规范。参数 skill_key 来自 list_skills 或 search_skills 的返回。")
    public String load_skill(
            @ToolParam(description = "Skill 唯一标识（来自 list_skills / search_skills 的返回）") String skill_key) {
        try {
            if (skill_key == null || skill_key.isBlank()) {
                return "skill_key 不能为空。请先调用 list_skills 查看可用的 Skill。";
            }

            List<AiSkill> all = aiSkillService.listSkills(null, Boolean.TRUE);
            if (all == null) {
                return "Skill 加载失败：无法访问 Skill 库。";
            }

            AiSkill target = all.stream()
                    .filter(s -> skill_key.equalsIgnoreCase(s.getSkillKey()))
                    .findFirst()
                    .orElse(null);

            if (target == null) {
                return "未找到 skill_key=\"" + skill_key + "\" 的 Skill，或该 Skill 未启用。"
                        + "请调用 list_skills 查看可用的 Skill。";
            }

            String body = target.getSkillBody();
            if ((body == null || body.isBlank()) && StringUtils.hasText(target.getSkillContent())) {
                // skillBody 字段为空时，从完整 SKILL.md 内容重新解析
                try {
                    AiSkillDocument doc = AiSkillDocumentLoader.load(target.getSkillContent());
                    if (doc.hasBody()) {
                        body = doc.body();
                    }
                } catch (Exception ignored) {
                    // 解析失败则继续，下面会返回"无可用正文"
                }
            }

            if (body == null || body.isBlank()) {
                return "Skill \"" + skill_key + "\" 没有可用的指令正文。";
            }

            logger.info("AI 自主加载 Skill: skill_key={}, scene={}", skill_key, target.getScene());

            StringBuilder sb = new StringBuilder();
            sb.append("=== Skill: ").append(safe(target.getSkillName()))
                    .append(" (v").append(safe(target.getVersion())).append(") ===\n");
            sb.append("scene: ").append(safe(target.getScene())).append("\n");
            sb.append("description: ").append(safe(target.getDescription())).append("\n");
            sb.append("=== 指令正文 ===\n");
            sb.append(body);
            return sb.toString();
        } catch (Exception e) {
            logger.warn("load_skill 执行失败: skill_key={}, error={}", skill_key, e.getMessage());
            return "Skill 加载失败：" + safe(e.getMessage());
        }
    }

    private static boolean containsIgnoreCase(String source, String target) {
        return source != null && source.toLowerCase().contains(target);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
