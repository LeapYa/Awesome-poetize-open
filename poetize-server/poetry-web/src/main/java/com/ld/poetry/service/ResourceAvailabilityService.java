package com.ld.poetry.service;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.poetry.entity.Resource;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Slf4j
@Service
public class ResourceAvailabilityService {

    private static final int INVALID_CHECK_CONCURRENCY = 8;
    private static final Duration HTTP_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration HTTP_REQUEST_TIMEOUT = Duration.ofSeconds(3);

    @Autowired
    private ResourceService resourceService;

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
        if (CollectionUtils.isEmpty(resources)) {
            return Collections.emptyList();
        }

        int threadCount = Math.min(INVALID_CHECK_CONCURRENCY, Math.max(resources.size(), 1));
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        try {
            List<Callable<Boolean>> tasks = resources.stream()
                    .map(resource -> (Callable<Boolean>) () -> !isResourceLoadable(resource == null ? null : resource.getPath()))
                    .toList();
            List<Future<Boolean>> futures = executorService.invokeAll(tasks);

            List<Resource> invalidResources = new ArrayList<>();
            for (int i = 0; i < futures.size(); i++) {
                try {
                    if (Boolean.TRUE.equals(futures.get(i).get())) {
                        invalidResources.add(resources.get(i));
                    }
                } catch (ExecutionException e) {
                    log.warn("检测资源可用性失败，按无效资源处理: {}", resourcePathOf(resources.get(i)), e);
                    invalidResources.add(resources.get(i));
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

        try {
            int headStatus = sendHead(uri);
            if (isLoadableHttpStatus(headStatus)) {
                return true;
            }
            if (!shouldFallbackToGet(headStatus)) {
                return false;
            }

            int getStatus = sendRangeGet(uri);
            return isLoadableHttpStatus(getStatus);
        } catch (IOException | IllegalArgumentException e) {
            log.debug("远程资源检测失败: {}", resourcePath, e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean isLocalResourceLoadable(String resourcePath) {
        for (Path filePath : resolveLocalResourcePaths(resourcePath)) {
            if (isLocalFileLoadable(filePath, resourcePath)) {
                return true;
            }
        }
        return false;
    }

    List<Path> resolveLocalResourcePaths(String resourcePath) {
        String relativePath = stripQueryAndFragment(resourcePath.replace('\\', '/'));
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

    private int sendHead(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(HTTP_REQUEST_TIMEOUT)
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private int sendRangeGet(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(HTTP_REQUEST_TIMEOUT)
                .header("Range", "bytes=0-0")
                .GET()
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            return response.statusCode();
        }
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
