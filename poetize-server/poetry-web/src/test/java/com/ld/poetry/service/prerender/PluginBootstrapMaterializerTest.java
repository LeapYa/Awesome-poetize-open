package com.ld.poetry.service.prerender;

import com.ld.poetry.service.PluginBootstrapDataProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginBootstrapMaterializerTest {

    @TempDir
    Path tempDir;

    private final PluginBootstrapDataProvider dataProvider = mock(PluginBootstrapDataProvider.class);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void materializeReplacesPlaceholderInIndexHtml() throws IOException {
        Path templatePath = tempDir.resolve("index.html");
        Files.writeString(templatePath, """
                <!doctype html>
                <html>
                <head><title>Site</title></head>
                <body>
                  <!--PB_BOOTSTRAP-->
                  <div id="app"></div>
                </body>
                </html>
                """, StandardCharsets.UTF_8);
        Path outputDir = tempDir.resolve("static");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("activePlugins", java.util.List.of());
        data.put("version", "1.0.0");
        when(dataProvider.buildBootstrapData()).thenReturn(data);

        PluginBootstrapMaterializer materializer = newMaterializer(templatePath, outputDir);
        materializer.materialize();

        String html = Files.readString(templatePath, StandardCharsets.UTF_8);
        assertFalse(html.contains(PluginBootstrapMaterializer.PLUGIN_BOOTSTRAP_PLACEHOLDER),
                "占位符必须被替换为 script 引用");
        assertTrue(html.contains("<script src=\"/static/pb."),
                "应注入 pb.*.js script 引用");

        String injectedScript = extractScriptSrc(html);
        assertTrue(injectedScript.startsWith("/static/pb.") && injectedScript.endsWith(".js"),
                "script src 应为 /static/pb.<hash>.js 形式，实际: " + injectedScript);

        Path jsFile = outputDir.resolve(injectedScript.substring("/static/".length()));
        assertTrue(Files.isRegularFile(jsFile), "pb.*.js 文件应被写入");
        String jsContent = Files.readString(jsFile, StandardCharsets.UTF_8);
        assertTrue(jsContent.startsWith("window.__PB__=") && jsContent.endsWith(";"),
                "JS 文件内容应为 window.__PB__=<json>;");
    }

    @Test
    void materializeUpdatesExistingScriptRefToNewHash() throws IOException {
        Path templatePath = tempDir.resolve("index.html");
        // 模拟已被物化过的 index.html，引用旧 hash
        String oldRef = "<script src=\"/static/pb.aaaa11112222.js\"></script>";
        Files.writeString(templatePath, """
                <!doctype html>
                <html>
                <head><title>Site</title></head>
                <body>
                  """ + oldRef + """
                  <div id="app"></div>
                </body>
                </html>
                """, StandardCharsets.UTF_8);
        Path outputDir = tempDir.resolve("static");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("version", "2.0.0");
        when(dataProvider.buildBootstrapData()).thenReturn(data);

        PluginBootstrapMaterializer materializer = newMaterializer(templatePath, outputDir);
        materializer.materialize();

        String html = Files.readString(templatePath, StandardCharsets.UTF_8);
        assertFalse(html.contains("pb.aaaa11112222.js"),
                "旧 hash 引用必须被替换");
        String newRef = extractScriptSrc(html);
        assertTrue(newRef.startsWith("/static/pb.") && !newRef.contains("aaaa11112222"),
                "应替换为新 hash 引用，实际: " + newRef);
    }

    @Test
    void ensureMaterializedSkipsWhenScriptAlreadyPresent() throws IOException {
        Path templatePath = tempDir.resolve("index.html");
        // index.html 已含合法 pb.<hex>.js 引用，应跳过物化
        Files.writeString(templatePath, """
                <!doctype html>
                <html>
                <head><title>Site</title></head>
                <body>
                  <script src="/static/pb.0123456789ab.js"></script>
                  <div id="app"></div>
                </body>
                </html>
                """, StandardCharsets.UTF_8);
        Path outputDir = tempDir.resolve("static");

        PluginBootstrapMaterializer materializer = newMaterializer(templatePath, outputDir);
        materializer.ensureMaterialized();

        verify(dataProvider, never()).buildBootstrapData();
    }

    @Test
    void ensureMaterializedTriggersMaterializeWhenPlaceholderPresent() throws IOException {
        Path templatePath = tempDir.resolve("index.html");
        Files.writeString(templatePath, """
                <!doctype html>
                <html>
                <head><title>Site</title></head>
                <body>
                  <!--PB_BOOTSTRAP-->
                  <div id="app"></div>
                </body>
                </html>
                """, StandardCharsets.UTF_8);
        Path outputDir = tempDir.resolve("static");

        when(dataProvider.buildBootstrapData()).thenReturn(Map.of("version", "1.0.0"));

        PluginBootstrapMaterializer materializer = newMaterializer(templatePath, outputDir);
        materializer.ensureMaterialized();

        verify(dataProvider).buildBootstrapData();
        String html = Files.readString(templatePath, StandardCharsets.UTF_8);
        assertFalse(html.contains(PluginBootstrapMaterializer.PLUGIN_BOOTSTRAP_PLACEHOLDER),
                "ensureMaterialized 触发物化后占位符应被替换");
    }

    private PluginBootstrapMaterializer newMaterializer(Path templatePath, Path outputDir) {
        PluginBootstrapMaterializer materializer = new PluginBootstrapMaterializer(dataProvider, jsonMapper);
        ReflectionTestUtils.setField(materializer, "templatePath", templatePath.toString());
        ReflectionTestUtils.setField(materializer, "outputDir", outputDir.toString());
        ReflectionTestUtils.setField(materializer, "enabled", true);
        return materializer;
    }

    private static String extractScriptSrc(String html) {
        int idx = html.indexOf("<script src=\"/static/pb.");
        int end = html.indexOf("\"></script>", idx);
        return html.substring(idx + "<script src=\"".length(), end);
    }
}
