package com.ld.poetry.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownSectionEditorTest {

    @Test
    void replacePreservesCrLfWithoutRebuildingUntouchedContent() {
        String original = "## First\r\n\r\nOld\r\n## Second\r\n\r\nKeep\r\n";

        String updated = update(original, "First", "replace", "## First\n\nNew", null);

        assertEquals("## First\r\n\r\nNew\r\n## Second\r\n\r\nKeep\r\n", updated);
        assertFalse(updated.replace("\r\n", "").contains("\n"));
    }

    @Test
    void shorterOrDifferentFenceCannotCloseActiveFence() {
        String original = """
                ## Example

                ````markdown
                ```java
                # This is code, not the article title
                ```
                ~~~~
                ## This is also inside the outer fence
                ~~~~
                ````

                ## Target

                Old
                """;

        String updated = update(original, "Target", "replace", "## Target\n\nNew", null);

        assertTrue(updated.contains("# This is code, not the article title"));
        assertTrue(updated.contains("## This is also inside the outer fence"));
        assertTrue(updated.endsWith("## Target\n\nNew"));
    }

    @Test
    void replacePreservesHeadingLevelByDefault() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> update("## Parent\n\nBody", "Parent", "replace", "### Parent\n\nBody", null));

        assertTrue(error.getMessage().contains("默认必须保持原标题层级"));
    }

    @Test
    void replaceAllowsExplicitHeadingLevelChangeWithinBodyRange() {
        String updated = update(
                "## Parent\n\nBody\n## Sibling\n\nKeep",
                "Parent",
                "replace",
                "### Parent\n\nBody",
                3);

        assertEquals("### Parent\n\nBody\n## Sibling\n\nKeep", updated);
    }

    @Test
    void articleBodyRejectsH1ButIgnoresH1InsideMatchingFence() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> MarkdownSectionEditor.validateArticleBody("# Duplicate page title\n\nBody"));
        assertTrue(error.getMessage().contains("唯一 H1"));

        MarkdownSectionEditor.validateArticleBody("""
                ## Example

                ~~~~markdown
                # Code sample
                ~~~~
                """);
    }

    @Test
    void explicitHeadingLevelIsReplaceOnlyAndCannotBeH1() {
        assertThrows(
                IllegalArgumentException.class,
                () -> update("## A\n", "A", "insert_after", "## B\n", 3));
        assertThrows(
                IllegalArgumentException.class,
                () -> update("## A\n", "A", "replace", "# A\n", 1));
    }

    @Test
    void insertAfterAppendsNewSectionAfterTarget() {
        String original = "## First\n\nBody1\n## Second\n\nBody2\n";
        String updated = update(original, "First", "insert_after", "## Inserted\n\nNew\n", null);
        assertTrue(updated.contains("## Inserted"));
        assertTrue(updated.indexOf("## Inserted") > original.indexOf("## First"));
        assertTrue(updated.contains("## Second"));
    }

    @Test
    void insertBeforeAddsSectionBeforeTarget() {
        String original = "## First\n\nBody1\n## Second\n\nBody2\n";
        String updated = update(original, "Second", "insert_before", "## Before\n\nNew\n", null);
        assertTrue(updated.indexOf("## Before") < updated.indexOf("## Second"));
        assertTrue(updated.contains("## First"));
    }

    @Test
    void deleteRemovesTargetSection() {
        String original = "## First\n\nBody1\n## Second\n\nBody2\n## Third\n\nBody3\n";
        String updated = update(original, "Second", "delete", null, null);
        assertFalse(updated.contains("## Second"));
        assertFalse(updated.contains("Body2"));
        assertTrue(updated.contains("## First"));
        assertTrue(updated.contains("## Third"));
    }

    @Test
    void appendAddsContentAtEnd() {
        String original = "## First\n\nBody1\n";
        String updated = update(original, null, "append", "## Appended\n\nNew\n", null);
        assertTrue(updated.endsWith("## Appended\n\nNew\n"));
        assertTrue(updated.contains("## First"));
    }

    @Test
    void duplicateHeadingThrowsAmbiguousError() {
        String original = "## Section\n\nBody1\n## Section\n\nBody2\n";
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> update(original, "Section", "delete", null, null));
        assertTrue(error.getMessage().contains("匹配到 2 个章节"));
    }

    @Test
    void nonExistentHeadingThrowsNotFoundError() {
        String original = "## First\n\nBody1\n";
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> update(original, "Nonexistent", "replace", "## New\n\nBody\n", null));
        assertTrue(error.getMessage().contains("未找到匹配的标题"));
    }

    @Test
    void setextH1IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MarkdownSectionEditor.validateArticleBody("Title\n=====\n\nBody"));
    }

    @Test
    void htmlH1IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MarkdownSectionEditor.validateArticleBody("<h1>Title</h1>\n\nBody"));
    }

    private String update(
            String original,
            String heading,
            String action,
            String content,
            Integer newHeadingLevel) {
        return MarkdownSectionEditor.apply(
                original,
                new MarkdownSectionEditor.SectionUpdate(
                        heading, action, content, newHeadingLevel));
    }
}