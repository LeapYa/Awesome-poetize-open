package com.ld.poetry.service.ai;

/**
 * 已加载的 Agent Skill 文档。
 *
 * @param name        frontmatter name（Skill 唯一标识）
 * @param description frontmatter description
 * @param body        解析后的正文
 * @param version     frontmatter version（可选，默认 1.0.0）
 * @param author      frontmatter author（可选）
 * @param scene       frontmatter scene（可选，默认 comment）
 */
public record AiSkillDocument(String name, String description, String body,
                              String version, String author, String scene) {

    /**
     * 兼容旧调用：仅 name/description/body，version/author/scene 使用默认值。
     */
    public AiSkillDocument(String name, String description, String body) {
        this(name, description, body, "1.0.0", "", "comment");
    }

    public boolean hasBody() {
        return body != null && !body.isBlank();
    }
}
