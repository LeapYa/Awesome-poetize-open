package com.ld.poetry.config;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.poetry.dao.ResourceLocationMapper;
import com.ld.poetry.entity.Resource;
import com.ld.poetry.entity.ResourceLocation;
import com.ld.poetry.service.ResourceService;
import com.ld.poetry.utils.storage.FileStorageService;
import com.ld.poetry.utils.storage.StoreEnum;
import com.ld.poetry.utils.storage.StoreService;
import com.ld.poetry.utils.storage.StorageReadHandle;
import com.ld.poetry.utils.storage.StorageResourceRef;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 启动后后台自动补齐历史本地资源的内容哈希与可信基线。
 *
 * <p>扫描 resource_hash 为空或 hash_verified_at 为空的本地资源，通过
 * resource_location.access_path 读取物理文件计算 SHA-256，同时回写
 * resource 和 resource_location 两表的 hash + verified_at + hash_source，
 * 建立 {@code ResourceMediaService} 所要求的完整可信基线。
 *
 * <p>归一化后 resource.path 是 /media/{publicId}，无法直接定位物理文件，
 * 因此必须用 resource_location.access_path（仍是 /static/xxx 物理路径）。
 * 文件读取复用 LocalUtil 的回退逻辑（上传目录找不到时回退到 web-dist/static）。
 *
 * <p>顺便修复 mimeType 为 application/octet-stream 的资源：从 originalName
 * 扩展名反推更准确的 MIME 类型（如 .jpg→image/jpeg, .png→image/png）。
 */
@Component
@Order(39)
@Slf4j
public class ResourceHashWarmupRunner implements ApplicationRunner {

    private static final long HASH_READ_MAX_BYTES = 256L * 1024 * 1024; // 256MB 上限

    private final ResourceService resourceService;
    private final ResourceLocationMapper resourceLocationMapper;
    private final FileStorageService fileStorageService;

    @Value("${resource.hash.warmup.enabled:true}")
    private boolean warmupEnabled;

    @Value("${resource.hash.warmup.delay-seconds:15}")
    private int warmupDelaySeconds;

    @Value("${resource.hash.warmup.batch-size:100}")
    private int warmupBatchSize;

    public ResourceHashWarmupRunner(ResourceService resourceService,
                                     ResourceLocationMapper resourceLocationMapper,
                                     FileStorageService fileStorageService) {
        this.resourceService = resourceService;
        this.resourceLocationMapper = resourceLocationMapper;
        this.fileStorageService = fileStorageService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!warmupEnabled) {
            log.info("资源哈希后台补齐已禁用，跳过历史资源扫描");
            return;
        }

        Thread.ofVirtual().name("resource-hash-warmup").start(() -> {
            try {
                Thread.sleep(Math.max(0, warmupDelaySeconds) * 1000L);
                warmupMissingHashes();
                logDuplicateGroups();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("资源哈希后台补齐任务被中断");
            } catch (Exception e) {
                log.warn("资源哈希后台补齐任务执行失败，不影响应用启动", e);
            }
        });
    }

    private void warmupMissingHashes() {
        int totalUpdated = 0;
        int totalSkipped = 0;
        int lastId = 0;

        while (true) {
            Page<Resource> page = new Page<>(1, Math.max(1, warmupBatchSize), false);
            resourceService.lambdaQuery()
                    .select(Resource::getId, Resource::getPath, Resource::getStoreType,
                            Resource::getResourceHash, Resource::getHashVerifiedAt,
                            Resource::getOriginalName, Resource::getMimeType,
                            Resource::getActiveLocationId)
                    .gt(Resource::getId, lastId)
                    .and(wrapper -> wrapper.isNull(Resource::getResourceHash)
                            .or().isNull(Resource::getHashVerifiedAt))
                    .and(wrapper -> wrapper.eq(Resource::getStoreType, StoreEnum.LOCAL.getCode())
                            .or().isNull(Resource::getStoreType))
                    .isNotNull(Resource::getActiveLocationId)
                    .orderByAsc(Resource::getId)
                    .page(page);

            List<Resource> records = page.getRecords();
            if (CollectionUtils.isEmpty(records)) {
                log.info("资源哈希后台补齐完成：写入 {} 条，跳过 {} 条", totalUpdated, totalSkipped);
                return;
            }

            for (Resource resource : records) {
                lastId = Math.max(lastId, resource.getId());
                if (warmupOne(resource)) {
                    totalUpdated++;
                } else {
                    totalSkipped++;
                }
            }

            if (records.size() < warmupBatchSize) {
                log.info("资源哈希后台补齐完成：写入 {} 条，跳过 {} 条", totalUpdated, totalSkipped);
                return;
            }

            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("资源哈希后台补齐任务在批次间隔时被中断");
                return;
            }
        }
    }

    /**
     * 补齐单个资源的 hash 可信基线。返回 true 表示已更新，false 表示跳过。
     */
    private boolean warmupOne(Resource resource) {
        ResourceLocation location = resolveActiveLocation(resource);
        if (location == null) {
            log.debug("资源哈希补齐跳过：无活动物理副本 resourceId={}", resource.getId());
            return false;
        }

        StoreService storeService = resolveStoreService(location.getStoreType());
        if (storeService == null) {
            log.debug("资源哈希补齐跳过：存储适配器不可用 resourceId={} storeType={}",
                    resource.getId(), location.getStoreType());
            return false;
        }

        StorageResourceRef ref = new StorageResourceRef(
                resource.getId(),
                location.getAccessPath(),
                location.getStorageKey(),
                resource.getOriginalName(),
                location.getSize(),
                location.getContentHash(),
                location.getMimeType() != null ? location.getMimeType() : resource.getMimeType()
        );

        String hash;
        long actualSize = -1;
        try (StorageReadHandle handle = storeService.openRead(ref, HASH_READ_MAX_BYTES)) {
            try (InputStream is = handle.inputStream()) {
                hash = DigestUtils.sha256Hex(is);
            }
            if (handle.contentLength() != null && handle.contentLength() > 0) {
                actualSize = handle.contentLength();
            }
        } catch (Exception e) {
            log.debug("资源哈希补齐失败: resourceId={}, accessPath={}, err={}",
                    resource.getId(), location.getAccessPath(), e.getMessage());
            return false;
        }

        if (!StringUtils.hasText(hash)) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        String hashSource = "LEGACY_EXISTING";

        // 更新 resource 表：hash + verified_at + hash_source + storeType 兜底 + mimeType 修正
        Resource update = new Resource();
        update.setId(resource.getId());
        update.setResourceHash(hash);
        update.setHashVerifiedAt(now);
        update.setHashSource(hashSource);
        if (!StringUtils.hasText(resource.getStoreType())) {
            update.setStoreType(StoreEnum.LOCAL.getCode());
        }
        String correctedMime = correctMimeIfNecessary(resource.getMimeType(), resource.getOriginalName());
        if (correctedMime != null) {
            update.setMimeType(correctedMime);
        }
        resourceService.updateById(update);

        // 更新 resource_location 表：content_hash + verified_at + mimeType 修正 + size 兜底
        ResourceLocation locUpdate = new ResourceLocation();
        locUpdate.setId(location.getId());
        locUpdate.setContentHash(hash);
        locUpdate.setVerifiedAt(now);
        String locCorrectedMime = correctMimeIfNecessary(location.getMimeType(), resource.getOriginalName());
        if (locCorrectedMime != null) {
            locUpdate.setMimeType(locCorrectedMime);
        }
        if (actualSize > 0 && (location.getSize() == null || location.getSize() <= 0)) {
            locUpdate.setSize(actualSize);
        }
        resourceLocationMapper.updateById(locUpdate);

        return true;
    }

    private ResourceLocation resolveActiveLocation(Resource resource) {
        if (resource.getActiveLocationId() == null) {
            return null;
        }
        List<ResourceLocation> locations = resourceLocationMapper.findByResourceId(resource.getId());
        for (ResourceLocation loc : locations) {
            if (resource.getActiveLocationId().equals(loc.getId())) {
                return loc;
            }
        }
        // 兜底：取第一个 ACTIVE
        for (ResourceLocation loc : locations) {
            if ("ACTIVE".equals(loc.getStatus())) {
                return loc;
            }
        }
        return null;
    }

    private StoreService resolveStoreService(String storeType) {
        String type = StringUtils.hasText(storeType) ? storeType : StoreEnum.LOCAL.getCode();
        try {
            StoreService service = fileStorageService.getFileStorageByStoreType(type);
            if (!service.getCapability().enabled() || !service.getCapability().readSupported()) {
                return null;
            }
            return service;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 当 mimeType 为空或 application/octet-stream 时，从 originalName 扩展名反推更准确的 MIME。
     * 返回 null 表示无需修正。
     */
    private String correctMimeIfNecessary(String currentMime, String originalName) {
        boolean needsCorrection = !StringUtils.hasText(currentMime)
                || "application/octet-stream".equalsIgnoreCase(currentMime.trim());
        if (!needsCorrection) {
            return null;
        }
        return guessMimeTypeFromName(originalName);
    }

    private String guessMimeTypeFromName(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".bmp")) return "image/bmp";
        if (lower.endsWith(".ico")) return "image/x-icon";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".woff")) return "font/woff";
        if (lower.endsWith(".woff2")) return "font/woff2";
        if (lower.endsWith(".ttf")) return "font/ttf";
        if (lower.endsWith(".otf")) return "font/otf";
        if (lower.endsWith(".eot")) return "application/vnd.ms-fontobject";
        return null;
    }

    private void logDuplicateGroups() {
        List<Resource> resources = resourceService.lambdaQuery()
                .select(Resource::getResourceHash, Resource::getPath, Resource::getStoreType)
                .isNotNull(Resource::getResourceHash)
                .and(wrapper -> wrapper.eq(Resource::getStoreType, StoreEnum.LOCAL.getCode())
                        .or().isNull(Resource::getStoreType))
                .list();

        if (CollectionUtils.isEmpty(resources)) {
            return;
        }

        Map<String, Integer> countByHash = new HashMap<>();
        for (Resource resource : resources) {
            countByHash.merge(resource.getResourceHash(), 1, Integer::sum);
        }

        long duplicateGroups = countByHash.values().stream()
                .filter(count -> count > 1)
                .count();
        int duplicateRecords = countByHash.values().stream()
                .filter(count -> count > 1)
                .mapToInt(count -> count - 1)
                .sum();

        if (duplicateGroups > 0) {
            log.info("检测到历史重复本地资源：{} 组，{} 条可作为重复副本。为避免破坏旧引用，暂不自动删除。",
                    duplicateGroups, duplicateRecords);
        }
    }
}
