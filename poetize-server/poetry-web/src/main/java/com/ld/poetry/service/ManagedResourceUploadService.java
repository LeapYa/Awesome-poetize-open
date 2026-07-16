package com.ld.poetry.service;

import com.ld.poetry.entity.Resource;
import com.ld.poetry.entity.ResourceLocation;
import com.ld.poetry.utils.security.FileSecurityValidator;
import com.ld.poetry.utils.storage.FileStorageService;
import com.ld.poetry.utils.security.FileSecurityValidator;
import com.ld.poetry.utils.storage.StorageCapability;
import com.ld.poetry.utils.storage.StorageDeleteResult;
import com.ld.poetry.utils.storage.StorageResourceRef;
import com.ld.poetry.utils.storage.StorageSnapshot;
import com.ld.poetry.utils.storage.StoreService;
import com.ld.poetry.vo.FileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class ManagedResourceUploadService {

    private final FileStorageService fileStorageService;
    private final ResourceStorageSnapshotService snapshotService;
    private final ResourceLocationService resourceLocationService;
    private final FileSecurityValidator fileSecurityValidator;

    public ManagedUploadResult upload(FileVO request, Integer userId) {
        UploadSource source = inspectSource(request, userId);
        StoreService storeService = fileStorageService.getFileStorage(request.getStoreType());
        StorageResourceRef expectedRef = validateTarget(storeService, request, source);

        request.setResourceHash(source.sha256());
        request.setCreateOnly(true);
        FileVO saved = null;
        boolean physicalCreated = false;
        try {
            saved = storeService.saveFile(request);
            if (Boolean.TRUE.equals(saved.getReuseExistingResource())) {
                throw new IllegalStateException("严格受管上传不接受存储适配器复用已有对象");
            }
            physicalCreated = true;
            validateSavedResult(storeService, saved, expectedRef);

            VerifiedTarget target = captureVerifiedTarget(storeService, expectedRef, source);
            ResourceLocationService.RegisteredUpload registered =
                    resourceLocationService.registerVerifiedUpload(
                            new ResourceLocationService.VerifiedUpload(
                                    userId,
                                    source.type(),
                                    storeService.getStoreName(),
                                    expectedRef.storageKey(),
                                    expectedRef.path(),
                                    target.sha256(),
                                    target.size(),
                                    target.mimeType(),
                                    source.originalName(),
                                    target.width(),
                                    target.height(),
                                    target.verifiedAt()
                            )
                    );
            physicalCreated = false;
            return new ManagedUploadResult(
                    registered.resource(),
                    registered.location(),
                    registered.stablePath(),
                    registered.reused()
            );
        } catch (RuntimeException e) {
            if (physicalCreated) {
                compensatePhysicalUpload(storeService, expectedRef, e);
            }
            throw e;
        }
    }

    private UploadSource inspectSource(FileVO request, Integer userId) {
        if (request == null || userId == null || request.getFile() == null) {
            throw new IllegalArgumentException("上传文件、资源信息和用户不能为空");
        }
        MultipartFile file = request.getFile();
        if (file.isEmpty() || file.getSize() <= 0 || file.getSize() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("上传文件大小不合法");
        }

        String type = StringUtils.hasText(request.getType()) ? request.getType().trim() : null;
        if (!StringUtils.hasText(type) || type.length() > 32) {
            throw new IllegalArgumentException("上传资源类型不合法");
        }
        String relativePath = normalizeRelativePath(request.getRelativePath());
        request.setType(type);
        request.setRelativePath(relativePath);

        String fileName = fileNameFromRelativePath(relativePath);
        FileSecurityValidator.ValidationResult validation =
                fileSecurityValidator.validateFile(file, fileName, file.getContentType());
        if (!validation.isSuccess()) {
            throw new IllegalArgumentException("文件验证失败: " + validation.getMessage());
        }

        String originalName = StringUtils.hasText(request.getOriginalName())
                ? request.getOriginalName().trim()
                : StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename().trim()
                : fileName;
        if (!StringUtils.hasText(originalName) || originalName.length() > 512) {
            throw new IllegalArgumentException("上传文件名称不合法");
        }
        String mimeType = normalizeMimeType(file.getContentType());
        try (InputStream inputStream = file.getInputStream()) {
            String sha256 = DigestUtils.sha256Hex(inputStream);
            if (!sha256.matches("[a-f0-9]{64}")) {
                throw new IllegalStateException("无法计算上传源文件SHA-256");
            }
            return new UploadSource(type, originalName, mimeType, file.getSize(), sha256);
        } catch (IOException e) {
            throw new IllegalStateException("读取上传源文件失败: " + e.getMessage(), e);
        }
    }

    private StorageResourceRef validateTarget(StoreService storeService,
                                              FileVO request,
                                              UploadSource source) {
        if (storeService == null) {
            throw new IllegalStateException("上传存储平台不存在");
        }
        StorageCapability capability = storeService.getCapability();
        if (!capability.enabled()
                || !capability.uploadSupported()
                || !capability.readSupported()
                || !capability.deleteSupported()
                || !storeService.supportsDeterministicWrite()) {
            throw new IllegalStateException("目标存储不支持严格受管上传所需的新建、回读和补偿删除能力");
        }
        if (!capability.supports(source.mimeType(), source.size())) {
            throw new IllegalStateException("上传文件大小或MIME类型不受目标存储支持");
        }

        String storageKey = request.getRelativePath();
        String accessPath = storeService.resolveAccessPath(storageKey);
        if (!StringUtils.hasText(accessPath)
                || accessPath.length() > 2048
                || accessPath.startsWith("/media/")) {
            throw new IllegalStateException("目标存储无法重建合法的确定性访问地址");
        }
        return new StorageResourceRef(
                null,
                accessPath,
                storageKey,
                fileNameFromRelativePath(storageKey),
                source.size(),
                source.sha256(),
                source.mimeType()
        );
    }

    private void validateSavedResult(StoreService storeService,
                                     FileVO saved,
                                     StorageResourceRef expectedRef) {
        if (saved == null
                || !storeService.getStoreName().equals(saved.getStoreType())
                || !expectedRef.storageKey().equals(saved.getStorageKey())
                || !expectedRef.path().equals(saved.getVisitPath())) {
            throw new IllegalStateException("存储平台返回的物理对象引用与确定性目标不一致");
        }
        if (StringUtils.hasText(saved.getResourceHash())
                && !expectedRef.hash().equalsIgnoreCase(saved.getResourceHash())) {
            throw new IllegalStateException("存储平台返回的上传哈希与源文件SHA-256不一致");
        }
    }

    private VerifiedTarget captureVerifiedTarget(StoreService storeService,
                                                  StorageResourceRef targetRef,
                                                  UploadSource source) {
        String sha256;
        long size;
        String mimeType;
        Integer width = null;
        Integer height = null;
        try (StorageSnapshot snapshot = snapshotService.capture(storeService, targetRef)) {
            if (!source.sha256().equalsIgnoreCase(snapshot.sha256())) {
                throw new IllegalStateException(
                        "上传目标完整回读SHA-256不一致：源=" + source.sha256()
                                + "，目标=" + snapshot.sha256()
                );
            }
            if (source.size() != snapshot.size()) {
                throw new IllegalStateException(
                        "上传目标完整回读字节数不一致：源=" + source.size()
                                + "，目标=" + snapshot.size()
                );
            }
            int[] dimensions = readImageDimensions(snapshot, source.mimeType());
            if (dimensions != null) {
                width = dimensions[0];
                height = dimensions[1];
            }
            sha256 = snapshot.sha256().toLowerCase(Locale.ROOT);
            size = snapshot.size();
            mimeType = normalizeVerifiedMimeType(snapshot.contentType(), source.mimeType());
        } catch (IOException e) {
            throw new IllegalStateException("上传目标快照清理失败: " + e.getMessage(), e);
        }
        return new VerifiedTarget(sha256, size, mimeType, width, height, LocalDateTime.now());
    }

    private int[] readImageDimensions(StorageSnapshot snapshot, String sourceMimeType) {
        if (snapshot == null
                || !StringUtils.hasText(sourceMimeType)
                || !sourceMimeType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return null;
        }
        try {
            BufferedImage image = ImageIO.read(snapshot.path().toFile());
            return image == null ? null : new int[]{image.getWidth(), image.getHeight()};
        } catch (IOException e) {
            log.debug("读取上传目标图片尺寸失败: {}", e.getMessage());
            return null;
        }
    }

    private void compensatePhysicalUpload(StoreService storeService,
                                          StorageResourceRef targetRef,
                                          Exception originalError) {
        try {
            List<StorageDeleteResult> results = storeService.deleteFiles(List.of(targetRef));
            if (results.size() != 1
                    || (!results.getFirst().success() && !results.getFirst().missing())) {
                IllegalStateException cleanupError = new IllegalStateException(
                        "上传登记失败，且本次创建的物理对象补偿删除失败"
                );
                originalError.addSuppressed(cleanupError);
                log.error("上传物理对象补偿删除失败: store={}, path={}",
                        storeService.getStoreName(), targetRef.path());
            }
        } catch (RuntimeException cleanupError) {
            originalError.addSuppressed(cleanupError);
            log.error("上传物理对象补偿删除异常: store={}, path={}",
                    storeService.getStoreName(), targetRef.path(), cleanupError);
        }
    }

    private String normalizeRelativePath(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("上传物理对象路径不能为空");
        }
        String path = value.trim();
        if (path.length() > 512
                || path.startsWith("/")
                || path.endsWith("/")
                || path.contains("\\")
                || path.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("上传物理对象路径不合法");
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("上传物理对象路径不合法");
            }
        }
        return path;
    }

    private String fileNameFromRelativePath(String relativePath) {
        int slash = relativePath.lastIndexOf('/');
        return slash >= 0 ? relativePath.substring(slash + 1) : relativePath;
    }

    private String normalizeVerifiedMimeType(String targetContentType, String sourceContentType) {
        String target = normalizeMimeType(targetContentType);
        if ("application/octet-stream".equals(target) && StringUtils.hasText(sourceContentType)) {
            return normalizeMimeType(sourceContentType);
        }
        return target;
    }

    private String normalizeMimeType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "application/octet-stream";
        }
        int separator = contentType.indexOf(';');
        String normalized = separator >= 0 ? contentType.substring(0, separator) : contentType;
        normalized = normalized.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 256) {
            throw new IllegalArgumentException("上传文件MIME类型过长");
        }
        return normalized;
    }

    private record UploadSource(
            String type,
            String originalName,
            String mimeType,
            long size,
            String sha256
    ) {
    }

    private record VerifiedTarget(
            String sha256,
            long size,
            String mimeType,
            Integer width,
            Integer height,
            LocalDateTime verifiedAt
    ) {
    }

    public record ManagedUploadResult(
            Resource resource,
            ResourceLocation location,
            String stablePath,
            boolean reused
    ) {
    }
}