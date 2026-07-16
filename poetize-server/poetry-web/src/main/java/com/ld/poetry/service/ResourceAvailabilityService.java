package com.ld.poetry.service;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.poetry.constants.CacheConstants;
import com.ld.poetry.entity.Resource;
import com.ld.poetry.utils.RedisUtil;
import com.ld.poetry.vo.ResourceScanTaskVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class ResourceAvailabilityService {

    private static final int INVALID_CHECK_CONCURRENCY = 8;
    private static final Duration HTTP_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration HTTP_REQUEST_TIMEOUT = Duration.ofSeconds(3);

    /**
     * 资源类型标识：无效资源
     */
    public static final String SCAN_TYPE_INVALID = "invalid";
    /**
     * 资源类型标识：孤儿资源
     */
    public static final String SCAN_TYPE_ORPHAN = "orphan";

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private RedisUtil redisUtil;

    @Value("${local.uploadUrl:/app/static/}")
    private String localUploadUrl;

    @Value("${local.downloadUrl:/static/}")
    private String localDownloadUrl;

    @Value("${resource.availability.staticRoots:}")
    private String staticResourceRoots;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(HTTP_CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public Page<Resource> listInvalidResources(Page<Resource> page) {
        return listInvalidResources(page, "createTime", false);
    }

    public Page<Resource> listInvalidResources(Page<Resource> page, String order, boolean asc) {
        LambdaQueryChainWrapper<Resource> query = resourceService.lambdaQuery();
        applyResourceOrder(query, order, asc);
        List<Resource> resources = query.list();
        return buildInvalidResourcePage(resources, page.getCurrent(), page.getSize());
    }

    Page<Resource> buildInvalidResourcePage(List<Resource> resources, long current, long size) {
        List<Resource> invalidResources = findInvalidResources(resources);
        long safeCurrent = Math.max(current, 1L);
        long safeSize = size > 0L ? size : 10L;

        Page<Resource> page = new Page<>(safeCurrent, safeSize);
        page.setTotal(invalidResources.size());

        long from = (safeCurrent - 1L) * safeSize;
        if (from >= invalidResources.size() || from > Integer.MAX_VALUE) {
            page.setRecords(Collections.emptyList());
            return page;
        }

        long to = Math.min(invalidResources.size(), from + safeSize);
        page.setRecords(new ArrayList<>(invalidResources.subList((int) from, (int) to)));
        return page;
    }

    boolean isResourceLoadable(String resourcePath) {
        if (!StringUtils.hasText(resourcePath)) {
            return false;
        }

        String normalizedPath = resourcePath.trim();
        String lowerPath = normalizedPath.toLowerCase();
        if (lowerPath.startsWith("data:")) {
            return true;
        }
        if (lowerPath.startsWith("blob:")) {
            return false;
        }
        if (isRemoteResource(normalizedPath)) {
            return isRemoteResourceLoadable(normalizedPath);
        }
        return isLocalResourceLoadable(normalizedPath);
    }

    private List<Resource> findInvalidResources(List<Resource> resources) {
        return findInvalidResources(resources, null, null);
    }

    /**
     * 检测无效资源，支持进度回调与取消信号
     *
     * @param resources 待检测资源
     * @param progressCallback 每完成一个资源后回调（可为null）
     * @param cancelled 取消信号（可为null，true时尽快终止）
     */
    private List<Resource> findInvalidResources(List<Resource> resources,
                                                Runnable progressCallback,
                                                AtomicBoolean cancelled) {
        if (CollectionUtils.isEmpty(resources)) {
            return Collections.emptyList();
        }

        int threadCount = Math.min(INVALID_CHECK_CONCURRENCY, Math.max(resources.size(), 1));
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        try {
            List<Callable<Boolean>> tasks = resources.stream()
                    .map(resource -> (Callable<Boolean>) () -> {
                        if (cancelled != null && cancelled.get()) {
                            return false;
                        }
                        return !isResourceLoadable(resource == null ? null : resource.getPath());
                    })
                    .toList();
            List<Future<Boolean>> futures = executorService.invokeAll(tasks);

            List<Resource> invalidResources = new ArrayList<>();
            for (int i = 0; i < futures.size(); i++) {
                if (cancelled != null && cancelled.get()) {
                    log.info("无效资源检测已取消，已处理 {} / {}", i, futures.size());
                    break;
                }
                try {
                    if (Boolean.TRUE.equals(futures.get(i).get())) {
                        invalidResources.add(resources.get(i));
                    }
                } catch (ExecutionException e) {
                    log.warn("检测资源可用性失败，按无效资源处理: {}", resourcePathOf(resources.get(i)), e);
                    invalidResources.add(resources.get(i));
                }
                if (progressCallback != null) {
                    progressCallback.run();
                }
            }
            return invalidResources;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("无效资源检测被中断", e);
            return Collections.emptyList();
        } finally {
            executorService.shutdownNow();
        }
    }

    // ================================ 异步检测任务 ================================

    /**
     * 启动无效资源异步检测任务
     *
     * @param order 排序字段
     * @param asc 是否升序
     * @return 任务VO（含taskId）
     */
    public ResourceScanTaskVO startInvalidResourceScanTask(String order, boolean asc) {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        ResourceScanTaskVO task = new ResourceScanTaskVO(taskId, SCAN_TYPE_INVALID);
        saveTask(task);

        // 使用守护线程异步执行，避免阻塞请求线程
        Thread worker = new Thread(() -> runInvalidResourceScan(task, order, asc), "resource-scan-" + taskId);
        worker.setDaemon(true);
        worker.start();

        return task;
    }

    private void runInvalidResourceScan(ResourceScanTaskVO task, String order, boolean asc) {
        AtomicBoolean cancelled = new AtomicBoolean(false);

        try {
            // 检查取消标记
            ResourceScanTaskVO current = getTask(task.getTaskId());
            if (current != null && current.getStatus() == ResourceScanTaskVO.Status.CANCELLED) {
                return;
            }

            task.setStatus(ResourceScanTaskVO.Status.RUNNING);
            task.setStartedAt(System.currentTimeMillis());
            saveTask(task);

            LambdaQueryChainWrapper<Resource> query = resourceService.lambdaQuery();
            applyResourceOrder(query, order, asc);
            List<Resource> resources = query.list();
            task.setTotal(resources.size());
            saveTask(task);

            if (resources.isEmpty()) {
                task.setHitResourceIds(Collections.emptyList());
                task.setStatus(ResourceScanTaskVO.Status.SUCCESS);
                task.setFinishedAt(System.currentTimeMillis());
                saveTask(task);
                return;
            }

            List<Resource> invalidResources = findInvalidResources(resources, () -> {
                task.setProcessed(task.getProcessed() + 1);
                // 每处理5个或全部完成时刷新一次Redis，降低写入频率
                if (task.getProcessed() % 5 == 0 || task.getProcessed() == task.getTotal()) {
                    saveTask(task);
                }
                // 检查取消信号（来自cancelScanTask）
                ResourceScanTaskVO latest = getTask(task.getTaskId());
                if (latest != null && latest.getStatus() == ResourceScanTaskVO.Status.CANCELLED) {
                    cancelled.set(true);
                }
            }, cancelled);

            if (cancelled.get()) {
                task.setStatus(ResourceScanTaskVO.Status.CANCELLED);
            } else {
                task.setStatus(ResourceScanTaskVO.Status.SUCCESS);
                task.setHitCount(invalidResources.size());
                task.setHitResourceIds(invalidResources.stream()
                        .map(Resource::getId)
                        .collect(java.util.stream.Collectors.toList()));
            }
            task.setFinishedAt(System.currentTimeMillis());
            saveTask(task);

            // 缓存检测结果（仅成功时），供listResource快速分页
            if (task.getStatus() == ResourceScanTaskVO.Status.SUCCESS) {
                redisUtil.set(CacheConstants.buildResourceScanResultKey(SCAN_TYPE_INVALID),
                        task.getHitResourceIds(),
                        CacheConstants.RESOURCE_SCAN_RESULT_EXPIRE_TIME);
            }
        } catch (Exception e) {
            log.error("无效资源检测任务失败: taskId={}", task.getTaskId(), e);
            task.setStatus(ResourceScanTaskVO.Status.FAILED);
            task.setErrorMessage(e.getMessage());
            task.setFinishedAt(System.currentTimeMillis());
            saveTask(task);
        }
    }

    /**
     * 获取任务状态
     */
    public ResourceScanTaskVO getTask(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            return null;
        }
        Object obj = redisUtil.get(CacheConstants.buildResourceScanTaskKey(taskId));
        if (obj instanceof ResourceScanTaskVO) {
            return (ResourceScanTaskVO) obj;
        }
        return null;
    }

    /**
     * 取消任务
     */
    public boolean cancelScanTask(String taskId) {
        ResourceScanTaskVO task = getTask(taskId);
        if (task == null) {
            return false;
        }
        if (task.getStatus() == ResourceScanTaskVO.Status.SUCCESS
                || task.getStatus() == ResourceScanTaskVO.Status.FAILED
                || task.getStatus() == ResourceScanTaskVO.Status.CANCELLED) {
            return false;
        }
        task.setStatus(ResourceScanTaskVO.Status.CANCELLED);
        task.setFinishedAt(System.currentTimeMillis());
        saveTask(task);
        return true;
    }

    /**
     * 根据缓存的结果ID列表构建分页
     */
    public Page<Resource> listInvalidResourcesFromCache(Page<Resource> page, String order, boolean asc) {
        Object cached = redisUtil.get(CacheConstants.buildResourceScanResultKey(SCAN_TYPE_INVALID));
        if (!(cached instanceof List)) {
            return null;
        }
        // 安全转换：Jackson 反序列化后元素可能是 Integer/Long，统一转为 Integer
        List<Integer> hitIds = ((List<?>) cached).stream()
                .filter(java.util.Objects::nonNull)
                .map(item -> ((Number) item).intValue())
                .collect(java.util.stream.Collectors.toList());
        if (hitIds.isEmpty()) {
            Page<Resource> empty = new Page<>(page.getCurrent(), page.getSize());
            empty.setTotal(0);
            empty.setRecords(Collections.emptyList());
            return empty;
        }

        long safeCurrent = Math.max(page.getCurrent(), 1L);
        long safeSize = page.getSize() > 0L ? page.getSize() : 10L;
        long from = (safeCurrent - 1L) * safeSize;
        if (from >= hitIds.size()) {
            Page<Resource> empty = new Page<>(safeCurrent, safeSize);
            empty.setTotal(hitIds.size());
            empty.setRecords(Collections.emptyList());
            return empty;
        }
        long to = Math.min(hitIds.size(), from + safeSize);
        List<Integer> pageIds = new ArrayList<>(hitIds.subList((int) from, (int) to));

        LambdaQueryChainWrapper<Resource> query = resourceService.lambdaQuery()
                .in(Resource::getId, pageIds);
        applyResourceOrder(query, order, asc);
        List<Resource> records = query.list();

        // 按 hitIds 顺序排列
        java.util.Map<Integer, Resource> idMap = records.stream()
                .collect(java.util.stream.Collectors.toMap(Resource::getId, r -> r, (a, b) -> a));
        List<Resource> ordered = pageIds.stream()
                .map(idMap::get)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toList());

        Page<Resource> result = new Page<>(safeCurrent, safeSize);
        result.setTotal(hitIds.size());
        result.setRecords(ordered);
        return result;
    }

    private void saveTask(ResourceScanTaskVO task) {
        redisUtil.set(CacheConstants.buildResourceScanTaskKey(task.getTaskId()),
                task, CacheConstants.RESOURCE_SCAN_TASK_EXPIRE_TIME);
    }

    private void applyResourceOrder(LambdaQueryChainWrapper<Resource> query, String order, boolean asc) {
        switch (order) {
            case "id":
                query.orderBy(true, asc, Resource::getId);
                break;
            case "originalName":
                query.orderBy(true, asc, Resource::getOriginalName);
                break;
            case "userId":
                query.orderBy(true, asc, Resource::getUserId);
                break;
            case "type":
                query.orderBy(true, asc, Resource::getType);
                break;
            case "status":
                query.orderBy(true, asc, Resource::getStatus);
                break;
            case "path":
                query.orderBy(true, asc, Resource::getPath);
                break;
            case "size":
                query.orderBy(true, asc, Resource::getSize);
                break;
            case "mimeType":
                query.orderBy(true, asc, Resource::getMimeType);
                break;
            case "storeType":
                query.orderBy(true, asc, Resource::getStoreType);
                break;
            case "createTime":
            default:
                query.orderBy(true, asc, Resource::getCreateTime);
                break;
        }
        if (!"id".equals(order)) {
            query.orderBy(true, asc, Resource::getId);
        }
    }

    private boolean isRemoteResourceLoadable(String resourcePath) {
        URI uri = toHttpUri(resourcePath);
        if (uri == null) {
            return false;
        }

        boolean imageResource = isImageResourcePath(resourcePath);
        try {
            HttpResponse<Void> headResponse = sendHead(uri);
            int headStatus = headResponse.statusCode();
            if (isLoadableHttpStatus(headStatus)) {
                return !isSpaFallbackResponse(headResponse, imageResource);
            }
            if (!shouldFallbackToGet(headStatus)) {
                return false;
            }

            HttpResponse<InputStream> getResponse = sendRangeGet(uri);
            int getStatus = getResponse.statusCode();
            if (!isLoadableHttpStatus(getStatus)) {
                return false;
            }
            return !isSpaFallbackResponse(getResponse, imageResource);
        } catch (IOException | IllegalArgumentException e) {
            log.debug("远程资源检测失败: {}", resourcePath, e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 判断响应是否为SPA fallback（如nginx try_files回退到index.html）。
     * 对于图片类资源，如果响应Content-Type为text/html，则说明资源不存在但服务器返回了前端页面，
     * 应判定为不可加载，避免假阴性。
     */
    private boolean isSpaFallbackResponse(HttpResponse<?> response, boolean imageResource) {
        if (!imageResource) {
            return false;
        }
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        return contentType.toLowerCase().startsWith("text/html");
    }

    private boolean isLocalResourceLoadable(String resourcePath) {
        for (Path filePath : resolveSafeLocalResourcePaths(resourcePath)) {
            if (isLocalFileLoadable(filePath, resourcePath)) {
                return true;
            }
        }
        return false;
    }

    List<Path> resolveSafeLocalResourcePaths(String resourcePath) {
        if (!StringUtils.hasText(resourcePath)) {
            return Collections.emptyList();
        }
        List<Path> managedRoots = managedLocalRoots();
        return resolveLocalResourcePaths(resourcePath).stream()
                .map(path -> resolveSafeLocalPath(path, managedRoots))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    List<Path> resolveLocalResourcePaths(String resourcePath) {
        String normalizedResourcePath = resourcePath.replace('\\', '/');
        if (isRemoteResource(normalizedResourcePath)) {
            try {
                String uriPath = URI.create(normalizedResourcePath).getPath();
                if (StringUtils.hasText(uriPath)) {
                    normalizedResourcePath = uriPath;
                }
            } catch (IllegalArgumentException ignored) {
                return Collections.emptyList();
            }
        }

        String relativePath = stripQueryAndFragment(normalizedResourcePath);
        String originalRelativePath = trimLeadingSlashes(relativePath);
        String normalizedDownloadUrl = normalizeDownloadUrl();
        if (StringUtils.hasText(normalizedDownloadUrl) && relativePath.startsWith(normalizedDownloadUrl)) {
            relativePath = relativePath.substring(normalizedDownloadUrl.length());
        } else if (relativePath.startsWith("/static/")) {
            relativePath = relativePath.substring("/static/".length());
        }

        relativePath = trimLeadingSlashes(relativePath);
        if (!StringUtils.hasText(relativePath)) {
            return Collections.emptyList();
        }

        List<String> relativeVariants = new ArrayList<>();
        relativeVariants.add(relativePath);
        if (StringUtils.hasText(originalRelativePath) && !originalRelativePath.equals(relativePath)) {
            relativeVariants.add(originalRelativePath);
        }

        List<Path> paths = new ArrayList<>();
        Path basePath = Paths.get(normalizeLocalUploadUrl()).toAbsolutePath().normalize();
        addResolvedPath(paths, basePath, relativePath);

        for (Path staticRoot : buildStaticResourceRoots()) {
            for (String relativeVariant : relativeVariants) {
                addResolvedPath(paths, staticRoot, relativeVariant);
            }
        }
        return paths;
    }

    private void addResolvedPath(List<Path> paths, Path basePath, String relativePath) {
        if (basePath == null || !StringUtils.hasText(relativePath)) {
            return;
        }

        Path normalizedBase = basePath.toAbsolutePath().normalize();
        Path filePath = normalizedBase.resolve(relativePath.replace("/", File.separator)).normalize();
        if (filePath.startsWith(normalizedBase)) {
            paths.add(filePath);
        }
    }

    private List<Path> managedLocalRoots() {
        List<Path> roots = new ArrayList<>();
        roots.add(Paths.get(normalizeLocalUploadUrl()).toAbsolutePath().normalize());
        roots.addAll(buildStaticResourceRoots());
        return roots.stream().distinct().toList();
    }

    private Path resolveSafeLocalPath(Path candidate, List<Path> managedRoots) {
        if (candidate == null || Files.isSymbolicLink(candidate)
                || !Files.isRegularFile(candidate, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        try {
            Path realCandidate = candidate.toRealPath();
            for (Path root : managedRoots) {
                try {
                    Path realRoot = root.toRealPath();
                    if (realCandidate.startsWith(realRoot)) {
                        return realCandidate;
                    }
                } catch (IOException ignored) {
                    // 不存在或无法解析的根目录不能构成可信边界。
                }
            }
        } catch (IOException ignored) {
            // 无法取得真实路径时按不可读取处理。
        }
        return null;
    }

    private List<Path> buildStaticResourceRoots() {
        List<Path> roots = new ArrayList<>();
        addConfiguredStaticRoots(roots);

        Path currentPath = Paths.get("").toAbsolutePath().normalize();
        List<Path> baseCandidates = new ArrayList<>();
        for (Path path = currentPath; path != null; path = path.getParent()) {
            baseCandidates.add(path);
        }

        for (Path baseCandidate : baseCandidates) {
            addStaticRootVariants(roots, baseCandidate);
        }
        return roots;
    }

    private void addConfiguredStaticRoots(List<Path> roots) {
        if (!StringUtils.hasText(staticResourceRoots)) {
            return;
        }

        for (String root : staticResourceRoots.split(",")) {
            if (StringUtils.hasText(root)) {
                roots.add(Paths.get(root.trim()).toAbsolutePath().normalize());
            }
        }
    }

    private void addStaticRootVariants(List<Path> roots, Path basePath) {
        addExistingRoot(roots, basePath.resolve("public/static"));
        addExistingRoot(roots, basePath.resolve("public"));
        addExistingRoot(roots, basePath.resolve("dist/static"));
        addExistingRoot(roots, basePath.resolve("dist"));
        addExistingRoot(roots, basePath.resolve("web-dist/static"));
        addExistingRoot(roots, basePath.resolve("poetize-web/public/static"));
        addExistingRoot(roots, basePath.resolve("poetize-web/public"));
        addExistingRoot(roots, basePath.resolve("poetize-web/dist/static"));
        addExistingRoot(roots, basePath.resolve("poetize-web/dist"));
        addExistingRoot(roots, basePath.resolve("poetize-admin/public/static"));
        addExistingRoot(roots, basePath.resolve("poetize-admin/public"));
        addExistingRoot(roots, basePath.resolve("poetize-admin/dist/static"));
        addExistingRoot(roots, basePath.resolve("poetize-admin/dist"));
    }

    private void addExistingRoot(List<Path> roots, Path root) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        if (Files.isDirectory(normalizedRoot) && !roots.contains(normalizedRoot)) {
            roots.add(normalizedRoot);
        }
    }

    private boolean isLocalFileLoadable(Path filePath, String resourcePath) {
        if (!Files.isRegularFile(filePath)) {
            return false;
        }
        if (!isImageResourcePath(resourcePath)) {
            return true;
        }
        return hasSupportedImageHeader(filePath, resourcePath);
    }

    private boolean hasSupportedImageHeader(Path filePath, String resourcePath) {
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            byte[] header = inputStream.readNBytes(32);
            if (header.length == 0) {
                return false;
            }

            String lowerPath = resourcePath.toLowerCase();
            if (lowerPath.matches(".*\\.svg(?:[?#].*)?$")) {
                String textHeader = new String(header, java.nio.charset.StandardCharsets.UTF_8).trim().toLowerCase();
                return textHeader.startsWith("<svg") || textHeader.startsWith("<?xml");
            }
            if (lowerPath.matches(".*\\.jpe?g(?:[?#].*)?$")) {
                return header.length >= 3
                        && (header[0] & 0xFF) == 0xFF
                        && (header[1] & 0xFF) == 0xD8
                        && (header[2] & 0xFF) == 0xFF;
            }
            if (lowerPath.matches(".*\\.png(?:[?#].*)?$")) {
                return startsWith(header, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});
            }
            if (lowerPath.matches(".*\\.gif(?:[?#].*)?$")) {
                return startsWith(header, new byte[]{0x47, 0x49, 0x46, 0x38});
            }
            if (lowerPath.matches(".*\\.webp(?:[?#].*)?$")) {
                return header.length >= 12
                        && startsWith(header, new byte[]{0x52, 0x49, 0x46, 0x46})
                        && header[8] == 0x57 && header[9] == 0x45 && header[10] == 0x42 && header[11] == 0x50;
            }
            if (lowerPath.matches(".*\\.bmp(?:[?#].*)?$")) {
                return startsWith(header, new byte[]{0x42, 0x4D});
            }
            if (lowerPath.matches(".*\\.ico(?:[?#].*)?$")) {
                return startsWith(header, new byte[]{0x00, 0x00, 0x01, 0x00});
            }
            if (lowerPath.matches(".*\\.avif(?:[?#].*)?$")) {
                return header.length >= 12
                        && header[4] == 0x66 && header[5] == 0x74 && header[6] == 0x79 && header[7] == 0x70
                        && header[8] == 0x61 && header[9] == 0x76 && header[10] == 0x69 && header[11] == 0x66;
            }
            return true;
        } catch (IOException e) {
            log.debug("本地资源文件读取失败: {}", filePath, e);
            return false;
        }
    }

    private boolean startsWith(byte[] source, byte[] prefix) {
        if (source.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (source[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean isImageResourcePath(String resourcePath) {
        return resourcePath != null
                && resourcePath.toLowerCase().matches(".*\\.(png|jpe?g|gif|svg|webp|bmp|avif|ico)(?:[?#].*)?$");
    }

    private HttpResponse<Void> sendHead(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(HTTP_REQUEST_TIMEOUT)
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.discarding());
    }

    private HttpResponse<InputStream> sendRangeGet(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(HTTP_REQUEST_TIMEOUT)
                .header("Range", "bytes=0-0")
                .GET()
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            // 消费body以满足HTTP连接复用要求，调用方仅使用headers()和statusCode()
        }
        return response;
    }

    private URI toHttpUri(String resourcePath) {
        try {
            String url = resourcePath.startsWith("//") ? "https:" + resourcePath : resourcePath;
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null
                    || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                    || !StringUtils.hasText(uri.getHost())) {
                return null;
            }
            return uri;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private boolean isRemoteResource(String resourcePath) {
        String lowerPath = resourcePath.toLowerCase();
        return lowerPath.startsWith("http://") || lowerPath.startsWith("https://") || lowerPath.startsWith("//");
    }

    private boolean isLoadableHttpStatus(int statusCode) {
        return statusCode >= 200 && statusCode < 400;
    }

    private boolean shouldFallbackToGet(int statusCode) {
        return statusCode == 403 || statusCode == 405 || statusCode == 501;
    }

    private String normalizeLocalUploadUrl() {
        String uploadPath = StringUtils.hasText(localUploadUrl) ? localUploadUrl : "/app/static/";
        if (uploadPath.startsWith("file:")) {
            uploadPath = uploadPath.substring("file:".length());
        }
        return uploadPath;
    }

    private String normalizeDownloadUrl() {
        if (!StringUtils.hasText(localDownloadUrl)) {
            return "";
        }
        return localDownloadUrl.replace('\\', '/');
    }

    private String stripQueryAndFragment(String path) {
        int queryIndex = path.indexOf('?');
        int fragmentIndex = path.indexOf('#');
        int cutIndex = -1;
        if (queryIndex >= 0) {
            cutIndex = queryIndex;
        }
        if (fragmentIndex >= 0 && (cutIndex < 0 || fragmentIndex < cutIndex)) {
            cutIndex = fragmentIndex;
        }
        return cutIndex >= 0 ? path.substring(0, cutIndex) : path;
    }

    private String trimLeadingSlashes(String path) {
        String result = path;
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        return result;
    }

    private String resourcePathOf(Resource resource) {
        return resource == null ? "" : resource.getPath();
    }
}
