package com.ld.poetry.service;

import com.ld.poetry.utils.storage.StorageRangeReadHandle;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LocalResourceFileService {

    private final ResourceAvailabilityService resourceAvailabilityService;

    @Value("${local.uploadUrl:/app/static/}")
    private String localUploadUrl;

    public Path resolveReadablePath(String resourcePath) throws IOException {
        return existingPaths(resourcePath).stream()
                .findFirst()
                // 文件缺失是永久状态，用 NoSuchFileException 让上层映射 404 而非 503
                .orElseThrow(() -> new NoSuchFileException(resourcePath, null, "未找到可读取的本地源文件"));
    }

    public StorageRangeReadHandle openRange(String resourcePath,
                                            long startInclusive,
                                            long endInclusive,
                                            long expectedTotalLength,
                                            String contentType) throws IOException {
        if (startInclusive < 0
                || endInclusive < startInclusive
                || expectedTotalLength <= endInclusive) {
            throw new IllegalArgumentException("本地资源读取区间不合法");
        }
        Path path = resolveReadablePath(resourcePath);
        FileChannel channel = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
        boolean success = false;
        try {
            long actualLength = channel.size();
            if (actualLength != expectedTotalLength) {
                throw new IOException("本地资源长度与已验证副本元数据不一致");
            }
            channel.position(startInclusive);
            long contentLength = endInclusive - startInclusive + 1;
            StorageRangeReadHandle handle = StorageRangeReadHandle.bounded(
                    Channels.newInputStream(channel),
                    contentLength,
                    actualLength,
                    contentType,
                    path.toUri()
            );
            success = true;
            return handle;
        } finally {
            if (!success) {
                channel.close();
            }
        }
    }

    public DeleteSummary deleteCopies(String resourcePath) {
        Path uploadRoot = normalizeUploadRoot();
        List<Path> paths = existingPaths(resourcePath).stream()
                .filter(path -> path.toAbsolutePath().normalize().startsWith(uploadRoot))
                .toList();
        int deleted = 0;
        int failed = 0;
        for (Path path : paths) {
            try {
                if (Files.deleteIfExists(path)) {
                    deleted++;
                }
            } catch (IOException e) {
                failed++;
            }
        }
        return new DeleteSummary(paths.size(), deleted, failed);
    }

    public boolean exists(String resourcePath) {
        return !existingPaths(resourcePath).isEmpty();
    }

    private List<Path> existingPaths(String resourcePath) {
        return resourceAvailabilityService.resolveSafeLocalResourcePaths(resourcePath).stream()
                .distinct()
                .toList();
    }

    private Path normalizeUploadRoot() {
        String uploadPath = localUploadUrl;
        if (uploadPath.startsWith("file:")) {
            uploadPath = uploadPath.substring("file:".length());
        }
        return Paths.get(uploadPath).toAbsolutePath().normalize();
    }

    public record DeleteSummary(int foundCount, int deletedCount, int failedCount) {
        public boolean fullyDeleted() {
            return foundCount > 0 && failedCount == 0 && deletedCount == foundCount;
        }
    }
}