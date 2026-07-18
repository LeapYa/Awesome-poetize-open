package com.ld.poetry.service.prerender;

import com.ld.poetry.service.prerender.PrerenderAssetConsistencyChecker.ConsistencyResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrerenderAssetConsistencyCheckerTest {

    @TempDir
    Path tempDir;

    @Test
    void checkReturnsConsistentWhenPrerenderDirMissing() {
        PrerenderAssetConsistencyChecker checker = newChecker(
                tempDir.resolve("no-prerender").toString(),
                tempDir.resolve("no-admin-prerender").toString(),
                tempDir.resolve("web-dist/index.html").toString(),
                tempDir.resolve("admin-dist/index.html").toString());

        ConsistencyResult result = checker.check();

        assertTrue(result.isConsistent());
        assertEquals(0, result.scannedFiles());
    }

    @Test
    void checkReturnsConsistentWhenAllReferencedAssetsExist() throws IOException {
        Path webDist = tempDir.resolve("web-dist");
        Path prerender = webDist.resolve("prerender");
        Files.createDirectories(prerender);
        Files.createDirectories(webDist.resolve("static/js"));
        Files.createDirectories(webDist.resolve("static/css"));
        Files.writeString(webDist.resolve("static/js/app.abc123.js"), "js");
        Files.writeString(webDist.resolve("static/css/index.def456.css"), "css");
        Files.writeString(prerender.resolve("index.html"), """
                <html><head>
                  <link rel="stylesheet" href="/static/css/index.def456.css">
                  <script src="/static/js/app.abc123.js"></script>
                </head><body></body></html>
                """, StandardCharsets.UTF_8);

        PrerenderAssetConsistencyChecker checker = newChecker(
                prerender.toString(),
                tempDir.resolve("no-admin-prerender").toString(),
                webDist.resolve("index.html").toString(),
                tempDir.resolve("admin-dist/index.html").toString());

        ConsistencyResult result = checker.check();

        assertTrue(result.isConsistent(), "所有引用资源存在时应返回一致");
        assertEquals(1, result.scannedFiles());
    }

    @Test
    void checkDetectsMissingJsAsset() throws IOException {
        Path webDist = tempDir.resolve("web-dist");
        Path prerender = webDist.resolve("prerender");
        Files.createDirectories(prerender);
        Files.writeString(prerender.resolve("index.html"), """
                <html><head>
                  <script src="/static/js/app.oldhash.js"></script>
                </head><body></body></html>
                """, StandardCharsets.UTF_8);

        PrerenderAssetConsistencyChecker checker = newChecker(
                prerender.toString(),
                tempDir.resolve("no-admin-prerender").toString(),
                webDist.resolve("index.html").toString(),
                tempDir.resolve("admin-dist/index.html").toString());

        ConsistencyResult result = checker.check();

        assertFalse(result.isConsistent(), "引用的 js 不存在时应返回不一致");
        assertTrue(result.missingAssets().contains("/static/js/app.oldhash.js"),
                "missingAssets 应包含缺失的 js 引用");
    }

    @Test
    void checkDetectsMissingCssAsset() throws IOException {
        Path webDist = tempDir.resolve("web-dist");
        Path prerender = webDist.resolve("prerender");
        Files.createDirectories(prerender);
        Files.writeString(prerender.resolve("index.html"), """
                <html><head>
                  <link rel="stylesheet" href="/static/css/missing.deadbeef.css">
                </head><body></body></html>
                """, StandardCharsets.UTF_8);

        PrerenderAssetConsistencyChecker checker = newChecker(
                prerender.toString(),
                tempDir.resolve("no-admin-prerender").toString(),
                webDist.resolve("index.html").toString(),
                tempDir.resolve("admin-dist/index.html").toString());

        ConsistencyResult result = checker.check();

        assertFalse(result.isConsistent());
        assertTrue(result.missingAssets().contains("/static/css/missing.deadbeef.css"));
    }

    @Test
    void checkIgnoresExternalCdnUrls() throws IOException {
        Path webDist = tempDir.resolve("web-dist");
        Path prerender = webDist.resolve("prerender");
        Files.createDirectories(prerender);
        Files.writeString(prerender.resolve("index.html"), """
                <html><head>
                  <script src="https://cdn.jsdelivr.net/npm/vue@3/dist/vue.global.js"></script>
                  <link rel="stylesheet" href="https://fonts.googleapis.com/css?family=Roboto">
                </head><body></body></html>
                """, StandardCharsets.UTF_8);

        PrerenderAssetConsistencyChecker checker = newChecker(
                prerender.toString(),
                tempDir.resolve("no-admin-prerender").toString(),
                webDist.resolve("index.html").toString(),
                tempDir.resolve("admin-dist/index.html").toString());

        ConsistencyResult result = checker.check();

        assertTrue(result.isConsistent(), "外部 CDN URL 不应参与校验");
    }

    @Test
    void checkScansNestedHtmlFilesAndDeduplicatesMissingAssets() throws IOException {
        Path webDist = tempDir.resolve("web-dist");
        Path prerender = webDist.resolve("prerender");
        Path articleDir = prerender.resolve("article/42");
        Files.createDirectories(articleDir);
        // 两个 HTML 引用同一个缺失资源
        Files.writeString(prerender.resolve("index.html"),
                "<script src=\"/static/pb.missing.js\"></script>", StandardCharsets.UTF_8);
        Files.writeString(articleDir.resolve("index.html"),
                "<script src=\"/static/pb.missing.js\"></script>", StandardCharsets.UTF_8);

        PrerenderAssetConsistencyChecker checker = newChecker(
                prerender.toString(),
                tempDir.resolve("no-admin-prerender").toString(),
                webDist.resolve("index.html").toString(),
                tempDir.resolve("admin-dist/index.html").toString());

        ConsistencyResult result = checker.check();

        assertEquals(2, result.scannedFiles(), "应递归扫描嵌套 HTML");
        assertEquals(1, result.missingAssets().size(), "相同缺失资源应去重");
        assertTrue(result.missingAssets().contains("/static/pb.missing.js"));
    }

    @Test
    void checkStripsQueryStringFromAssetUrl() throws IOException {
        Path webDist = tempDir.resolve("web-dist");
        Path prerender = webDist.resolve("prerender");
        Files.createDirectories(prerender);
        Files.createDirectories(webDist.resolve("static"));
        Files.writeString(webDist.resolve("static/app.v1.js"), "js");
        Files.writeString(prerender.resolve("index.html"),
                "<script src=\"/static/app.v1.js?v=20260719\"></script>", StandardCharsets.UTF_8);

        PrerenderAssetConsistencyChecker checker = newChecker(
                prerender.toString(),
                tempDir.resolve("no-admin-prerender").toString(),
                webDist.resolve("index.html").toString(),
                tempDir.resolve("admin-dist/index.html").toString());

        ConsistencyResult result = checker.check();

        assertTrue(result.isConsistent(), "查询参数应被剥离后再校验文件存在性");
    }

    @Test
    void checkScansAdminPrerenderDir() throws IOException {
        Path adminDist = tempDir.resolve("admin-dist");
        Path adminPrerender = adminDist.resolve("prerender");
        Files.createDirectories(adminPrerender);
        Files.writeString(adminPrerender.resolve("index.html"),
                "<script src=\"/static/admin.oldhash.js\"></script>", StandardCharsets.UTF_8);

        PrerenderAssetConsistencyChecker checker = newChecker(
                tempDir.resolve("no-web-prerender").toString(),
                adminPrerender.toString(),
                tempDir.resolve("web-dist/index.html").toString(),
                adminDist.resolve("index.html").toString());

        ConsistencyResult result = checker.check();

        assertFalse(result.isConsistent(), "后台预渲染目录也应被校验");
        assertTrue(result.missingAssets().contains("/static/admin.oldhash.js"));
    }

    private PrerenderAssetConsistencyChecker newChecker(String outputRoot, String adminOutputRoot,
                                                        String templatePath, String adminTemplatePath) {
        PrerenderAssetConsistencyChecker checker = new PrerenderAssetConsistencyChecker();
        ReflectionTestUtils.setField(checker, "outputRoot", outputRoot);
        ReflectionTestUtils.setField(checker, "adminOutputRoot", adminOutputRoot);
        ReflectionTestUtils.setField(checker, "templatePath", templatePath);
        ReflectionTestUtils.setField(checker, "adminTemplatePath", adminTemplatePath);
        return checker;
    }
}
