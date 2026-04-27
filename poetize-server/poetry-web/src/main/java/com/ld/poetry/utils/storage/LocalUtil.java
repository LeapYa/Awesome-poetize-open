package com.ld.poetry.utils.storage;

import cn.hutool.core.io.FileUtil;
import com.ld.poetry.entity.Resource;
import com.ld.poetry.handle.PoetryRuntimeException;
import com.ld.poetry.service.ResourceService;
import com.ld.poetry.utils.StringUtil;
import com.ld.poetry.vo.FileVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

    @Autowired
    private ResourceService resourceService;

    @Override
    public void deleteFile(List<String> files) {
        if (CollectionUtils.isEmpty(files)) {
            return;
        }

        for (String filePath : files) {
            File file = new File(filePath.replace(downloadUrl, uploadUrl));
            if (file.exists() && file.isFile()) {
                if (file.delete()) {
                    log.info("文件删除成功：" + filePath);
                    resourceService.lambdaUpdate().eq(Resource::getPath, filePath).remove();
                } else {
                    log.error("文件删除失败：" + filePath);
                }
            } else {
                log.error("文件不存在或者不是一个文件：" + filePath);
            }
        }
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

            Resource existingResource = findReusableResource(resourceHash);
            if (existingResource == null && StringUtils.hasText(resourceHash)) {
                existingResource = findAndBackfillReusableResource(resourceHash, fileVO.getFile().getSize());
            }
            if (existingResource != null) {
                FileUtil.del(tempFile);
                FileVO result = new FileVO();
                result.setAbsolutePath(existingResource.getPath().replace(downloadUrl, uploadUrl)
                        .replace("/", File.separator));
                result.setVisitPath(existingResource.getPath());
                result.setStoreType(StoreEnum.LOCAL.getCode());
                result.setResourceHash(resourceHash);
                result.setReuseExistingResource(true);
                log.info("LocalUtil.saveFile 命中相同哈希资源，复用已有路径: {}", result.getVisitPath());
                return result;
            }

            log.info("准备保存文件: {}", newFile.getAbsolutePath());
            moveTempFile(tempFile, newFile);
            tempFile = null;
            log.info("文件内容写入成功，文件大小: {} bytes", newFile.length());
            FileVO result = new FileVO();
            result.setAbsolutePath(absolutePath);
            result.setVisitPath(downloadUrl + path);
            result.setStoreType(StoreEnum.LOCAL.getCode());
            result.setResourceHash(resourceHash);
            log.info("LocalUtil.saveFile 完成 - VisitPath: {}", result.getVisitPath());
            return result;
        } catch (IOException e) {
            log.error("文件上传失败：", e);
            FileUtil.del(absolutePath);
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
        try {
            java.nio.file.Files.move(
                    tempFile.toPath(),
                    newFile.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE
            );
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            java.nio.file.Files.move(tempFile.toPath(), newFile.toPath());
        }
    }

    private Resource findReusableResource(String resourceHash) {
        if (!StringUtils.hasText(resourceHash)) {
            return null;
        }

        List<Resource> resources = resourceService.lambdaQuery()
                .select(Resource::getPath, Resource::getResourceHash, Resource::getStoreType)
                .eq(Resource::getResourceHash, resourceHash)
                .and(wrapper -> wrapper.eq(Resource::getStoreType, StoreEnum.LOCAL.getCode())
                        .or()
                        .isNull(Resource::getStoreType))
                .isNotNull(Resource::getPath)
                .list();

        if (CollectionUtils.isEmpty(resources)) {
            return null;
        }

        for (Resource resource : resources) {
            String path = resource.getPath();
            if (!StringUtils.hasText(path)) {
                continue;
            }
            File file = new File(path.replace(downloadUrl, uploadUrl));
            if (file.exists() && file.isFile()) {
                return resource;
            }
            log.warn("资源哈希匹配但本地文件不存在，跳过复用: {}", path);
        }
        return null;
    }

    private Resource findAndBackfillReusableResource(String resourceHash, long fileSize) {
        if (!StringUtils.hasText(resourceHash) || fileSize > Integer.MAX_VALUE) {
            return null;
        }

        List<Resource> resources = resourceService.lambdaQuery()
                .select(Resource::getId, Resource::getPath, Resource::getSize, Resource::getStoreType)
                .isNull(Resource::getResourceHash)
                .eq(Resource::getSize, Integer.valueOf(Long.toString(fileSize)))
                .and(wrapper -> wrapper.eq(Resource::getStoreType, StoreEnum.LOCAL.getCode())
                        .or()
                        .isNull(Resource::getStoreType))
                .isNotNull(Resource::getPath)
                .list();

        if (CollectionUtils.isEmpty(resources)) {
            return null;
        }

        for (Resource resource : resources) {
            String path = resource.getPath();
            if (!StringUtils.hasText(path)) {
                continue;
            }

            File file = new File(path.replace(downloadUrl, uploadUrl));
            if (!file.exists() || !file.isFile()) {
                log.warn("待回填哈希的本地资源文件不存在，跳过: {}", path);
                continue;
            }

            String existingHash = calculateFileHash(file);
            if (!StringUtils.hasText(existingHash)) {
                continue;
            }

            Resource update = new Resource();
            update.setId(resource.getId());
            update.setResourceHash(existingHash);
            if (!StringUtils.hasText(resource.getStoreType())) {
                update.setStoreType(StoreEnum.LOCAL.getCode());
                resource.setStoreType(StoreEnum.LOCAL.getCode());
            }
            resourceService.updateById(update);
            resource.setResourceHash(existingHash);

            if (resourceHash.equals(existingHash)) {
                log.info("LocalUtil.saveFile 回填旧资源哈希后命中复用路径: {}", path);
                return resource;
            }
        }

        return null;
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
