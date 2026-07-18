package com.ld.poetry.service.prerender;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 预渲染 HTML 与前端构建产物一致性校验器。
 *
 * <p>预渲染 HTML 会固化 index.html 中的 &lt;script src="/static/*.js"&gt; 与
 * &lt;link href="/static/*.css"&gt; 引用。前端重新构建后文件名 hash 变化，
 * 旧预渲染 HTML 引用的资源将 404。本组件扫描预渲染输出目录下所有 HTML，
 * 校验其中引用的本地静态资源是否实际存在于 web-dist 根目录下。
 *
 * <p>仅校验 /static/ 开头的本地资源，外部 CDN URL（https://...）忽略。
 *
 * @author LeapYa
 * @since 2026-07-19
 */
@Service
@Slf4j
public class PrerenderAssetConsistencyChecker {

    /** 匹配 <script src=".../static/*.js"> 与 <link href=".../static/*.css"> 中的资源 URL */
    private static final Pattern STATIC_ASSET_PATTERN = Pattern.compile(
            "(?:src|href)\\s*=\\s*[\"']([^\"']*/static/[^\"']*\\.(?:js|css))(?:[?#][^\"']*)?[\"']",
            Pattern.CASE_INSENSITIVE);

    @Value("${prerender.output-root:/app/web-dist/prerender}")
    private String outputRoot;

    @Value("${prerender.admin-output-root:/app/admin-dist/prerender}")
    private String adminOutputRoot;

    @Value("${prerender.template-path:/app/web-dist/index.html}")
    private String templatePath;

    @Value("${prerender.admin-template-path:/app/admin-dist/index.html}")
    private String adminTemplatePath;

    /**
     * 校验前台与后台预渲染 HTML 中引用的本地静态资源是否都存在。
     *
     * <p>任一预渲染目录不存在时跳过该目录（视为一致，不阻塞启动）。
     *
     * @return 不一致资源列表（空集合表示一致）
     */
    public ConsistencyResult check() {
        Set<String> missing = new TreeSet<>();
        int webScanned = scanDirectory(Path.of(outputRoot), Path.of(templatePath).getParent(), missing);
        int adminScanned = scanDirectory(Path.of(adminOutputRoot), Path.of(adminTemplatePath).getParent(), missing);

        int totalScanned = webScanned + adminScanned;
        if (missing.isEmpty()) {
            log.info("预渲染资源一致性校验通过，扫描 {} 个 HTML 文件", totalScanned);
        } else {
            log.warn("预渲染 HTML 引用了 {} 个不存在的静态资源: {}", missing.size(), missing);
        }
        return new ConsistencyResult(totalScanned, Set.copyOf(missing));
    }

    /**
     * 扫描指定预渲染目录下所有 HTML，校验资源引用。
     *
     * @return 扫描的 HTML 文件数
     */
    private int scanDirectory(Path prerenderRoot, Path distRoot, Set<String> missing) {
        if (prerenderRoot == null || !Files.isDirectory(prerenderRoot)) {
            return 0;
        }
        if (distRoot == null) {
            log.warn("构建产物根目录无法解析，跳过校验: {}", prerenderRoot);
            return 0;
        }

        int scanned = 0;
        try (Stream<Path> stream = Files.walk(prerenderRoot)) {
            var htmlFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".html"))
                    .toList();

            for (Path htmlFile : htmlFiles) {
                String html = Files.readString(htmlFile, StandardCharsets.UTF_8);
                Matcher matcher = STATIC_ASSET_PATTERN.matcher(html);
                while (matcher.find()) {
                    String url = matcher.group(1);
                    String relativePath = extractStaticRelativePath(url);
                    if (relativePath == null) {
                        continue;
                    }
                    Path assetFile = distRoot.resolve(relativePath);
                    if (!Files.exists(assetFile)) {
                        missing.add(url);
                    }
                }
                scanned++;
            }
        } catch (IOException e) {
            log.warn("扫描预渲染目录失败，跳过该目录: {} ({})", prerenderRoot, e.getMessage());
        }
        return scanned;
    }

    /**
     * 从 URL 中提取 /static/ 开头的相对路径（用于在 distRoot 下定位文件）。
     * 形如 /static/pb.abc123.js → static/pb.abc123.js
     */
    private static String extractStaticRelativePath(String url) {
        String clean = url.split("[?#]", 2)[0];
        int idx = clean.indexOf("/static/");
        if (idx < 0) {
            return null;
        }
        return clean.substring(idx + 1);
    }

    /**
     * 一致性校验结果。
     *
     * @param scannedFiles 扫描的 HTML 文件总数
     * @param missingAssets 不存在的资源 URL 集合（空集合表示一致）
     */
    public record ConsistencyResult(int scannedFiles, Set<String> missingAssets) {
        public static ConsistencyResult consistent() {
            return new ConsistencyResult(0, Set.of());
        }

        public boolean isConsistent() {
            return missingAssets.isEmpty();
        }
    }
}
