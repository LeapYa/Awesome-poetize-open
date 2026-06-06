package com.ld.poetry.service.prerender;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrerenderEngineTest {

    @TempDir
    Path tempDir;

    @Test
    void renderMarkdownMatchesFrontEndSoftBreaksWithoutAutolinkingBareUrls() {
        PrerenderEngine engine = createEngine();

        String markdown = "Line 1\nLine 2\n\nhttps://example.com\n\n| a | b |\n| - | - |\n| 1 | 2 |";
        String html = engine.renderMarkdown(markdown);

        assertTrue(html.contains("Line 1<br />\nLine 2"));
        assertTrue(html.contains("<p>https://example.com</p>"));
        assertFalse(html.contains("<a href=\"https://example.com\">https://example.com</a>"));
        assertTrue(html.contains("<table>"));
    }

    @Test
    void replaceTitlePreservesQuotes() {
        PrerenderEngine engine = createEngine();
        String htmlTemplate = "<html><head><title>Old</title></head><body></body></html>";
        String title = "测试“双引号”与[方括号]是否会被转义 & < >";
        String result = org.springframework.test.util.ReflectionTestUtils.invokeMethod(engine, "replaceTitle", htmlTemplate, title);
        assertTrue(result.contains("<title>测试“双引号”与[方括号]是否会被转义 &amp; &lt; &gt;</title>"));
    }

    @Test
    void buildPageInjectsSeoTagsAndRenderedContent() throws IOException {
        Path templatePath = tempDir.resolve("index.html");
        Files.writeString(templatePath, """
                <!doctype html>
                <html>
                <head>
                  <meta name="description" content="old">
                  <link rel="icon" id="default-favicon" href="/favicon.ico">
                  <title>Old</title>
                </head>
                <body>
                  <div id="app"></div>
                </body>
                </html>
                """, StandardCharsets.UTF_8);

        PrerenderEngine engine = createEngine();
        ReflectionTestUtils.setField(engine, "templatePath", templatePath.toString());
        ReflectionTestUtils.setField(engine, "outputRoot", tempDir.resolve("prerender").toString());

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("description", "new description");
        meta.put("canonical", "https://example.com/article/1");
        meta.put("site_icon", "/logo.png");
        meta.put("structured_data", "{\"@type\":\"WebSite\"}");
        meta.put("google_site_verification", "verify-token");
        meta.put("custom_head_code", "<meta name=\"custom-head\" content=\"1\">");

        String html = engine.buildPage(PrerenderPageData.builder()
                .title("Article Title")
                .meta(meta)
                .content("<section>hello</section>")
                .lang("en")
                .pageType("article")
                .build());

        assertTrue(html.contains("<html lang=\"en\">"));
        assertTrue(html.contains("<title>Article Title</title>"));
        assertTrue(html.contains("<link rel=\"canonical\" href=\"https://example.com/article/1\">"));
        assertTrue(html.contains("href=\"/logo.png\""));
        assertTrue(html.contains("rel=\"icon\""));
        assertTrue(html.contains("sizes=\"16x16 32x32 48x48\""));
        assertTrue(html.contains("type=\"image/png\""));
        assertTrue(html.contains("<script type=\"application/ld+json\" data-prerender-structured-data=\"true\">{\"@type\":\"WebSite\"}</script>"));
        assertTrue(html.contains("<meta name=\"google-site-verification\" content=\"verify-token\">"));
        assertTrue(html.contains("<meta name=\"custom-head\" content=\"1\">"));
        assertTrue(html.contains("<body data-prerender-type=\"article\" data-prerender-lang=\"en\">"));
        assertTrue(html.contains("<div id=\"app\" class=\"article-detail\"><section>hello</section></div>"));
    }

    @Test
    void writeAndDeleteRenderedPagesUseExpectedPaths() throws IOException {
        PrerenderEngine engine = createEngine();
        Path outputRoot = tempDir.resolve("prerender");
        ReflectionTestUtils.setField(engine, "outputRoot", outputRoot.toString());

        engine.writePage("article/42", "en", "<html>article</html>");
        engine.writePage("sort", "zh", "<html>sort</html>");

        Path articlePath = outputRoot.resolve("article/42/index-en.html");
        Path sortIndexPath = outputRoot.resolve("sort/index.html");
        assertTrue(Files.exists(articlePath));
        assertTrue(Files.exists(sortIndexPath));

        engine.deleteIndexFiles("sort");
        assertFalse(Files.exists(sortIndexPath));

        engine.deletePage("article/42");
        assertFalse(Files.exists(articlePath));
    }

    private PrerenderEngine createEngine() {
        return new PrerenderEngine(new ObjectMapper());
    }
}
