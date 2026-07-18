package com.ld.poetry.config;

import com.ld.poetry.entity.Resource;
import com.ld.poetry.service.ResourceLocationService;
import com.ld.poetry.service.ResourceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动后自动归一化「半归一化」资源的 path。
 *
 * <p>部分资源（如 SQL 种子数据中的前端构建产物）已有 publicId + activeLocationId +
 * CURRENT_PATH 别名，但 path 仍是物理路径（/static/xxx），无法参与跨存储迁移。
 * 本 Runner 在 {@link ResourceAdoptionStartupRunner} 之后执行，扫描这类资源并调用
 * {@link ResourceLocationService#normalizeStablePath} 把 path 切换为 /media/{publicId}。
 *
 * <p>归一化是幂等的：已是 /media/ 路径的资源不会被处理，已有 CURRENT_PATH 别名
 * 不会重复插入。并发安全由 normalizeStablePath 内部的乐观锁保证。
 *
 * <p>可通过配置 {@code resource.normalization.startup.enabled=false} 关闭，
 * 通过 {@code resource.normalization.startup.delay-seconds} 调整延迟
 * （默认 60s，晚于接管任务的 30s，确保接管先完成）。
 */
@Component
@Order(41)
@Slf4j
public class ResourceStablePathNormalizationRunner implements ApplicationRunner {

    private final ResourceService resourceService;
    private final ResourceLocationService resourceLocationService;

    @Value("${resource.normalization.startup.enabled:true}")
    private boolean startupEnabled;

    @Value("${resource.normalization.startup.delay-seconds:60}")
    private int startupDelaySeconds;

    public ResourceStablePathNormalizationRunner(ResourceService resourceService,
                                                  ResourceLocationService resourceLocationService) {
        this.resourceService = resourceService;
        this.resourceLocationService = resourceLocationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!startupEnabled) {
            log.info("资源路径归一化已禁用，跳过扫描");
            return;
        }

        Thread.ofVirtual().name("resource-normalization-startup").start(() -> {
            try {
                Thread.sleep(Math.max(0, startupDelaySeconds) * 1000L);
                normalizeStablePaths();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("资源路径归一化任务被中断");
            } catch (Exception e) {
                log.warn("资源路径归一化任务执行失败，不影响应用启动", e);
            }
        });
    }

    /**
     * 扫描所有「有 publicId + 有 activeLocationId + path 非 /media/」的资源，
     * 逐个调用 normalizeStablePath 归一化。
     */
    private void normalizeStablePaths() {
        List<Resource> candidates = resourceService.lambdaQuery()
                .isNotNull(Resource::getPublicId)
                .ne(Resource::getPublicId, "")
                .isNotNull(Resource::getActiveLocationId)
                .isNotNull(Resource::getPath)
                .notLike(Resource::getPath, "/media/%")
                .list();

        if (candidates.isEmpty()) {
            log.info("资源路径归一化扫描完成：无需归一化的资源");
            return;
        }

        log.info("资源路径归一化扫描开始: 待归一化资源数={}", candidates.size());

        int success = 0;
        int failed = 0;
        for (Resource resource : candidates) {
            try {
                resourceLocationService.normalizeStablePath(resource.getId(), resource.getPath());
                success++;
            } catch (Exception e) {
                // 个别资源归一化失败不影响整体流程
                log.warn("资源路径归一化失败: resourceId={}, path={}, err={}",
                        resource.getId(), resource.getPath(), e.getMessage());
                failed++;
            }
        }

        log.info("资源路径归一化扫描完成: total={}, success={}, failed={}",
                candidates.size(), success, failed);
    }
}
