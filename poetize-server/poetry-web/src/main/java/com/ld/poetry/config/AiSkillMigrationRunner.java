package com.ld.poetry.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ld.poetry.dao.AiSkillMapper;
import com.ld.poetry.entity.AiSkill;
import com.ld.poetry.entity.SysAiConfig;
import com.ld.poetry.service.AiSkillService;
import com.ld.poetry.service.SysAiConfigService;
import com.ld.poetry.service.ai.AiCommentSkillDefaults;
import com.ld.poetry.service.ai.AiSkillDocumentLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI Skill 数据迁移 Runner
 * <p>
 * 应用启动时检查 {@code sys_ai_skill} 表是否已有评论场景的内置 Skill：
 * <ul>
 *   <li>有则跳过迁移</li>
 *   <li>无则从 {@code sys_ai_config.extraConfig.commentSkill} 读取旧内容，
 *       若旧内容有效则安装为新表中的内置 Skill；否则安装默认评论 Skill</li>
 *   <li>安装后自动设为 comment 场景的活跃 Skill</li>
 * </ul>
 * 迁移失败不影响应用启动，仅记录警告日志。
 *
 * @author LeapYa
 * @since 2026-06-24
 */
@Component
@Order(30)
@Slf4j
@RequiredArgsConstructor
public class AiSkillMigrationRunner implements ApplicationRunner {

    private final AiSkillMapper aiSkillMapper;
    private final AiSkillService aiSkillService;
    private final SysAiConfigService sysAiConfigService;
    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Override
    public void run(ApplicationArguments args) {
        try {
            migrateCommentSkill();
        } catch (Exception e) {
            log.warn("AI Skill 数据迁移失败，不影响应用启动: {}", e.getMessage(), e);
        }
    }

    /**
     * 迁移评论场景 Skill。
     */
    private void migrateCommentSkill() {
        // 1. 检查是否已有评论场景的 Skill
        LambdaQueryWrapper<AiSkill> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiSkill::getScene, AiSkill.SCENE_COMMENT);
        Long existing = aiSkillMapper.selectCount(wrapper);
        if (existing != null && existing > 0) {
            log.info("AI Skill 迁移跳过：评论场景已存在 {} 个 Skill", existing);
            return;
        }

        // 2. 从 extraConfig.commentSkill 读取旧内容
        String legacySkillDocument = resolveLegacyCommentSkillDocument();

        // 3. 安装为内置 Skill
        String contentToInstall = StringUtils.hasText(legacySkillDocument)
                ? legacySkillDocument
                : AiCommentSkillDefaults.DEFAULT_COMMENT_SKILL_DOCUMENT;
        if (!AiSkillDocumentLoader.isValid(contentToInstall)) {
            log.warn("AI Skill 迁移：旧 commentSkill 内容无效，回退到默认 Skill");
            contentToInstall = AiCommentSkillDefaults.DEFAULT_COMMENT_SKILL_DOCUMENT;
        }

        AiSkill installed = aiSkillService.installFromMarkdown(contentToInstall, true);
        log.info("AI Skill 迁移完成：已安装评论场景内置 Skill skillKey={}（启用后自动生效，无需设为活跃）",
                installed.getSkillKey());
    }

    /**
     * 从 {@code sys_ai_config.extraConfig.commentSkill} 读取旧的评论 Skill 文档。
     * 读取失败或不存在时返回 null。
     */
    private String resolveLegacyCommentSkillDocument() {
        try {
            SysAiConfig config = sysAiConfigService.getAiChatConfigInternal("default");
            if (config == null || !StringUtils.hasText(config.getExtraConfig())) {
                return null;
            }
            JsonNode root = objectMapper.readTree(config.getExtraConfig());
            JsonNode commentSkillNode = root.get("commentSkill");
            if (commentSkillNode == null || !commentSkillNode.isTextual()) {
                return null;
            }
            String text = commentSkillNode.asText();
            return StringUtils.hasText(text) ? text : null;
        } catch (Exception e) {
            log.warn("AI Skill 迁移：读取旧 commentSkill 失败: {}", e.getMessage());
            return null;
        }
    }
}
