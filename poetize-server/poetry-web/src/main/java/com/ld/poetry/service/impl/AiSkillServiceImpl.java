package com.ld.poetry.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ld.poetry.dao.AiSkillMapper;
import com.ld.poetry.entity.AiSkill;
import com.ld.poetry.service.AiSkillService;
import com.ld.poetry.service.ai.AiSkillDocument;
import com.ld.poetry.service.ai.AiSkillDocumentLoader;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/**
 * AI Skill 管理服务实现
 *
 * @author LeapYa
 * @since 2026-06-24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiSkillServiceImpl extends ServiceImpl<AiSkillMapper, AiSkill> implements AiSkillService {

    /**
     * Skill 内容上限 64KB
     */
    private static final int MAX_SKILL_CONTENT_BYTES = 64 * 1024;

    /**
     * 允许的场景取值
     */
    private static final Set<String> ALLOWED_SCENES = Set.of(
            AiSkill.SCENE_COMMENT, AiSkill.SCENE_CHAT, AiSkill.SCENE_ARTICLE, AiSkill.SCENE_UNIVERSAL);

    /**
     * 内置 meta-skill：skill-creator。
     * <p>
     * 把"如何创建一个 Skill"这件事本身做成一个 Skill，让 AI 通过
     * 现有索引机制自主发现并 load_skill 加载，而非硬编码在 system prompt。
     * 这样创建流程可演化（站长可修改本 Skill 调整创建规范），泛化能力更强。
     * <p>
     * scene=universal：所有场景都能在索引里看到它，AI 据用户意图自主决定是否加载。
     * 管理工具（create_skill 等）仅在 chat 场景对站长/管理员注册，body 内会提示权限边界。
     */
    private static final String BUILTIN_SKILL_CREATOR_MD = """
            ---
            name: skill-creator
            description: "Skill 创建与管理指南。当用户想创建、新建、编写、修改、删除或管理 Agent Skill 时加载本 Skill，获取命名规范、场景选择、body 结构化模板与工具调用流程。"
            version: 1.0.0
            author: poetize
            scene: universal
            ---

            # Skill 创建指南

            ## 触发条件
            当用户表达以下任一意图时，加载并遵循本 Skill：
            - "帮我创建/新建/做一个 Skill"
            - "写一个评论回复风格的 Skill"
            - "怎么写 Skill / Skill 怎么写"
            - "修改/更新/删除某个 Skill"
            - "帮我配置/设置 Skill"
            - 任何涉及 Agent Skill 创建、编辑、管理、咨询的请求

            ## 权限边界
            管理工具（create_skill / toggle_skill / delete_skill）仅站长/管理员可用。
            - 若当前用户不是站长/管理员：解释 Skill 体系概念，告知管理操作仅限站长/管理员，引导联系管理员。仍可帮用户讲解 Skill 的写法和结构。
            - 若当前用户是站长/管理员：继续执行下列创建流程。

            ## 创建流程

            ### 1. 明确场景与用途
            与用户确认两件事（可分轮次，不要一次性问完）：
            - **场景**：这个 Skill 用在哪？
              - `comment`（评论）：访客留言后，AI 自动生成回复时遵循（服务端强制注入优先级最高的启用 Skill）
              - `chat`（聊天）：用户与看板娘对话时遵循
              - `article`（文章）：文章相关问答（摘要/解读/续写）时遵循
              - `universal`（通用）：所有场景都会加载，自动入索引，适合放跨场景的通用规则
            - **用途**：这个 Skill 要解决什么问题？（一句话）

            ### 2. 确定 skill_key
            - 小写字母、数字、连字符组成，开头需字母或数字，不超过 64 字符
            - 建议 kebab-case，语义化，如 `comment-friendly-reply`、`chat-code-review`
            - 创建前可调用 `list_skills(scene)` 查看是否已有同类 Skill，避免重复；也可 `load_skill` 加载相似的作为参考

            ### 3. 撰写 description（关键）
            description 决定 AI 能否通过意图匹配到这个 Skill，务必认真写：
            - 一句话说明用途，要包含触发关键词
            - 好的例子："评论区友好互动回复，适合轻松交流、提问解答场景"
            - 差的例子："评论 Skill"（太泛，无法匹配意图）

            ### 4. 撰写 body（指令正文）
            用 Markdown 结构化撰写，推荐包含以下部分：

            ```markdown
            # <Skill 名称>

            ## 触发条件
            <什么情况下应用这个 Skill，要具体>

            ## 执行步骤
            1. <步骤1>
            2. <步骤2>

            ## 输出要求
            - <格式/长度/语气约束，尽量量化>

            ## 注意事项
            - <边界情况/禁止行为>
            ```

            撰写要点：
            - 触发条件要具体，避免"当需要时"这种空泛描述
            - 执行步骤可操作，AI 能照着执行
            - 输出要求量化（如"2-4 句""200 字以内"）
            - 注意事项列出禁止项和边界
            - body 是给 AI 执行时遵循的指令，不是给人看的说明书

            ### 5. 调用工具保存
            调用 `create_skill(skill_key, scene, description, body, version, author)`：
            - version 默认 "1.0.0"，迭代时递增
            - author 可选
            - create_skill 是 upsert：同名 skill_key 已存在则更新，否则新建
            - 可在多轮对话中逐步细化 body 后再次调用覆盖

            ### 6. 后续操作
            - 新建 Skill 默认启用，**启用即生效**：AI 会按意图自主加载（universal/comment/chat/article 均如此）。
            - 评论场景额外行为：服务端会取优先级最高的启用评论 Skill 做强制注入（保证公开回复风格稳定），
              多个启用时按 sortOrder 升序、id 降序取第一个。
            - 用户想"禁用/启用"：`toggle_skill(skill_key)`
            - 用户想"删除"：先确认意图，再 `delete_skill(skill_key)`（内置 Skill 不可删）

            ## 协作规范
            - 不要一次性问完所有信息：先问场景和用途 → 拟定草稿给用户看 → 根据反馈调整 → 调用工具
            - 可以先草拟一版 body 让用户确认，再 create_skill
            - 完成后用一句话告知结果（skill_key、场景、启用状态），不重复工具返回的原文

            ## 质量标准
            一个好的 Skill 应满足：
            1. description 能让 AI 准确匹配意图（不漏不滥）
            2. body 可执行、可量化、有边界
            3. 不与现有 Skill 重复或冲突
            4. 场景选择正确（universal 只放真正跨场景的通用规则）
            """;

    /**
     * 应用启动时确保内置 meta-skill 存在。
     * <p>
     * 只在不存在时插入，不覆盖站长后续修改的内容（与 ensureBuiltInPlugins 策略一致）。
     * 若需恢复内置最新版本，站长可在后台删除后重启自动重建。
     */
    @PostConstruct
    public void ensureBuiltinSkills() {
        try {
            LambdaQueryWrapper<AiSkill> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AiSkill::getSkillKey, "skill-creator");
            Long count = this.baseMapper.selectCount(wrapper);
            if (count != null && count > 0) {
                return;
            }
            AiSkill saved = installFromMarkdown(BUILTIN_SKILL_CREATOR_MD, true);
            log.info("内置 meta-skill [skill-creator] 已初始化, id={}", saved.getId());
        } catch (Exception e) {
            log.warn("初始化内置 meta-skill [skill-creator] 失败，不影响启动: {}", e.getMessage());
        }
    }

    @Override
    public List<AiSkill> listSkills(String scene, Boolean enabled) {
        LambdaQueryWrapper<AiSkill> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(scene)) {
            wrapper.eq(AiSkill::getScene, scene);
        }
        if (enabled != null) {
            wrapper.eq(AiSkill::getEnabled, enabled);
        }
        wrapper.orderByAsc(AiSkill::getSortOrder).orderByDesc(AiSkill::getId);
        return this.list(wrapper);
    }

    @Override
    public AiSkill getSkill(Integer id) {
        if (id == null) {
            return null;
        }
        return this.getById(id);
    }

    @Override
    public AiSkill getFirstEnabledSkillForScene(String scene) {
        if (!StringUtils.hasText(scene)) {
            return null;
        }
        LambdaQueryWrapper<AiSkill> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiSkill::getScene, scene)
                .eq(AiSkill::getEnabled, Boolean.TRUE)
                .orderByAsc(AiSkill::getSortOrder)
                .orderByDesc(AiSkill::getId)
                .last("LIMIT 1");
        return this.getOne(wrapper);
    }

    @Override
    public AiSkillDocument getFirstEnabledSkillDocumentForScene(String scene) {
        AiSkill skill = getFirstEnabledSkillForScene(scene);
        if (skill == null || !StringUtils.hasText(skill.getSkillContent())) {
            return null;
        }
        try {
            return AiSkillDocumentLoader.load(skill.getSkillContent());
        } catch (Exception e) {
            log.warn("解析场景 {} 的启用 Skill 文档失败: skillKey={}, error={}",
                    scene, skill.getSkillKey(), e.getMessage());
            return null;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiSkill createSkill(AiSkill skill) {
        validateSkillContent(skill.getSkillContent());
        AiSkillDocument doc = AiSkillDocumentLoader.load(skill.getSkillContent());
        fillSkillFromDocument(skill, doc);
        // skillKey 唯一性校验
        ensureSkillKeyUnique(doc.name(), null);
        if (skill.getEnabled() == null) {
            skill.setEnabled(true);
        }
        if (skill.getIsBuiltin() == null) {
            skill.setIsBuiltin(false);
        }
        if (skill.getSortOrder() == null) {
            skill.setSortOrder(0);
        }
        this.save(skill);
        return skill;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiSkill updateSkill(Integer id, AiSkill skill) {
        AiSkill existing = this.getById(id);
        if (existing == null) {
            throw new IllegalArgumentException("Skill 不存在: id=" + id);
        }
        // 内置 Skill 的 skillKey 不允许修改
        if (StringUtils.hasText(skill.getSkillContent()) && !skill.getSkillContent().equals(existing.getSkillContent())) {
            validateSkillContent(skill.getSkillContent());
            AiSkillDocument doc = AiSkillDocumentLoader.load(skill.getSkillContent());
            // 内置 Skill 不允许通过更新改 skillKey
            if (Boolean.TRUE.equals(existing.getIsBuiltin()) && !doc.name().equals(existing.getSkillKey())) {
                throw new IllegalArgumentException("内置 Skill 的 skillKey 不允许修改");
            }
            fillSkillFromDocument(skill, doc);
            ensureSkillKeyUnique(doc.name(), id);
        }
        skill.setId(id);
        // 不允许通过更新改 is_builtin
        skill.setIsBuiltin(existing.getIsBuiltin());
        this.updateById(skill);
        return this.getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteSkill(Integer id) {
        AiSkill existing = this.getById(id);
        if (existing == null) {
            return false;
        }
        if (Boolean.TRUE.equals(existing.getIsBuiltin())) {
            throw new IllegalArgumentException("内置 Skill 不可删除");
        }
        return this.removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleEnabled(Integer id) {
        AiSkill existing = this.getById(id);
        if (existing == null) {
            return false;
        }
        boolean newEnabled = !Boolean.TRUE.equals(existing.getEnabled());
        LambdaUpdateWrapper<AiSkill> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(AiSkill::getId, id)
                .set(AiSkill::getEnabled, newEnabled);
        return this.update(updateWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiSkill installFromMarkdown(String content, boolean isBuiltin) {
        validateSkillContent(content);
        AiSkillDocument doc = AiSkillDocumentLoader.load(content);
        // 同名则更新
        LambdaQueryWrapper<AiSkill> existingWrapper = new LambdaQueryWrapper<>();
        existingWrapper.eq(AiSkill::getSkillKey, doc.name());
        AiSkill existing = this.getOne(existingWrapper);

        if (existing != null) {
            existing.setSkillContent(content);
            existing.setSkillBody(doc.body());
            existing.setDescription(doc.description());
            existing.setVersion(StringUtils.hasText(doc.version()) ? doc.version() : existing.getVersion());
            existing.setAuthor(StringUtils.hasText(doc.author()) ? doc.author() : existing.getAuthor());
            existing.setScene(StringUtils.hasText(doc.scene()) ? doc.scene() : existing.getScene());
            existing.setIsBuiltin(existing.getIsBuiltin() || isBuiltin);
            this.updateById(existing);
            return existing;
        }

        AiSkill skill = new AiSkill();
        fillSkillFromDocument(skill, doc);
        skill.setSkillContent(content);
        skill.setEnabled(true);
        skill.setIsBuiltin(isBuiltin);
        skill.setSortOrder(0);
        this.save(skill);
        return skill;
    }

    private void validateSkillContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("Skill 文档不能为空");
        }
        if (content.getBytes().length > MAX_SKILL_CONTENT_BYTES) {
            throw new IllegalArgumentException("Skill 文档超过 64KB 上限");
        }
    }

    private void ensureSkillKeyUnique(String skillKey, Integer excludeId) {
        LambdaQueryWrapper<AiSkill> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiSkill::getSkillKey, skillKey);
        if (excludeId != null) {
            wrapper.ne(AiSkill::getId, excludeId);
        }
        if (this.count(wrapper) > 0) {
            throw new IllegalArgumentException("Skill 标识已存在: " + skillKey);
        }
    }

    private void fillSkillFromDocument(AiSkill skill, AiSkillDocument doc) {
        skill.setSkillKey(doc.name());
        skill.setSkillName(doc.name());
        skill.setDescription(doc.description());
        skill.setSkillBody(doc.body());
        skill.setVersion(StringUtils.hasText(doc.version()) ? doc.version() : "1.0.0");
        skill.setAuthor(StringUtils.hasText(doc.author()) ? doc.author() : "");
        skill.setScene(StringUtils.hasText(doc.scene()) ? doc.scene() : AiSkill.SCENE_COMMENT);
    }
}
