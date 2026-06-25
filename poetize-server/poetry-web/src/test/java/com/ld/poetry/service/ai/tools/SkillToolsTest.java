package com.ld.poetry.service.ai.tools;

import com.ld.poetry.entity.AiSkill;
import com.ld.poetry.service.AiSkillService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 {@link SkillTools} 三个工具的行为：
 * <ul>
 *   <li>{@code list_skills} - 列出 Skill 索引（不含 body）</li>
 *   <li>{@code search_skills} - 按关键词搜索</li>
 *   <li>{@code load_skill} - 加载完整 body</li>
 * </ul>
 *
 * @author LeapYa
 * @since 2026-06-24
 */
class SkillToolsTest {

    private AiSkillService aiSkillService;
    private SkillTools skillTools;

    @BeforeEach
    void setUp() {
        aiSkillService = Mockito.mock(AiSkillService.class);
        skillTools = new SkillTools();
        // 手动注入 mock
        org.springframework.test.util.ReflectionTestUtils.setField(skillTools, "aiSkillService", aiSkillService);
    }

    @Test
    void listSkillsShouldReturnIndexWithoutBody() {
        AiSkill skill = createSkill("comment-style", "评论风格", "评论场景专用风格", "comment", "1.0.0",
                "这是 body 内容，不应该出现在 list 中");
        Mockito.when(aiSkillService.listSkills("comment", true))
                .thenReturn(List.of(skill));

        String result = skillTools.list_skills("comment");

        assertTrue(result.contains("comment-style"), "应包含 skill_key");
        assertTrue(result.contains("评论风格"), "应包含 skill_name");
        assertTrue(result.contains("评论场景专用风格"), "应包含 description");
        assertTrue(!result.contains("这是 body 内容"), "list_skills 不应返回 body");
    }

    @Test
    void listSkillsShouldHandleEmpty() {
        Mockito.when(aiSkillService.listSkills(null, true))
                .thenReturn(List.of());

        String result = skillTools.list_skills(null);

        assertTrue(result.contains("没有可用的 Skill"), "空列表应返回提示");
    }

    @Test
    void searchSkillsShouldMatchByKeyword() {
        AiSkill skill1 = createSkill("comment-style", "评论风格", "评论场景专用", "comment", "1.0.0", "body1");
        AiSkill skill2 = createSkill("code-review", "代码审查", "审查代码质量", "universal", "1.2.0", "body2");
        Mockito.when(aiSkillService.listSkills(null, true))
                .thenReturn(List.of(skill1, skill2));

        String result = skillTools.search_skills("评论");

        assertTrue(result.contains("comment-style"), "应匹配到评论风格 Skill");
        assertTrue(!result.contains("code-review"), "不应匹配到代码审查 Skill");
    }

    @Test
    void searchSkillsShouldHandleNoMatch() {
        Mockito.when(aiSkillService.listSkills(null, true))
                .thenReturn(List.of());

        String result = skillTools.search_skills("不存在的关键词");

        assertTrue(result.contains("未找到"), "无匹配应返回提示");
    }

    @Test
    void loadSkillShouldReturnFullBody() {
        AiSkill skill = createSkill("comment-style", "评论风格", "评论场景专用", "comment", "1.0.0",
                "你是评论助手，回复要简短友好。");
        Mockito.when(aiSkillService.listSkills(null, true))
                .thenReturn(List.of(skill));

        String result = skillTools.load_skill("comment-style");

        assertTrue(result.contains("你是评论助手，回复要简短友好。"), "load_skill 应返回完整 body");
        assertTrue(result.contains("评论风格"), "应包含 Skill 名称");
        assertTrue(result.contains("1.0.0"), "应包含版本号");
    }

    @Test
    void loadSkillShouldHandleNotFound() {
        Mockito.when(aiSkillService.listSkills(null, true))
                .thenReturn(List.of());

        String result = skillTools.load_skill("nonexistent");

        assertTrue(result.contains("未找到"), "不存在的 Skill 应返回提示");
        assertTrue(result.contains("list_skills"), "应引导 AI 调用 list_skills");
    }

    @Test
    void loadSkillShouldHandleEmptyKey() {
        String result = skillTools.load_skill("");

        assertTrue(result.contains("不能为空"), "空 key 应返回参数错误");
    }

    private AiSkill createSkill(String key, String name, String desc, String scene, String version, String body) {
        AiSkill skill = new AiSkill();
        skill.setSkillKey(key);
        skill.setSkillName(name);
        skill.setDescription(desc);
        skill.setScene(scene);
        skill.setVersion(version);
        skill.setSkillBody(body);
        skill.setEnabled(true);
        return skill;
    }
}
