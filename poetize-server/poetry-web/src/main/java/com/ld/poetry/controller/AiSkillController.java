package com.ld.poetry.controller;

import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.entity.AiSkill;
import com.ld.poetry.service.AiSkillService;
import com.ld.poetry.service.ai.AiSkillDocument;
import com.ld.poetry.service.ai.AiSkillDocumentLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * AI Skill 管理控制器
 * <p>
 * 提供 Skill 的 CRUD、安装、启停、场景活跃切换等接口。
 * 所有端点需管理员权限。
 *
 * @author LeapYa
 * @since 2026-06-24
 */
@Slf4j
@RestController
@RequestMapping("/webInfo/ai/skill")
@RequiredArgsConstructor
public class AiSkillController {

    private final AiSkillService aiSkillService;

    /**
     * Skill 列表（支持场景筛选）
     */
    @GetMapping("/list")
    @LoginCheck(0)
    public PoetryResult<List<AiSkill>> listSkills(
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) Boolean enabled) {
        return PoetryResult.success(aiSkillService.listSkills(scene, enabled));
    }

    /**
     * 单个详情
     */
    @GetMapping("/get/{id}")
    @LoginCheck(0)
    public PoetryResult<AiSkill> getSkill(@PathVariable Integer id) {
        AiSkill skill = aiSkillService.getSkill(id);
        if (skill == null) {
            return PoetryResult.fail("Skill 不存在");
        }
        return PoetryResult.success(skill);
    }

    /**
     * 创建 Skill
     */
    @PostMapping("/create")
    @LoginCheck(0)
    public PoetryResult<AiSkill> createSkill(@RequestBody AiSkill skill) {
        try {
            return PoetryResult.success(aiSkillService.createSkill(skill));
        } catch (IllegalArgumentException e) {
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("创建 Skill 失败: {}", e.getMessage(), e);
            return PoetryResult.fail("创建失败: " + e.getMessage());
        }
    }

    /**
     * 更新 Skill
     */
    @PutMapping("/update/{id}")
    @LoginCheck(0)
    public PoetryResult<AiSkill> updateSkill(@PathVariable Integer id, @RequestBody AiSkill skill) {
        try {
            return PoetryResult.success(aiSkillService.updateSkill(id, skill));
        } catch (IllegalArgumentException e) {
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("更新 Skill 失败: {}", e.getMessage(), e);
            return PoetryResult.fail("更新失败: " + e.getMessage());
        }
    }

    /**
     * 删除 Skill（内置不可删）
     */
    @DeleteMapping("/delete/{id}")
    @LoginCheck(0)
    public PoetryResult<Boolean> deleteSkill(@PathVariable Integer id) {
        try {
            boolean success = aiSkillService.deleteSkill(id);
            return success ? PoetryResult.success() : PoetryResult.fail("Skill 不存在");
        } catch (IllegalArgumentException e) {
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("删除 Skill 失败: {}", e.getMessage(), e);
            return PoetryResult.fail("删除失败: " + e.getMessage());
        }
    }

    /**
     * 启停 Skill
     */
    @PostMapping("/toggle/{id}")
    @LoginCheck(0)
    public PoetryResult<Boolean> toggleEnabled(@PathVariable Integer id) {
        boolean success = aiSkillService.toggleEnabled(id);
        return success ? PoetryResult.success() : PoetryResult.fail("Skill 不存在");
    }

    /**
     * 上传 .md 文件安装 Skill
     */
    @PostMapping("/install")
    @LoginCheck(0)
    public PoetryResult<AiSkill> installSkill(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return PoetryResult.fail("Skill 文件为空");
        }
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            return PoetryResult.success(aiSkillService.installFromMarkdown(content, false));
        } catch (IllegalArgumentException e) {
            return PoetryResult.fail(e.getMessage());
        } catch (IOException e) {
            log.error("读取 Skill 文件失败: {}", e.getMessage(), e);
            return PoetryResult.fail("读取文件失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("安装 Skill 失败: {}", e.getMessage(), e);
            return PoetryResult.fail("安装失败: " + e.getMessage());
        }
    }

    /**
     * 粘贴文本安装 Skill
     */
    @PostMapping("/install/text")
    @LoginCheck(0)
    public PoetryResult<AiSkill> installFromText(@RequestBody Map<String, String> body) {
        if (body == null || !StringUtils.hasText(body.get("content"))) {
            return PoetryResult.fail("Skill 文本不能为空");
        }
        try {
            return PoetryResult.success(aiSkillService.installFromMarkdown(body.get("content"), false));
        } catch (IllegalArgumentException e) {
            return PoetryResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("安装 Skill 失败: {}", e.getMessage(), e);
            return PoetryResult.fail("安装失败: " + e.getMessage());
        }
    }

    /**
     * 预览解析 Skill 文本（不落库），用于安装前确认 frontmatter 解析结果
     */
    @PostMapping("/preview")
    @LoginCheck(0)
    public PoetryResult<AiSkillDocument> previewSkill(@RequestBody Map<String, String> body) {
        if (body == null || !StringUtils.hasText(body.get("content"))) {
            return PoetryResult.fail("Skill 文本不能为空");
        }
        try {
            return PoetryResult.success(AiSkillDocumentLoader.load(body.get("content")));
        } catch (IllegalArgumentException e) {
            return PoetryResult.fail(e.getMessage());
        }
    }
}
