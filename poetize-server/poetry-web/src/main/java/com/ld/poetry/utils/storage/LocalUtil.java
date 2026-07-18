package com.ld.poetry.utils.storage;

import cn.hutool.core.io.FileUtil;
import com.ld.poetry.handle.PoetryRuntimeException;
import com.ld.poetry.utils.StringUtil;
import com.ld.poetry.vo.FileVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "local.enable", havingValue = "true")
public class LocalUtil implements StoreService {

    @Value("${local.uploadUrl}")
    private String uploadUrl;

    @Value("${local.downloadUrl}")
    private String downloadUrl;

    /**
     * 受控前端静态根目录（如 web-dist/static）。上传目录找不到物理副本时回退到这里读取，
     * 用于接管随前端构建产物发布的默认素材（backgroundPicture.jpg 等）。
     * 与 ResourceAvailabilityService/ResourceReplaceService 的同名配置保持一致。
     */
    @Value("${resource.availability.staticRoots:${RESOURCE_AVAILABILITY_STATICROOTS:}}")
    private String staticResourceRoots;

    @Override
    public List<StorageDeleteResult> deleteFiles(List<StorageResourceRef> resources) {
        if (CollectionUtils.isEmpty(resources)) {
            return List.of();
        }

        return resources.stream().map(resource -> {
            try {
                // 删除仅限上传目录下的受控副本，不回退到前端静态根（web-dist），
                // 避免误删随版本发布的默认素材导致 nginx 404。
                File file = resolveUploadFile(resource.path());
                if (file == null || !file.isFile()) {
                    log.warn("本地资源不存在：{}", resource.path());
                    return StorageDeleteResult.missing(resource);
                }
                if (!file.delete()) {
                    log.error("本地资源删除失败：{}", resource.path());
                    return StorageDeleteResult.failed(resource, "本地文件删除失败");
                }
                log.info("本地资源删除成功：{}", resource.path());
                return StorageDeleteResult.deleted(resource);
            } catch (Exception e) {
                log.error("本地资源删除异常：{}", resource.path(), e);
                return StorageDeleteResult.failed(resource, e.getMessage());
            }
        }).toList();
    }

    /**
     * 仅在上传目录内解析文件，不回退到前端静态根。用于删除场景。
     */
    private File resolveUploadFile(String resourcePath) {
        String relativePath = stripDownloadPrefix(resourcePath);
        File root = new File(uploadUrl).getAbsoluteFile();
        File candidate = new File(root, relativePath.replace("/", File.separator));
        return resolveWithin(root, candidate);
    }

    @Override
    public StorageReadHandle openRead(StorageResourceRef resource, long maxBytes) {
        try {
            File file = resolveLocalFile(resource.path());
            if (!file.isFile()) {
                throw new IllegalStateException("本地文件不存在");
            }
            return StorageReadHandle.bounded(
                    new FileInputStream(file),
                    file.length(),
                    resource.mimeType(),
                    file.toURI(),
                    maxBytes
            );
        } catch (IOException e) {
            throw new IllegalStateException("本地文件读取失败: " + e.getMessage(), e);
        }
    }

    @Override
    public StorageVerificationResult verify(StorageResourceRef resource) {
        try {
            File file = resolveLocalFile(resource.path());
            if (!file.isFile()) {
                return StorageVerificationResult.missing("本地文件不存在");
            }
            return StorageVerificationResult.available(file.length(), calculateFileHash(file));
        } catch (Exception e) {
            return StorageVerificationResult.unknown(e.getMessage());
        }
    }

    @Override
    public StorageCapability getCapability() {
        return new StorageCapability(
                StoreEnum.LOCAL.getCode(), true, true, true, true, true, 0, List.of()
        );
    }

    @Override
    public String resolveAccessPath(String storageKey) {
        if (!StringUtils.hasText(storageKey)) {
            return null;
        }
        String normalizedKey = storageKey.replace('\\', '/');
        while (normalizedKey.startsWith("/")) {
            normalizedKey = normalizedKey.substring(1);
        }
        return downloadUrl.endsWith("/") ? downloadUrl + normalizedKey : downloadUrl + "/" + normalizedKey;
    }

    @Override
    public String resolveStorageKey(String accessPath) {
        if (!StringUtils.hasText(accessPath)) {
            return null;
        }
        String normalizedPath = accessPath.trim().replace('\\', '/');
        if (normalizedPath.indexOf('?') >= 0 || normalizedPath.indexOf('#') >= 0 || normalizedPath.contains("%")) {
            return null;
        }
        String normalizedDownloadUrl = downloadUrl.replace('\\', '/');
        String relativePath;
        if (normalizedPath.startsWith(normalizedDownloadUrl)) {
            relativePath = normalizedPath.substring(normalizedDownloadUrl.length());
        } else if (normalizedPath.startsWith("/static/")) {
            relativePath = normalizedPath.substring("/static/".length());
        } else {
            return null;
        }
        while (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }
        if (!StringUtils.hasText(relativePath)) {
            return null;
        }
        for (String segment : relativePath.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                return null;
            }
        }
        return relativePath;
    }

    @Override
    public boolean isPublicAccessPathTrusted(String accessPath) {
        return resolveStorageKey(accessPath) != null;
    }

    @Override
    public boolean supportsDeterministicWrite() {
        return true;
    }

    private File resolveLocalFile(String resourcePath) {
        String relativePath = stripDownloadPrefix(resourcePath);

        File uploadRoot = new File(uploadUrl).getAbsoluteFile();
        File uploadCandidate = new File(uploadRoot, relativePath.replace("/", File.separator));
        File uploadResolved = resolveWithin(uploadRoot, uploadCandidate);
        if (uploadResolved != null && uploadResolved.isFile()) {
            return uploadResolved;
        }

        // 上传目录找不到物理副本时，回退到受控前端静态根（如 web-dist/static），
        // 用于接管随前端构建产物发布的默认素材。写操作仍只落到 uploadUrl，不污染静态根。
        for (Path staticRoot : buildStaticResourceRoots()) {
            File candidate = staticRoot.resolve(relativePath.replace("/", File.separator)).toFile();
            File resolved = resolveWithin(staticRoot.toFile(), candidate);
            if (resolved != null && resolved.isFile()) {
                return resolved;
            }
        }

        throw new PoetryRuntimeException("本地文件不存在：" + resourcePath);
    }

    private String stripDownloadPrefix(String resourcePath) {
        if (!StringUtils.hasText(resourcePath)) {
            throw new PoetryRuntimeException("资源路径不能为空！");
        }
        String normalizedDownloadUrl = downloadUrl.replace('\\', '/');
        String normalizedPath = resourcePath.replace('\\', '/');
        String relativePath;
        if (normalizedPath.startsWith(normalizedDownloadUrl)) {
            relativePath = normalizedPath.substring(normalizedDownloadUrl.length());
        } else if (normalizedPath.startsWith("/static/")) {
            relativePath = normalizedPath.substring("/static/".length());
        } else {
            throw new PoetryRuntimeException("不是可管理的本地资源路径：" + resourcePath);
        }
        while (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }
        return relativePath;
    }

    private File resolveWithin(File root, File candidate) {
        try {
            String rootPath = root.getCanonicalPath();
            String resolvedPath = candidate.getCanonicalPath();
            if (!resolvedPath.equals(rootPath)
                    && !resolvedPath.startsWith(rootPath + File.separator)) {
                return null;
            }
            return candidate;
        } catch (IOException e) {
            throw new PoetryRuntimeException("本地资源路径解析失败：" + e.getMessage());
        }
    }

    private List<Path> buildStaticResourceRoots() {
        List<Path> roots = new ArrayList<>();
        if (!StringUtils.hasText(staticResourceRoots)) {
            return roots;
        }
        for (String root : staticResourceRoots.split(",")) {
            if (StringUtils.hasText(root)) {
                Path normalized = Paths.get(root.trim()).toAbsolutePath().normalize();
                if (Files.isDirectory(normalized) && !roots.contains(normalized)) {
                    roots.add(normalized);
                }
            }
        }
        return roots;
    }

    @Override
    public FileVO saveFile(FileVO fileVO) {
        log.info("LocalUtil.saveFile 开始 - uploadUrl: {}, downloadUrl: {}", uploadUrl, downloadUrl);
        log.info("接收到的文件信息 - RelativePath: {}", fileVO.getRelativePath());
        
        if (!StringUtils.hasText(fileVO.getRelativePath()) ||
                fileVO.getRelativePath().startsWith("/") ||
                fileVO.getRelativePath().endsWith("/")) {
            throw new PoetryRuntimeException("文件路径不合法！");
        }

        String path = fileVO.getRelativePath();
        if (path.contains("/")) {
            String[] split = path.split("/");
            if (split.length > 5) {
                throw new PoetryRuntimeException("文件路径不合法！");
            }
            for (int i = 0; i < split.length - 1; i++) {
                if (!StringUtil.isValidDirectoryName(split[i])) {
                    throw new PoetryRuntimeException("文件路径不合法！");
                }
            }
            if (!StringUtil.isValidFileName(split[split.length - 1])) {
                throw new PoetryRuntimeException("文件路径不合法！");
            }
        }

        // 统一使用File.separator处理路径分隔符，确保Windows兼容
        String absolutePath = (uploadUrl + path).replace("/", File.separator);
        log.info("计算出的绝对路径: {}", absolutePath);
        if (FileUtil.exist(absolutePath)) {
            throw new PoetryRuntimeException("文件已存在！");
        }
        File tempFile = null;
        try {
            // 手动创建文件，确保更可靠
            File newFile = new File(absolutePath);
            File parentDir = newFile.getParentFile();
            log.info("父目录路径: {}", parentDir != null ? parentDir.getAbsolutePath() : "null");
            log.info("父目录是否存在: {}", parentDir != null && parentDir.exists());
            
            // 确保父目录存在
            if (parentDir != null) {
                if (parentDir.exists()) {
                    // 检查是否为目录
                    if (!parentDir.isDirectory()) {
                        log.warn("路径存在但不是目录，是一个文件！删除并重新创建: {}", parentDir.getAbsolutePath());
                        boolean deleted = parentDir.delete();
                        log.info("删除文件结果: {}", deleted);
                        if (deleted) {
                            boolean created = parentDir.mkdirs();
                            log.info("重新创建目录结果: {}", created);
                        } else {
                            throw new PoetryRuntimeException("无法删除同名文件: " + parentDir.getAbsolutePath());
                        }
                    } else {
                        log.info("父目录已存在且是目录");
                    }
                } else {
                    log.info("父目录不存在，开始创建: {}", parentDir.getAbsolutePath());
                    boolean created = parentDir.mkdirs();
                    log.info("创建父目录结果: {}, 目录是否存在: {}", created, parentDir.exists());
                    if (!created && !parentDir.exists()) {
                        throw new PoetryRuntimeException("创建父目录失败: " + parentDir.getAbsolutePath());
                    }
                }
            }

            tempFile = File.createTempFile(newFile.getName() + ".", ".uploading", parentDir);
            String resourceHash = writeTempFileAndCalculateHash(fileVO.getFile(), tempFile);
            fileVO.setResourceHash(resourceHash);

            log.info("准备保存文件: {}", newFile.getAbsolutePath());
            moveTempFile(tempFile, newFile);
            tempFile = null;
            log.info("文件内容写入成功，文件大小: {} bytes", newFile.length());
            FileVO result = new FileVO();
            result.setAbsolutePath(absolutePath);
            result.setVisitPath(resolveAccessPath(path));
            result.setStoreType(StoreEnum.LOCAL.getCode());
            result.setStorageKey(path);
            result.setResourceHash(resourceHash);
            log.info("LocalUtil.saveFile 完成 - VisitPath: {}", result.getVisitPath());
            return result;
        } catch (IOException e) {
            log.error("文件上传失败：", e);
            throw new PoetryRuntimeException("文件上传失败！");
        } finally {
            if (tempFile != null && tempFile.exists()) {
                FileUtil.del(tempFile);
            }
        }
    }

    private String writeTempFileAndCalculateHash(MultipartFile file, File tempFile) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream inputStream = new DigestInputStream(file.getInputStream(), digest);
                 OutputStream outputStream = new FileOutputStream(tempFile)) {
                inputStream.transferTo(outputStream);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前JDK不支持SHA-256算法", e);
        }
    }

    private void moveTempFile(File tempFile, File newFile) throws IOException {
        // 不使用 ATOMIC_MOVE：Java 对“原子移动且目标已存在”是否覆盖的行为不作保证。
        // 临时文件与目标位于同一目录，普通 move 保留 create-only 语义，目标存在时必定失败。
        java.nio.file.Files.move(tempFile.toPath(), newFile.toPath());
    }

    private String calculateFileHash(File file) {
        try (InputStream inputStream = new java.io.FileInputStream(file)) {
            return DigestUtils.sha256Hex(inputStream);
        } catch (IOException e) {
            log.warn("计算已有本地资源哈希失败: {}, err={}", file.getAbsolutePath(), e.getMessage());
            return null;
        }
    }

    @Override
    public String getStoreName() {
        return StoreEnum.LOCAL.getCode();
    }
}
