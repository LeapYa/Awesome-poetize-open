package com.ld.poetry.service.ai;

import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 解析并校验配置型 SKILL.md 文档。
 */
public final class AiSkillDocumentLoader {

    private static final Pattern SKILL_NAME_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{0,63}$");

    private AiSkillDocumentLoader() {
    }

    public static AiSkillDocument load(String skillDocument) {
        if (!StringUtils.hasText(skillDocument)) {
            throw new IllegalArgumentException("Skill文档不能为空");
        }

        String normalized = skillDocument.stripLeading();
        String[] lines = normalized.split("\\R", -1);
        if (lines.length < 4 || !"---".equals(lines[0].trim())) {
            throw new IllegalArgumentException("Skill文档必须以YAML frontmatter开头");
        }

        int closingFenceIndex = findClosingFrontmatterFence(lines);
        Map<String, String> metadata = parseFrontmatter(lines, closingFenceIndex);
        String body = parseBody(lines, closingFenceIndex);

        String name = metadata.get("name");
        String description = metadata.get("description");
        if (!StringUtils.hasText(name) || !SKILL_NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("Skill frontmatter.name 必须为小写字母、数字和连字符组成，且不超过64个字符");
        }
        if (!StringUtils.hasText(description)) {
            throw new IllegalArgumentException("Skill frontmatter.description 不能为空");
        }
        if (!StringUtils.hasText(body)) {
            throw new IllegalArgumentException("Skill正文不能为空");
        }

        return new AiSkillDocument(name, description, body.strip());
    }

    public static boolean isValid(String skillDocument) {
        try {
            load(skillDocument);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static int findClosingFrontmatterFence(String[] lines) {
        for (int i = 1; i < lines.length; i++) {
            if ("---".equals(lines[i].trim())) {
                return i;
            }
        }
        throw new IllegalArgumentException("Skill文档缺少YAML frontmatter结束标记");
    }

    private static Map<String, String> parseFrontmatter(String[] lines, int closingFenceIndex) {
        if (closingFenceIndex <= 1) {
            throw new IllegalArgumentException("Skill frontmatter 不能为空");
        }

        Map<String, String> metadata = new LinkedHashMap<>();
        for (int i = 1; i < closingFenceIndex; i++) {
            String line = lines[i].trim();
            if (!StringUtils.hasText(line)) {
                continue;
            }
            int separator = line.indexOf(':');
            if (separator <= 0) {
                throw new IllegalArgumentException("Skill frontmatter 只支持 key: value 格式");
            }
            String key = line.substring(0, separator).trim();
            String value = unquote(line.substring(separator + 1).trim());
            if (!"name".equals(key) && !"description".equals(key)) {
                throw new IllegalArgumentException("Skill frontmatter 仅支持 name 和 description 字段");
            }
            if (metadata.containsKey(key)) {
                throw new IllegalArgumentException("Skill frontmatter 字段重复: " + key);
            }
            metadata.put(key, value);
        }
        return metadata;
    }

    private static String parseBody(String[] lines, int closingFenceIndex) {
        StringBuilder body = new StringBuilder();
        for (int i = closingFenceIndex + 1; i < lines.length; i++) {
            if (body.length() > 0) {
                body.append('\n');
            }
            body.append(lines[i]);
        }
        return body.toString();
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
