package com.ld.poetry.service.ai;

import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 解析并校验配置型 SKILL.md 文档。
 * <p>
 * 支持的 frontmatter 字段：name、description、version、author、scene。
 * 未知字段会被忽略而非报错，便于 Skill 文档扩展。
 */
public final class AiSkillDocumentLoader {

    private static final Pattern SKILL_NAME_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{0,63}$");

    /**
     * 已知 frontmatter 字段白名单，未在此集合中的字段将被忽略。
     */
    private static final Set<String> KNOWN_FIELDS = Set.of("name", "description", "version", "author", "scene");

    /**
     * scene 字段允许的取值。
     */
    private static final Set<String> ALLOWED_SCENES = Set.of("comment", "chat", "article", "universal");

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

        String version = metadata.getOrDefault("version", "1.0.0");
        String author = metadata.getOrDefault("author", "");
        String scene = metadata.getOrDefault("scene", "comment");
        if (!ALLOWED_SCENES.contains(scene)) {
            throw new IllegalArgumentException("Skill frontmatter.scene 取值必须是 comment/chat/article/universal 之一");
        }

        return new AiSkillDocument(name, description, body.strip(), version, author, scene);
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
            // 未知字段忽略而非报错，便于 Skill 文档扩展
            if (!KNOWN_FIELDS.contains(key)) {
                continue;
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
