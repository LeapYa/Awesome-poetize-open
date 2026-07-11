package com.ld.poetry.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 对数据库中的 Markdown 文章正文执行章节级纯文本编辑。 */
public final class MarkdownSectionEditor {

    private static final Set<String> SUPPORTED_ACTIONS = Set.of(
            "replace", "insert_after", "insert_before", "delete", "append");

    private MarkdownSectionEditor() {
    }

    public record SectionUpdate(
            String heading,
            Integer headingIndex,
            String action,
            String content,
            Integer newHeadingLevel) {
        /** 向后兼容的便捷构造器：不指定 headingIndex。 */
        public SectionUpdate(String heading, String action, String content, Integer newHeadingLevel) {
            this(heading, null, action, content, newHeadingLevel);
        }
    }

    /** 章节编辑的完整结果，包含更新后内容、被替换的原始内容、以及可能的警告信息。 */
    public record SectionEditResult(
            String updatedContent,
            String originalSectionContent,
            String warning) {
    }

    public static SectionEditResult apply(String articleContent, SectionUpdate update) {
        if (!hasText(articleContent)) {
            throw new IllegalArgumentException("文章内容为空，无法执行章节更新");
        }
        if (update == null || !hasText(update.action())) {
            throw new IllegalArgumentException("action 不能为空");
        }

        String action = update.action().trim();
        if (!SUPPORTED_ACTIONS.contains(action)) {
            throw new IllegalArgumentException(
                    "不支持的操作: " + action + "。可用: replace, insert_after, insert_before, delete, append");
        }
        validateRequestedHeadingLevel(action, update.newHeadingLevel());

        String lineSeparator = detectPrimaryLineSeparator(articleContent);
        String normalizedNewContent = normalizeLineSeparators(update.content(), lineSeparator);
        String updatedContent;
        String originalSectionContent = null;
        String warning = null;

        if ("append".equals(action)) {
            validateInsertedContent(action, null, normalizedNewContent, update.newHeadingLevel());
            String separator = endsWithLineBreak(articleContent) ? "" : lineSeparator;
            updatedContent = articleContent + separator + lineSeparator + normalizedNewContent;
        } else {
            if (!hasText(update.heading())) {
                throw new IllegalArgumentException("非 append 操作必须指定 heading");
            }
            SectionInfo target = findSection(articleContent, update.heading(), update.headingIndex());
            validateInsertedContent(action, target, normalizedNewContent, update.newHeadingLevel());
            // 只对 replace/delete 捕获被移除的原始内容，供回滚使用；insert 操作不移除内容
            if ("replace".equals(action) || "delete".equals(action)) {
                originalSectionContent = articleContent.substring(target.startOffset, target.endOffset);
            }
            warning = detectLevelJumpWarning(action, target, update.newHeadingLevel());
            updatedContent = switch (action) {
                case "replace" -> spliceContent(
                        articleContent, target.startOffset, target.endOffset, normalizedNewContent, lineSeparator);
                case "delete" -> spliceContent(
                        articleContent, target.startOffset, target.endOffset, "", lineSeparator);
                case "insert_before" -> spliceContent(
                        articleContent, target.startOffset, target.startOffset, normalizedNewContent, lineSeparator);
                case "insert_after" -> spliceContent(
                        articleContent, target.endOffset, target.endOffset, normalizedNewContent, lineSeparator);
                default -> throw new IllegalStateException("未处理的章节操作: " + action);
            };
        }

        validateArticleBody(updatedContent);
        return new SectionEditResult(updatedContent, originalSectionContent, warning);
    }

    /** 数据库正文只允许 H2-H6；页面唯一 H1 由独立文章标题生成。 */
    public static void validateArticleBody(String content) {
        if (!hasText(content)) {
            return;
        }
        if (parseSections(content).stream().anyMatch(section -> section.level == 1)) {
            throw new IllegalArgumentException(
                    "文章正文不能包含一级标题（H1）；页面唯一 H1 由文章大标题生成，正文标题必须使用 H2-H6。");
        }
        // 同时拦截 Setext 风格 H1（下划线 = 或 - ）和 HTML <h1> 标签
        if (containsSetextH1(content) || containsHtmlH1(content)) {
            throw new IllegalArgumentException(
                    "文章正文不能包含一级标题（H1）；页面唯一 H1 由文章大标题生成，正文标题必须使用 H2-H6。");
        }
    }

    /** 检测 Setext 风格 H1：文本行下紧跟由 `=` 组成的下划线行。 */
    private static boolean containsSetextH1(String content) {
        List<MarkdownLine> lines = splitMarkdownLines(content);
        CodeFence activeFence = null;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).content;
            if (activeFence != null) {
                if (isClosingCodeFence(line, activeFence)) {
                    activeFence = null;
                }
                continue;
            }
            CodeFence openingFence = parseOpeningCodeFence(line);
            if (openingFence != null) {
                activeFence = openingFence;
                continue;
            }
            if (i > 0) {
                String prevLine = lines.get(i - 1).content;
                int blockStart = markdownBlockStart(prevLine);
                if (blockStart >= 0 && blockStart < prevLine.length()
                        && isSetextUnderline(line, '=')
                        && !prevLine.substring(blockStart).trim().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 检测 HTML h1 标签（如 `<h1>`, `<H1>`, `<h1 class="...">`）。 */
    private static boolean containsHtmlH1(String content) {
        return java.util.regex.Pattern.compile("<h1[\\s>]", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(content)
                .find();
    }

    private static boolean isSetextUnderline(String line, char marker) {
        int start = markdownBlockStart(line);
        if (start < 0 || start >= line.length()) {
            return false;
        }
        for (int i = start; i < line.length(); i++) {
            if (line.charAt(i) != marker) {
                return false;
            }
        }
        return line.length() > start;
    }

    private static void validateRequestedHeadingLevel(String action, Integer newHeadingLevel) {
        if (newHeadingLevel == null) {
            return;
        }
        if (!"replace".equals(action)) {
            throw new IllegalArgumentException("newHeadingLevel 仅用于 replace 操作");
        }
        if (newHeadingLevel < 2 || newHeadingLevel > 6) {
            throw new IllegalArgumentException(
                    "newHeadingLevel 必须在 2 到 6 之间；正文一级标题（H1）由文章大标题独占");
        }
    }

    private static SectionInfo findSection(String content, String headingText, Integer headingIndex) {
        List<SectionInfo> sections = parseSections(content);
        String normalizedTarget = headingText.trim();
        List<SectionInfo> matches = sections.stream()
                .filter(section -> section.headingText.trim().equalsIgnoreCase(normalizedTarget))
                .toList();

        if (matches.isEmpty()) {
            String availableHeadings = sections.stream()
                    .map(section -> "\"" + section.headingText + "\" (H" + section.level + ")")
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("无");
            throw new IllegalArgumentException(
                    "未找到匹配的标题: \"" + headingText + "\"。可用标题: " + availableHeadings);
        }

        if (headingIndex != null) {
            if (headingIndex < 1 || headingIndex > matches.size()) {
                throw new IllegalArgumentException(
                        "headingIndex 超出范围：共匹配到 " + matches.size()
                                + " 个章节，有效范围为 1-" + matches.size()
                                + "，传入值为 " + headingIndex);
            }
            return matches.get(headingIndex - 1);
        }

        if (matches.size() > 1) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < matches.size(); i++) {
                SectionInfo m = matches.get(i);
                if (i > 0) sb.append(", ");
                sb.append("#").append(i + 1)
                        .append(": 第").append(m.startLine + 1)
                        .append("行 (H").append(m.level).append(")");
            }
            throw new IllegalArgumentException(
                    "标题 \"" + headingText + "\" 匹配到 " + matches.size()
                            + " 个章节。使用 --heading-index 指定目标（1-based）: " + sb);
        }
        return matches.get(0);
    }

    private static void validateInsertedContent(
            String action,
            SectionInfo target,
            String newContent,
            Integer newHeadingLevel) {
        if ("delete".equals(action)) {
            return;
        }
        if (!hasText(newContent)) {
            throw new IllegalArgumentException("非 delete 操作必须提供 content");
        }

        validateArticleBody(newContent);
        if (!"replace".equals(action)) {
            return;
        }

        List<SectionInfo> newSections = parseSections(newContent);
        if (newSections.isEmpty()) {
            throw new IllegalArgumentException("replace 内容必须包含章节标题");
        }

        SectionInfo replacement = newSections.get(0);
        if (!newContent.substring(0, replacement.startOffset).isBlank()) {
            throw new IllegalArgumentException("replace 内容必须从章节标题开始，标题前只能包含空白行");
        }

        int expectedLevel = newHeadingLevel != null ? newHeadingLevel : target.level;
        if (replacement.level != expectedLevel) {
            String guidance = newHeadingLevel == null
                    ? "默认必须保持原标题层级；如需主动调整 H2-H6，请显式提供 newHeadingLevel。"
                    : "新内容首标题必须与 newHeadingLevel 一致。";
            throw new IllegalArgumentException(
                    "章节标题层级不符合要求：目标为 H" + target.level
                            + "，新内容为 H" + replacement.level
                            + "，期望 H" + expectedLevel + "。" + guidance);
        }
    }

    /** 当 replace 操作显式改变层级且跨度超过 1 级时，返回警告信息（不阻止操作）。 */
    private static String detectLevelJumpWarning(String action, SectionInfo target, Integer newHeadingLevel) {
        if (!"replace".equals(action) || newHeadingLevel == null) {
            return null;
        }
        if (Math.abs(newHeadingLevel - target.level) > 1) {
            return "层级跳跃较大：从 H" + target.level + " 变为 H" + newHeadingLevel
                    + "，可能破坏大纲结构，请确认是否合理。";
        }
        return null;
    }

    private static String spliceContent(
            String content,
            int startOffset,
            int endOffset,
            String replacement,
            String lineSeparator) {
        String prefix = content.substring(0, startOffset);
        String suffix = content.substring(endOffset);
        if (replacement == null || replacement.isEmpty()) {
            return prefix + suffix;
        }

        StringBuilder result = new StringBuilder(
                prefix.length() + replacement.length() + suffix.length() + lineSeparator.length() * 2);
        result.append(prefix);
        if (!prefix.isEmpty() && !endsWithLineBreak(prefix) && !startsWithLineBreak(replacement)) {
            result.append(lineSeparator);
        }
        result.append(replacement);
        if (!suffix.isEmpty() && !endsWithLineBreak(replacement) && !startsWithLineBreak(suffix)) {
            result.append(lineSeparator);
        }
        return result.append(suffix).toString();
    }

    private static List<SectionInfo> parseSections(String content) {
        List<MarkdownLine> lines = splitMarkdownLines(content);
        List<SectionInfo> sections = new ArrayList<>();
        CodeFence activeFence = null;

        for (MarkdownLine line : lines) {
            if (activeFence != null) {
                if (isClosingCodeFence(line.content, activeFence)) {
                    activeFence = null;
                }
                continue;
            }

            CodeFence openingFence = parseOpeningCodeFence(line.content);
            if (openingFence != null) {
                activeFence = openingFence;
                continue;
            }

            HeadingInfo heading = parseHeading(line.content);
            if (heading != null) {
                sections.add(new SectionInfo(
                        line.lineNumber, line.startOffset, heading.level, heading.text));
            }
        }

        // O(n) 单遍扫描：用栈记录尚未闭合的章节，遇到同级或更高级标题时弹出并设置 endOffset
        java.util.Deque<SectionInfo> stack = new java.util.ArrayDeque<>();
        for (SectionInfo section : sections) {
            while (!stack.isEmpty() && stack.peek().level >= section.level) {
                stack.pop().endOffset = section.startOffset;
            }
            stack.push(section);
        }
        while (!stack.isEmpty()) {
            stack.pop().endOffset = content.length();
        }
        return sections;
    }

    private static List<MarkdownLine> splitMarkdownLines(String content) {
        List<MarkdownLine> lines = new ArrayList<>();
        int lineNumber = 0;
        int offset = 0;
        while (offset < content.length()) {
            int startOffset = offset;
            while (offset < content.length()
                    && content.charAt(offset) != '\r'
                    && content.charAt(offset) != '\n') {
                offset++;
            }
            int contentEndOffset = offset;
            if (offset < content.length()) {
                if (content.charAt(offset) == '\r'
                        && offset + 1 < content.length()
                        && content.charAt(offset + 1) == '\n') {
                    offset += 2;
                } else {
                    offset++;
                }
            }
            lines.add(new MarkdownLine(
                    lineNumber++, startOffset, content.substring(startOffset, contentEndOffset)));
        }
        return lines;
    }

    private static CodeFence parseOpeningCodeFence(String line) {
        int markerStart = markdownBlockStart(line);
        if (markerStart < 0 || markerStart >= line.length()) {
            return null;
        }

        char marker = line.charAt(markerStart);
        if (marker != '`' && marker != '~') {
            return null;
        }
        int markerEnd = markerStart;
        while (markerEnd < line.length() && line.charAt(markerEnd) == marker) {
            markerEnd++;
        }
        int length = markerEnd - markerStart;
        if (length < 3) {
            return null;
        }
        if (marker == '`' && line.substring(markerEnd).indexOf('`') >= 0) {
            return null;
        }
        return new CodeFence(marker, length);
    }

    private static boolean isClosingCodeFence(String line, CodeFence activeFence) {
        int markerStart = markdownBlockStart(line);
        if (markerStart < 0 || markerStart >= line.length()
                || line.charAt(markerStart) != activeFence.marker) {
            return false;
        }

        int markerEnd = markerStart;
        while (markerEnd < line.length() && line.charAt(markerEnd) == activeFence.marker) {
            markerEnd++;
        }
        if (markerEnd - markerStart < activeFence.length) {
            return false;
        }
        for (int index = markerEnd; index < line.length(); index++) {
            if (line.charAt(index) != ' ' && line.charAt(index) != '\t') {
                return false;
            }
        }
        return true;
    }

    private static HeadingInfo parseHeading(String line) {
        int headingStart = markdownBlockStart(line);
        if (headingStart < 0 || headingStart >= line.length() || line.charAt(headingStart) != '#') {
            return null;
        }

        int markerEnd = headingStart;
        while (markerEnd < line.length()
                && markerEnd - headingStart < 6
                && line.charAt(markerEnd) == '#') {
            markerEnd++;
        }
        if (markerEnd >= line.length()
                || (line.charAt(markerEnd) != ' ' && line.charAt(markerEnd) != '\t')) {
            return null;
        }

        String headingText = line.substring(markerEnd + 1).trim();
        if (!hasText(headingText)) {
            return null;
        }
        return new HeadingInfo(markerEnd - headingStart, headingText);
    }

    /** Markdown 块最多允许三个前导空格；四个空格及以上属于缩进代码块。 */
    private static int markdownBlockStart(String line) {
        int offset = 0;
        while (offset < line.length() && offset < 4 && line.charAt(offset) == ' ') {
            offset++;
        }
        return offset == 4 ? -1 : offset;
    }

    private static String detectPrimaryLineSeparator(String content) {
        int crlfCount = 0;
        int lfCount = 0;
        int crCount = 0;
        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);
            if (current == '\r') {
                if (index + 1 < content.length() && content.charAt(index + 1) == '\n') {
                    crlfCount++;
                    index++;
                } else {
                    crCount++;
                }
            } else if (current == '\n') {
                lfCount++;
            }
        }

        if (crlfCount >= lfCount && crlfCount >= crCount && crlfCount > 0) {
            return "\r\n";
        }
        if (lfCount >= crCount && lfCount > 0) {
            return "\n";
        }
        return crCount > 0 ? "\r" : "\n";
    }

    private static String normalizeLineSeparators(String value, String lineSeparator) {
        if (value == null) {
            return null;
        }
        return value.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace("\n", lineSeparator);
    }

    private static boolean startsWithLineBreak(String value) {
        return !value.isEmpty() && (value.charAt(0) == '\r' || value.charAt(0) == '\n');
    }

    private static boolean endsWithLineBreak(String value) {
        return !value.isEmpty()
                && (value.charAt(value.length() - 1) == '\r' || value.charAt(value.length() - 1) == '\n');
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static final class SectionInfo {
        private final int startLine;
        private final int startOffset;
        private final int level;
        private final String headingText;
        private int endOffset;

        private SectionInfo(int startLine, int startOffset, int level, String headingText) {
            this.startLine = startLine;
            this.startOffset = startOffset;
            this.level = level;
            this.headingText = headingText;
            this.endOffset = startOffset;
        }
    }

    private record MarkdownLine(int lineNumber, int startOffset, String content) {
    }

    private record HeadingInfo(int level, String text) {
    }

    private record CodeFence(char marker, int length) {
    }
}