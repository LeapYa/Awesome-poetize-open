package com.ld.poetry.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.fontbox.ttf.CmapSubtable;
import org.apache.fontbox.ttf.CmapTable;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 字体子集化服务。
 * <p>
 * 运行时调用 cn-font-split，生成 font.css + 多个细粒度 woff2 分片，
 * 由前端直接按 unicode-range 自动按需加载。
 */
@Slf4j
@Service
public class FontSubsetService {

    private static final String DEFAULT_FONT_FAMILY = "MyAwesomeFont";
    private static final String DEFAULT_CSS_FILE_NAME = "font.css";
    private static final String MODULE_PATH_ENV = "CN_FONT_SPLIT_MODULE_PATH";
    private static final String GH_HOST_ENV = "CN_FONT_SPLIT_GH_HOST";
    private static final int DEFAULT_CHUNK_SIZE = 48 * 1024;

    /**
     * 用于给 font.css 中引用 woff2 的 url(...) 追加缓存失效参数 ?v= 的正则。
     * 仅匹配 .woff2 资源，避免误伤 svg / data: 等其它 url()。
     */
    private static final Pattern FONT_URL_PATTERN =
            Pattern.compile("url\\((['\"]?)([^)'\"\\s]+?\\.woff2)\\1\\)");

    @Value("${local.uploadUrl:/app/static/}")
    private String uploadUrl;

    /**
     * Docker compose 中 java-backend 容器的 RESOURCE_AVAILABILITY_STATICROOTS 环境变量值。
     * 仅在自动发现失败时使用。
     */
    @Value("${resource.availability.staticRoots:${RESOURCE_AVAILABILITY_STATICROOTS:}}")
    private String configuredStaticRoots;

    private Path runnerScriptPath;

    @PostConstruct
    public void init() {
        try {
            runnerScriptPath = extractRunnerScript();
            log.info("cn-font-split 运行脚本已准备: {}", runnerScriptPath);
        } catch (IOException e) {
            log.error("初始化 cn-font-split 运行脚本失败", e);
        }
    }

    /**
     * 执行字体子集化。
     *
     * @param ttfData   上传的字体文件字节
     * @param outputDir 输出目录 (font_chunks)
     * @return 处理结果摘要
     */
    public Map<String, Object> subsetFont(byte[] ttfData, Path outputDir) throws IOException {
        long startTime = System.currentTimeMillis();
        int totalChars = countFontChars(ttfData);

        Files.createDirectories(outputDir);
        cleanGeneratedFiles(outputDir);

        Path tempFontFile = Files.createTempFile("poetize-font-upload-", ".ttf");
        Files.write(tempFontFile, ttfData);

        try {
            executeCnFontSplit(tempFontFile, outputDir);
        } finally {
            Files.deleteIfExists(tempFontFile);
        }

        List<Path> chunkFiles = listChunkFiles(outputDir);
        Path cssFile = outputDir.resolve(DEFAULT_CSS_FILE_NAME);
        long cssFileSize = Files.exists(cssFile) ? Files.size(cssFile) : 0L;
        long totalGeneratedSize = cssFileSize;
        Map<String, Long> fileSizes = new LinkedHashMap<>();

        if (Files.exists(cssFile)) {
            fileSizes.put(DEFAULT_CSS_FILE_NAME, cssFileSize);
        }

        for (Path chunkFile : chunkFiles) {
            long size = Files.size(chunkFile);
            totalGeneratedSize += size;
            fileSizes.put(chunkFile.getFileName().toString(), size);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("cn-font-split 字体子集化完成, 分片 {} 个, 耗时 {} ms", chunkFiles.size(), elapsed);

        // 生成资源版本号（时间戳），用于给字体静态文件追加缓存失效参数 ?v=，
        // 解决「重新上传字体后浏览器仍使用旧缓存，必须 Ctrl+Shift+R 才能看到新字体」的问题。
        String fontVersion = String.valueOf(System.currentTimeMillis());
        try {
            rewriteFontCssWithVersion(outputDir, fontVersion);
        } catch (IOException e) {
            log.warn("为 font.css 追加缓存失效参数失败，前台可能仍需硬刷新才能看到新字体: {}", outputDir, e);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("engine", "cn-font-split");
        result.put("elapsedMs", elapsed);
        result.put("originalSize", ttfData.length);
        result.put("totalChars", totalChars);
        result.put("chunkCount", chunkFiles.size());
        result.put("cssFile", DEFAULT_CSS_FILE_NAME);
        result.put("cssFileSize", cssFileSize);
        result.put("generatedSize", totalGeneratedSize);
        result.put("fileSizes", fileSizes);
        result.put("version", fontVersion);
        result.put("outputDir", outputDir.toString());
        return result;
    }

    /**
     * 获取默认的 font_chunks 输出目录（用户上传的自定义字体目录，位于上传卷）。
     */
    public Path getDefaultOutputDir() {
        String basePath = uploadUrl.endsWith("/") ? uploadUrl : uploadUrl + "/";
        return Path.of(basePath, "assets", "font_chunks");
    }

    /**
     * 获取内置默认字体分片目录（前端构建产物里的 font_chunks）。
     * 仅当用户未上传自定义字体、又想下载原始字体上传到 CDN 时使用。
     */
    public Path getBuiltinOutputDir() {
        return resolveStaticRoot().resolve("assets").resolve("font_chunks");
    }

    /**
     * 下载/打包时使用的实际目录：
     * 优先用户上传的自定义字体目录；若为空则回退到内置默认字体分片目录。
     */
    public Path getEffectiveOutputDir() {
        Path uploadDir = getDefaultOutputDir();
        if (hasFontFiles(uploadDir)) {
            return uploadDir;
        }
        return getBuiltinOutputDir();
    }

    private boolean hasFontFiles(Path dir) {
        if (!Files.exists(dir)) {
            return false;
        }
        // 必须存在 .woff2 才算有效字体分片目录（仅剩 font.css / metadata 的目录不应被判定为可用，
        // 否则会下载到无字体数据的空包）；并使用 Files.walk 递归扫描，与 collectFontChunkFiles 保持一致
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile)
                    .anyMatch(p -> p.getFileName().toString().toLowerCase().endsWith(".woff2"));
        } catch (IOException e) {
            log.warn("读取字体分片目录失败: {}", dir, e);
            return false;
        }
    }

    /**
     * 解析前端构建产物的静态根目录，与 {@code ResourceAvailabilityService} /
     * {@code ResourceReplaceService} 的发现逻辑保持一致：从 JVM 工作目录逐级向上遍历父目录，
     * 在每一层尝试 web / admin 的 dist/static、public/static 等子路径。
     */
    private Path resolveStaticRoot() {
        // 1) Docker compose / 显式配置的静态根目录（RESOURCE_AVAILABILITY_STATICROOTS）
        if (StringUtils.hasText(configuredStaticRoots)) {
            for (String root : configuredStaticRoots.split(",")) {
                String trimmed = root.trim();
                if (!trimmed.isEmpty()) {
                    Path candidate = Paths.get(trimmed);
                    if (Files.isDirectory(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        // 2) 从当前工作目录向上遍历，尝试常见前端构建产物子目录（对齐 ResourceAvailabilityService）
        Path currentPath = Paths.get("").toAbsolutePath().normalize();
        for (Path path = currentPath; path != null; path = path.getParent()) {
            for (String variant : new String[]{
                    "public/static", "public",
                    "dist/static", "dist",
                    "web-dist/static",
                    "poetize-web/public/static", "poetize-web/public",
                    "poetize-web/dist/static", "poetize-web/dist",
                    "poetize-admin/public/static", "poetize-admin/public",
                    "poetize-admin/dist/static", "poetize-admin/dist"
            }) {
                Path candidate = path.resolve(variant);
                if (Files.isDirectory(candidate)) {
                    return candidate;
                }
            }
        }
        // 3) 最终回退：Docker 默认路径
        return Paths.get("/app/web-dist/static");
    }

    /**
     * 获取当前字体文件状态。
     */
    public Map<String, Object> getStatus() {
        Path outputDir = getDefaultOutputDir();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("outputDir", outputDir.toString());

        Map<String, Object> files = new LinkedHashMap<>();
        List<Path> chunkFiles = listChunkFiles(outputDir);
        long totalSize = 0L;

        if (Files.exists(outputDir)) {
            try (Stream<Path> stream = Files.list(outputDir)) {
                List<Path> existingFiles = stream
                        .filter(Files::isRegularFile)
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .toList();

                for (Path file : existingFiles) {
                    long size = Files.size(file);
                    files.put(file.getFileName().toString(), Map.of("exists", true, "size", size));
                    totalSize += size;
                }
            } catch (IOException e) {
                log.error("读取字体状态失败: {}", outputDir, e);
            }
        }

        Path cssFile = outputDir.resolve(DEFAULT_CSS_FILE_NAME);
        boolean cssReady = Files.exists(cssFile) && !chunkFiles.isEmpty();
        boolean legacyReady = hasLegacySubsetFiles(outputDir);
        long cssFileSize = 0L;

        try {
            if (Files.exists(cssFile)) {
                cssFileSize = Files.size(cssFile);
            }
        } catch (IOException e) {
            log.warn("读取字体 CSS 大小失败: {}", cssFile, e);
        }

        status.put("engine", cssReady ? "cn-font-split" : (legacyReady ? "legacy" : "none"));
        status.put("cssFile", Files.exists(cssFile) ? DEFAULT_CSS_FILE_NAME : null);
        status.put("cssFileSize", cssFileSize);
        status.put("chunkCount", cssReady ? chunkFiles.size() : (legacyReady ? 4 : 0));
        status.put("totalSize", totalSize);
        status.put("files", files);
        status.put("ready", cssReady || legacyReady);
        // 与下载端点的内置字体回退逻辑保持一致：区分下载来源 custom / builtin / none，
        // ready 仅表示是否存在「自定义」字体，downloadReady 表示能否下载（含内置默认）
        String source;
        boolean downloadReady;
        if (cssReady || legacyReady) {
            source = "custom";
            downloadReady = true;
        } else if (hasFontFiles(getBuiltinOutputDir())) {
            source = "builtin";
            downloadReady = true;
        } else {
            source = "none";
            downloadReady = false;
        }
        status.put("source", source);
        status.put("downloadReady", downloadReady);
        return status;
    }

    /**
     * 清理已生成的字体文件。
     */
    public boolean cleanSubsets() {
        Path outputDir = getDefaultOutputDir();
        if (!Files.exists(outputDir)) {
            return true;
        }

        try {
            cleanGeneratedFiles(outputDir);
            return true;
        } catch (IOException e) {
            log.error("清理字体子集文件失败", e);
            return false;
        }
    }

    /**
     * 将 font_chunks 目录打包为 ZIP 写入输出流。
     * 包含 font.css 及全部 woff2 分片，用于下载后上传至 CDN。
     *
     * @param outputStream ZIP 数据写入目标
     */
    public void zipFontChunks(OutputStream outputStream) throws IOException {
        Path outputDir = getEffectiveOutputDir();
        List<Path> files = collectFontChunkFiles(outputDir);
        writeZip(outputDir, files, outputStream);
    }

    /**
     * 预检并收集要打包的字体分片文件列表。
     * <p>
     * 该方法只做目录存在性/非空校验与文件遍历，不写任何响应字节，
     * 供调用方在提交响应前完成校验：若失败可返回 JSON 错误而非写出损坏的 zip。
     *
     * @param outputDir font_chunks 目录
     * @return 排序后的待打包文件列表
     * @throws IOException 目录不存在或为空时抛出
     */
    public List<Path> collectFontChunkFiles(Path outputDir) throws IOException {
        if (!Files.exists(outputDir)) {
            throw new IOException("字体分片目录不存在，请先上传字体进行切片");
        }
        try (Stream<Path> stream = Files.walk(outputDir)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            if (files.isEmpty()) {
                throw new IOException("字体分片目录为空，请先上传字体进行切片");
            }
            return files;
        }
    }

    /**
     * 将给定文件列表打包为 ZIP 写入输出流（不再做目录校验，调用前应先用 {@link #collectFontChunkFiles} 预检）。
     *
     * @param baseDir       用于生成 zip entry 相对路径的基准目录
     * @param files         待打包文件列表
     * @param outputStream  ZIP 数据写入目标
     */
    public void writeZip(Path baseDir, List<Path> files, OutputStream outputStream) throws IOException {
        ZipOutputStream zos = new ZipOutputStream(outputStream);
        IOException exception = null;
        try {
            for (Path file : files) {
                String entryName = baseDir.relativize(file).toString().replace('\\', '/');
                ZipEntry entry = new ZipEntry(entryName);
                zos.putNextEntry(entry);
                Files.copy(file, zos);
                zos.closeEntry();
            }
        } catch (IOException e) {
            exception = e;
        } finally {
            // 只结束 ZIP 数据，不关闭底层 OutputStream；Servlet 容器/调用方负责关闭
            try {
                zos.finish();
            } catch (IOException e) {
                if (exception == null) {
                    exception = e;
                } else {
                    exception.addSuppressed(e);
                }
            }
        }
        if (exception != null) {
            throw exception;
        }
    }

    /**
     * 给 font.css 中引用 woff2 分片的 url(...) 追加缓存失效参数 ?v=版本号。
     * 这样重新上传字体（同名文件）后，浏览器会因 URL 变化而重新拉取新分片，
     * 无需手动清缓存或 Ctrl+Shift+R。版本号同时通过系统配置 font.asset.version 下发到前端，
     * 用于给 font.css 本身的请求追加同样的 ?v= 参数。
     *
     * @param outputDir   font_chunks 目录
     * @param version     资源版本号
     */
    private void rewriteFontCssWithVersion(Path outputDir, String version) throws IOException {
        Path cssFile = outputDir.resolve(DEFAULT_CSS_FILE_NAME);
        if (!Files.exists(cssFile)) {
            return;
        }
        String css = Files.readString(cssFile, StandardCharsets.UTF_8);
        String versioned = FONT_URL_PATTERN.matcher(css).replaceAll(mr -> {
            String quote = mr.group(1);
            String url = mr.group(2);
            if (url.contains("?")) {
                return mr.group(0);
            }
            return "url(" + quote + url + "?v=" + version + quote + ")";
        });
        if (!versioned.equals(css)) {
            Files.writeString(cssFile, versioned, StandardCharsets.UTF_8);
        }
    }

    private int countFontChars(byte[] fontData) throws IOException {
        try (RandomAccessReadBuffer rar = new RandomAccessReadBuffer(fontData);
             TrueTypeFont ttf = new TTFParser().parse(rar)) {
            return extractAllChars(ttf).size();
        }
    }

    private Set<Integer> extractAllChars(TrueTypeFont ttf) throws IOException {
        Set<Integer> chars = new LinkedHashSet<>();
        CmapTable cmapTable = ttf.getCmap();
        if (cmapTable == null) {
            return chars;
        }

        for (CmapSubtable subtable : cmapTable.getCmaps()) {
            for (int code = 32; code <= 0xFFFF; code++) {
                int glyphId = subtable.getGlyphId(code);
                if (glyphId > 0) {
                    chars.add(code);
                }
            }
        }
        return chars;
    }

    private void executeCnFontSplit(Path inputFontFile, Path outputDir) throws IOException {
        Path modulePath = resolveCnFontSplitModulePath();
        if (modulePath == null) {
            throw new IOException("未找到 cn-font-split 模块，请先安装 split_font/package.json 依赖，或配置 CN_FONT_SPLIT_MODULE_PATH");
        }

        Path runnerPath = ensureRunnerScriptReady();
        ProcessBuilder processBuilder = new ProcessBuilder(
                "node",
                runnerPath.toString(),
                inputFontFile.toAbsolutePath().toString(),
                outputDir.toAbsolutePath().toString(),
                DEFAULT_FONT_FAMILY,
                DEFAULT_CSS_FILE_NAME,
                String.valueOf(DEFAULT_CHUNK_SIZE));
        processBuilder.redirectErrorStream(true);

        Map<String, String> env = processBuilder.environment();
        env.putIfAbsent(MODULE_PATH_ENV, modulePath.toAbsolutePath().toString());
        env.putIfAbsent(GH_HOST_ENV, "https://ik.imagekit.io/github");

        Process process = processBuilder.start();
        String output;
        try (InputStream inputStream = process.getInputStream();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            inputStream.transferTo(outputStream);
            output = outputStream.toString(StandardCharsets.UTF_8);
        }

        try {
            int exitCode = process.waitFor();
            log.info("cn-font-split 输出:\n{}", output);
            if (exitCode != 0) {
                throw new IOException("cn-font-split 执行失败，退出码=" + exitCode + "，输出=" + output);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("等待 cn-font-split 执行完成时被中断", e);
        }
    }

    private Path ensureRunnerScriptReady() throws IOException {
        if (runnerScriptPath == null || !Files.exists(runnerScriptPath)) {
            runnerScriptPath = extractRunnerScript();
        }
        return runnerScriptPath;
    }

    private Path extractRunnerScript() throws IOException {
        ClassPathResource resource = new ClassPathResource("font/cn-font-split-runner.mjs");
        Path tempDir = Files.createTempDirectory("poetize-font-tools-");
        Path target = tempDir.resolve("cn-font-split-runner.mjs");
        try (InputStream inputStream = resource.getInputStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        }
        target.toFile().deleteOnExit();
        tempDir.toFile().deleteOnExit();
        return target;
    }

    private Path resolveCnFontSplitModulePath() {
        String configuredPath = System.getenv(MODULE_PATH_ENV);
        if (configuredPath != null && !configuredPath.isBlank()) {
            Path path = Path.of(configuredPath).toAbsolutePath().normalize();
            if (Files.exists(path)) {
                return path;
            }
        }

        List<Path> candidates = new ArrayList<>();
        candidates.add(Path.of("/opt/cn-font-split-runtime/node_modules/cn-font-split/dist/node/index.mjs"));

        Path current = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        for (int i = 0; i < 6 && current != null; i++) {
            candidates.add(current.resolve("split_font/node_modules/cn-font-split/dist/node/index.mjs"));
            current = current.getParent();
        }

        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (Files.exists(normalized)) {
                return normalized;
            }
        }
        return null;
    }

    private List<Path> listChunkFiles(Path outputDir) {
        if (!Files.exists(outputDir)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.list(outputDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".woff2"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            log.error("读取字体分片失败: {}", outputDir, e);
            return List.of();
        }
    }

    private boolean hasLegacySubsetFiles(Path outputDir) {
        String[] legacyFiles = {
                "font.base.woff2",
                "font.level1.woff2",
                "font.level2.woff2",
                "font.other.woff2",
                "unicode_ranges.json"
        };

        for (String fileName : legacyFiles) {
            if (!Files.exists(outputDir.resolve(fileName))) {
                return false;
            }
        }
        return true;
    }

    private void cleanGeneratedFiles(Path outputDir) throws IOException {
        if (!Files.exists(outputDir)) {
            return;
        }

        try (Stream<Path> stream = Files.list(outputDir)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                String name = file.getFileName().toString().toLowerCase();
                if (name.endsWith(".woff2")
                        || name.endsWith(".css")
                        || name.endsWith(".json")
                        || name.endsWith(".bin")
                        || name.endsWith(".proto")
                        || name.endsWith(".html")) {
                    Files.deleteIfExists(file);
                }
            }
        }
    }
}
