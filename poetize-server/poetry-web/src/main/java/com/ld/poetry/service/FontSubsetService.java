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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    @Value("${local.uploadUrl:/app/static/}")
    private String uploadUrl;

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
        result.put("outputDir", outputDir.toString());
        return result;
    }

    /**
     * 获取默认的 font_chunks 输出目录。
     */
    public Path getDefaultOutputDir() {
        String basePath = uploadUrl.endsWith("/") ? uploadUrl : uploadUrl + "/";
        return Path.of(basePath, "assets", "font_chunks");
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
        Path outputDir = getDefaultOutputDir();
        if (!Files.exists(outputDir)) {
            throw new IOException("字体分片目录不存在，请先上传字体进行切片");
        }

        List<Path> files;
        try (Stream<Path> stream = Files.walk(outputDir)) {
            files = stream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }

        if (files.isEmpty()) {
            throw new IOException("字体分片目录为空，请先上传字体进行切片");
        }

        try (ZipOutputStream zos = new ZipOutputStream(outputStream)) {
            for (Path file : files) {
                String entryName = outputDir.relativize(file).toString().replace('\\', '/');
                ZipEntry entry = new ZipEntry(entryName);
                zos.putNextEntry(entry);
                Files.copy(file, zos);
                zos.closeEntry();
            }
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
