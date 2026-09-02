package com.ld.poetry.service;

import com.ld.poetry.dao.ResourceMapper;
import com.ld.poetry.entity.Resource;
import com.ld.poetry.entity.ResourceLocation;
import com.ld.poetry.enums.ResourceContentState;
import com.ld.poetry.enums.ResourceLocationStatus;
import com.ld.poetry.utils.storage.FileStorageService;
import com.ld.poetry.utils.storage.StorageClientAccess;
import com.ld.poetry.utils.storage.StorageRangeReadHandle;
import com.ld.poetry.utils.storage.StorageResourceRef;
import com.ld.poetry.utils.storage.StoreEnum;
import com.ld.poetry.utils.storage.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ResourceMediaService {

    private final ResourceMapper resourceMapper;
    private final ResourceLocationService resourceLocationService;
    private final FileStorageService fileStorageService;
    private final LocalResourceFileService localResourceFileService;

    public MediaDescriptor resolve(String publicId) {
        String normalizedPublicId = normalizePublicId(publicId);
        Resource resource = resourceMapper.findByPublicId(normalizedPublicId);
        if (resource == null || !Boolean.TRUE.equals(resource.getStatus())) {
            throw notFound("稳定资源不存在或未启用");
        }
        if (!ResourceContentState.isActive(resource.getContentState())) {
            throw unavailable("资源内容替换尚未完成");
        }
        if (resource.getActiveLocationId() == null) {
            throw unavailable("资源尚未登记活动物理副本");
        }

        ResourceLocation location;
        try {
            location = resourceLocationService.requireActiveLocation(resource);
        } catch (RuntimeException e) {
            throw unavailable("资源活动物理副本不可用", e);
        }
        return descriptor(resource, location, normalizedPublicId);
    }

    public StorageClientAccess resolveClientAccess(MediaDescriptor descriptor) {
        if (descriptor.local()) {
            return null;
        }
        StoreService storeService = requireStore(descriptor.storeType());
        StorageClientAccess access;
        try {
            access = storeService.resolveClientAccess(descriptor.storageRef());
        } catch (RuntimeException e) {
            throw unavailable("存储平台无法生成客户端访问地址", e);
        }
        if (access == null || !storeService.isPublicAccessPathTrusted(access.url())) {
            throw unavailable("存储平台未提供受控客户端访问地址");
        }
        assertCurrent(descriptor);
        return access;
    }

    public StorageRangeReadHandle openRange(MediaDescriptor descriptor,
                                            long startInclusive,
                                            long endInclusive) {
        if (startInclusive < 0
                || endInclusive < startInclusive
                || endInclusive >= descriptor.size()) {
            throw new IllegalArgumentException("媒体读取区间不合法");
        }

        StorageRangeReadHandle handle = null;
        try {
            if (descriptor.local()) {
                handle = localResourceFileService.openRange(
                        descriptor.accessPath(),
                        startInclusive,
                        endInclusive,
                        descriptor.size(),
                        descriptor.mimeType()
                );
            } else {
                handle = requireStore(descriptor.storeType()).openReadRange(
                        descriptor.storageRef(),
                        startInclusive,
                        endInclusive
                );
            }
            long expectedLength = endInclusive - startInclusive + 1;
            if (handle.contentLength() != expectedLength
                    || handle.totalLength() != descriptor.size()) {
                throw unavailable("物理副本区间响应与已验证元数据不一致");
            }
            assertCurrent(descriptor);
            return handle;
        } catch (ResourceMediaAccessException e) {
            closeQuietly(handle);
            throw e;
        } catch (NoSuchFileException e) {
            // 物理副本缺失是永久状态：404 让 CDN/监控正确处理，避免 503 触发反复回源
            closeQuietly(handle);
            throw notFound("物理副本不存在");
        } catch (IOException | RuntimeException e) {
            closeQuietly(handle);
            throw unavailable("物理副本读取失败", e);
        }
    }

    private MediaDescriptor descriptor(Resource resource,
                                       ResourceLocation location,
                                       String normalizedPublicId) {
        String resourceHash = normalizeHash(resource.getResourceHash());
        String locationHash = normalizeHash(location.getContentHash());
        if (resourceHash == null
                || locationHash == null
                || !resourceHash.equals(locationHash)
                || resource.getHashVerifiedAt() == null
                || location.getVerifiedAt() == null) {
            throw unavailable("资源尚未建立完整SHA-256可信基线");
        }
        if (!ResourceLocationStatus.ACTIVE.name().equals(location.getStatus())) {
            throw unavailable("资源活动物理副本状态不一致");
        }
        if (location.getSize() == null || location.getSize() <= 0) {
            throw unavailable("资源活动物理副本缺少可信字节数");
        }
        if (!StringUtils.hasText(location.getStoreType())
                || !StringUtils.hasText(location.getAccessPath())) {
            throw unavailable("资源活动物理副本地址不完整");
        }
        String mimeType = StringUtils.hasText(location.getMimeType())
                ? location.getMimeType().trim()
                : StringUtils.hasText(resource.getMimeType())
                ? resource.getMimeType().trim()
                : "application/octet-stream";
        String originalName = StringUtils.hasText(resource.getOriginalName())
                ? resource.getOriginalName().trim()
                : "resource-" + normalizedPublicId;
        if ("application/octet-stream".equals(mimeType)) {
            mimeType = inferMimeTypeFromExtension(originalName, location.getAccessPath());
        }

        return new MediaDescriptor(
                resource.getId(),
                normalizedPublicId,
                location.getId(),
                resource.getLocationVersion() == null ? 0 : resource.getLocationVersion(),
                location.getStoreType(),
                location.getStorageKey(),
                location.getAccessPath(),
                resourceHash,
                location.getSize(),
                mimeType,
                originalName,
                resource.getHashVerifiedAt(),
                location.getVerifiedAt()
        );
    }

    private void assertCurrent(MediaDescriptor expected) {
        Resource resource = resourceMapper.findByPublicId(expected.publicId());
        if (resource == null
                || !Boolean.TRUE.equals(resource.getStatus())
                || !ResourceContentState.isActive(resource.getContentState())
                || !Objects.equals(resource.getId(), expected.resourceId())
                || !Objects.equals(resource.getActiveLocationId(), expected.locationId())
                || normalizeVersion(resource.getLocationVersion()) != expected.locationVersion()
                || !expected.contentHash().equals(normalizeHash(resource.getResourceHash()))) {
            throw unavailable("资源状态在响应期间发生变化");
        }

        ResourceLocation location;
        try {
            location = resourceLocationService.requireLocation(expected.resourceId(), expected.locationId());
        } catch (RuntimeException e) {
            throw unavailable("活动物理副本在响应期间发生变化", e);
        }
        if (!ResourceLocationStatus.ACTIVE.name().equals(location.getStatus())
                || !Objects.equals(location.getStoreType(), expected.storeType())
                || !Objects.equals(location.getStorageKey(), expected.storageKey())
                || !Objects.equals(location.getAccessPath(), expected.accessPath())
                || !expected.contentHash().equals(normalizeHash(location.getContentHash()))
                || !Objects.equals(location.getSize(), expected.size())
                || location.getVerifiedAt() == null) {
            throw unavailable("活动物理副本在响应期间发生变化");
        }
    }

    private StoreService requireStore(String storeType) {
        try {
            StoreService service = fileStorageService.getFileStorageByStoreType(storeType);
            if (!service.getCapability().enabled() || !service.getCapability().readSupported()) {
                throw unavailable("活动存储平台不支持可信读取");
            }
            return service;
        } catch (ResourceMediaAccessException e) {
            throw e;
        } catch (RuntimeException e) {
            throw unavailable("活动存储平台不可用", e);
        }
    }

    private String normalizePublicId(String publicId) {
        if (!StringUtils.hasText(publicId) || !publicId.matches("[a-fA-F0-9]{32}")) {
            throw notFound("资源稳定ID不合法");
        }
        return publicId.toLowerCase(Locale.ROOT);
    }

    private String normalizeHash(String hash) {
        if (!StringUtils.hasText(hash) || !hash.matches("(?i)[a-f0-9]{64}")) {
            return null;
        }
        return hash.toLowerCase(Locale.ROOT);
    }

    private int normalizeVersion(Integer version) {
        return version == null ? 0 : version;
    }

    private ResourceMediaAccessException notFound(String message) {
        return new ResourceMediaAccessException(
                ResourceMediaAccessException.Reason.NOT_FOUND,
                message
        );
    }

    private ResourceMediaAccessException unavailable(String message) {
        return new ResourceMediaAccessException(
                ResourceMediaAccessException.Reason.TEMPORARILY_UNAVAILABLE,
                message
        );
    }

    private ResourceMediaAccessException unavailable(String message, Throwable cause) {
        return new ResourceMediaAccessException(
                ResourceMediaAccessException.Reason.TEMPORARILY_UNAVAILABLE,
                message,
                cause
        );
    }

    private String inferMimeTypeFromExtension(String originalName, String accessPath) {
        // 物理副本(accessPath)的扩展名反映真实存储格式，优先采用；
        // originalName 是用户上传时的文件名，可能与实际存储格式不符（如 .jpg 名存 .webp 内容），
        // 推断错误叠加 nosniff 会导致浏览器拒绝渲染
        String extension = extractExtension(accessPath);
        if (extension == null) {
            extension = extractExtension(originalName);
        }
        if (extension == null) {
            return "application/octet-stream";
        }
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "apng" -> "image/apng";
            case "avif" -> "image/avif";
            case "svg" -> "image/svg+xml";
            case "bmp" -> "image/bmp";
            case "ico" -> "image/x-icon";
            case "mp4" -> "video/mp4";
            case "webm" -> "video/webm";
            case "ogg", "ogv" -> "video/ogg";
            case "mov" -> "video/quicktime";
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "pdf" -> "application/pdf";
            case "woff" -> "font/woff";
            case "woff2" -> "font/woff2";
            case "ttf" -> "font/ttf";
            case "otf" -> "font/otf";
            default -> "application/octet-stream";
        };
    }

    private String extractExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return null;
        }
        String clean = filename.replace('\\', '/');
        int queryIndex = clean.indexOf('?');
        if (queryIndex >= 0) {
            clean = clean.substring(0, queryIndex);
        }
        int dotIndex = clean.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == clean.length() - 1) {
            return null;
        }
        String ext = clean.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        return ext.length() <= 10 ? ext : null;
    }

    private void closeQuietly(StorageRangeReadHandle handle) {
        if (handle == null) {
            return;
        }
        try {
            handle.close();
        } catch (IOException ignored) {
            // 打开失败时尽力释放底层文件或网络连接。
        }
    }

    public record MediaDescriptor(
            Integer resourceId,
            String publicId,
            Long locationId,
            int locationVersion,
            String storeType,
            String storageKey,
            String accessPath,
            String contentHash,
            long size,
            String mimeType,
            String originalName,
            LocalDateTime resourceVerifiedAt,
            LocalDateTime locationVerifiedAt
    ) {
        public boolean local() {
            return StoreEnum.LOCAL.getCode().equals(storeType);
        }

        public String etag() {
            return "\"sha256-" + contentHash + "\"";
        }

        public StorageResourceRef storageRef() {
            return new StorageResourceRef(
                    resourceId,
                    accessPath,
                    storageKey,
                    originalName,
                    size,
                    contentHash,
                    mimeType
            );
        }
    }
}