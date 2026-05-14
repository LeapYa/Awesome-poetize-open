package com.ld.poetry.service.ai;

/**
 * 已加载的 Agent Skill 文档。
 */
public record AiSkillDocument(String name, String description, String body) {

    public boolean hasBody() {
        return body != null && !body.isBlank();
    }
}
