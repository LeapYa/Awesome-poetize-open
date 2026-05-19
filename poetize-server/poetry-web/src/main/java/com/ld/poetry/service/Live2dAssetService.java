package com.ld.poetry.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
public class Live2dAssetService {

    private static final String LOCAL_BASE_URL = "/static/live2d_api/";
    private static final String DEFAULT_CDN_BASE_URL = "https://cdn.jsdelivr.net/gh/fghrsh/live2d_api/";
    private static final String DEFAULT_DOWNLOAD_URL = "https://github.com/fghrsh/live2d_api/archive/refs/heads/master.zip";
    private static final String DEFAULT_DOWNLOAD_PROXY_URL = "https://ghproxy.com/";
    private static final String DEFAULT_CONNECTIVITY_CHECK_URL = "https://www.google.com/generate_204";
    private static final String TARGET_DIR_NAME = "live2d_api";

    private final Object installLock = new Object();
    private final ExecutorService installExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "live2d-asset-installer");
        thread.setDaemon(true);
        return thread;
    });

    private volatile Future<?> installFuture;
    private volatile InstallTask installTask = InstallTask.idle();

    @Value("${live2d.assets.static-root:${LIVE2D_ASSETS_STATIC_ROOT:${RESOURCE_AVAILABILITY_STATICROOTS:}}}")
    private String configuredStaticRoots;

    @Value("${live2d.assets.download-url:${LIVE2D_API_DOWNLOAD_URL:https://github.com/fghrsh/live2d_api/archive/refs/heads/master.zip}}")
    private String downloadUrl;

    @Value("${live2d.assets.download-proxy-url:${LIVE2D_API_DOWNLOAD_PROXY_URL:https://ghproxy.com/}}")
    private String downloadProxyUrl;

    @Value("${live2d.assets.connectivity-check-url:${LIVE2D_CONNECTIVITY_CHECK_URL:https://www.google.com/generate_204}}")
    private String connectivityCheckUrl;

    @Value("${live2d.assets.connectivity-check-enabled:${LIVE2D_CONNECTIVITY_CHECK_ENABLED:true}}")
    private boolean connectivityCheckEnabled;

    @Value("${live2d.assets.cdn-base-url:${LIVE2D_API_CDN_BASE_URL:https://cdn.jsdelivr.net/gh/fghrsh/live2d_api/}}")
    private String cdnBaseUrl;

    public Map<String, Object> getStatus() {
        Path targetDir = getTargetDir();
        boolean installed = isInstalled(targetDir);

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("installed", installed);
        status.put("localBaseUrl", LOCAL_BASE_URL);
        status.put("cdnBaseUrl", normalizeBaseUrl(cdnBaseUrl, DEFAULT_CDN_BASE_URL));
        status.put("modelBaseUrl", installed ? LOCAL_BASE_URL : normalizeBaseUrl(cdnBaseUrl, DEFAULT_CDN_BASE_URL));
        status.put("downloadUrl", normalizeDownloadUrl());
        status.put("downloadProxyUrl", normalizeDownloadProxyUrl());
        status.put("connectivityCheckUrl", normalizeConnectivityCheckUrl());
        status.put("outputDir", targetDir.toString());
        status.put("modelListExists", Files.isRegularFile(targetDir.resolve("model_list.json")));
        status.put("modelDirExists", Files.isDirectory(targetDir.resolve("model")));
        status.put("totalSize", installed ? directorySize(targetDir) : 0L);
        status.put("installTask", installTask.toMap());
        return status;
    }

    public Map<String, Object> install(boolean force) {
        synchronized (installLock) {
            Path targetDir = getTargetDir();
            if (!force && isInstalled(targetDir)) {
                installTask = InstallTask.completed("本地 Live2D 模型资源已存在");
                Map<String, Object> status = getStatus();
                status.put("skipped", true);
                return status;
            }

            if (isInstallRunning()) {
                Map<String, Object> status = getStatus();
                status.put("alreadyRunning", true);
                return status;
            }

            InstallTask task = InstallTask.create(force);
            installTask = task;
            installFuture = installExecutor.submit(() -> installInBackground(task, force));

            Map<String, Object> status = getStatus();
            status.put("started", true);
            return status;
        }
    }

    @PreDestroy
    public void destroy() {
        installExecutor.shutdownNow();
        try {
            if (!installExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Live2D 模型资源下载任务未能在关闭前结束");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean isInstallRunning() {
        Future<?> future = installFuture;
        return installTask.isRunning() || (future != null && !future.isDone());
    }

    private void installInBackground(InstallTask task, boolean force) {
        Path zipFile = null;
        Path stagingDir = null;
        try {
            task.update("preparing", "准备下载目录", 2, "正在准备 Live2D 模型资源下载目录");
            Path staticRoot = resolveStaticRoot();
            Path targetDir = staticRoot.resolve(TARGET_DIR_NAME);

            if (!force && isInstalled(targetDir)) {
                task.complete("本地 Live2D 模型资源已存在");
                return;
            }

            Files.createDirectories(staticRoot);
            zipFile = Files.createTempFile(staticRoot, ".live2d_api_", ".zip");
            stagingDir = Files.createTempDirectory(staticRoot, ".live2d_api_install_");

            downloadArchive(zipFile, task);

            task.update("extracting", "解压模型包", 76, "正在解压 Live2D 模型资源包");
            unzipArchive(zipFile, stagingDir, task);

            task.update("installing", "校验模型包", 90, "正在校验 Live2D 模型资源结构");
            Path contentRoot = findContentRoot(stagingDir)
                    .orElseThrow(() -> new IOException("下载包中未找到 model_list.json"));
            if (!Files.isDirectory(contentRoot.resolve("model"))) {
                throw new IOException("下载包结构不完整，缺少 model 目录");
            }

            task.update("installing", "写入静态目录", 94, "正在写入本地静态目录");
            deleteRecursively(targetDir);
            Files.move(contentRoot, targetDir, StandardCopyOption.REPLACE_EXISTING);

            task.complete("Live2D 模型资源下载完成，已切换为本地优先加载");
            log.info("Live2D 模型资源安装完成: {}", targetDir);
        } catch (Exception e) {
            task.fail(e.getMessage() == null ? "Live2D 模型资源下载失败" : e.getMessage());
            log.error("安装 Live2D 模型资源失败", e);
        } finally {
            try {
                if (zipFile != null) {
                    Files.deleteIfExists(zipFile);
                }
                if (stagingDir != null) {
                    deleteRecursively(stagingDir);
                }
            } catch (IOException e) {
                log.warn("清理 Live2D 模型资源临时文件失败", e);
            }
        }
    }

    private Path getTargetDir() {
        return resolveStaticRoot().resolve(TARGET_DIR_NAME);
    }

    private Path resolveStaticRoot() {
        String configuredRoot = firstConfiguredStaticRoot();
        if (StringUtils.hasText(configuredRoot)) {
            return Paths.get(configuredRoot.trim()).toAbsolutePath().normalize();
        }

        List<Path> candidates = List.of(
                Paths.get("poetize-web/public/static"),
                Paths.get("../poetize-web/public/static"),
                Paths.get("../../poetize-web/public/static"),
                Paths.get("web-dist/static"),
                Paths.get("/app/web-dist/static")
        );

        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (Files.isDirectory(normalized)) {
                return normalized;
            }
        }

        return Paths.get("poetize-web/public/static").toAbsolutePath().normalize();
    }

    private String firstConfiguredStaticRoot() {
        if (!StringUtils.hasText(configuredStaticRoots)) {
            return "";
        }
        for (String root : configuredStaticRoots.split(",")) {
            if (StringUtils.hasText(root)) {
                return root;
            }
        }
        return "";
    }

    private boolean isInstalled(Path targetDir) {
        return Files.isRegularFile(targetDir.resolve("model_list.json"))
                && Files.isDirectory(targetDir.resolve("model"));
    }

    private void downloadArchive(Path zipFile, InstallTask task) throws IOException {
        List<String> sourceUrls = resolveDownloadCandidates(task);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        IOException lastException = null;
        for (String sourceUrl : sourceUrls) {
            try {
                downloadArchiveFromUrl(client, sourceUrl, zipFile, task);
                log.info("Live2D 模型包下载完成: {}", sourceUrl);
                return;
            } catch (IOException e) {
                lastException = e;
                task.update("downloading", "切换下载源", task.progress(),
                        "当前下载源失败，正在尝试下一个地址: " + e.getMessage());
                log.warn("Live2D 模型包下载失败，准备尝试下一个地址: {}", sourceUrl, e);
            } catch (IllegalArgumentException e) {
                lastException = new IOException("下载地址无效: " + sourceUrl, e);
                task.update("downloading", "切换下载源", task.progress(),
                        "下载地址无效，正在尝试下一个地址");
                log.warn("Live2D 模型包下载地址无效，准备尝试下一个地址: {}", sourceUrl, e);
            }
        }

        throw new IOException("下载 Live2D 模型包失败，已尝试地址: " + String.join(", ", sourceUrls),
                lastException);
    }

    private void downloadArchiveFromUrl(HttpClient client, String sourceUrl, Path zipFile, InstallTask task) throws IOException {
        Files.deleteIfExists(zipFile);
        task.startDownload(sourceUrl, isProxyDownloadUrl(sourceUrl) ? "proxy" : "direct");
        HttpRequest request = HttpRequest.newBuilder(URI.create(sourceUrl))
                .timeout(Duration.ofMinutes(20))
                .header("User-Agent", "Poetize-Live2D-Asset-Installer")
                .GET()
                .build();

        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream responseBody = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IOException("HTTP 状态码: " + response.statusCode());
                }

                long totalBytes = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
                long downloadedBytes = 0L;
                byte[] buffer = new byte[8192];
                try (OutputStream outputStream = Files.newOutputStream(zipFile)) {
                    int read;
                    while ((read = responseBody.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, read);
                        downloadedBytes += read;
                        task.updateDownloadProgress(downloadedBytes, totalBytes);
                    }
                }
                task.updateDownloadProgress(downloadedBytes, totalBytes);
                if (!Files.isRegularFile(zipFile) || Files.size(zipFile) == 0L) {
                    throw new IOException("下载文件为空");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("下载 Live2D 模型包时被中断", e);
        }
    }

    private List<String> resolveDownloadCandidates(InstallTask task) {
        String directUrl = normalizeDownloadUrl();
        String proxyUrl = buildProxyDownloadUrl(directUrl);
        boolean hasProxyUrl = StringUtils.hasText(proxyUrl);
        boolean preferProxy = hasProxyUrl && shouldPreferProxyDownload(task);

        List<String> sourceUrls = new ArrayList<>();
        if (preferProxy) {
            addCandidate(sourceUrls, proxyUrl);
            addCandidate(sourceUrls, directUrl);
        } else {
            addCandidate(sourceUrls, directUrl);
            addCandidate(sourceUrls, proxyUrl);
        }
        return sourceUrls;
    }

    private boolean shouldPreferProxyDownload(InstallTask task) {
        if (!connectivityCheckEnabled) {
            return false;
        }

        String checkUrl = normalizeConnectivityCheckUrl();
        if (!StringUtils.hasText(checkUrl)) {
            return false;
        }

        task.update("probing", "探测网络环境", 5, "正在探测 Google 连通性，判断是否优先使用 GitHub 代理");
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(checkUrl))
                .timeout(Duration.ofSeconds(5))
                .header("User-Agent", "Poetize-Live2D-Network-Probe")
                .GET()
                .build();

        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            boolean reachable = response.statusCode() >= 200 && response.statusCode() < 500;
            if (!reachable) {
                log.info("Live2D 下载网络探测未通过，优先使用代理下载: {} -> {}", checkUrl, response.statusCode());
            }
            task.update("probing", "网络探测完成", 8,
                    reachable ? "Google 可访问，优先直连 GitHub" : "Google 不可访问，优先使用 ghproxy");
            return !reachable;
        } catch (IOException e) {
            task.update("probing", "网络探测完成", 8, "Google 不可访问，优先使用 ghproxy");
            log.info("Live2D 下载网络探测未通过，优先使用代理下载: {}", checkUrl);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            task.update("probing", "网络探测中断", 8, "网络探测被中断，优先使用 ghproxy");
            log.info("Live2D 下载网络探测被中断，优先使用代理下载: {}", checkUrl);
            return true;
        } catch (IllegalArgumentException e) {
            task.update("probing", "网络探测跳过", 8, "网络探测地址无效，优先直连 GitHub");
            log.warn("Live2D 下载网络探测地址无效，使用直连优先: {}", checkUrl, e);
            return false;
        }
    }

    private String buildProxyDownloadUrl(String sourceUrl) {
        if (!StringUtils.hasText(sourceUrl) || !isGithubDownloadUrl(sourceUrl)) {
            return "";
        }

        String proxyBase = normalizeDownloadProxyUrl();
        if (!StringUtils.hasText(proxyBase)) {
            return "";
        }
        if (proxyBase.contains("{url}")) {
            return proxyBase.replace("{url}", sourceUrl);
        }
        return normalizeBaseUrl(proxyBase, DEFAULT_DOWNLOAD_PROXY_URL) + sourceUrl;
    }

    private boolean isGithubDownloadUrl(String sourceUrl) {
        try {
            String host = URI.create(sourceUrl).getHost();
            return host != null && (host.equals("github.com") || host.endsWith(".github.com"));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void addCandidate(List<String> candidates, String sourceUrl) {
        if (StringUtils.hasText(sourceUrl) && !candidates.contains(sourceUrl)) {
            candidates.add(sourceUrl);
        }
    }

    private boolean isProxyDownloadUrl(String sourceUrl) {
        String proxyBase = normalizeDownloadProxyUrl();
        if (!StringUtils.hasText(proxyBase)) {
            return false;
        }
        return sourceUrl.startsWith(proxyBase.replace("{url}", ""));
    }

    private void unzipArchive(Path zipFile, Path stagingDir, InstallTask task) throws IOException {
        try (InputStream inputStream = Files.newInputStream(zipFile);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            int entryCount = 0;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path outputPath = stagingDir.resolve(entry.getName()).normalize();
                if (!outputPath.startsWith(stagingDir)) {
                    throw new IOException("下载包包含非法路径: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(outputPath);
                } else {
                    Files.createDirectories(outputPath.getParent());
                    Files.copy(zipInputStream, outputPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zipInputStream.closeEntry();
                entryCount++;
                if (entryCount % 100 == 0) {
                    task.update("extracting", "解压模型包", Math.min(88, 76 + entryCount / 100),
                            "正在解压 Live2D 模型资源包，已处理 " + entryCount + " 个文件");
                }
            }
            task.update("extracting", "解压模型包", 88,
                    "Live2D 模型资源包解压完成，已处理 " + entryCount + " 个文件");
        }
    }

    private Optional<Path> findContentRoot(Path stagingDir) throws IOException {
        try (Stream<Path> stream = Files.walk(stagingDir)) {
            return stream
                    .filter(path -> Files.isRegularFile(path)
                            && "model_list.json".equals(path.getFileName().toString()))
                    .map(Path::getParent)
                    .findFirst();
        }
    }

    private long directorySize(Path directory) {
        if (!Files.exists(directory)) {
            return 0L;
        }
        try (Stream<Path> stream = Files.walk(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            return 0L;
                        }
                    })
                    .sum();
        } catch (IOException e) {
            log.warn("统计 Live2D 资源大小失败: {}", directory, e);
            return 0L;
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            List<Path> paths = stream
                    .sorted((left, right) -> right.compareTo(left))
                    .toList();
            for (Path item : paths) {
                Files.deleteIfExists(item);
            }
        }
    }

    private String normalizeDownloadUrl() {
        return StringUtils.hasText(downloadUrl) ? downloadUrl.trim() : DEFAULT_DOWNLOAD_URL;
    }

    private String normalizeDownloadProxyUrl() {
        return StringUtils.hasText(downloadProxyUrl) ? downloadProxyUrl.trim() : DEFAULT_DOWNLOAD_PROXY_URL;
    }

    private String normalizeConnectivityCheckUrl() {
        return StringUtils.hasText(connectivityCheckUrl) ? connectivityCheckUrl.trim() : DEFAULT_CONNECTIVITY_CHECK_URL;
    }

    private String normalizeBaseUrl(String value, String fallback) {
        String baseUrl = StringUtils.hasText(value) ? value.trim() : fallback;
        return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    private static final class InstallTask {
        private final String id;
        private final boolean force;
        private volatile String state;
        private volatile String stage;
        private volatile String message;
        private volatile String currentUrl;
        private volatile String sourceType;
        private volatile int progress;
        private volatile long downloadedBytes;
        private volatile long totalBytes;
        private volatile long startedAt;
        private volatile long updatedAt;
        private volatile long completedAt;

        private InstallTask(String id, boolean force, String state, String stage, int progress, String message) {
            long now = System.currentTimeMillis();
            this.id = id;
            this.force = force;
            this.state = state;
            this.stage = stage;
            this.progress = progress;
            this.message = message;
            this.startedAt = now;
            this.updatedAt = now;
            this.completedAt = 0L;
            this.currentUrl = "";
            this.sourceType = "";
            this.downloadedBytes = 0L;
            this.totalBytes = -1L;
        }

        static InstallTask idle() {
            return new InstallTask("", false, "idle", "空闲", 0, "Live2D 模型资源未在下载");
        }

        static InstallTask create(boolean force) {
            return new InstallTask(UUID.randomUUID().toString(), force, "queued", "排队中", 0,
                    "Live2D 模型资源下载任务已创建");
        }

        static InstallTask completed(String message) {
            InstallTask task = new InstallTask("", false, "completed", "已完成", 100, message);
            task.completedAt = task.updatedAt;
            return task;
        }

        boolean isRunning() {
            return "queued".equals(state)
                    || "preparing".equals(state)
                    || "probing".equals(state)
                    || "downloading".equals(state)
                    || "extracting".equals(state)
                    || "installing".equals(state);
        }

        int progress() {
            return progress;
        }

        void update(String state, String stage, int progress, String message) {
            this.state = state;
            this.stage = stage;
            this.progress = Math.max(0, Math.min(100, progress));
            this.message = message;
            this.updatedAt = System.currentTimeMillis();
        }

        void startDownload(String currentUrl, String sourceType) {
            this.currentUrl = currentUrl;
            this.sourceType = sourceType;
            this.downloadedBytes = 0L;
            this.totalBytes = -1L;
            update("downloading", sourceTypeLabel(sourceType) + "下载", 10,
                    "正在从" + sourceTypeLabel(sourceType) + "下载 Live2D 模型资源包");
        }

        void updateDownloadProgress(long downloadedBytes, long totalBytes) {
            this.downloadedBytes = Math.max(0L, downloadedBytes);
            this.totalBytes = totalBytes;
            int nextProgress = 15;
            if (totalBytes > 0L) {
                nextProgress = 10 + (int) Math.min(60L, downloadedBytes * 60L / totalBytes);
            }
            update("downloading", sourceTypeLabel(sourceType) + "下载", nextProgress,
                    "正在下载 Live2D 模型资源包");
        }

        void complete(String message) {
            update("completed", "已完成", 100, message);
            this.completedAt = this.updatedAt;
        }

        void fail(String message) {
            update("failed", "下载失败", progress, message);
            this.completedAt = this.updatedAt;
        }

        Map<String, Object> toMap() {
            Map<String, Object> task = new LinkedHashMap<>();
            task.put("id", id);
            task.put("force", force);
            task.put("state", state);
            task.put("running", isRunning());
            task.put("stage", stage);
            task.put("progress", progress);
            task.put("message", message);
            task.put("currentUrl", currentUrl);
            task.put("sourceType", sourceType);
            task.put("downloadedBytes", downloadedBytes);
            task.put("totalBytes", totalBytes);
            task.put("startedAt", startedAt);
            task.put("updatedAt", updatedAt);
            task.put("completedAt", completedAt);
            return task;
        }

        private String sourceTypeLabel(String sourceType) {
            return "proxy".equals(sourceType) ? "代理源" : "直连源";
        }
    }
}
