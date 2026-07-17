package com.ld.poetry.config;

import com.ld.poetry.controller.dto.ResourceAdoptionRequest;
import com.ld.poetry.entity.ResourceAdoptionTask;
import com.ld.poetry.service.ResourceAdoptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动后后台自动接管未归一化的历史资源。
 *
 * <p>历史资源（path 非 /media/{publicId}）无法参与跨存储迁移。本 Runner 在应用启动后，
 * 仿照 {@link ResourceHashWarmupRunner} 的异步模式，延迟触发一次全量历史资源接管，
 * 使升级后的老资源自动具备迁移能力，无需管理员手动操作。
 *
 * <p>接管任务由 {@link ResourceAdoptionService#create} 统一编排，复用其断点恢复与线程池驱动能力，
 * 任务条目失败不影响整体流程。首次大库扫描可能耗时较长，通过延迟启动避免与启动期流量竞争。
 *
 * <p>可通过配置 {@code resource.adoption.startup.enabled=false} 关闭，
 * 通过 {@code resource.adoption.startup.delay-seconds} 调整延迟。
 */
@Component
@Order(40)
@Slf4j
public class ResourceAdoptionStartupRunner implements ApplicationRunner {

    private final ResourceAdoptionService adoptionService;

    @Value("${resource.adoption.startup.enabled:true}")
    private boolean startupEnabled;

    @Value("${resource.adoption.startup.delay-seconds:30}")
    private int startupDelaySeconds;

    public ResourceAdoptionStartupRunner(ResourceAdoptionService adoptionService) {
        this.adoptionService = adoptionService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!startupEnabled) {
            log.info("历史资源启动自动接管已禁用，跳过扫描");
            return;
        }

        Thread.ofVirtual().name("resource-adoption-startup").start(() -> {
            try {
                Thread.sleep(Math.max(0, startupDelaySeconds) * 1000L);
                triggerAdoption();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("历史资源启动自动接管任务被中断");
            } catch (Exception e) {
                log.warn("历史资源启动自动接管任务执行失败，不影响应用启动", e);
            }
        });
    }

    /**
     * 触发一次全量接管。sourceUrls 传空表示接管全部扫描到的历史资源。
     *
     * <p>若没有可接管资源（白名单业务字段无历史 URL，或已全部归一化），
     * {@link ResourceAdoptionService#create} 会抛出 IllegalArgumentException，
     * 此处静默跳过，视为无需接管。
     */
    private void triggerAdoption() {
        try {
            ResourceAdoptionTask task = adoptionService.create(
                    new ResourceAdoptionRequest(null),
                    0
            );
            log.info("历史资源启动自动接管任务已创建: taskId={}, totalCount={}",
                    task.getTaskId(), task.getTotalCount());
        } catch (IllegalArgumentException e) {
            // 无可接管候选属正常情况（新安装或已全部归一化）
            log.info("历史资源启动自动接管跳过: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("历史资源启动自动接管任务创建失败: {}", e.getMessage());
        }
    }
}
