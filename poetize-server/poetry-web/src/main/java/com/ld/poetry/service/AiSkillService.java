package com.ld.poetry.service;

import com.ld.poetry.entity.AiSkill;
import com.ld.poetry.service.ai.AiSkillDocument;

import java.util.List;

/**
 * AI Skill 管理服务接口
 * <p>
 * 提供 Skill 的 CRUD、安装、启停等能力。
 * <p>
 * Skill 加载机制统一为「启用即生效」：
 * <ul>
 *   <li>启用的 Skill 进入 AI 索引，AI 按意图自主 {@code load_skill} 加载（泛化路径）</li>
 *   <li>评论场景额外取优先级最高的启用 Skill 做服务端强制注入（确定性强约束）</li>
 * </ul>
 * 不再有"活跃"概念，同一场景可启用多个 Skill。
 *
 * @author LeapYa
 * @since 2026-06-24
 */
public interface AiSkillService {

    /**
     * 按场景筛选 Skill 列表
     *
     * @param scene   场景（comment/chat/article/universal），为空时返回全部
     * @param enabled 是否仅返回启用的，为 null 时不筛选
     * @return Skill 列表
     */
    List<AiSkill> listSkills(String scene, Boolean enabled);

    /**
     * 获取单个 Skill 详情
     *
     * @param id Skill 主键
     * @return Skill 对象，不存在返回 null
     */
    AiSkill getSkill(Integer id);

    /**
     * 获取某场景优先级最高的启用 Skill（按 sortOrder 升序、id 降序取第一个）。
     * <p>
     * 用于评论场景的服务端强制注入：评论回复必须遵循固定风格，不能依赖 AI 自主判断，
     * 因此服务端在调用模型前直接取一个启用的评论 Skill 注入系统提示。
     * 若该场景无启用 Skill，返回 null（由调用方决定回退策略）。
     *
     * @param scene 场景（comment/chat/article）
     * @return 优先级最高的启用 Skill，不存在返回 null
     */
    AiSkill getFirstEnabledSkillForScene(String scene);

    /**
     * 获取某场景优先级最高启用 Skill 的文档对象（已解析）
     *
     * @param scene 场景
     * @return 已加载的 Skill 文档，不存在返回 null
     */
    AiSkillDocument getFirstEnabledSkillDocumentForScene(String scene);

    /**
     * 新增 Skill（解析验证 frontmatter）
     *
     * @param skill Skill 对象（skillContent 必填）
     * @return 新增后的 Skill（含 id）
     */
    AiSkill createSkill(AiSkill skill);

    /**
     * 更新 Skill
     *
     * @param id    Skill 主键
     * @param skill Skill 对象
     * @return 更新后的 Skill
     */
    AiSkill updateSkill(Integer id, AiSkill skill);

    /**
     * 删除 Skill（内置不可删）
     *
     * @param id Skill 主键
     * @return 是否删除成功
     */
    boolean deleteSkill(Integer id);

    /**
     * 切换 Skill 启用状态
     *
     * @param id Skill 主键
     * @return 切换后的启用状态
     */
    boolean toggleEnabled(Integer id);

    /**
     * 从 .md 文本安装 Skill（同名则更新版本）
     *
     * @param content   SKILL.md 全文
     * @param isBuiltin 是否标记为内置
     * @return 安装后的 Skill
     */
    AiSkill installFromMarkdown(String content, boolean isBuiltin);
}
