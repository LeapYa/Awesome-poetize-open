package com.ld.poetry.config;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.poetry.entity.Resource;
import com.ld.poetry.service.ResourceService;
import com.ld.poetry.utils.storage.StoreEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 启动后后台自动补齐历史本地资源的内容哈希。
 */
@Component
@Order(39)
@Slf4j
public class ResourceHashWarmupRunner implements ApplicationRunner {

    private final ResourceService resourceService;

    @Value("${resource.hash.warmup.enabled:true}")
    private boolean warmupEnabled;

    @Value("${resource.hash.warmup.delay-seconds:15}")
    private int warmupDelaySeconds;

    @Value("${resource.hash.warmup.batch-size:100}")
    private int warmupBatchSize;

    @Value("${local.uploadUrl:}")
    private String localUploadUrl;

    @Value("${local.downloadUrl:}")
    private String localDownloadUrl;

    public ResourceHashWarmupRunner(ResourceService resourceService) {
        this.resourceService = resourceService;
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
                    .select(Resource::getId, Resource::getPath, Resource::getStoreType)
                    .gt(Resource::getId, lastId)
                    .isNull(Resource::getResourceHash)
                    .and(wrapper -> wrapper.eq(Resource::getStoreType, StoreEnum.LOCAL.getCode())
                            .or()
                            .isNull(Resource::getStoreType))
                    .isNotNull(Resource::getPath)
                    .orderByAsc(Resource::getId)
                    .page(page);

            List<Resource> records = page.getRecords();
            if (CollectionUtils.isEmpty(records)) {
                log.info("资源哈希后台补齐完成：写入 {} 条，跳过 {} 条", totalUpdated, totalSkipped);
                return;
            }

            for (Resource resource : records) {
                lastId = Math.max(lastId, resource.getId());
                String hash = calculateHash(resource.getPath());
                if (!StringUtils.hasText(hash)) {
                    totalSkipped++;
                    continue;
                }

                Resource update = new Resource();
                update.setId(resource.getId());
                update.setResourceHash(hash);
                if (!StringUtils.hasText(resource.getStoreType())) {
                    update.setStoreType(StoreEnum.LOCAL.getCode());
                }

                if (resourceService.updateById(update)) {
                    totalUpdated++;
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

    private void logDuplicateGroups() {
        List<Resource> resources = resourceService.lambdaQuery()
                .select(Resource::getResourceHash, Resource::getPath, Resource::getStoreType)
                .isNotNull(Resource::getResourceHash)
                .and(wrapper -> wrapper.eq(Resource::getStoreType, StoreEnum.LOCAL.getCode())
                        .or()
                        .isNull(Resource::getStoreType))
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

    private String calculateHash(String path) {
        String localFilePath = resolveLocalFilePath(path);
        if (!StringUtils.hasText(localFilePath)) {
            return null;
        }

        File file = new File(localFilePath);
        if (!file.exists() || !file.isFile()) {
            log.debug("资源哈希补齐跳过不存在的本地文件: {}", path);
            return null;
        }

        try (InputStream inputStream = new FileInputStream(file)) {
            return DigestUtils.sha256Hex(inputStream);
        } catch (Exception e) {
            log.debug("资源哈希补齐失败: path={}, err={}", path, e.getMessage());
            return null;
        }
    }

    private String resolveLocalFilePath(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }

        if (StringUtils.hasText(localDownloadUrl) && path.startsWith(localDownloadUrl) && StringUtils.hasText(localUploadUrl)) {
            return path.replace(localDownloadUrl, localUploadUrl).replace("/", File.separator);
        }

        if (path.startsWith("/") && StringUtils.hasText(localUploadUrl)) {
            String normalizedBase = localUploadUrl.endsWith("/") || localUploadUrl.endsWith("\\")
                    ? localUploadUrl
                    : localUploadUrl + File.separator;
            String relative = path.substring(1).replace("/", File.separator);
            return normalizedBase + relative;
        }

        return path;
    }
}
