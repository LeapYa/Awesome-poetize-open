package com.ld.poetry.service;

import com.ld.poetry.utils.security.FileSecurityValidator;
import com.ld.poetry.utils.storage.PathMultipartFile;
import com.ld.poetry.utils.storage.StorageCapability;
import com.ld.poetry.utils.storage.StorageReadHandle;
import com.ld.poetry.utils.storage.StorageResourceRef;
import com.ld.poetry.utils.storage.StorageSnapshot;
import com.ld.poetry.utils.storage.StoreService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class ResourceStorageSnapshotService {

    private final FileSecurityValidator fileSecurityValidator;
    private final Path workspace;
    private final long maxFileSize;

    public ResourceStorageSnapshotService(
            FileSecurityValidator fileSecurityValidator,
            @Value("${resource.migration.workspace:${java.io.tmpdir}/poetize-resource-migration}") String workspace,
            @Value("${resource.migration.max-file-size:536870912}") long maxFileSize) {
        this.fileSecurityValidator = fileSecurityValidator;
        this.workspace = Path.of(workspace).toAbsolutePath().normalize();
        this.maxFileSize = maxFileSize;
    }

    public StorageSnapshot capture(StoreService storeService, StorageResourceRef resource) {
        if (storeService == null || resource == null) {
            throw new IllegalArgumentException("存储服务和资源引用不能为空");
        }
        StorageCapability capability = storeService.getCapability();
        if (!capability.enabled() || !capability.readSupported()) {
            throw new IllegalStateException("当前存储平台不支持完整读取原始文件");
        }
        if (maxFileSize <= 0) {
            throw new IllegalStateException("资源迁移读取上限配置不合法");
        }
        if (resource.size() != null && resource.size() > maxFileSize) {
            throw new IllegalStateException("资源大小超过迁移读取上限");
        }

        Path snapshotPath = null;
        try {
            Files.createDirectories(workspace);
            snapshotPath = Files.createTempFile(workspace, "resource-", ".snapshot");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String contentType;
            Long declaredLength;
            try (StorageReadHandle readHandle = storeService.openRead(resource, maxFileSize);
                 InputStream inputStream = new DigestInputStream(readHandle.inputStream(), digest);
                 OutputStream outputStream = Files.newOutputStream(
                         snapshotPath,
                         StandardOpenOption.TRUNCATE_EXISTING,
                         StandardOpenOption.WRITE
                 )) {
                declaredLength = readHandle.contentLength();
                contentType = normalizeContentType(
                        StringUtils.hasText(readHandle.contentType())
                                ? readHandle.contentType()
                                : resource.mimeType()
                );
                inputStream.transferTo(outputStream);
            }

            long size = Files.size(snapshotPath);
            if (size == 0) {
                throw new IllegalStateException("读取到的资源内容为空");
            }
            if (declaredLength != null && declaredLength != size) {
                throw new IllegalStateException(
                        "远端声明大小与完整读取大小不一致：声明=" + declaredLength + "，实际=" + size
                );
            }
            if (size > maxFileSize) {
                throw new IllegalStateException("资源内容超过迁移读取上限");
            }

            String originalName = resolveOriginalName(resource, contentType);
            rejectErrorDocument(snapshotPath, contentType);
            PathMultipartFile multipartFile = new PathMultipartFile(
                    "file",
                    originalName,
                    contentType,
                    snapshotPath
            );
            FileSecurityValidator.ValidationResult validation = fileSecurityValidator.validateFile(
                    multipartFile,
                    originalName,
                    contentType
            );
            if (!validation.isSuccess()) {
                throw new IllegalStateException("资源内容安全校验失败：" + validation.getMessage());
            }

            return new StorageSnapshot(
                    snapshotPath,
                    HexFormat.of().formatHex(digest.digest()),
                    size,
                    contentType,
                    originalName
            );
        } catch (IOException e) {
            deleteQuietly(snapshotPath);
            throw new IllegalStateException("资源快照写入失败: " + e.getMessage(), e);
        } catch (NoSuchAlgorithmException e) {
            deleteQuietly(snapshotPath);
            throw new IllegalStateException("当前JDK不支持SHA-256算法", e);
        } catch (RuntimeException e) {
            deleteQuietly(snapshotPath);
            throw e;
        }
    }

    private String resolveOriginalName(StorageResourceRef resource, String contentType) {
        String name = StringUtils.hasText(resource.originalName())
                ? resource.originalName()
                : fileNameFromPath(resource.path());
        name = sanitizeFileName(name);
        if (!name.contains(".")) {
            name += extensionFor(contentType);
        }
        return name;
    }

    private String fileNameFromPath(String value) {
        if (!StringUtils.hasText(value)) {
            return "resource";
        }
        try {
            URI uri = URI.create(value);
            String path = StringUtils.hasText(uri.getPath()) ? uri.getPath() : value;
            int slash = path.lastIndexOf('/');
            return slash >= 0 ? path.substring(slash + 1) : path;
        } catch (IllegalArgumentException e) {
            int query = value.indexOf('?');
            String path = query >= 0 ? value.substring(0, query) : value;
            int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            return slash >= 0 ? path.substring(slash + 1) : path;
        }
    }

    private String sanitizeFileName(String value) {
        String normalized = StringUtils.hasText(value) ? value : "resource";
        normalized = normalized.replace('\\', '_').replace('/', '_');
        normalized = normalized.replaceAll("[^\\p{L}\\p{N}._-]", "_");
        if (normalized.isBlank()) {
            return "resource";
        }
        return normalized.length() <= 180 ? normalized : normalized.substring(normalized.length() - 180);
    }

    private String extensionFor(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return ".bin";
        }
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "image/bmp" -> ".bmp";
            case "image/x-icon", "image/vnd.microsoft.icon" -> ".ico";
            case "video/mp4" -> ".mp4";
            case "video/webm" -> ".webm";
            case "audio/mpeg" -> ".mp3";
            case "audio/ogg" -> ".ogg";
            default -> ".bin";
        };
    }

    private String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "application/octet-stream";
        }
        int separator = contentType.indexOf(';');
        String normalized = separator >= 0 ? contentType.substring(0, separator) : contentType;
        return normalized.trim().toLowerCase(Locale.ROOT);
    }

    private void rejectErrorDocument(Path path, String contentType) throws IOException {
        if ("text/html".equals(contentType) || "application/xhtml+xml".equals(contentType)) {
            throw new IllegalStateException("远端返回了HTML错误页而不是资源文件");
        }
        byte[] prefix = new byte[(int) Math.min(512, Files.size(path))];
        try (InputStream inputStream = Files.newInputStream(path)) {
            int read = inputStream.read(prefix);
            if (read <= 0) {
                return;
            }
            String text = new String(prefix, 0, read, java.nio.charset.StandardCharsets.UTF_8)
                    .stripLeading()
                    .toLowerCase(Locale.ROOT);
            if (text.startsWith("<!doctype html") || text.startsWith("<html")) {
                throw new IllegalStateException("远端返回了HTML错误页而不是资源文件");
            }
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 主异常更能说明失败原因。
        }
    }
}