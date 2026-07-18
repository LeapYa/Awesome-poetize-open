package com.ld.poetry.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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

import java.io.BufferedOutputStream;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
     * 文件 IO 缓冲区大小。字体分片通常 45~65KB，64KB 缓冲能让大部分分片一次 read 完成。
     * 与 {@link #DEFAULT_CHUNK_SIZE}（控制切割分片大小）解耦，互不影响。
     */
    private static final int IO_BUFFER_SIZE = 64 * 1024;

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

    /**
     * 后台单线程预打包执行器：串行执行避免并发打包冲突，daemon 线程 JVM 退出时自动结束。
     */
    private final ExecutorService cacheBuilder = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "font-zip-cache-builder");
        t.setDaemon(true);
        return t;
    });

    /** 跟踪最近一次预打包任务，供清理/重新切割前同步等待 */
    private volatile Future<?> pendingCacheBuild;

    /**
     * 串行化对同一字体输出目录的写操作（清理/切割/提交缓存），
     * 避免并发上传时后台缓存任务读到已被清空的目录或半成品文件。
     */
    private final Object subsetLock = new Object();

    @PostConstruct
    public void init() {
        try {
            runnerScriptPath = extractRunnerScript();
            log.info("cn-font-split 运行脚本已准备: {}", runnerScriptPath);
        } catch (IOException e) {
            log.error("初始化 cn-font-split 运行脚本失败", e);
        }
        // 启动时异步预打包内置字体缓存 ZIP，避免首次下载走实时打包导致前端转圈
        submitBuiltinCacheBuildIfNeeded();
    }

    /**
     * 启动时检测内置字体目录存在但缓存 ZIP 缺失时，异步预打包。
     * 内置字体是前端构建产物，不会触发 {@link #subsetFont}，缓存 ZIP 不会被自动生成，
     * 每次下载都走实时打包路径（收集数百个 woff2 分片 + 同步 ZIP 压缩），导致前端转圈。
     */
    private void submitBuiltinCacheBuildIfNeeded() {
        try {
            Path builtinDir = getBuiltinOutputDir();
            if (!hasFontFiles(builtinDir)) {
                return;
            }
            Path cachedZip = getCachedZipPath(builtinDir);
            if (Files.exists(cachedZip) && Files.size(cachedZip) > 0) {
                return;
            }
            log.info("检测到内置字体缓存 ZIP 缺失，启动异步预打包: {}", builtinDir);
            submitCacheBuild(builtinDir);
        } catch (Exception e) {
            log.warn("启动时预打包内置字体缓存失败，下载时将回退到实时打包", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        cacheBuilder.shutdownNow();
    }

    /**
     * 异步提交预打包任务，不阻塞上传响应。
     */
    private void submitCacheBuild(Path outputDir) {
        pendingCacheBuild = cacheBuilder.submit(() -> {
            try {
                buildCachedZip(outputDir);
            } catch (IOException e) {
                log.warn("后台预打包字体切割包失败，下载时将回退到实时打包", e);
            }
        });
    }

    /**
     * 等待上一次预打包完成（清理/重新切割前调用），避免打包到正在被清空的目录。
     * 若超时仍未完成，则取消旧任务，防止其继续读写已被删除的文件。
     */
    private void awaitPendingCacheBuild() {
        Future<?> f = pendingCacheBuild;
        if (f != null && !f.isDone()) {
            try {
                f.get(60, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("等待上一次字体预打包完成超时，将取消旧任务", e);
                f.cancel(true);
            }
        }
    }

    /**
     * 等待后台预打包完成并返回缓存是否可用。
     * <p>
     * 用于下载接口：上传后立即点击下载时，缓存 ZIP 尚未生成（后台正在打包），
     * 此时等待预打包完成再发缓存文件，比回退到慢速实时打包更优。
     * 无进行中的任务时立即返回，零开销。
     *
     * @param outputDir       字体分片目录
     * @param timeoutSeconds  最长等待秒数
     * @return 缓存 ZIP 是否已就绪
     */
    public boolean awaitCacheReady(Path outputDir, long timeoutSeconds) {
        Future<?> f = pendingCacheBuild;
        if (f != null && !f.isDone()) {
            try {
                f.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("等待字体预打包完成超时", e);
            }
        }
        Path cachedZip = getCachedZipPath(outputDir);
        return Files.exists(cachedZip) && Files.isRegularFile(cachedZip);
    }

    /**
     * 执行字体子集化。
     *
     * @param ttfData   上传的字体文件字节
     * @param outputDir 输出目录 (font_chunks)
     * @return 处理结果摘要
     */
    public Map<String, Object> subsetFont(byte[] ttfData, Path outputDir) throws IOException {
        synchronized (subsetLock) {
            long startTime = System.currentTimeMillis();
            int totalChars = countFontChars(ttfData);

            Files.createDirectories(outputDir);
            // 清理旧分片前，等待上一次预打包完成，避免打包到正在被清空的目录
            awaitPendingCacheBuild();
            cleanGeneratedFiles(outputDir);
            // 删除旧缓存 ZIP：重新上传字体后旧包已失效，避免预打包完成前下载到旧包
            deleteCachedZip(outputDir);

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

            // 异步预打包 ZIP 缓存：切割完成后后台打包，不阻塞上传响应（避免上传卡在 99%）。
            // 下载接口可直接发送缓存文件（秒级零拷贝）。
            submitCacheBuild(outputDir);

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
        synchronized (subsetLock) {
            awaitPendingCacheBuild();
            Path outputDir = getDefaultOutputDir();
            if (!Files.exists(outputDir)) {
                deleteCachedZip(outputDir);
                return true;
            }

            try {
                cleanGeneratedFiles(outputDir);
                deleteCachedZip(outputDir);
                return true;
            } catch (IOException e) {
                log.error("清理字体子集文件失败", e);
                return false;
            }
        }
    }

    /**
     * 获取字体切割包缓存 ZIP 的路径。
     * <p>
     * 缓存统一放在上传卷（{@link #getDefaultOutputDir()} 的父目录），保证可写；
     * 内置字体目录可能只读，其缓存同样落到上传卷并以不同文件名区分。
     *
     * @param outputDir 实际生效的 font_chunks 目录（自定义或内置）
     * @return 缓存 ZIP 文件路径
     */
    public Path getCachedZipPath(Path outputDir) {
        Path cacheBase = getDefaultOutputDir().getParent();
        boolean isCustom = outputDir.equals(getDefaultOutputDir());
        String name = isCustom ? "font_chunks.zip" : "font_chunks_builtin.zip";
        return cacheBase.resolve(name);
    }

    /**
     * 预打包字体切割包为 ZIP 缓存文件。
     * <p>
     * 在字体切割完成后由后台线程调用，将 font_chunks 目录打包为 ZIP 缓存到磁盘。
     * 下载接口可直接发送该缓存文件（零拷贝），避免下载时实时打包导致耗时过长。
     * 先写临时文件再原子替换，保证缓存完整性：打包中途失败/被中断不会留下损坏的 ZIP。
     *
     * @param outputDir font_chunks 目录
     */
    public void buildCachedZip(Path outputDir) throws IOException {
        List<Path> files = collectFontChunkFiles(outputDir);
        Path zipPath = getCachedZipPath(outputDir);
        Files.createDirectories(zipPath.getParent());
        Path tempPath = zipPath.resolveSibling(zipPath.getFileName() + ".tmp");
        try (OutputStream os = Files.newOutputStream(tempPath)) {
            writeZip(outputDir, files, os);
        }
        Files.move(tempPath, zipPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        log.info("字体切割包预打包完成: {}, 共 {} 个文件, 大小 {} bytes",
                zipPath, files.size(), Files.size(zipPath));
    }

    /**
     * 删除字体切割包缓存 ZIP 及残留临时文件（清理时调用）。
     */
    private void deleteCachedZip(Path outputDir) {
        Path zipPath = getCachedZipPath(outputDir);
        try {
            Files.deleteIfExists(zipPath);
            Files.deleteIfExists(zipPath.resolveSibling(zipPath.getFileName() + ".tmp"));
        } catch (IOException e) {
            log.warn("删除字体切割包缓存失败: {}", zipPath, e);
        }
    }

    /**
     * 将 font_chunks 目录打包为 ZIP 写入输出流。
     * 包含 font.css 及全部 woff2 分片，用于下载后上传至 CDN。
     *
     * @deprecated 请使用 {@link #buildCachedZip(Path)} + {@link #awaitCacheReady(Path, long)}
     *             的缓存路径，以保持与 /fontSubset/download 一致的缓存优先行为。
     * @param outputStream ZIP 数据写入目标
     */
    @Deprecated
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
        // 用缓冲流包装底层输出流：字体切割包通常包含数百个 woff2 分片，
        // 若每次小写入都直达 Servlet 输出流会产生大量 syscall，拖慢打包导致前端超时。
        BufferedOutputStream bos = new BufferedOutputStream(outputStream, IO_BUFFER_SIZE);
        ZipOutputStream zos = new ZipOutputStream(bos);
        // 显式的大缓冲区复制文件内容，替代 Files.copy 内部的 8KB 缓冲，进一步降低 IO 次数。
        byte[] copyBuffer = new byte[IO_BUFFER_SIZE];
        IOException exception = null;
        try {
            for (Path file : files) {
                String entryName = baseDir.relativize(file).toString().replace('\\', '/');
                ZipEntry entry = new ZipEntry(entryName);
                zos.putNextEntry(entry);
                try (InputStream fis = Files.newInputStream(file)) {
                    int n;
                    while ((n = fis.read(copyBuffer)) != -1) {
                        zos.write(copyBuffer, 0, n);
                    }
                }
                zos.closeEntry();
            }
        } catch (IOException e) {
            exception = e;
        } finally {
            // 只结束 ZIP 数据，不关闭底层 OutputStream；Servlet 容器/调用方负责关闭。
            // finish 后需手动 flush 缓冲流，确保缓冲区内的数据真正写入底层输出流。
            try {
                zos.finish();
            } catch (IOException e) {
                if (exception == null) {
                    exception = e;
                } else {
                    exception.addSuppressed(e);
                }
            }
            try {
                bos.flush();
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
